package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.telme.faq.dto.res.EmbedResponse;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EmbeddingClientTest {

    private static final String EMBED_URL = "http://ollama.test/api/embed";

    private MockRestServiceServer server;
    private EmbeddingClient embeddingClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        embeddingClient = new EmbeddingClient(client, client);
    }

    @Test
    @DisplayName("요청 개수와 벡터 차원이 맞으면 응답을 그대로 반환한다")
    void 정상_응답이면_그대로_반환한다() {
        List<String> texts = List.of("질문1", "질문2");
        float[] vector1 = new float[1024];
        vector1[0] = 1.5f;
        float[] vector2 = new float[1024];
        vector2[0] = 2.5f;
        List<float[]> embeddings = List.of(vector1, vector2);

        List<float[]> result = embeddingClient.validate(texts, new EmbedResponse(embeddings));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(vector1);
        assertThat(result.get(1)).containsExactly(vector2);
    }

    @Test
    @DisplayName("Ollama 응답 자체가 null이면 예외를 던진다")
    void 응답이_null이면_예외를_던진다() {
        List<String> texts = List.of("질문1");

        assertThatThrownBy(() -> embeddingClient.validate(texts, null))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("embeddings 필드가 null이면 예외를 던진다")
    void embeddings가_null이면_예외를_던진다() {
        List<String> texts = List.of("질문1");

        assertThatThrownBy(() ->
                embeddingClient.validate(texts, new EmbedResponse(null)))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("요청 개수와 응답 벡터 개수가 다르면 예외를 던진다")
    void 개수가_다르면_예외를_던진다() {
        List<String> texts = List.of("질문1", "질문2");
        List<float[]> embeddings = List.of(new float[1024]);

        assertThatThrownBy(() ->
                embeddingClient.validate(texts, new EmbedResponse(embeddings)))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("응답에 null 벡터가 포함되면 예외를 던진다")
    void null_벡터가_있으면_예외를_던진다() {
        List<String> texts = List.of("질문1");
        List<float[]> embeddings = Arrays.asList((float[]) null);

        assertThatThrownBy(() ->
                embeddingClient.validate(texts, new EmbedResponse(embeddings)))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("벡터 차원이 1024가 아니면 예외를 던진다")
    void 차원이_다르면_예외를_던진다() {
        List<String> texts = List.of("질문1");
        List<float[]> embeddings = List.of(new float[10]);

        assertThatThrownBy(() ->
                embeddingClient.validate(texts, new EmbedResponse(embeddings)))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("embed 호출 시 text가 null이면 예외를 던진다")
    void embed_text가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> embeddingClient.embed(null))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
    }

    @Test
    @DisplayName("embed 호출 시 text가 공백이면 예외를 던진다")
    void embed_text가_공백이면_예외를_던진다() {
        assertThatThrownBy(() -> embeddingClient.embed("   "))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
    }

    @Test
    @DisplayName("embedBatch 호출 시 texts가 null이면 Ollama를 호출하지 않고 예외를 던진다")
    void embedBatch_texts가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> embeddingClient.embedBatch(null))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
    }

    @Test
    @DisplayName("embedBatch 호출 시 texts가 비어있으면 예외를 던진다")
    void embedBatch_texts가_비어있으면_예외를_던진다() {
        assertThatThrownBy(() -> embeddingClient.embedBatch(List.of()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
    }

    @Test
    @DisplayName("embedBatch 목록에 공백 원소가 있으면 예외를 던진다")
    void embedBatch_공백_원소가_있으면_예외를_던진다() {
        assertThatThrownBy(() -> embeddingClient.embedBatch(Arrays.asList("질문1", "   ")))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
    }

    @Test
    @DisplayName("Ollama가 4xx로 응답하면 요청 거부 코드로 변환한다")
    void ollama가_4xx면_요청거부_예외를_던진다() {
        server.expect(requestTo(EMBED_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> embeddingClient.embed("질문"))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_REJECTED);
    }

    @Test
    @DisplayName("Ollama가 5xx로 응답하면 호출 실패 코드로 변환한다")
    void ollama가_5xx면_호출실패_예외를_던진다() {
        server.expect(requestTo(EMBED_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> embeddingClient.embed("질문"))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_FAILED);
    }

    @Test
    @DisplayName("Ollama에 연결하지 못하면 호출 실패 코드로 변환한다")
    void 연결_실패_시_호출실패_예외를_던진다() {
        server.expect(requestTo(EMBED_URL))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> embeddingClient.embed("질문"))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.EMBEDDING_REQUEST_FAILED);
    }
}
