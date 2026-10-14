package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;

class BedrockClientTest {

    private final BedrockRuntimeClient runtime = mock(BedrockRuntimeClient.class);
    private final BedrockClient client = new BedrockClient(runtime, new BedrockRequestConverter(
            new BedrockProperties(true, "us-east-1", "openai.gpt-oss-120b-1:0", Duration.ofSeconds(5), "low", 1000)));

    @Test
    @DisplayName("생각 블록은 빼고 답 글만 반환한다")
    void 생각_블록은_빼고_답만_반환한다() {
        respond(ContentBlock.fromReasoningContent(ReasoningContentBlock.fromReasoningText(
                        ReasoningTextBlock.builder().text("질문은 비용을 묻는다").build())),
                ContentBlock.fromText(" {\"answerable\": true} "));

        assertThat(client.generate(request())).isEqualTo("{\"answerable\": true}");
    }

    @Test
    @DisplayName("답 글이 없으면 응답 형식 오류로 바꾼다")
    void 답_글이_없으면_형식_오류() {
        respond(ContentBlock.fromReasoningContent(ReasoningContentBlock.fromReasoningText(
                ReasoningTextBlock.builder().text("생각만 하다 잘림").build())));

        assertError(LlmErrorCode.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("호출 시간 초과는 TIMEOUT으로 바꾼다")
    void 시간_초과() {
        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("timeout").build());

        assertError(LlmErrorCode.TIMEOUT);
    }

    @Test
    @DisplayName("자격 증명이 없거나 연결하지 못하면 CONNECTION_FAILED로 바꾼다")
    void 연결_실패() {
        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(SdkClientException.builder().message("Unable to load credentials").build());

        assertError(LlmErrorCode.CONNECTION_FAILED);
    }

    @Test
    @DisplayName("Bedrock이 오류를 돌려주면 MODEL_ERROR로 바꾼다")
    void 서버_오류() {
        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(AwsServiceException.builder().statusCode(503).message("unavailable").build());

        assertError(LlmErrorCode.MODEL_ERROR);
    }

    @Test
    @DisplayName("stream은 받은 글을 한 번에 넘기고, 실패는 onError로 전달한다")
    void stream은_한_번에_넘긴다() {
        respond(ContentBlock.fromText("{\"answerable\": false}"));
        CollectingHandler ok = new CollectingHandler();
        client.stream(request(), ok);
        assertThat(ok.tokens).containsExactly("{\"answerable\": false}");
        assertThat(ok.completed).isTrue();

        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("timeout").build());
        CollectingHandler failed = new CollectingHandler();
        client.stream(request(), failed);
        assertThat(failed.tokens).isEmpty();
        assertThat(failed.error).isInstanceOf(GeneralException.class);
    }

    private void respond(ContentBlock... blocks) {
        when(runtime.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().content(blocks).build()))
                .build());
    }

    private void assertError(LlmErrorCode code) {
        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private LlmRequest request() {
        return LlmRequest.builder().taskType(TaskType.ROUTING).systemPrompt("판정").userPrompt("질문").build();
    }

    private static final class CollectingHandler implements LlmStreamHandler {

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
}
