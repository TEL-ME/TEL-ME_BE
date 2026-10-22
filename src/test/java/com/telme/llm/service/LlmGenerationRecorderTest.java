package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmCallException;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.repository.LlmGenerationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LlmGenerationRecorderTest {

    private final LlmGenerationRepository generations = mock(LlmGenerationRepository.class);
    private final OllamaRequestConverter converter = mock(OllamaRequestConverter.class);
    private final LlmGenerationRecorder recorder = new LlmGenerationRecorder(
            generations, mock(ChatExecutionRepository.class), converter, new ObjectMapper());

    @Test
    @DisplayName("요청 옵션을 만들지 못해도 호출 기록은 옵션 없이 저장한다")
    void 요청_옵션_생성이_실패해도_호출_기록을_남긴다() {
        when(converter.toChatRequest(any(), anyBoolean())).thenThrow(new IllegalStateException("options"));

        recorder.record(request(), "exaone3.5:7.8b", new LlmGenerationRecorder.Result(1, Status.TIMEOUT, null, 10, null));

        ArgumentCaptor<LlmGeneration> saved = ArgumentCaptor.forClass(LlmGeneration.class);
        verify(generations).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Status.TIMEOUT);
        assertThat(saved.getValue().getModel()).isEqualTo("exaone3.5:7.8b");
        assertThat(saved.getValue().getRequestOptions()).isNull();
    }

    @Test
    @DisplayName("Bedrock 호출은 provider와 요청값을 기록하고 Ollama 옵션 변환을 쓰지 않는다")
    void Bedrock_호출은_provider를_남긴다() throws Exception {
        recorder.record(LlmRequest.builder().executionId(42L).taskType(TaskType.ROUTING).userPrompt("질문")
                        .maxTokens(1000).build(),
                "openai.gpt-oss-120b-1:0", "bedrock",
                new LlmGenerationRecorder.Result(1, Status.SUCCESS, null, 1600, null));

        ArgumentCaptor<LlmGeneration> saved = ArgumentCaptor.forClass(LlmGeneration.class);
        verify(generations).save(saved.capture());
        var options = new ObjectMapper().readTree(saved.getValue().getRequestOptions());
        assertThat(options.path("provider").asText()).isEqualTo("bedrock");
        assertThat(options.path("maxTokens").asInt()).isEqualTo(1000);
        assertThat(options.has("options")).isFalse();
        verify(converter, never()).toChatRequest(any(), anyBoolean());
    }

    @Test
    @DisplayName("메시지가 없는 GeneralException은 오류 코드와 문구를 오류 내용으로 남긴다")
    void 메시지_없는_예외는_오류_코드로_남긴다() {
        var result = LlmGenerationRecorder.Result.failure(1, new GeneralException(LlmErrorCode.TIMEOUT), null, 8000);

        assertThat(result.status()).isEqualTo(Status.TIMEOUT);
        assertThat(result.errorMessage()).isEqualTo("LLM504-0 LLM 응답 시간이 초과되었습니다.");
    }

    @Test
    @DisplayName("원인 상세가 있는 예외는 상세까지 오류 내용으로 남긴다")
    void 원인_상세를_남긴다() {
        var result = LlmGenerationRecorder.Result.failure(1,
                new LlmCallException(LlmErrorCode.INVALID_RESPONSE, "stopReason=end_turn, outputTokens=12"), null, 2064);

        assertThat(result.status()).isEqualTo(Status.MODEL_ERROR);
        assertThat(result.errorMessage())
                .isEqualTo("LLM502-1 LLM 응답 형식이 올바르지 않습니다. (stopReason=end_turn, outputTokens=12)");
    }

    private LlmRequest request() {
        return LlmRequest.builder()
                .executionId(42L)
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt("질문")
                .build();
    }
}
