package com.telme.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 검색 회색 구간의 FAQ 하나가 고객 질문에 답이 되는지 판정한다(evidence-check-v4, docs/GRAY_ZONE_UPPER_BAND.md)
// 실패(시간 초과·호출 오류·응답 해석 실패)는 답 됨/안 됨과 구분해 돌려준다. 실패를 어떻게 처리할지는 점수 구간마다
// 다르므로 호출하는 쪽이 정한다. 판정 LLM은 Bedrock이 켜졌을 때만 있어 이 판정기도 그때만 만든다
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.bedrock.enabled", havingValue = "true")
public class FaqEvidenceJudge {

    public enum Verdict {
        ANSWERABLE, NOT_ANSWERABLE, FAILED
    }

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public FaqEvidenceJudge(
            @Qualifier(BedrockConfig.EVIDENCE_JUDGE_LLM_CLIENT) LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Verdict judge(Long executionId, Long consultRequestId, String question, String faqQuestion, String faqAnswer) {
        LlmRequest request = LlmRequest.builder()
                .executionId(executionId)
                .consultRequestId(consultRequestId)
                .taskType(TaskType.EVIDENCE_CHECK)
                .systemPrompt(FaqEvidencePromptTemplates.SYSTEM_PROMPT)
                .userPrompt(FaqEvidencePromptTemplates.userPrompt(faqQuestion, faqAnswer, question))
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .contextCount(1)
                .promptVersion(FaqEvidencePromptTemplates.PROMPT_VERSION)
                .build();
        String response;
        try {
            response = llmClient.generate(request);
        } catch (RuntimeException e) {
            log.warn("[FaqEvidenceJudge] 판정 호출 실패 executionId={} type={}",
                    executionId, e.getClass().getSimpleName());
            return Verdict.FAILED;
        }
        Verdict verdict = parse(response);
        if (verdict == Verdict.FAILED) {
            log.warn("[FaqEvidenceJudge] 판정 응답 해석 실패 executionId={}", executionId);
        }
        return verdict;
    }

    // 실험 스크립트와 같은 범위(처음 '{'부터 마지막 '}')를 읽는다. 다만 실험은 해석 실패를 '답 됨'으로 봤고,
    // 여기서는 실패로 돌려 구간 규칙을 따르게 한다(실험 판정 1,227회 중 해석 실패 0회)
    Verdict parse(String text) {
        if (text == null) {
            return Verdict.FAILED;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return Verdict.FAILED;
        }
        try {
            JsonNode answerable = objectMapper.readTree(text.substring(start, end + 1)).path("answerable");
            if (answerable.isBoolean()) {
                return answerable.booleanValue() ? Verdict.ANSWERABLE : Verdict.NOT_ANSWERABLE;
            }
            if (answerable.isTextual()) {
                String value = answerable.textValue().strip().toLowerCase();
                if (value.equals("true")) {
                    return Verdict.ANSWERABLE;
                }
                if (value.equals("false")) {
                    return Verdict.NOT_ANSWERABLE;
                }
            }
            return Verdict.FAILED;
        } catch (Exception e) {
            return Verdict.FAILED;
        }
    }
}
