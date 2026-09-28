package com.telme.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.EvidenceCheckProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 검색은 문장 전체 유사도로 걸러 묻는 항목이 근거에 없는 질문도 통과시킨다
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceRelevanceChecker {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final EvidenceCheckProperties properties;

    public boolean canAnswer(Long executionId, String question, String context) {
        if (!properties.enabled()) {
            return true;
        }

        LlmRequest request = LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(EvidenceRelevancePromptTemplates.SYSTEM_PROMPT)
                .userPrompt(EvidenceRelevancePromptTemplates.userPrompt(context, question))
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .maxTokens(properties.maxTokens())
                .promptVersion(EvidenceRelevancePromptTemplates.PROMPT_VERSION)
                .build();

        try {
            return parse(llmClient.generate(request));
        } catch (RuntimeException e) {
            // 판정 실패가 답변을 막지 않게 통과시킨다
            log.warn("[EvidenceRelevanceChecker] 판정 실패, 답변을 진행한다 executionId={}", executionId, e);
            return true;
        }
    }

    private boolean parse(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(json).path("answerable");
            return node.isMissingNode() || node.asBoolean(true);
        } catch (Exception e) {
            log.warn("[EvidenceRelevanceChecker] 판정 응답 파싱 실패: {}", json, e);
            return true;
        }
    }
}
