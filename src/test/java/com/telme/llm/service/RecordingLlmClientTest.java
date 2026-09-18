package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmGenerationRecorder.Result;

class RecordingLlmClientTest {

    private final RecordingRecorder recorder = new RecordingRecorder();

    @Test
    @DisplayName("generate가 성공하면 SUCCESS로 기록한다")
    void generate_성공을_기록한다() {
        RecordingLlmClient client = client(new StubClient(null));

        String result = client.generate(request(42L));

        assertThat(result).isEqualTo("답변");
        assertThat(recorder.results).hasSize(1);
        assertThat(recorder.results.getFirst().status()).isEqualTo(Status.SUCCESS);
        assertThat(recorder.models).containsExactly("exaone3.5:7.8b");
    }

    @Test
    @DisplayName("generate가 실패하면 원인에 맞는 상태로 기록하고 예외를 그대로 던진다")
    void generate_실패를_기록한다() {
        RecordingLlmClient client =
                client(new StubClient(new GeneralException(LlmErrorCode.TIMEOUT)));

        assertThatThrownBy(() -> client.generate(request(42L)))
                .isInstanceOf(GeneralException.class);

        assertThat(recorder.results.getFirst().status()).isEqualTo(Status.TIMEOUT);
    }

    @Test
    @DisplayName("executionId가 없으면 기록을 남기지 않는다")
    void executionId가_없으면_기록하지_않는다() {
        RecordingLlmClient client = client(new StubClient(null));

        client.generate(request(null));

        assertThat(recorder.skipped).isEqualTo(1);
        assertThat(recorder.results).isEmpty();
    }

    @Test
    @DisplayName("stream이 성공하면 첫 토큰 시간과 SUCCESS를 기록한다")
    void stream_성공을_기록한다() {
        RecordingLlmClient client = client(new StubClient(null));
        CollectingHandler handler = new CollectingHandler();

        client.stream(request(42L), handler);

        assertThat(handler.tokens).containsExactly("안녕", "하세요");
        assertThat(handler.completeCount).isEqualTo(1);
        Result recorded = recorder.results.getFirst();
        assertThat(recorded.status()).isEqualTo(Status.SUCCESS);
        assertThat(recorded.firstTokenMs()).isNotNull();
        assertThat(recorded.totalMs()).isNotNull();
    }

    @Test
    @DisplayName("stream이 중단되면 CANCELLED로 기록한다")
    void stream_중단을_기록한다() {
        RecordingLlmClient client =
                client(new StubClient(new LlmStreamCancelledException()));
        CollectingHandler handler = new CollectingHandler();

        client.stream(request(42L), handler);

        assertThat(recorder.results.getFirst().status()).isEqualTo(Status.CANCELLED);
        assertThat(handler.error).isInstanceOf(LlmStreamCancelledException.class);
    }

    @Test
    @DisplayName("stream이 실패하면 원인에 맞는 상태로 기록하고 onError도 그대로 전달한다")
    void stream_실패를_기록한다() {
        RecordingLlmClient client =
                client(new StubClient(new GeneralException(LlmErrorCode.CONNECTION_FAILED)));
        CollectingHandler handler = new CollectingHandler();

        client.stream(request(42L), handler);

        assertThat(recorder.results.getFirst().status()).isEqualTo(Status.CONNECTION_FAILED);
        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    @Test
    @DisplayName("안쪽이 예외를 그대로 던져도 기록을 남기고 예외를 전달한다")
    void stream_예외가_전파돼도_기록한다() {
        LlmClient throwing = new LlmClient() {

            @Override
            public String generate(LlmRequest request) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void stream(LlmRequest request, LlmStreamHandler handler) {
                throw new IllegalStateException("boom");
            }
        };
        CollectingHandler handler = new CollectingHandler();

        assertThatThrownBy(() -> client(throwing).stream(request(42L), handler))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recorder.results).hasSize(1);
        assertThat(recorder.results.getFirst().status()).isEqualTo(Status.MODEL_ERROR);
    }

    @Test
    @DisplayName("기록 저장이 실패해도 generate 결과와 stream 흐름은 그대로 유지된다")
    void 기록_저장_실패가_응답을_막지_않는다() {
        LlmGenerationRecorder failing = new LlmGenerationRecorder(null, null) {

            @Override
            public void record(LlmRequest request, String model, Result result) {
                throw new IllegalStateException("기록 저장 실패");
            }
        };
        RecordingLlmClient client = new RecordingLlmClient(new StubClient(null), failing, "exaone3.5:7.8b");
        CollectingHandler handler = new CollectingHandler();

        assertThat(client.generate(request(42L))).isEqualTo("답변");

        client.stream(request(42L), handler);
        assertThat(handler.tokens).containsExactly("안녕", "하세요");
        assertThat(handler.completeCount).isEqualTo(1);
    }

    private RecordingLlmClient client(LlmClient delegate) {
        return new RecordingLlmClient(delegate, recorder, "exaone3.5:7.8b");
    }

    private LlmRequest request(Long executionId) {
        return LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt("질문")
                .build();
    }

    /** 저장 대신 기록 내용을 모아두는 가짜 Recorder */
    private static final class RecordingRecorder extends LlmGenerationRecorder {

        private final List<Result> results = new ArrayList<>();
        private final List<String> models = new ArrayList<>();
        private int skipped;

        private RecordingRecorder() {
            super(null, null);
        }

        @Override
        public void record(LlmRequest request, String model, Result result) {
            if (request.executionId() == null) {
                skipped++;
                return;
            }
            results.add(result);
            models.add(model);
        }
    }

    /** 정해진 예외가 있으면 실패하고, 없으면 성공하는 가짜 구현체 */
    private record StubClient(RuntimeException failure) implements LlmClient {

        @Override
        public String generate(LlmRequest request) {
            if (failure != null) {
                throw failure;
            }
            return "답변";
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            if (failure instanceof LlmStreamCancelledException) {
                handler.onToken("안녕");
                handler.onError(failure);
                return;
            }
            if (failure != null) {
                handler.onError(failure);
                return;
            }
            handler.onToken("안녕");
            handler.onToken("하세요");
            handler.onComplete();
        }
    }

    private static final class CollectingHandler implements LlmStreamHandler {

        private final List<String> tokens = new ArrayList<>();
        private int completeCount;
        private Throwable error;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
            completeCount++;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}
