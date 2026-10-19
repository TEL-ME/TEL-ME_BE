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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

class BedrockClientCompletionTest {
    private final BedrockRuntimeClient runtime = mock(BedrockRuntimeClient.class);
    private final BedrockRequestConverter converter = new BedrockRequestConverter(new BedrockProperties(
            true, "us-east-1", "openai.gpt-oss-120b-1:0", Duration.ofSeconds(8), "low", 768, 2,
            Duration.ofSeconds(1)));
    private final BedrockClient client = new BedrockClient(runtime, converter, true);
    private final LlmRequest request = LlmRequest.builder().taskType(TaskType.SUMMARY).userPrompt("요약").build();

    @Test
    @DisplayName("종료 요약은 정상 종료 사유와 텍스트가 있어야 반환한다")
    void 정상_종료만_허용한다() {
        respond("end_turn", "{\"topics\":{}}");

        assertThat(client.generate(request)).isEqualTo("{\"topics\":{}}");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"max_tokens", "guardrail_intervened", "content_filtered", "tool_use",
            "stop_sequence", "model_context_window_exceeded", "unknown_reason"})
    @DisplayName("출력 잘림·보안 차단·알 수 없는 종료 사유는 유효한 JSON이 있어도 실패한다")
    void 비정상_종료의_텍스트를_허용하지_않는다(String reason) {
        respond(reason, "{\"topics\":{}}");

        assertInvalidResponse();
    }

    @Test
    @DisplayName("정상 종료라도 본문이 비었으면 실패한다")
    void 빈_본문은_허용하지_않는다() {
        respond("end_turn", " ");

        assertInvalidResponse();
    }

    @Test
    @DisplayName("종료 요약의 엄격한 검사 설정은 기존 판정기 연결의 계약을 바꾸지 않는다")
    void 기존_클라이언트의_설정을_유지한다() {
        respond(null, "기존 판정 응답");

        assertThat(new BedrockClient(runtime, converter).generate(request)).isEqualTo("기존 판정 응답");
    }

    private void respond(String reason, String text) {
        when(runtime.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .stopReason(reason).output(ConverseOutput.fromMessage(Message.builder()
                        .content(ContentBlock.fromText(text)).build())).build());
    }

    private void assertInvalidResponse() {
        assertThatThrownBy(() -> client.generate(request)).isInstanceOfSatisfying(GeneralException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(LlmErrorCode.INVALID_RESPONSE));
    }
}
