package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.repository.LlmGenerationRepository;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagAnswerGeneratorTest {

    private final RecordingHandler handler = new RecordingHandler();
    private final SpyRecorder recorder = new SpyRecorder();

    @Test
    @DisplayName("검색 결과가 없으면 LLM을 부르지 않고 안내 문구를 반환한다")
    void 근거가_없으면_LLM을_부르지_않는다() {
        StubClient client = new StubClient(List.of("쓰이지", "않음"));
        RagAnswerGenerator generator = generator(client);

        AnswerResult result = generator.generate(request(List.of()), handler);

        assertThat(client.called).isFalse();
        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
        assertThat(handler.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(handler.completed).isTrue();
        assertThat(recorder.statuses).containsExactly(Status.NO_EVIDENCE);
    }

    @Test
    @DisplayName("토큰을 handler로 보내면서 최종 답변을 모아 반환한다")
    void 토큰을_모아_답변을_만든다() {
        RagAnswerGenerator generator = generator(new StubClient(List.of("요금제는 ", "월 1회 ", "변경됩니다.")));

        AnswerResult result = generator.generate(request(List.of(faq(1L))), handler);

        assertThat(result.answer()).isEqualTo("요금제는 월 1회 변경됩니다.");
        assertThat(handler.tokens).containsExactly("요금제는 ", "월 1회 ", "변경됩니다.");
        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("전달한 검색 결과를 근거로 담아 반환한다")
    void 검색_결과를_근거로_담는다() {
        RagAnswerGenerator generator = generator(new StubClient(List.of("답변")));

        AnswerResult result = generator.generate(request(List.of(faq(1L), faq(2L))), handler);

        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources()).extracting(AnswerResult.AnswerSource::faqId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("RAG_ANSWER 작업 유형과 executionId를 담아 호출한다")
    void 요청을_조립한다() {
        StubClient client = new StubClient(List.of("답변"));
        RagAnswerGenerator generator = generator(client);

        generator.generate(request(List.of(faq(1L))), handler);

        LlmRequest sent = client.received;
        assertThat(sent.taskType()).isEqualTo(TaskType.RAG_ANSWER);
        assertThat(sent.executionId()).isEqualTo(42L);
        assertThat(sent.systemPrompt()).isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
        assertThat(sent.userPrompt()).contains("[1] Q: 질문1").contains("강남").contains("요금제 바꾸고 싶어요");
        assertThat(sent.contextCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("토큰 없이 완료되면 INVALID_RESPONSE로 실패시킨다")
    void 빈_답변은_실패로_처리한다() {
        LlmClient empty = new LlmClient() {
            @Override
            public String generate(LlmRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stream(LlmRequest request, LlmStreamHandler handler) {
                handler.onComplete();
            }
        };

        assertThatThrownBy(() -> generator(empty).generate(request(List.of(faq(1L))), handler))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("사용자 질문이 비어 있으면 요청을 만들 수 없다")
    void 빈_질문은_거부한다() {
        assertThatThrownBy(() -> AnswerRequest.builder()
                .userQuery(" ")
                .searchResults(List.of(faq(1L)))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성에 실패하면 onError로 알리고 예외를 던진다")
    void 실패하면_예외를_던진다() {
        RagAnswerGenerator generator = generator(
                new StubClient(new GeneralException(LlmErrorCode.TIMEOUT)));

        assertThatThrownBy(() -> generator.generate(request(List.of(faq(1L))), handler))
                .isInstanceOf(GeneralException.class);

        assertThat(handler.error).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("재시도하면 앞서 모은 토큰을 버린다")
    void 재시도하면_토큰을_버린다() {
        RagAnswerGenerator generator = generator(new RetryingStubClient());

        AnswerResult result = generator.generate(request(List.of(faq(1L))), handler);

        assertThat(result.answer()).isEqualTo("정상 답변");
        assertThat(handler.retries).isEqualTo(1);
    }

    private RagAnswerGenerator generator(LlmClient client) {
        return new RagAnswerGenerator(client, new AnswerContextConverter(), recorder);
    }

    private AnswerRequest request(List<FaqSearchResponse> searchResults) {
        return AnswerRequest.builder()
                .executionId(42L)
                .userQuery("요금제 바꾸고 싶어요")
                .conditions(Map.of("location", "강남"))
                .searchResults(searchResults)
                .build();
    }

    private FaqSearchResponse faq(Long faqId) {
        return new FaqSearchResponse(
                faqId, "BILLING", "질문" + faqId, "답변" + faqId,
                0.9, 1, LocalDate.of(2026, 9, 17), faqId.intValue());
    }

    private static final class StubClient implements LlmClient {
        private final List<String> tokens;
        private final RuntimeException failure;
        private boolean called;
        private LlmRequest received;

        private StubClient(List<String> tokens) {
            this(tokens, null);
        }

        private StubClient(RuntimeException failure) {
            this(List.of(), failure);
        }

        private StubClient(List<String> tokens, RuntimeException failure) {
            this.tokens = tokens;
            this.failure = failure;
        }

        @Override
        public String generate(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            called = true;
            received = request;
            if (failure != null) {
                handler.onError(failure);
                return;
            }
            tokens.forEach(handler::onToken);
            handler.onComplete();
        }
    }

    // 첫 시도에서 토큰을 흘린 뒤 재시도하는 상황
    private static final class RetryingStubClient implements LlmClient {
        @Override
        public String generate(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onToken("버려질 ");
            handler.onRetry(2, new GeneralException(LlmErrorCode.TIMEOUT));
            handler.onToken("정상 답변");
            handler.onComplete();
        }
    }

    private static final class SpyRecorder extends LlmGenerationRecorder {
        private final List<Status> statuses = new ArrayList<>();

        private SpyRecorder() {
            super((LlmGenerationRepository) null, null);
        }

        @Override
        public void record(LlmRequest request, String model, Result result) {
            statuses.add(result.status());
        }
    }

    private static final class RecordingHandler implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private boolean completed;
        private Throwable error;
        private int retries;

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

        @Override
        public void onRetry(int attempt, Throwable cause) {
            retries++;
        }
    }
}
