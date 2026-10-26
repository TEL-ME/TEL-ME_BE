package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;

class FakeLlmClientTest {

    private final FakeLlmClient fakeLlmClient = new FakeLlmClient();

    @Test
    @DisplayName("TEXT 요청이면 가짜 문장을 반환한다")
    void TEXT_요청이면_가짜_문장을_반환한다() {
        String result = fakeLlmClient.generate(request(ResponseFormat.TEXT));

        assertThat(result).isEqualTo("[FAKE] 테스트용 응답입니다.");
    }

    @Test
    @DisplayName("format을 지정하지 않으면 TEXT로 처리한다")
    void format이_없으면_TEXT로_처리한다() {
        String result = fakeLlmClient.generate(request(null));

        assertThat(result).isEqualTo("[FAKE] 테스트용 응답입니다.");
    }

    @Test
    @DisplayName("JSON 요청이면 빈 JSON을 반환한다")
    void JSON_요청이면_빈_JSON을_반환한다() {
        String result = fakeLlmClient.generate(request(ResponseFormat.JSON));

        assertThat(result).isEqualTo("{}");
    }

    @Test
    @DisplayName("stream은 토큰을 여러 번 전달한 뒤 onComplete만 호출한다")
    void stream은_토큰을_전달한_뒤_완료를_호출한다() {
        RecordingHandler handler = new RecordingHandler();

        fakeLlmClient.stream(request(ResponseFormat.TEXT), handler);

        assertThat(handler.tokens).hasSizeGreaterThan(1);
        assertThat(String.join("", handler.tokens)).isEqualTo("[FAKE] 테스트용 응답입니다.");
        assertThat(handler.completeCount).isEqualTo(1);
        assertThat(handler.error).isNull();
    }

    @Test
    @DisplayName("onToken 처리 중 실패하면 onError만 호출하고 onComplete는 호출하지 않는다")
    void onToken_실패_시_onError만_호출한다() {
        RecordingHandler handler = new RecordingHandler();
        handler.failOnToken = true;

        fakeLlmClient.stream(request(ResponseFormat.TEXT), handler);

        assertThat(handler.error).isInstanceOf(IllegalStateException.class);
        assertThat(handler.completeCount).isZero();
    }

    private LlmRequest request(ResponseFormat format) {
        return LlmRequest.builder()
                .taskType(TaskType.RAG_ANSWER)
                .systemPrompt("system")
                .userPrompt("user")
                .format(format)
                .build();
    }

    private static class RecordingHandler implements LlmStreamHandler {

        private final List<String> tokens = new ArrayList<>();
        private int completeCount;
        private Throwable error;
        private boolean failOnToken;

        @Override
        public void onToken(String token) {
            if (failOnToken) {
                throw new IllegalStateException("SSE 전송 실패");
            }
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
