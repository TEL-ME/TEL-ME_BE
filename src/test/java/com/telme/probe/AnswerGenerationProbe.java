package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.service.AnswerGuard;
import com.telme.rag.service.AnswerPromptTemplates;
import com.telme.rag.service.EvidenceRelevanceChecker;
import com.telme.rag.service.RagAnswerGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

/**
 * TELME-178 답변 생성 모델 비교. 저장해 둔 답변 생성 직전 입력을 엑사원(Ollama)과 gpt-oss(Bedrock)에 똑같이 넣어
 * 실제 {@link RagAnswerGenerator}(프롬프트, 입력 예산, AnswerGuard)를 거친 답변과 가드 전 원본 답변을 저장한다.
 * 앱·DB는 띄우지 않고 앱의 모델 설정도 바꾸지 않는다. 품질 채점은 하지 않는다.
 *
 * 입력(JSON 배열): [{"id","userQuery","resolvedQuery"(선택),"conditions"{키:값},"conditionLabels"{키:되물은 질문},
 *   "searchResults"[{"faqId","question","answer","slotId","category","score","version","matchedVariant"}],
 *   "history"[{"role":"USER|ASSISTANT","content"}](선택),"summary"(선택)}]. 샘플은 scripts/answer-generation-evaluation/sample-inputs.json.
 * 실행: ANSWER_PROBE=true ANSWER_PROBE_INPUT=<입력 json> ANSWER_PROBE_OUT=<결과 jsonl>
 *       [ANSWER_PROBE_MODELS=exaone,gptoss] [ANSWER_PROBE_EXAONE_REPEATS=1] [ANSWER_PROBE_GPTOSS_REPEATS=3]
 *       [ANSWER_PROBE_OLLAMA_URL=http://127.0.0.1:11434] [ANSWER_PROBE_OLLAMA_MODEL=exaone3.5:7.8b]
 *       [LLM_BEDROCK_REGION=ap-northeast-1] [ANSWER_PROBE_MAX_TOKENS=1000] [ANSWER_PROBE_TIMEOUT_SECONDS=60]
 *       ./gradlew test --tests '*AnswerGenerationProbe'
 * 모델 이름 fake는 호출 없이 입력의 첫 FAQ 답변을 그대로 돌려주는 점검용이다(비용 없음).
 * Bedrock 자격 증명은 AWS 기본 자격 증명을 쓴다. Bedrock API 키는 환경 변수 AWS_BEARER_TOKEN_BEDROCK로 준다.
 */
@EnabledIfEnvironmentVariable(named = "ANSWER_PROBE", matches = "true")
class AnswerGenerationProbe {
    private static final long INPUT_MESSAGE_ID = 9_999L;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generateAnswers() throws Exception {
        JsonNode inputs = mapper.readTree(Files.readString(Path.of(required("ANSWER_PROBE_INPUT"))));
        List<String> models = list("ANSWER_PROBE_MODELS", List.of("exaone", "gptoss"));
        Map<String, ExecutorService> pools = new LinkedHashMap<>();
        List<Future<Map<String, Object>>> rows = new ArrayList<>();
        for (String model : models) {
            ExecutorService pool = Executors.newFixedThreadPool("exaone".equals(model) ? 1 : 4);
            pools.put(model, pool);
            int repeats = Integer.parseInt(System.getenv().getOrDefault(
                    "ANSWER_PROBE_" + model.toUpperCase() + "_REPEATS", "gptoss".equals(model) ? "3" : "1"));
            for (int run = 1; run <= repeats; run++) {
                for (JsonNode input : inputs) {
                    int current = run;
                    rows.add(pool.submit(() -> generate(model, current, input)));
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (Future<Map<String, Object>> row : rows) {
            lines.add(mapper.writeValueAsString(row.get()));
        }
        pools.values().forEach(ExecutorService::shutdown);
        Path out = Path.of(required("ANSWER_PROBE_OUT"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, lines);
    }

    private Map<String, Object> generate(String model, int run, JsonNode input) {
        List<Map<String, Object>> calls = new ArrayList<>();
        CapturingClient client = new CapturingClient(clientFor(model, calls, input));
        var generator = new RagAnswerGenerator(client, new AnswerContextConverter(), new AnswerGuard(),
                new EvidenceRelevanceChecker(client, mapper, new EvidenceCheckProperties(false, 300)),
                Mockito.mock(LlmGenerationRecorder.class), ExecutionTrace.noop());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", input.path("id").asText());
        row.put("model", model);
        row.put("run", run);
        row.put("userQuery", input.path("userQuery").asText());
        // 입력에 붙은 평가 정보(출처, 정답 FAQ, 위험 설명)를 그대로 남겨 나중에 채점할 때 쓴다
        row.put("meta", mapper.convertValue(input.path("meta"), Object.class));
        try {
            AnswerResult result = generator.generate(request(input), new LlmStreamHandler() {
                @Override
                public void onToken(String token) {
                }

                @Override
                public void onComplete() {
                }

                @Override
                public void onError(Throwable error) {
                }
            });
            String raw = client.raw();
            row.put("answer", result.answer());
            row.put("answerBasis", String.valueOf(result.answerBasis()));
            row.put("rawAnswer", raw);
            // 근거가 없으면 모델을 부르지 않고 고정 문구로 답한다
            boolean called = !raw.isBlank();
            row.put("guardOutcome", called ? guardOutcome(raw, result.answer()) : "NO_CALL");
            row.put("truncationSuspected", called && truncationSuspected(raw, calls));
        } catch (RuntimeException failure) {
            row.put("error", failure.toString());
            row.put("rawAnswer", client.raw());
        }
        row.put("calls", calls);
        return row;
    }

    // 가드가 답변을 바꿨는지. RagAnswerGenerator의 가드 기록과 같은 기준이다
    private static String guardOutcome(String raw, String finalAnswer) {
        if (finalAnswer.equals(raw.strip())) {
            return "KEPT";
        }
        return finalAnswer.contains(AnswerPromptTemplates.NO_EVIDENCE_MESSAGE) ? "REPLACED" : "MODIFIED";
    }

    // Bedrock은 종료 사유가 max_tokens이면, 엑사원은 종료 사유를 알 수 없어 문장이 끝나지 않으면 잘림 의심으로 본다
    private static boolean truncationSuspected(String raw, List<Map<String, Object>> calls) {
        for (Map<String, Object> call : calls) {
            if ("max_tokens".equals(call.get("stopReason"))) {
                return true;
            }
        }
        String text = raw == null ? "" : raw.strip();
        return text.isEmpty() || !text.matches("(?s).*[.!?)\"'。]$|(?s).*[요다죠니까][.!?]?$");
    }

    private AnswerRequest request(JsonNode input) {
        List<FaqSearchResponse> results = new ArrayList<>();
        int rank = 1;
        for (JsonNode faq : input.path("searchResults")) {
            results.add(new FaqSearchResponse(faq.path("faqId").asLong(rank), text(faq, "slotId"), text(faq, "category"),
                    faq.path("question").asText(), faq.path("answer").asText(), faq.path("score").asDouble(0.9),
                    faq.path("version").asInt(1), null, rank, text(faq, "matchedVariant")));
            rank++;
        }
        String question = input.path("userQuery").asText();
        return AnswerRequest.builder().userQuery(question)
                .resolvedQuery(text(input, "resolvedQuery"))
                .conditions(map(input.path("conditions"))).conditionLabels(map(input.path("conditionLabels")))
                .searchResults(results).chatContext(context(input, question)).build();
    }

    private ChatContext context(JsonNode input, String question) {
        JsonNode history = input.path("history");
        String summary = text(input, "summary");
        if (!history.isArray() && summary == null) {
            return null;
        }
        List<ChatContextMessage> messages = new ArrayList<>();
        int sequence = 1;
        for (JsonNode message : history) {
            boolean user = !"ASSISTANT".equalsIgnoreCase(message.path("role").asText("USER"));
            messages.add(new ChatContextMessage(100L + sequence, sequence,
                    user ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT,
                    user ? ChatMessage.MessageType.QUESTION : ChatMessage.MessageType.ANSWER,
                    message.path("content").asText(), null));
            sequence++;
        }
        return new ChatContext(1L, INPUT_MESSAGE_ID, summary, messages, question, 100);
    }

    private LlmClient clientFor(String model, List<Map<String, Object>> calls, JsonNode input) {
        return switch (model) {
            case "exaone", "gptoss" -> ProbeModelClients.create(model, mapper, calls, false);
            case "fake" -> new FakeClient(input.path("searchResults").path(0).path("answer").asText("확인할 수 없습니다."));
            default -> throw new IllegalArgumentException("알 수 없는 모델: " + model);
        };
    }

    private static Map<String, String> map(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다");
        }
        return value;
    }

    private static List<String> list(String name, List<String> fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : List.of(value.split(","));
    }

    /** 가드 전 원본 답변을 모아 둔다. RagAnswerGenerator는 토큰을 모아 가드를 거친 뒤에 내보낸다. */
    private static final class CapturingClient implements LlmClient {
        private final LlmClient base;
        private final StringBuilder raw = new StringBuilder();

        private CapturingClient(LlmClient base) {
            this.base = base;
        }

        @Override
        public String generate(LlmRequest request) {
            return base.generate(request);
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            raw.setLength(0);
            base.stream(request, new LlmStreamHandler() {
                @Override
                public void onToken(String token) {
                    raw.append(token);
                    handler.onToken(token);
                }

                @Override
                public void onComplete() {
                    handler.onComplete();
                }

                @Override
                public void onError(Throwable error) {
                    handler.onError(error);
                }

                @Override
                public void onProgress() {
                    handler.onProgress();
                }

                @Override
                public void onRetry(int attempt, Throwable cause) {
                    raw.setLength(0);
                    handler.onRetry(attempt, cause);
                }
            });
        }

        private String raw() {
            return raw.toString();
        }
    }

    /** 호출 없이 첫 FAQ 답변을 그대로 돌려주는 점검용 모델이다. */
    private record FakeClient(String answer) implements LlmClient {
        @Override
        public String generate(LlmRequest request) {
            return answer;
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onToken(answer);
            handler.onComplete();
        }
    }
}
