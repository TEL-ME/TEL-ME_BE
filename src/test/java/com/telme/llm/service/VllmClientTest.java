package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.converter.VllmRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;

class VllmClientTest {

    private static final String CHAT_URL = "http://vllm.test/v1/chat/completions";

    private MockRestServiceServer server;
    private VllmClient vllmClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vllm.test");
        server = MockRestServiceServer.bindTo(builder).build();

        VllmRequestConverter converter = new VllmRequestConverter(new OllamaRequestConverter(
                new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60), 8192)));
        vllmClient = new VllmClient(builder.build(), converter, new ObjectMapper());
    }

    @Test
    @DisplayName("generate는 Ollama와 같은 작업별 기본값으로 요청하고 첫 선택지의 글을 반환한다")
    void generate는_작업별_기본값으로_요청하고_글을_반환한다() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("exaone3.5:7.8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("인사해줘"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"파란색\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        String result = vllmClient.generate(LlmRequest.builder()
                .taskType(TaskType.RAG_ANSWER)
                .systemPrompt("상담사")
                .userPrompt("인사해줘")
                .build());

        assertThat(result).isEqualTo("파란색");
        server.verify();
    }

    @Test
    @DisplayName("JSON 형식은 json_object로, 스키마는 json_schema로 보낸다")
    void JSON_형식과_스키마를_vLLM_형식으로_보낸다() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.type").value("object"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}",
                        MediaType.APPLICATION_JSON));

        vllmClient.generate(LlmRequest.builder().userPrompt("분류").format(ResponseFormat.JSON).build());
        vllmClient.generate(LlmRequest.builder().userPrompt("분류")
                .responseSchema(Map.of("type", "object")).build());

        server.verify();
    }

    @Test
    @DisplayName("generate 응답에 선택지가 없으면 INVALID_RESPONSE를 던진다")
    void generate_선택지가_없으면_예외() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> vllmClient.generate(request()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(LlmErrorCode.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("generate가 서버 오류를 받으면 MODEL_ERROR를 던진다")
    void generate_서버_오류면_MODEL_ERROR() {
        server.expect(requestTo(CHAT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> vllmClient.generate(request()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(LlmErrorCode.MODEL_ERROR);
    }

    @Test
    @DisplayName("stream은 delta 조각마다 onToken을 부르고 [DONE]에서 onComplete만 부른다")
    void stream은_조각을_전달한_뒤_완료를_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.stream").value(true))
                .andRespond(withSuccess("""
                        data: {"choices":[{"delta":{"role":"assistant"}}]}

                        data: {"choices":[{"delta":{"content":"안녕"}}]}

                        data: {"choices":[{"delta":{"content":"하세요"}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                        """, MediaType.TEXT_EVENT_STREAM));
        RecordingHandler handler = new RecordingHandler();

        vllmClient.stream(request(), handler);

        assertThat(handler.tokens).containsExactly("안녕", "하세요");
        assertThat(handler.completeCount).isEqualTo(1);
        assertThat(handler.error).isNull();
    }

    @Test
    @DisplayName("[DONE] 없이 끊기면 STREAM_INTERRUPTED로 onError만 부른다")
    void DONE_없이_끊기면_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("""
                        data: {"choices":[{"delta":{"content":"안녕"}}]}

                        """, MediaType.TEXT_EVENT_STREAM));
        RecordingHandler handler = new RecordingHandler();

        vllmClient.stream(request(), handler);

        assertThat(handler.completeCount).isZero();
        assertThat(handler.error)
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.STREAM_INTERRUPTED));
    }

    @Test
    @DisplayName("토큰 없이 [DONE]이 오면 INVALID_RESPONSE로 onError를 부른다")
    void 토큰_없이_끝나면_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("""
                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                        """, MediaType.TEXT_EVENT_STREAM));
        RecordingHandler handler = new RecordingHandler();

        vllmClient.stream(request(), handler);

        assertThat(handler.completeCount).isZero();
        assertThat(handler.error)
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("vLLM에 연결하지 못하면 CONNECTION_FAILED로 onError만 부른다")
    void 연결_실패_시_onError만_호출한다() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withException(new IOException("Connection refused")));
        RecordingHandler handler = new RecordingHandler();

        vllmClient.stream(request(), handler);

        assertThat(handler.completeCount).isZero();
        assertThat(handler.error)
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.CONNECTION_FAILED));
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
