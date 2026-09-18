package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmRetryProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;

class RetryingLlmClientTest {

    private final LlmRetryProperties properties = new LlmRetryProperties(2, Duration.ZERO);

    @Test
    @DisplayName("generate가 실패하면 다시 호출한다")
    void generate_실패하면_재시도한다() {
        CountingClient delegate = new CountingClient(1);
        RetryingLlmClient client = new RetryingLlmClient(delegate, properties);

        String result = client.generate(request());

        assertThat(result).isEqualTo("성공");
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("generate가 마지막 시도까지 실패하면 예외를 던진다")
    void generate_계속_실패하면_예외를_던진다() {
        CountingClient delegate = new CountingClient(5);
        RetryingLlmClient client = new RetryingLlmClient(delegate, properties);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(LlmErrorCode.CONNECTION_FAILED);
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 횟수가 0으로 설정돼도 generate는 한 번 호출하고 원래 예외를 던진다")
    void generate_재시도_횟수가_0이어도_한_번은_호출한다() {
        CountingClient delegate = new CountingClient(5);
        RetryingLlmClient client =
                new RetryingLlmClient(delegate, new LlmRetryProperties(0, Duration.ZERO));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(LlmErrorCode.CONNECTION_FAILED);
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 횟수가 0으로 설정돼도 stream은 onError를 한 번 부른다")
    void stream_재시도_횟수가_0이어도_onError를_호출한다() {
        StreamStub delegate = new StreamStub(List.of(StreamStub.Behavior.failBeforeToken()));
        RecordingHandler handler = new RecordingHandler();

        new RetryingLlmClient(delegate, new LlmRetryProperties(0, Duration.ZERO))
                .stream(request(), handler);

        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
        assertThat(handler.retries).isEmpty();
    }

    @Test
    @DisplayName("다시 호출해도 같은 결과인 오류는 재시도하지 않는다")
    void generate_재시도_대상이_아닌_오류는_바로_전달한다() {
        FailingClient delegate = new FailingClient(new IllegalArgumentException("잘못된 요청"));
        RetryingLlmClient client = new RetryingLlmClient(delegate, properties);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("stream도 재시도 대상이 아닌 오류는 바로 onError로 전달한다")
    void stream_재시도_대상이_아닌_오류는_바로_전달한다() {
        FailingClient delegate = new FailingClient(new IllegalArgumentException("잘못된 요청"));
        RecordingHandler handler = new RecordingHandler();

        new RetryingLlmClient(delegate, properties).stream(request(), handler);

        assertThat(delegate.calls).isEqualTo(1);
        assertThat(handler.retries).isEmpty();
        assertThat(handler.error).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("토큰 전에 실패하면 onRetry를 부르고 다시 호출한다")
    void stream_토큰_전_실패는_재시도한다() {
        StreamStub delegate = new StreamStub(List.of(
                StreamStub.Behavior.failBeforeToken(),
                StreamStub.Behavior.succeed("안녕", "하세요")));
        RecordingHandler handler = new RecordingHandler();

        new RetryingLlmClient(delegate, properties).stream(request(), handler);

        assertThat(handler.retries).hasSize(1);
        assertThat(handler.tokens).containsExactly("안녕", "하세요");
        assertThat(handler.completeCount).isEqualTo(1);
        assertThat(handler.error).isNull();
    }

    @Test
    @DisplayName("토큰이 나간 뒤 실패하면 재시도하지 않고 onError만 부른다")
    void stream_토큰_후_실패는_재시도하지_않는다() {
        StreamStub delegate = new StreamStub(List.of(
                StreamStub.Behavior.failAfterToken("안녕"),
                StreamStub.Behavior.succeed("다시", "생성")));
        RecordingHandler handler = new RecordingHandler();

        new RetryingLlmClient(delegate, properties).stream(request(), handler);

        assertThat(handler.retries).isEmpty();
        assertThat(handler.tokens).containsExactly("안녕");
        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    @Test
    @DisplayName("마지막 시도까지 실패하면 onError만 부른다")
    void stream_계속_실패하면_onError만_호출한다() {
        StreamStub delegate = new StreamStub(List.of(
                StreamStub.Behavior.failBeforeToken(),
                StreamStub.Behavior.failBeforeToken()));
        RecordingHandler handler = new RecordingHandler();

        new RetryingLlmClient(delegate, properties).stream(request(), handler);

        assertThat(handler.retries).hasSize(1);
        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    @Test
    @DisplayName("호출한 쪽이 중단하면 재시도하지 않는다")
    void stream_중단되면_재시도하지_않는다() {
        StreamStub delegate = new StreamStub(List.of(
                StreamStub.Behavior.succeed("안녕"),
                StreamStub.Behavior.succeed("다시")));
        RecordingHandler handler = new RecordingHandler();
        handler.cancelOnToken = true;

        new RetryingLlmClient(delegate, properties).stream(request(), handler);

        assertThat(handler.retries).isEmpty();
        assertThat(handler.error).isInstanceOf(LlmStreamCancelledException.class);
        assertThat(handler.completeCount).isZero();
    }

    private LlmRequest request() {
        return LlmRequest.builder().userPrompt("질문").build();
    }

    /** 정해진 횟수만큼 실패한 뒤 성공하는 가짜 구현체 */
    private static final class CountingClient implements LlmClient {

        private final int failCount;
        private int calls;

        private CountingClient(int failCount) {
            this.failCount = failCount;
        }

        @Override
        public String generate(LlmRequest request) {
            calls++;
            if (calls <= failCount) {
                throw new GeneralException(LlmErrorCode.CONNECTION_FAILED);
            }
            return "성공";
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
        }
    }

    /** 항상 같은 예외로 실패하는 가짜 구현체 */
    private static final class FailingClient implements LlmClient {

        private final RuntimeException failure;
        private int calls;

        private FailingClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public String generate(LlmRequest request) {
            calls++;
            throw failure;
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            calls++;
            handler.onError(failure);
        }
    }

    /** 시도마다 정해진 동작을 하는 가짜 구현체 */
    private static final class StreamStub implements LlmClient {

        private final List<Behavior> behaviors;
        private int calls;

        private StreamStub(List<Behavior> behaviors) {
            this.behaviors = behaviors;
        }

        @Override
        public String generate(LlmRequest request) {
            return "";
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            Behavior behavior = behaviors.get(Math.min(calls++, behaviors.size() - 1));
            try {
                for (String token : behavior.tokens) {
                    handler.onToken(token);
                }
            } catch (RuntimeException e) {
                // 실제 구현체와 같이 예외를 onError로 바꿔 전달한다
                handler.onError(e);
                return;
            }
            if (behavior.fails) {
                handler.onError(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
                return;
            }
            handler.onComplete();
        }

        private record Behavior(List<String> tokens, boolean fails) {

            private static Behavior failBeforeToken() {
                return new Behavior(List.of(), true);
            }

            private static Behavior failAfterToken(String token) {
                return new Behavior(List.of(token), true);
            }

            private static Behavior succeed(String... tokens) {
                return new Behavior(List.of(tokens), false);
            }
        }
    }

    private static final class RecordingHandler implements LlmStreamHandler {

        private final List<String> tokens = new ArrayList<>();
        private final List<Integer> retries = new ArrayList<>();
        private int completeCount;
        private Throwable error;
        private boolean cancelOnToken;

        @Override
        public void onToken(String token) {
            tokens.add(token);
            if (cancelOnToken) {
                throw new LlmStreamCancelledException();
            }
        }

        @Override
        public void onComplete() {
            completeCount++;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            retries.add(attempt);
        }
    }
}
