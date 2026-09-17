package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;

class OllamaClientTest {

    private static final String CHAT_URL = "http://ollama.test/api/chat";
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private MockRestServiceServer server;
    private OllamaClient ollamaClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        server = MockRestServiceServer.bindTo(builder).build();

        OllamaRequestConverter converter = new OllamaRequestConverter(
                new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60)));
        ollamaClient = new OllamaClient(builder.build(), converter, new ObjectMapper());
    }

    @Test
    @DisplayName("generate는 Ollama 응답의 글을 반환한다")
    void generate는_응답의_글을_반환한다() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"파란색\"},\"done\":true}", MediaType.APPLICATION_JSON));

        String result = ollamaClient.generate(request());

        assertThat(result).isEqualTo("파란색");
        server.verify();
    }

    @Test
    @DisplayName("stream은 조각마다 onToken을 부르고 끝나면 onComplete만 부른다")
    void stream은_조각을_전달한_뒤_완료를_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.stream").value(true))
                .andRespond(withSuccess("""
                        {"message":{"content":"안녕"},"done":false}
                        {"message":{"content":"하세요"},"done":false}
                        {"message":{"content":""},"done":true}
                        """, NDJSON));
        RecordingHandler handler = new RecordingHandler();

        ollamaClient.stream(request(), handler);

        assertThat(handler.tokens).containsExactly("안녕", "하세요");
        assertThat(handler.completeCount).isEqualTo(1);
        assertThat(handler.error).isNull();
    }

    @Test
    @DisplayName("끝 신호 없이 끊기면 onError만 부른다")
    void 끝_신호_없이_끊기면_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("""
                        {"message":{"content":"안녕"},"done":false}
                        {"message":{"content":"하세요"},"done":false}
                        """, NDJSON));
        RecordingHandler handler = new RecordingHandler();

        ollamaClient.stream(request(), handler);

        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    @Test
    @DisplayName("Ollama가 에러 상태로 응답하면 onError만 부른다")
    void 에러_상태면_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withServerError());
        RecordingHandler handler = new RecordingHandler();

        ollamaClient.stream(request(), handler);

        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    @Test
    @DisplayName("Ollama에 연결하지 못하면 onError만 부른다")
    void 연결_실패_시_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withException(new IOException("Connection refused")));
        RecordingHandler handler = new RecordingHandler();

        ollamaClient.stream(request(), handler);

        assertThat(handler.error).isNotNull();
        assertThat(handler.completeCount).isZero();
    }

    private LlmRequest request() {
        return LlmRequest.builder()
                .userPrompt("인사해줘")
                .build();
    }

    private static class RecordingHandler implements LlmStreamHandler {

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
