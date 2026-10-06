package com.telme.rag.service;

import com.telme.chat.service.ExecutionTrace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage.AnswerBasis;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.RagSearchResultAnswerGenerator;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmRetryProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.llm.service.RecordingLlmClient;
import com.telme.llm.service.RetryingLlmClient;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RagAnswerDeliveryTest {
    private static final String SUPPORTED = "재발급 비용은 7,700원입니다.";
    private static final String UNSUPPORTED = " 택배비는 이 금액에 포함됩니다.";
    private final DeliveryHandler delivery = new DeliveryHandler();
    private final List<Status> statuses = new ArrayList<>();
    private final LlmGenerationRecorder recorder = new LlmGenerationRecorder(null, null, null, null) {
        @Override
        public void record(LlmRequest request, String model, Result result) {
            statuses.add(result.status());
        }
    };

    @Test
    void exposesNothingUntilCompletionAndDeliversOnlyGuardedAnswer() {
        var client = scripted(stream -> {
            stream.onToken(SUPPORTED);
            assertThat(delivery.tokens).isEmpty();
            stream.onToken(UNSUPPORTED);
            assertThat(delivery.tokens).isEmpty();
            assertThat(delivery.completed).isFalse();
            stream.onComplete();
        });

        var result = generator(client).generate(request(), delivery);

        assertThat(result.answer()).isEqualTo(SUPPORTED);
        assertThat(delivery.tokens).containsExactly(result.answer());
        assertThat(delivery.events).containsExactly("token", "complete");
    }

    @Test
    void removesUnsupportedTextFromDeliveredAndReturnedAnswer() {
        var result = generator(scripted(stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onComplete();
        })).generate(request(), delivery);

        assertThat(result.answer()).isEqualTo(SUPPORTED);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.GROUNDED);
        assertThat(delivery.tokens).containsExactly(SUPPORTED);
    }

    @Test
    void deliversOnlyNoEvidenceWhenGuardRemovesEntireAnswer() {
        var result = generator(scripted(stream -> {
            stream.onToken("택배비는 이 금액에 포함됩니다.");
            stream.onComplete();
        })).generate(request(), delivery);

        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(delivery.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(delivery.completed).isTrue();
    }

    @Test
    void guardRejectionNeverExposesRawAnswer() {
        var generator = generator(scripted(stream -> {
            stream.onToken("재발급 비용은 84,700원입니다.");
            stream.onComplete();
        }));

        var result = generator.generate(request(), delivery);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(delivery.tokens).containsExactly(result.answer());
        assertThat(delivery.completed).isTrue();
        assertThat(delivery.error).isNull();
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void discardsPartialAnswerOnModelFailure(RuntimeException failure) {
        var generator = generator(scripted(stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onError(failure);
        }));

        assertThatThrownBy(() -> generator.generate(request(), delivery)).isSameAs(failure);

        assertThat(delivery.tokens).isEmpty();
        assertThat(delivery.completed).isFalse();
        assertThat(delivery.error).isSameAs(failure);
    }

    static Stream<RuntimeException> modelFailures() {
        return Stream.of(new GeneralException(LlmErrorCode.TIMEOUT),
                new GeneralException(LlmErrorCode.INVALID_RESPONSE),
                new LlmStreamCancelledException());
    }

    @Test
    void synchronousFailureAlsoExposesNoPartialAnswer() {
        var failure = new GeneralException(LlmErrorCode.CONNECTION_FAILED);
        var generator = generator(scripted(stream -> {
            stream.onToken(SUPPORTED);
            throw failure;
        }));

        assertThatThrownBy(() -> generator.generate(request(), delivery)).isSameAs(failure);
        assertThat(delivery.tokens).isEmpty();
        assertThat(delivery.completed).isFalse();
    }

    @Test
    void retryDiscardsPreviousAttemptBeforeDelivery() {
        var result = generator(scripted(stream -> {
            stream.onToken("재발급 비용은 84,700원입니다.");
            stream.onRetry(2, new GeneralException(LlmErrorCode.TIMEOUT));
            assertThat(delivery.tokens).isEmpty();
            stream.onToken(SUPPORTED);
            stream.onComplete();
        })).generate(request(), delivery);

        assertThat(result.answer()).isEqualTo(SUPPORTED);
        assertThat(delivery.tokens).containsExactly(SUPPORTED);
        assertThat(delivery.events).containsExactly("retry", "token", "complete");
    }

    @Test
    void recordingClientStillRecordsGuardFailure() {
        var generator = generator(recorded(scripted(stream -> {
            stream.onToken("재발급 비용은 84,700원입니다.");
            stream.onComplete();
        })));

        var result = generator.generate(request(), delivery);

        assertThat(delivery.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(statuses).containsExactly(Status.MODEL_ERROR);
    }

    @Test
    void deliveryCancellationPreventsCompletionAndIsRecorded() {
        var generator = generator(recorded(scripted(stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onComplete();
        })));
        var disconnected = new LlmStreamHandler() {
            @Override
            public void onToken(String token) {
                assertThat(token).isEqualTo(SUPPORTED);
                throw new LlmStreamCancelledException();
            }

            @Override
            public void onComplete() {
                delivery.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                delivery.onError(error);
            }
        };

        assertThatThrownBy(() -> generator.generate(request(), disconnected))
                .isInstanceOf(LlmStreamCancelledException.class);
        assertThat(delivery.completed).isFalse();
        assertThat(statuses).containsExactly(Status.CANCELLED);
    }

    @Test
    void consultAdapterReceivesSameGuardedAnswerForDeliveryAndPersistence() {
        var adapter = new RagSearchResultAnswerGenerator(generator(scripted(stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            assertThat(delivery.tokens).isEmpty();
            stream.onComplete();
        })), executionId -> delivery);
        var input = new AnswerInput(42L, 7L, 11L, Purpose.GENERAL_FAQ,
                "유심 재발급 비용과 배송비를 알려주세요", "유심 재발급 비용", Map.of());

        var result = adapter.generate(input, request().searchResults());

        assertThat(result.answer().content()).isEqualTo(SUPPORTED);
        assertThat(delivery.tokens).containsExactly(result.answer().content());
        // 상담 서비스가 저장을 마친 뒤 완료 이벤트를 보내도록 어댑터는 완료를 전달하지 않는다.
        assertThat(delivery.completed).isFalse();
        assertThat(result.sources()).hasSize(1);
    }

    @Test
    void progressCancellationStopsCollectingWithoutExposingContent() {
        var client = scripted(stream -> {
            stream.onToken(SUPPORTED);
            throw new AssertionError("취소 후 모델 토큰을 계속 소비하지 않는다");
        });
        var cancelled = new LlmStreamHandler() {
            @Override
            public void onProgress() { throw new LlmStreamCancelledException(); }
            @Override
            public void onToken(String token) { delivery.onToken(token); }
            @Override
            public void onComplete() { delivery.onComplete(); }
            @Override
            public void onError(Throwable error) { delivery.onError(error); }
        };

        assertThatThrownBy(() -> generator(recorded(client)).generate(request(), cancelled))
                .isInstanceOf(LlmStreamCancelledException.class);
        assertThat(delivery.tokens).isEmpty();
        assertThat(delivery.completed).isFalse();
        assertThat(statuses).containsExactly(Status.CANCELLED);
    }

    @Test
    void duplicateCompletionDoesNotDeliverAnswerTwice() {
        var result = generator(scripted(stream -> {
            stream.onToken(SUPPORTED);
            stream.onComplete();
            stream.onToken(UNSUPPORTED);
            stream.onComplete();
        })).generate(request(), delivery);

        assertThat(delivery.tokens).containsExactly(result.answer());
        assertThat(delivery.events).containsExactly("token", "complete");
    }

    @Test
    void failedAttemptCannotBeCompletedWithItsPartialAnswer() {
        var error = new GeneralException(LlmErrorCode.TIMEOUT);
        var generator = generator(scripted(stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onError(error);
            stream.onComplete();
        }));

        assertThatThrownBy(() -> generator.generate(request(), delivery)).isSameAs(error);
        assertThat(delivery.tokens).isEmpty();
        assertThat(delivery.events).containsExactly("error");
    }

    @Test
    void completionDeliveryCancellationReportedByClientDoesNotReturnSuccess() {
        var cancelled = new LlmStreamCancelledException();
        var output = new LlmStreamHandler() {
            @Override
            public void onToken(String token) { throw cancelled; }
            @Override
            public void onComplete() { delivery.onComplete(); }
            @Override
            public void onError(Throwable error) { delivery.onError(error); }
        };
        var generator = generator(scripted(stream -> {
            stream.onToken(SUPPORTED);
            try {
                stream.onComplete();
            } catch (LlmStreamCancelledException error) {
                stream.onError(error);
            }
        }));

        assertThatThrownBy(() -> generator.generate(request(), output)).isSameAs(cancelled);
        assertThat(delivery.tokens).isEmpty();
        assertThat(delivery.completed).isFalse();
        assertThat(delivery.error).isSameAs(cancelled);
    }

    private LlmClient recorded(LlmClient client) {
        return new RetryingLlmClient(new RecordingLlmClient(client, recorder, "test-model"),
                new LlmRetryProperties(2, Duration.ZERO));
    }

    private RagAnswerGenerator generator(LlmClient client) {
        var checker = new EvidenceRelevanceChecker(client, new ObjectMapper(),
                new EvidenceCheckProperties(false, 300));
        return new RagAnswerGenerator(client, new AnswerContextConverter(), new AnswerGuard(),
                checker, recorder, ExecutionTrace.noop());
    }

    private AnswerRequest request() {
        var faq = new FaqSearchResponse(1L, null, "USIM", "유심 재발급 비용은 얼마인가요?",
                "재발급 비용은 7,700원이며 택배로 2~3 영업일이 걸립니다.",
                0.9, 1, LocalDate.of(2026, 9, 17), 1, null);
        return AnswerRequest.builder().executionId(42L)
                .userQuery("유심 재발급 비용과 배송비를 알려주세요")
                .searchResults(List.of(faq)).build();
    }

    private LlmClient scripted(Consumer<LlmStreamHandler> script) {
        return new LlmClient() {
            @Override
            public String generate(LlmRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stream(LlmRequest request, LlmStreamHandler handler) {
                script.accept(handler);
            }
        };
    }

    private static final class DeliveryHandler implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private boolean completed;
        private Throwable error;

        @Override
        public void onToken(String token) {
            tokens.add(token);
            events.add("token");
        }

        @Override
        public void onComplete() {
            completed = true;
            events.add("complete");
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            events.add("error");
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            events.add("retry");
        }
    }
}
