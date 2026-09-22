package com.telme.chat.service;

import com.telme.chat.config.ChatContextProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatContextBuilder {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatContextProperties chatContextProperties;
    private final ChatTokenEstimator chatTokenEstimator;

    public ChatContext build(ChatProcessingCommand command, int availableContextTokens) {
        Objects.requireNonNull(command, "command");
        if (availableContextTokens < 1) {
            throw new IllegalArgumentException("Context 토큰 예산은 1 이상이어야 합니다.");
        }

        ChatExecution execution = findExecution(command);
        ChatMessage inputMessage = execution.getInputMessage();
        validateInputMessage(inputMessage);
        String currentQuestion = inputMessage.getContent();
        String summary = normalizeSummary(execution.getSession().getSummary());
        int currentQuestionTokens = chatTokenEstimator.estimatePromptPart(currentQuestion);
        if (currentQuestionTokens > availableContextTokens) {
            throw new IllegalArgumentException("현재 질문이 Context 토큰 예산을 초과합니다.");
        }

        int summaryTokens = chatTokenEstimator.estimatePromptPart(summary);
        if (currentQuestionTokens + summaryTokens > availableContextTokens) {
            log.warn("Context 토큰 예산을 초과한 세션 요약 제외: sessionId={}, summaryTokens={}, budget={}",
                    command.sessionId(), summaryTokens, availableContextTokens);
            summary = null;
            summaryTokens = 0;
        }
        int historyTokenBudget = Math.min(
                availableContextTokens - currentQuestionTokens - summaryTokens,
                chatContextProperties.maxHistoryTokens()
        );

        List<ChatMessage> candidates = chatMessageRepository.findCompletedContextMessagesBefore(
                command.sessionId(),
                execution.getSession().getSummaryThroughSequenceNo(),
                inputMessage.getSequenceNo(),
                ChatMessage.Status.COMPLETED,
                ChatMessage.MessageType.ERROR,
                PageRequest.of(0, historyQueryLimit())
        );

        List<ChatContextMessage> selected = new ArrayList<>();
        int estimatedTokens = 0;
        for (int index = 0; index < candidates.size();) {
            if (selected.size() >= chatContextProperties.maxHistoryMessages()) {
                break;
            }

            ContextExchange exchange = nextExchange(candidates, index);
            index += exchange.consumedCandidates();
            if (exchange.messages().isEmpty()) {
                continue;
            }

            int exchangeTokens = exchange.messages().stream()
                    .mapToInt(chatTokenEstimator::estimate)
                    .sum();
            if (selected.size() + exchange.messages().size() > chatContextProperties.maxHistoryMessages()
                    || estimatedTokens + exchangeTokens > historyTokenBudget) {
                break;
            }
            selected.addAll(exchange.messages());
            estimatedTokens += exchangeTokens;
        }
        Collections.reverse(selected);

        return new ChatContext(
                command.sessionId(),
                inputMessage.getMessageId(),
                summary,
                selected,
                currentQuestion,
                currentQuestionTokens + summaryTokens + estimatedTokens
        );
    }

    private int historyQueryLimit() {
        int configuredLimit = chatContextProperties.maxHistoryMessages();
        return configuredLimit == Integer.MAX_VALUE ? configuredLimit : configuredLimit + 1;
    }

    private String normalizeSummary(String summary) {
        return summary == null || summary.isBlank() ? null : summary;
    }

    private ChatExecution findExecution(ChatProcessingCommand command) {
        ChatExecution execution = chatExecutionRepository.findContextExecution(command.executionId())
                .orElseThrow(() -> new IllegalArgumentException("채팅 실행을 찾을 수 없습니다."));
        if (!execution.isRunning()
                || !Objects.equals(execution.getSession().getSessionId(), command.sessionId())
                || !Objects.equals(execution.getInputMessage().getMessageId(), command.inputMessageId())
                || !Objects.equals(
                        execution.getInputMessage().getSession().getSessionId(),
                        execution.getSession().getSessionId())) {
            throw new IllegalArgumentException("실행 정보와 Context 기준 메시지가 일치하지 않습니다.");
        }
        return execution;
    }

    private ContextExchange nextExchange(List<ChatMessage> candidates, int index) {
        ChatMessage newest = candidates.get(index);
        if (newest.getRole() != ChatMessage.Role.ASSISTANT) {
            ChatContextMessage message = toContextMessage(newest);
            return new ContextExchange(message == null ? List.of() : List.of(message), 1);
        }

        if (index + 1 >= candidates.size()) {
            log.warn("Context에서 연결된 사용자 질문이 없는 어시스턴트 메시지 제외: messageId={}, sequenceNo={}",
                    newest.getMessageId(), newest.getSequenceNo());
            return new ContextExchange(List.of(), 1);
        }

        ChatMessage question = candidates.get(index + 1);
        if (!isReplyTo(newest, question)) {
            log.warn("Context에서 연결된 사용자 질문이 없는 어시스턴트 메시지 제외: messageId={}, sequenceNo={}",
                    newest.getMessageId(), newest.getSequenceNo());
            return new ContextExchange(List.of(), 1);
        }

        ChatContextMessage answer = toContextMessage(newest);
        ChatContextMessage userQuestion = toContextMessage(question);
        if (answer == null || userQuestion == null) {
            return new ContextExchange(List.of(), 2);
        }
        return new ContextExchange(List.of(answer, userQuestion), 2);
    }

    private boolean isReplyTo(ChatMessage answer, ChatMessage question) {
        return question.getRole() == ChatMessage.Role.USER
                && answer.getReplyTo() != null
                && Objects.equals(answer.getReplyTo().getMessageId(), question.getMessageId());
    }

    private ChatContextMessage toContextMessage(ChatMessage message) {
        try {
            return ChatContextMessage.from(message);
        } catch (IllegalArgumentException exception) {
            log.warn("Context에서 유효하지 않은 채팅 메시지 제외: messageId={}, sequenceNo={}, reason={}",
                    message.getMessageId(), message.getSequenceNo(), exception.getMessage());
            return null;
        }
    }

    private void validateInputMessage(ChatMessage inputMessage) {
        if (inputMessage.getRole() != ChatMessage.Role.USER
                || inputMessage.getMessageType() != ChatMessage.MessageType.QUESTION
                || inputMessage.getStatus() != ChatMessage.Status.COMPLETED
                || inputMessage.getContent() == null
                || inputMessage.getContent().isBlank()) {
            throw new IllegalArgumentException("완료된 사용자 질문만 Context 기준 메시지로 사용할 수 있습니다.");
        }
    }

    private record ContextExchange(List<ChatContextMessage> messages, int consumedCandidates) {
    }
}
