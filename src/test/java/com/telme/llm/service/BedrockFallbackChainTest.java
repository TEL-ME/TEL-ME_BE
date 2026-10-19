package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmGenerationRecorder.Result;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;

// 운영과 같은 순서(Bedrock 클라이언트 → 호출 기록 → 대체)로 이어 실제 Bedrock 오류가 엑사원으로 넘어가는지 확인한다
class BedrockFallbackChainTest {

    private static final String MODEL = "openai.gpt-oss-120b-1:0";

    private final BedrockRuntimeClient runtime = mock(BedrockRuntimeClient.class);
    private final LlmGenerationRecorder recorder = mock(LlmGenerationRecorder.class);
    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient client = new FallbackLlmClient(
            new RecordingLlmClient(new BedrockClient(runtime, new BedrockRequestConverter(
                    new BedrockProperties(true, "us-east-1", MODEL, Duration.ofSeconds(8), "low", 1000, 50, Duration.ofSeconds(1)))),
                    recorder, MODEL, "bedrock"),
            exaone);
    private final LlmRequest request = LlmRequest.builder()
            .executionId(7L).taskType(TaskType.RAG_ANSWER).systemPrompt("s").userPrompt("u").build();

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of("자격 증명 없음", SdkClientException.builder().message("Unable to load credentials").build(),
                        Status.CONNECTION_FAILED),
                Arguments.of("시간 초과", ApiCallTimeoutException.builder().message("timeout").build(), Status.TIMEOUT),
                Arguments.of("권한 없음", AwsServiceException.builder().statusCode(403).message("denied").build(),
                        Status.MODEL_ERROR),
                Arguments.of("호출 한도 초과", AwsServiceException.builder().statusCode(429).message("throttled").build(),
                        Status.MODEL_ERROR),
                Arguments.of("서버 오류", AwsServiceException.builder().statusCode(503).message("unavailable").build(),
                        Status.MODEL_ERROR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    @DisplayName("Bedrock 호출이 실패하면 실패를 기록하고 엑사원 결과를 돌려준다")
    void generate_실패는_엑사원으로_대체한다(String name, RuntimeException failure, Status recorded) {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(failure);
        when(exaone.generate(request)).thenReturn("엑사원 응답");

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
        assertThat(recordedResult().status()).isEqualTo(recorded);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    @DisplayName("스트리밍도 Bedrock이 첫 글자 전에 실패하면 실패를 기록하고 엑사원으로 다시 부른다")
    void stream_실패는_엑사원으로_대체한다(String name, RuntimeException failure, Status recorded) {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(failure);
        LlmStreamHandler handler = mock(LlmStreamHandler.class);

        client.stream(request, handler);

        verify(exaone).stream(request, handler);
        verify(handler, never()).onError(any());
        assertThat(recordedResult().status()).isEqualTo(recorded);
    }

    @Test
    @DisplayName("생각만 하고 답 글이 없는 응답도 실패로 보고 엑사원으로 대체한다")
    void 답_글이_없으면_엑사원으로_대체한다() {
        respond(ContentBlock.fromReasoningContent(ReasoningContentBlock.fromReasoningText(
                ReasoningTextBlock.builder().text("생각만 하다 잘림").build())));
        when(exaone.generate(request)).thenReturn("엑사원 응답");

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
        assertThat(recordedResult().status()).isEqualTo(Status.MODEL_ERROR);
    }

    @Test
    @DisplayName("Bedrock이 성공하면 엑사원을 부르지 않고 성공을 기록한다")
    void 성공하면_엑사원을_부르지_않는다() {
        respond(ContentBlock.fromText("Bedrock 응답"));

        assertThat(client.generate(request)).isEqualTo("Bedrock 응답");
        verifyNoInteractions(exaone);
        assertThat(recordedResult().status()).isEqualTo(Status.SUCCESS);
    }

    private Result recordedResult() {
        ArgumentCaptor<Result> result = ArgumentCaptor.forClass(Result.class);
        verify(recorder).record(eq(request), eq(MODEL), eq("bedrock"), result.capture());
        return result.getValue();
    }

    private void respond(ContentBlock... blocks) {
        when(runtime.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.builder().message(Message.builder()
                        .role(ConversationRole.ASSISTANT).content(blocks).build()).build())
                .build());
    }
}
