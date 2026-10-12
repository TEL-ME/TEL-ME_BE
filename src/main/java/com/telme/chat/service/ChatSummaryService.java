package com.telme.chat.service;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import com.telme.global.common.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class ChatSummaryService {

    private final ChatSummaryStore chatSummaryStore;
    private final LlmClient llmClient;
    private final ChatSummaryProperties properties;
    private final ChatSummaryConverter converter;

    @Autowired
    public ChatSummaryService(ChatSummaryStore store, LlmClient client, ChatSummaryProperties properties,
            ChatSummaryConverter converter) {
        this.chatSummaryStore = store;
        this.llmClient = client;
        this.properties = properties;
        this.converter = converter;
    }

    public ChatSummaryService(ChatSummaryStore store, LlmClient client, ChatSummaryProperties properties) {
        this(store, client, properties, new ChatSummaryConverter(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    public boolean summarizeIfNeeded(ChatSummaryRequested request) {
        return chatSummaryStore.prepare(request)
                .map(this::generateAndSave)
                .orElse(false);
    }

    private boolean generateAndSave(ChatSummarySnapshot snapshot) {
        ChatTokenEstimator estimator = new ChatTokenEstimator();
        if (properties.groundedOutput()
                && converter.candidatesFor(snapshot.previousSummary(), snapshot.messages()).isEmpty()) {
            String emptyMemory = converter.validateAndStore("{\"messageIds\":[]}", snapshot.previousSummary(),
                    snapshot.messages(), properties.maxOutputTokens(), estimator);
            return chatSummaryStore.saveIfCurrent(snapshot, emptyMemory);
        }
        String input = properties.groundedOutput()
                ? ChatSummaryPrompt.buildSelectionPrompt(snapshot.previousSummary(), snapshot.messages(),
                        properties.maxOutputTokens(), converter)
                : ChatSummaryPrompt.buildUserPrompt(converter.render(snapshot.previousSummary()), snapshot.messages());
        String system = properties.groundedOutput()
                ? ChatSummaryPrompt.SELECTION_SYSTEM_PROMPT : ChatSummaryPrompt.SYSTEM_PROMPT;
        if (estimator.estimatePromptPart(system) + estimator.estimatePromptPart(input) > properties.maxInputTokens()) {
            throw new IllegalArgumentException("상담 요약의 실제 입력이 토큰 예산을 초과합니다.");
        }
        LlmRequest request = LlmRequest.builder()
                .executionId(snapshot.executionId())
                .taskType(TaskType.SUMMARY)
                .systemPrompt(system)
                .userPrompt(input)
                .format(properties.groundedOutput() ? ResponseFormat.JSON : ResponseFormat.TEXT)
                .promptVersion(properties.groundedOutput() ? "summary-grounded-v1" : "summary-text-v2")
                .temperature(0.0)
                .maxTokens(properties.maxOutputTokens())
                .build();

        String generated = llmClient.generate(request);
        String summary = properties.groundedOutput()
                ? converter.validateAndStore(generated, snapshot.previousSummary(), snapshot.messages(),
                        properties.maxOutputTokens(), estimator)
                : ChatSummaryNormalizer.normalize(generated);
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
