package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.EvidenceCheckProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvidenceRelevanceCheckerTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final EvidenceRelevanceChecker checker = new EvidenceRelevanceChecker(
            llmClient, new ObjectMapper(), new EvidenceCheckProperties(true, 300));

    @Test
    void recordsRelevanceCheckAsEvidenceCheckNotRouting() {
        when(llmClient.generate(any())).thenReturn("{\"answerable\":false}");

        boolean answerable = checker.canAnswer(42L, "유심 재발급 비용은?", "Q: 유심 재발급 비용\nA: 7,700원");

        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(request.capture());
        assertThat(answerable).isFalse();
        assertThat(request.getValue().executionId()).isEqualTo(42L);
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.EVIDENCE_CHECK);
    }
}
