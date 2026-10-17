package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.EvidenceResolverConfiguration;
import com.telme.consult.service.CandidateSubjectRule;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver.Options;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.FaqEvidenceJudge;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 후보 근거 검증 비교. 캡처한 실제 입력(faqCandidateEvidenceInputs의 문장과 인용문 재시도 허용, searchResults의
 * FAQ_CANDIDATE 후보, 고객 원문)을 앱의 후보 근거 검증에 그대로 넣고 판정 방식·모델·회차를 모두 동시에 돌린다.
 * 앱의 핵심 단어 검사와 인용문 재시도도 판정 방식에 따라 그대로 탄다. gpt-oss는 엑사원 대체 없이 Bedrock만 부른다.
 * 판정 방식(CANDIDATE_REPLAY_VARIANTS, 쉼표로 여럿, 기본 P0):
 *   P0 지금 방식(단어 검사, 하위 질문만) / P1 단어 검사 없음 / P2 P1 + 고객 원문 /
 *   P3 P2 + 고른 FAQ를 회색 구간 판정기(v4)로 하위 질문만 다시 판정 / P4 P2 + 같은 판정에 고객 원문을 함께 줌 /
 *   P5 P4 + 고객 원문으로도 후보를 검색해 하위 질문 후보 뒤에 붙임(앱 검색을 부르므로 측정 DB가 필요하다, POSTGRES_PORT=5440)
 *   (P6 "모델이 다시 쓴 하위 질문만 판정기에 줌"은 TELME-184에서 재 본 뒤 효과가 흔들림 범위라 코드에서 뺐다) /
 *   P7 P4 + 대상 낱말 규칙(CandidateSubjectRule)을 판정기 앞에 둠. 운영의 candidate-judge 설정과 같다
 * 모델(CANDIDATE_REPLAY_MODELS, 기본 exaone:1,gptoss:3)은 모델:회차 수다. CANDIDATE_REPLAY_LIMIT을 주면 앞에서 그 수만큼만 돈다.
 * 응답 시간은 호출마다(callMs: 고르기와 인용문 재시도, judgeMs: 판정기) 남긴다. 시간을 잴 때는 방식·모델·회차를 하나만 준다.
 * 실행: CANDIDATE_REPLAY_PROBE=true CANDIDATE_REPLAY_INPUT=<입력 json> CANDIDATE_REPLAY_OUT=<결과 jsonl> LLM_PROVIDER=ollama
 *       LLM_BEDROCK_ENABLED=true AWS_PROFILE=... ./gradlew test --tests '*CandidateEvidenceReplayProbe'
 */
@SpringBootTest(properties = "llm.provider=ollama")
@EnabledIfEnvironmentVariable(named = "CANDIDATE_REPLAY_PROBE", matches = "true")
class CandidateEvidenceReplayProbe {

    @Autowired LlmClient exaone;
    @Autowired @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) LlmClient bedrock;
    @Autowired FaqEvidenceJudge judge;
    @Autowired ObjectMapper mapper;
    @Autowired FaqSearchService searches;

    @Test
    void 같은_입력으로_판정_방식과_모델을_비교한다() throws Exception {
        JsonNode all = mapper.readTree(new File(System.getenv("CANDIDATE_REPLAY_INPUT")));
        int limit = Integer.parseInt(System.getenv().getOrDefault("CANDIDATE_REPLAY_LIMIT", "0"));
        var limited = mapper.createArrayNode();
        for (JsonNode input : all) {
            if (limit > 0 && limited.size() >= limit) {
                break;
            }
            limited.add(input);
        }
        JsonNode inputs = limited;
        Path out = Path.of(System.getenv("CANDIDATE_REPLAY_OUT"));
        List<String> variants = Arrays.asList(System.getenv().getOrDefault("CANDIDATE_REPLAY_VARIANTS", "P0").split(","));
        List<Map<String, Object>> runs = new ArrayList<>();
        for (String variant : variants) {
            for (String model : System.getenv().getOrDefault("CANDIDATE_REPLAY_MODELS", "exaone:1,gptoss:3").split(",")) {
                String[] parts = model.split(":");
                for (int run = 1; run <= Integer.parseInt(parts[1]); run++) {
                    runs.add(Map.of("variant", variant.strip(), "model", parts[0], "run", run));
                }
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(runs.size());
        List<Future<List<String>>> futures = new ArrayList<>();
        for (Map<String, Object> run : runs) {
            LlmClient client = "exaone".equals(run.get("model")) ? exaone : bedrock;
            futures.add(pool.submit(() -> replay(inputs, client, run)));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            lines.addAll(future.get());
        }
        pool.shutdown();
        Files.write(out, lines);
        System.out.println("CANDIDATE_REPLAY|" + lines.size() + " rows");
    }

    private Options options(String variant, List<String> verdicts) {
        LlmFaqCandidateEvidenceResolver.CandidateVerifier partOnly = (executionId, consultRequestId, question,
                customerMessage, candidate) -> verify(verdicts, question, candidate);
        LlmFaqCandidateEvidenceResolver.CandidateVerifier withMessage = (executionId, consultRequestId, question,
                customerMessage, candidate) -> verify(verdicts,
                        EvidenceResolverConfiguration.judgeQuestion(question, customerMessage), candidate);
        return switch (variant) {
            case "P0" -> Options.CURRENT;
            case "P1" -> new Options(false, false, null);
            case "P2" -> new Options(false, true, null);
            case "P3" -> new Options(false, true, partOnly);
            case "P4", "P5" -> new Options(false, true, withMessage);
            case "P7" -> new Options(false, true, (executionId, consultRequestId, question, customerMessage, candidate) -> {
                if (!CandidateSubjectRule.passes(question, customerMessage, candidate.question(), candidate.answer())) {
                    verdicts.add("SUBJECT_RULE_REJECTED:0");
                    return false;
                }
                return withMessage.answers(executionId, consultRequestId, question, customerMessage, candidate);
            });
            default -> throw new IllegalArgumentException(variant);
        };
    }

    private boolean verify(List<String> verdicts, String question, FaqSearchResponse candidate) {
        long startedAt = System.nanoTime();
        FaqEvidenceJudge.Verdict verdict = judge.judge(null, null, question, candidate.question(), candidate.answer());
        verdicts.add(verdict.name() + ":" + (System.nanoTime() - startedAt) / 1_000_000);
        return verdict == FaqEvidenceJudge.Verdict.ANSWERABLE;
    }

    private List<String> replay(JsonNode inputs, LlmClient client, Map<String, Object> run) throws Exception {
        Recording recording = new Recording(client);
        List<String> verdicts = new ArrayList<>();
        LlmFaqCandidateEvidenceResolver resolver =
                new LlmFaqCandidateEvidenceResolver(recording, mapper, options((String) run.get("variant"), verdicts));
        List<String> lines = new ArrayList<>();
        for (JsonNode input : inputs) {
            List<FaqSearchResponse> candidates = new ArrayList<>();
            for (JsonNode candidate : input.path("candidates")) {
                candidates.add(mapper.treeToValue(candidate, FaqSearchResponse.class));
            }
            String message = input.path("customerQuestion").isTextual() ? input.path("customerQuestion").asText() : null;
            if ("P5".equals(run.get("variant")) && message != null && !message.equals(input.path("question").asText())) {
                candidates = withMessageCandidates(candidates, message);
            }
            recording.reset();
            verdicts.clear();
            long startedAt = System.currentTimeMillis();
            String failure = null;
            FaqSearchResponse picked = null;
            try {
                String customerMessage = input.path("customerQuestion").isTextual()
                        ? input.path("customerQuestion").asText() : null;
                picked = resolver.resolve(null, null, input.path("question").asText(), customerMessage, candidates,
                        input.path("allowQuoteRepairFailure").asBoolean());
            } catch (RuntimeException exception) {
                failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            Map<String, Object> row = new LinkedHashMap<>(run);
            row.put("id", input.path("id").asText());
            row.put("pickedFaqId", picked == null ? null : picked.faqId());
            row.put("pickedSlotId", picked == null ? null : picked.slotId());
            row.put("candidateFaqIds", candidates.stream().map(FaqSearchResponse::faqId).toList());
            // P5가 고객 원문으로 더 붙인 후보. 채점에서 정답 FAQ(slotId)와 대조하려고 문답까지 남긴다
            row.put("addedCandidates", candidates.subList(input.path("candidates").size(), candidates.size()));
            row.put("calls", recording.raws.size());
            row.put("raws", recording.raws);
            row.put("verdicts", verdicts.stream().map(v -> v.substring(0, v.indexOf(':'))).toList());
            row.put("judgeMs", verdicts.stream().map(v -> Long.parseLong(v.substring(v.indexOf(':') + 1))).toList());
            row.put("callMs", recording.callMs);
            row.put("errors", recording.errors);
            row.put("failure", failure);
            row.put("ms", System.currentTimeMillis() - startedAt);
            lines.add(mapper.writeValueAsString(row));
        }
        return lines;
    }

    // 앱의 FAQ_CANDIDATE 검색과 같은 방식(임계값 없는 후보 10개)으로 고객 원문을 검색해, 하위 질문 후보에 없는 것만 뒤에 붙인다
    private List<FaqSearchResponse> withMessageCandidates(List<FaqSearchResponse> candidates, String message) {
        List<FaqSearchResponse> merged = new ArrayList<>(candidates);
        var seen = new java.util.HashSet<Long>();
        candidates.forEach(candidate -> seen.add(candidate.faqId()));
        for (FaqSearchResponse found : searches.searchCandidates(new FaqSearchRequest(message, 10))) {
            if (seen.add(found.faqId())) {
                merged.add(found);
            }
        }
        return merged;
    }

    private static final class Recording implements LlmClient {
        private final LlmClient delegate;
        List<String> raws = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Long> callMs = new ArrayList<>();

        Recording(LlmClient delegate) {
            this.delegate = delegate;
        }

        void reset() {
            raws = new ArrayList<>();
            errors = new ArrayList<>();
            callMs = new ArrayList<>();
        }

        @Override
        public String generate(LlmRequest request) {
            long startedAt = System.nanoTime();
            try {
                String raw = delegate.generate(request);
                raws.add(raw);
                return raw;
            } catch (RuntimeException exception) {
                // GeneralException은 메시지가 비어 있어 오류 코드를 함께 남긴다
                String code = exception instanceof com.telme.global.common.exception.GeneralException general
                        ? " [" + general.getErrorCode() + "]" : "";
                errors.add(exception.getClass().getSimpleName() + code + ": " + exception.getMessage());
                throw exception;
            } finally {
                callMs.add((System.nanoTime() - startedAt) / 1_000_000);
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
