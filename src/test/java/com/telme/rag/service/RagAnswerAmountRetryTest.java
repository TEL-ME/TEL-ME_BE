package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// 가드가 근거에 없는 금액으로 막은 답을 추론 강도 medium으로 한 번 다시 생성한다(TELME-205)
class RagAnswerAmountRetryTest {

    private static final String EVIDENCE = "7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.";

    private final Handler handler = new Handler();
    private final Trace trace = new Trace();

    @Test
    @DisplayName("금액으로 막히면 추론 강도 medium으로 다시 불러 통과한 답을 보낸다")
    void 금액으로_막히면_다시_생성한다() {
        Sequence client = new Sequence("재발급 비용은 7,800원이 아니라 7,700원입니다.", "유심 재발급 비용은 7,700원입니다.");

        AnswerResult result = generator(client, client).generate(request(), handler);

        assertThat(result.answer()).isEqualTo("유심 재발급 비용은 7,700원입니다.");
        assertThat(client.requests).hasSize(2);
        assertThat(client.requests.get(0).reasoningEffort()).isNull();
        assertThat(client.requests.get(1).reasoningEffort()).isEqualTo("medium");
        assertThat(handler.tokens).containsExactly("유심 재발급 비용은 7,700원입니다.");
        assertThat(handler.completed).isTrue();
        assertThat(handler.error).isNull();
        assertThat(trace.retries).singleElement().satisfies(retry ->
                assertThat(retry).containsEntry("outcome", "PASSED").containsEntry("reasoningEffort", "medium"));
    }

    @Test
    @DisplayName("다시 생성도 막히면 지금처럼 안전 안내로 끝낸다")
    void 다시도_막히면_안전_안내() {
        Sequence client = new Sequence("유심 재발급 비용은 7,800원입니다.", "유심 재발급 비용은 7,800원입니다.");

        AnswerResult result = generator(client, client).generate(request(), handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(client.requests).hasSize(2);
        assertThat(handler.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(handler.error).isNull();
        assertThat(trace.retries).singleElement().satisfies(retry ->
                assertThat(retry).containsEntry("outcome", "BLOCKED"));
    }

    @Test
    @DisplayName("다시 생성이 실패하면 오류를 알리지 않고 안전 안내로 끝낸다")
    void 다시_생성이_실패하면_안전_안내() {
        Sequence client = new Sequence("유심 재발급 비용은 7,800원입니다.", null);

        AnswerResult result = generator(client, client).generate(request(), handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(handler.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(handler.completed).isTrue();
        assertThat(handler.error).isNull();
        assertThat(trace.retries).singleElement().satisfies(retry ->
                assertThat(retry).containsEntry("outcome", "FAILED"));
    }

    @Test
    @DisplayName("설정이 꺼져 있으면 다시 부르지 않는다")
    void 꺼져_있으면_다시_부르지_않는다() {
        Sequence client = new Sequence("유심 재발급 비용은 7,800원입니다.", "유심 재발급 비용은 7,700원입니다.");

        AnswerResult result = generator(client, null).generate(request(), handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(client.requests).hasSize(1);
        assertThat(trace.retries).isEmpty();
    }

    @Test
    @DisplayName("금액이 아닌 수치로 막히면 다시 부르지 않는다")
    void 수치로_막히면_다시_부르지_않는다() {
        Sequence client = new Sequence("택배로 5일 걸립니다.", "택배로 2~3 영업일 걸립니다.");

        AnswerResult result = generator(client, client).generate(request("택배로 받으면 얼마나 걸려요"), handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(client.requests).hasSize(1);
        assertThat(trace.retries).isEmpty();
    }

    @Test
    @DisplayName("막히지 않은 답은 한 번만 부르고 추론 강도를 정하지 않는다")
    void 막히지_않으면_한_번만_부른다() {
        Sequence client = new Sequence("유심 재발급 비용은 7,700원입니다.");

        AnswerResult result = generator(client, client).generate(request(), handler);

        assertThat(result.answer()).isEqualTo("유심 재발급 비용은 7,700원입니다.");
        assertThat(client.requests).singleElement().satisfies(request ->
                assertThat(request.reasoningEffort()).isNull());
        assertThat(trace.retries).isEmpty();
    }

    @Test
    @DisplayName("다시 생성은 gpt-oss만 부르고 실패해도 엑사원으로 넘기지 않는다")
    void 다시_생성은_엑사원으로_넘기지_않는다() {
        Sequence bedrock = new Sequence("유심 재발급 비용은 7,800원입니다.", null);
        Sequence exaone = new Sequence("유심 재발급 비용은 7,700원입니다.");

        AnswerResult result = generator(new FallbackLlmClient(bedrock, exaone), bedrock).generate(request(), handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(bedrock.requests).hasSize(2);
        assertThat(exaone.requests).isEmpty();
        assertThat(handler.error).isNull();
        assertThat(trace.retries).singleElement().satisfies(retry ->
                assertThat(retry).containsEntry("outcome", "FAILED"));
    }

    private RagAnswerGenerator generator(LlmClient client, LlmClient amountRetryClient) {
        EvidenceRelevanceChecker checker = new EvidenceRelevanceChecker(
                client, new ObjectMapper(), new EvidenceCheckProperties(false, 300));
        return new RagAnswerGenerator(client, new AnswerContextConverter(), new AnswerGuard(), checker,
                Mockito.mock(LlmGenerationRecorder.class), trace, amountRetryClient);
    }

    private AnswerRequest request() {
        return request("유심 재발급 비용");
    }

    private AnswerRequest request(String userQuery) {
        return AnswerRequest.builder()
                .executionId(7L)
                .userQuery(userQuery)
                .conditions(Map.of())
                .searchResults(List.of(new FaqSearchResponse(1L, null, "USIM", "유심 재발급 비용이 얼마예요?",
                        EVIDENCE, 0.95, 1, LocalDate.of(2026, 10, 20), 1, null)))
                .build();
    }

    // 부를 때마다 다음 답을 한 번에 보낸다(Bedrock처럼). 답이 null이면 첫 글자 전에 실패한다
    private static final class Sequence implements LlmClient {
        private final List<String> answers;
        private final List<LlmRequest> requests = new ArrayList<>();

        private Sequence(String... answers) {
            this.answers = new ArrayList<>(java.util.Arrays.asList(answers));
        }

        @Override
        public String generate(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            requests.add(request);
            String answer = answers.get(requests.size() - 1);
            if (answer == null) {
                handler.onError(new IllegalStateException("Bedrock 호출 실패"));
                return;
            }
            handler.onToken(answer);
            handler.onComplete();
        }
    }

    private static final class Handler implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private boolean completed;
        private Throwable error;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }

    private static final class Trace implements ExecutionTrace {
        private final List<Map<String, Object>> retries = new ArrayList<>();

        @Override
        public void stage(Long executionId, String stage, Object value) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public void append(Long executionId, String stage, Object value) {
            if ("answerRetries".equals(stage)) {
                retries.add((Map<String, Object>) value);
            }
        }
    }
}
