package com.telme.chat.service;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import com.telme.global.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatSummaryService {

    private final ChatSummaryStore chatSummaryStore;
    private final LlmClient llmClient;
    private final ChatSummaryProperties properties;

    public boolean summarizeIfNeeded(ChatSummaryRequested request) {
        return chatSummaryStore.prepare(request)
                .map(this::generateAndSave)
                .orElse(false);
    }

    private boolean generateAndSave(ChatSummarySnapshot snapshot) {
        // TODO: 내용 검증이 필요해지면 고객 조건, 안내 내용, 미해결 질문을 구조화된 응답으로 강제한다.
        LlmRequest request = LlmRequest.builder()
                .executionId(snapshot.executionId())
                .taskType(TaskType.SUMMARY)
                .systemPrompt(ChatSummaryPrompt.SYSTEM_PROMPT)
                .userPrompt(ChatSummaryPrompt.buildUserPrompt(snapshot.previousSummary(), snapshot.messages()))
                .temperature(0.2)
                .maxTokens(properties.maxOutputTokens())
                .build();

        String summary = ChatSummaryNormalizer.normalize(llmClient.generate(request));
        if (summary == null) {
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }

        boolean saved = chatSummaryStore.saveIfCurrent(snapshot, summary);
        if (!saved) {
            log.info("더 최신 상담 요약이 있어 생성 결과를 저장하지 않음: sessionId={}, expectedSequenceNo={}",
                    snapshot.sessionId(), snapshot.expectedSequenceNo());
        }
        return saved;
    }
}
