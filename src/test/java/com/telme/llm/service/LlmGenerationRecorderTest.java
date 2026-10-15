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
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.entity.LlmGeneration.TaskType;
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

    private LlmRequest request() {
        return LlmRequest.builder()
                .executionId(42L)
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt("질문")
                .build();
    }
}
