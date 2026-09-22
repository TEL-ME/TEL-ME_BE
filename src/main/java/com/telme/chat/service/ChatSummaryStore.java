package com.telme.chat.service;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
class ChatSummaryStore {

    private static final int PROMPT_OVERHEAD_TOKENS = 128;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatSummaryProperties properties;
    private final ChatTokenEstimator tokenEstimator;

    @Transactional(readOnly = true)
    public Optional<ChatSummarySnapshot> prepare(ChatSummaryRequested request) {
        ChatExecution execution = chatExecutionRepository.findContextExecution(request.executionId())
                .orElse(null);
        if (execution == null
                || execution.getStatus() != ChatExecution.Status.COMPLETED
                || !Objects.equals(execution.getSession().getSessionId(), request.sessionId())) {
            return Optional.empty();
        }

        ChatSession session = execution.getSession();
        int summaryCursor = session.getSummaryThroughSequenceNo();
        List<ChatMessage> recentCandidates = chatMessageRepository.findCompletedContextMessagesBefore(
                request.sessionId(),
                summaryCursor,
                nextSequenceNo(request.completedThroughSequenceNo()),
                ChatMessage.Status.COMPLETED,
                ChatMessage.MessageType.ERROR,
                PageRequest.of(0, triggerQueryLimit())
        );
        Integer summarizeThrough = findSummarizeThrough(recentCandidates);
        if (summarizeThrough == null || summarizeThrough <= summaryCursor) {
            return Optional.empty();
        }

        int allowedMessages = properties.maxBatchMessages();
        int queryLimit = allowedMessages == Integer.MAX_VALUE ? allowedMessages : allowedMessages + 1;
        List<ChatMessage> candidates = chatMessageRepository.findOldestCompletedSummaryMessagesAfter(
                request.sessionId(),
                summaryCursor,
                summarizeThrough,
                ChatMessage.Status.COMPLETED,
                ChatMessage.MessageType.ERROR,
                PageRequest.of(0, queryLimit)
        );

        List<ChatContextMessage> selected = selectMessages(
                candidates,
                Math.min(allowedMessages, candidates.size()),
                normalize(session.getSummary())
        );
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ChatSummarySnapshot(
                request.executionId(),
                request.sessionId(),
                normalize(session.getSummary()),
                summaryCursor,
                selected.getLast().sequenceNo(),
                selected
        ));
    }

    @Transactional
    public boolean saveIfCurrent(ChatSummarySnapshot snapshot, String summary) {
        return chatSessionRepository.updateSummaryIfCurrent(
                snapshot.sessionId(),
                summary,
                snapshot.expectedSequenceNo(),
                snapshot.throughSequenceNo()
        ) == 1;
    }

    private List<ChatContextMessage> selectMessages(
            List<ChatMessage> candidates,
            int allowedMessages,
            String previousSummary
    ) {
        int remainingTokens = properties.maxInputTokens()
                - PROMPT_OVERHEAD_TOKENS
                - tokenEstimator.estimatePromptPart(previousSummary);
        if (remainingTokens < 1) {
            log.warn("상담 요약 입력 예산이 기존 요약만으로 소진됨");
            return List.of();
        }

        List<ChatContextMessage> selected = new ArrayList<>();
        for (int index = 0; index < allowedMessages;) {
            List<ChatMessage> exchange = nextExchange(candidates, index, allowedMessages);
            if (exchange.isEmpty()) {
                break;
            }

            List<ChatContextMessage> converted = new ArrayList<>(exchange.size());
            int exchangeTokens = 0;
            for (ChatMessage message : exchange) {
                try {
                    ChatContextMessage contextMessage = ChatContextMessage.from(message);
                    converted.add(contextMessage);
                    exchangeTokens += tokenEstimator.estimate(contextMessage);
                } catch (IllegalArgumentException exception) {
                    log.warn("상담 요약에서 유효하지 않은 메시지 제외: messageId={}, sequenceNo={}, reason={}",
                            message.getMessageId(), message.getSequenceNo(), exception.getMessage());
                }
            }
            if (exchangeTokens > remainingTokens) {
                break;
            }
            selected.addAll(converted);
            remainingTokens -= exchangeTokens;
            index += exchange.size();
        }
        return selected;
    }

    private Integer findSummarizeThrough(List<ChatMessage> candidates) {
        if (!shouldSummarize(candidates)) {
            return null;
        }

        List<ChatContextMessage> retained = new ArrayList<>();
        int retainedTokens = 0;
        int index = 0;
        while (index < candidates.size()) {
            ContextExchange exchange = nextRecentExchange(candidates, index);
            index += exchange.consumedCandidates();
            if (exchange.messages().isEmpty()) {
                continue;
            }

            int exchangeTokens = exchange.messages().stream()
                    .mapToInt(tokenEstimator::estimate)
                    .sum();
            if (retained.size() + exchange.messages().size() > properties.retainedMessages()
                    || retainedTokens + exchangeTokens > properties.retainedTokens()) {
                break;
            }
            retained.addAll(exchange.messages());
            retainedTokens += exchangeTokens;
        }

        if (retained.isEmpty()) {
            return candidates.getFirst().getSequenceNo();
        }
        return retained.stream()
                .mapToInt(ChatContextMessage::sequenceNo)
                .min()
                .orElseThrow() - 1;
    }

    private boolean shouldSummarize(List<ChatMessage> candidates) {
        int messageCount = 0;
        int tokenCount = 0;
        for (int index = 0; index < candidates.size();) {
            ContextExchange exchange = nextRecentExchange(candidates, index);
            index += exchange.consumedCandidates();
            if (exchange.messages().isEmpty()) {
                continue;
            }
            messageCount += exchange.messages().size();
            tokenCount += exchange.messages().stream()
                    .mapToInt(tokenEstimator::estimate)
                    .sum();
            if (messageCount >= properties.triggerMessages()
                    || tokenCount >= properties.triggerTokens()) {
                return true;
            }
        }
        return false;
    }

    private ContextExchange nextRecentExchange(List<ChatMessage> candidates, int index) {
        ChatMessage newest = candidates.get(index);
        if (newest.getRole() != ChatMessage.Role.ASSISTANT) {
            return new ContextExchange(toContextMessages(List.of(newest)), 1);
        }
        if (index + 1 >= candidates.size()) {
            return new ContextExchange(List.of(), 1);
        }

        ChatMessage question = candidates.get(index + 1);
        boolean paired = question.getRole() == ChatMessage.Role.USER
                && newest.getReplyTo() != null
                && Objects.equals(newest.getReplyTo().getMessageId(), question.getMessageId());
        return paired
                ? new ContextExchange(toContextMessages(List.of(newest, question)), 2)
                : new ContextExchange(List.of(), 1);
    }

    private List<ChatContextMessage> toContextMessages(List<ChatMessage> messages) {
        List<ChatContextMessage> converted = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            try {
                converted.add(ChatContextMessage.from(message));
            } catch (IllegalArgumentException exception) {
                log.warn("상담 요약 범위 계산에서 유효하지 않은 메시지 제외: messageId={}, sequenceNo={}, reason={}",
                        message.getMessageId(), message.getSequenceNo(), exception.getMessage());
                return List.of();
            }
        }
        return converted;
    }

    private int triggerQueryLimit() {
        int configuredLimit = properties.triggerMessages();
        return configuredLimit == Integer.MAX_VALUE ? configuredLimit : configuredLimit + 1;
    }

    private int nextSequenceNo(int sequenceNo) {
        return sequenceNo == Integer.MAX_VALUE ? Integer.MAX_VALUE : sequenceNo + 1;
    }

    private List<ChatMessage> nextExchange(List<ChatMessage> candidates, int index, int allowedMessages) {
        ChatMessage current = candidates.get(index);
        if (current.getRole() != ChatMessage.Role.USER || index + 1 >= candidates.size()) {
            return List.of(current);
        }

        ChatMessage next = candidates.get(index + 1);
        boolean paired = next.getRole() == ChatMessage.Role.ASSISTANT
                && next.getReplyTo() != null
                && Objects.equals(next.getReplyTo().getMessageId(), current.getMessageId());
        if (!paired) {
            return List.of(current);
        }
        if (index + 1 >= allowedMessages) {
            return List.of();
        }
        return List.of(current, next);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ContextExchange(List<ChatContextMessage> messages, int consumedCandidates) {
    }
}
