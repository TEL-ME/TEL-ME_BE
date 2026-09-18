package com.telme.consult.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;

import lombok.RequiredArgsConstructor;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
public class LlmClarificationTextGenerator implements ClarificationTextGenerator {
    private final LlmClient llmClient;

    @Override
    public String generate(ClarificationPrompt prompt) {
        var request =
                LlmRequest.builder()
                        .taskType(TaskType.CLARIFICATION)
                        .systemPrompt(prompt.systemPrompt())
                        .userPrompt(prompt.userPrompt())
                        .format(ResponseFormat.TEXT)
                        .temperature(0.0)
                        .maxTokens(128)
                        .build();
        try {
            return llmClient.generate(request);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 429) {
                throw new GenerationUnavailableException("되묻기 모델이 일시적으로 응답할 수 없습니다.", exception);
            }
            throw exception;
        } catch (GeneralException exception) {
            // 공통 LLM 오류 코드는 모두 고정 질문으로 이어간다.
            if (exception.getErrorCode() instanceof LlmErrorCode) {
                throw new GenerationUnavailableException("되묻기 모델을 사용할 수 없습니다.", exception);
            }
            throw exception;
        } catch (ResourceAccessException exception) {
            // 연결 실패면 고정 질문으로 이어간다.
            throw new GenerationUnavailableException("되묻기 모델에 연결할 수 없습니다.", exception);
        }
    }
}
