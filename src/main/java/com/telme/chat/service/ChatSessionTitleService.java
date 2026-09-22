package com.telme.chat.service;

import com.telme.chat.config.ChatSessionTitleProperties;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
class ChatSessionTitleService {

    private final LlmClient llmClient;
    private final ChatSessionTitleStore titleStore;
    private final ChatSessionTitleProperties properties;

    public boolean generateIfMissing(ChatSessionTitleRequested requested) {
        if (titleStore.hasTitle(requested.sessionId())) {
            return false;
        }

        LlmRequest request = LlmRequest.builder()
                .executionId(requested.executionId())
                .taskType(TaskType.SESSION_TITLE)
                .systemPrompt(ChatSessionTitlePrompt.systemPrompt(properties.maxLength()))
                .userPrompt(ChatSessionTitlePrompt.buildUserPrompt(requested.question()))
                .temperature(0.2)
                .maxTokens(properties.maxOutputTokens())
                .build();

        String generated = llmClient.generate(request);
        String title = normalize(generated);
        if (title == null) {
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }

        boolean saved = titleStore.saveIfMissing(requested.sessionId(), title);
        if (!saved) {
            log.info("세션 제목이 이미 지정되어 자동 생성 결과를 저장하지 않음: sessionId={}",
                    requested.sessionId());
        }
        return saved;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .replaceAll("\\s+", " ")
                .replaceFirst("^제목\\s*:\\s*", "");
        normalized = removeWrappingQuotes(normalized.trim());
        if (normalized.isBlank()) {
            return null;
        }

        int maxLength = properties.maxLength();
        if (normalized.codePointCount(0, normalized.length()) <= maxLength) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, maxLength);
        return normalized.substring(0, endIndex).stripTrailing();
    }

    private String removeWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        boolean wrapped = (first == '"' && last == '"')
                || (first == '\'' && last == '\'')
                || (first == '“' && last == '”')
                || (first == '‘' && last == '’');
        return wrapped ? value.substring(1, value.length() - 1).trim() : value;
    }
}
