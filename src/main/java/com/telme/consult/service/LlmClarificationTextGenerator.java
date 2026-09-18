package com.telme.consult.service;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
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
        } catch (IllegalStateException exception) {
            // 공통 오류 타입 도입 전에는 Ollama의 두 응답 오류만 구분한다.
            if ("Ollama 응답에 message가 없음".equals(exception.getMessage())
                    || "Ollama 응답이 완료되지 않음".equals(exception.getMessage())) {
                throw new GenerationUnavailableException("되묻기 모델 응답이 올바르지 않습니다.", exception);
            }
            throw exception;
        } catch (ResourceAccessException exception) {
            // 연결 실패면 고정 질문으로 이어간다.
            throw new GenerationUnavailableException("되묻기 모델에 연결할 수 없습니다.", exception);
        }
    }
}
