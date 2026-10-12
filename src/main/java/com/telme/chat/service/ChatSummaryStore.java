package com.telme.chat.service;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatSummaryProperties properties;
    private final ChatTokenEstimator tokenEstimator;
    private final ChatSummaryConverter converter;

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
                Set.of(ChatMessage.MessageType.ERROR, ChatMessage.MessageType.BLOCKED),
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
                Set.of(ChatMessage.MessageType.ERROR, ChatMessage.MessageType.BLOCKED),
                PageRequest.of(0, queryLimit)
        );

        SummarySelection selected = selectMessages(
                candidates,
                Math.min(allowedMessages, candidates.size()),
                normalize(session.getSummary())
        );
        if (selected.throughSequenceNo() <= summaryCursor) {
            return Optional.empty();
        }

        return Optional.of(new ChatSummarySnapshot(
                request.executionId(),
                request.sessionId(),
                selected.previousSummary(),
                summaryCursor,
                selected.throughSequenceNo(),
                selected.messages()
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

    private SummarySelection selectMessages(
            List<ChatMessage> candidates,
            int allowedMessages,
            String previousSummary
    ) {
        if (inputTokens(previousSummary, List.of()) > properties.maxInputTokens()) {
            // 예산 변경이나 오래된 대형 요약 때문에 새 대화 처리가 계속 막히지 않게 한다.
            log.warn("상담 요약 입력 예산으로 기존 요약 제외: budget={}", properties.maxInputTokens());
            previousSummary = null;
        }
        if (inputTokens(null, List.of()) > properties.maxInputTokens()) {
            throw new IllegalArgumentException("상담 요약 입력 예산이 시스템 프롬프트보다 작습니다.");
        }

        List<ChatContextMessage> selected = new ArrayList<>();
        int throughSequenceNo = 0;
        for (int index = 0; index < allowedMessages;) {
            List<ChatMessage> exchange = nextExchange(candidates, index, allowedMessages);
            if (exchange.isEmpty()) {
                break;
            }

            List<ChatContextMessage> converted = new ArrayList<>(exchange.size());
            for (ChatMessage message : exchange) {
                try {
                    ChatContextMessage contextMessage = ChatContextMessage.from(message);
                    converted.add(contextMessage);
                } catch (IllegalArgumentException exception) {
                    log.warn("상담 요약에서 유효하지 않은 메시지 제외: messageId={}, sequenceNo={}, reason={}",
                            message.getMessageId(), message.getSequenceNo(), exception.getMessage());
                    // 읽지 못한 메시지를 건너뛴 뒤 커서를 옮기면 다음 요약에서도 영원히 빠진다.
                    return new SummarySelection(previousSummary, throughSequenceNo, selected);
                }
            }
            List<ChatContextMessage> proposed = new ArrayList<>(selected);
            proposed.addAll(converted);
            if (inputTokens(previousSummary, proposed) > properties.maxInputTokens()) {
                if (inputTokens(null, converted) <= properties.maxInputTokens()) {
                    if (!selected.isEmpty()) {
                        break;
                    }
                    // 이전 기억과 함께만 넘치는 경우 새 원문을 우선해 한 번 진행한다.
                    previousSummary = null;
                    log.warn("상담 요약 새 원문 입력을 위해 기존 요약 제외: sequenceNo={}",
                            converted.getFirst().sequenceNo());
                } else {
                    List<ChatContextMessage> questions = converted.stream()
                            .filter(message -> message.role() == ChatMessage.Role.USER).toList();
                    // 긴 상담사 안내 때문에 짧은 고객 원문까지 버리지 않는다.
                    boolean keepQuestion = !questions.isEmpty()
                            && inputTokens(null, questions) <= properties.maxInputTokens();
                    log.warn("상담 요약 입력 예산 초과 구간 처리: sessionId={}, fromSequenceNo={}, "
                                    + "throughSequenceNo={}, keepQuestion={}, budget={}",
                            exchange.getFirst().getSession().getSessionId(), converted.getFirst().sequenceNo(),
                            converted.getLast().sequenceNo(), keepQuestion, properties.maxInputTokens());
                    if (keepQuestion) {
                        List<ChatContextMessage> withQuestions = new ArrayList<>(selected);
                        withQuestions.addAll(questions);
                        if (inputTokens(previousSummary, withQuestions) > properties.maxInputTokens()) {
                            if (!selected.isEmpty()) {
                                break;
                            }
                            previousSummary = null;
                            log.warn("상담 요약 고객 원문 입력을 위해 기존 요약 제외: sequenceNo={}",
                                    questions.getFirst().sequenceNo());
                        }
                        selected.addAll(questions);
                    } else {
                        // 생략한 장문이 정정일 수 있어 그보다 오래된 조건을 계속 사용하지 않는다.
                        previousSummary = null;
                        selected.clear();
                    }
                    throughSequenceNo = converted.getLast().sequenceNo();
                    index += exchange.size();
                    continue;
                }
            }
            selected.addAll(converted);
            throughSequenceNo = converted.getLast().sequenceNo();
            index += exchange.size();
        }
        return new SummarySelection(previousSummary, throughSequenceNo, selected);
    }

    private int inputTokens(String previousSummary, List<ChatContextMessage> messages) {
        String system = properties.groundedOutput()
                ? ChatSummaryPrompt.SELECTION_SYSTEM_PROMPT : ChatSummaryPrompt.SYSTEM_PROMPT;
        String input = properties.groundedOutput()
                ? ChatSummaryPrompt.buildSelectionPrompt(previousSummary, messages,
                        properties.maxOutputTokens(), converter)
                : ChatSummaryPrompt.buildUserPrompt(converter.render(previousSummary), messages);
        return tokenEstimator.estimatePromptPart(system) + tokenEstimator.estimatePromptPart(input);
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

    private record SummarySelection(String previousSummary, int throughSequenceNo, List<ChatContextMessage> messages) {
    }
}
