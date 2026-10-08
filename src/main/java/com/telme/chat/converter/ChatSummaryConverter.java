package com.telme.chat.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ChatTokenEstimator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 모델은 보존할 원문을 선택한다. 저장할 발언과 화자는 코드가 원본에서 가져온다.
@Component
@Slf4j
public class ChatSummaryConverter {
    public static final String FORMAT = "telme-summary-v1";
    private static final int MAX_MEMORY_MESSAGES = 16;
    private static final Pattern CONTEXT_DEPENDENT = Pattern.compile(
            "그\\s*(?:비용|부분|내용|상품|지역|것|거)|^(?:지역|조건)은|(?:아직|그대로).*"
                    + "(?:확인|알아보|답|둘)");
    private final ObjectMapper mapper;

    public ChatSummaryConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<ChatContextMessage> sources(String stored) {
        if (stored == null || !stored.stripLeading().startsWith("{")) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(stored);
            if (!FORMAT.equals(root.path("format").asText())) {
                return List.of();
            }
            Memory memory = mapper.treeToValue(root, Memory.class);
            if (memory.messages() == null || memory.messages().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("요약의 원본 발언이 없습니다.");
            }
            if (memory.messages().stream().map(ChatContextMessage::messageId).distinct().count()
                    != memory.messages().size()) {
                throw new IllegalArgumentException("요약의 원본 메시지 ID가 중복됩니다.");
            }
            return List.copyOf(memory.messages());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("상담 요약 형식이 올바르지 않습니다.", exception);
        }
    }

    public String render(String stored) {
        List<ChatContextMessage> messages = sources(stored);
        if (messages.isEmpty() && !isMemory(stored)) {
            return stored == null || stored.isBlank() ? null : stored.strip();
        }
        String legacy = legacy(stored);
        String prefix = legacy == null ? "" : "[미검증 이전 요약: 최신 고객 원문을 우선]\n" + legacy + "\n";
        String text = prefix + messages.stream().map(this::line).collect(java.util.stream.Collectors.joining("\n"));
        return text.isBlank() ? null : text;
    }

    public String input(String previous, List<ChatContextMessage> incoming) {
        return candidates(previous, incoming).values().stream()
                .sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo))
                .map(this::line).collect(java.util.stream.Collectors.joining("\n"));
    }

    public String validateAndStore(String output, String previous, List<ChatContextMessage> incoming,
            int memoryTokens, ChatTokenEstimator estimator) {
        Map<Long, ChatContextMessage> candidates = candidates(previous, incoming);
        try {
            JsonNode root = mapper.readTree(output);
            if (root == null || !root.isObject() || root.size() != 1
                    || !root.path("messageIds").isArray()
                    || (root.path("messageIds").isEmpty() && !candidates.isEmpty())) {
                throw new IllegalArgumentException("보존할 원본 메시지 ID 배열이 필요합니다.");
            }
            Map<Long, ChatContextMessage> selected = new LinkedHashMap<>();
            var explicitIds = new HashSet<Long>();
            for (JsonNode id : root.path("messageIds")) {
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
                    throw new IllegalArgumentException("요약 메시지 ID가 올바르지 않습니다.");
                }
                ChatContextMessage message = candidates.get(id.longValue());
                if (message == null || !explicitIds.add(id.longValue())) {
                    throw new IllegalArgumentException("입력에 없거나 중복된 요약 메시지 ID입니다.");
                }
                selected.putIfAbsent(id.longValue(), message);
                // 상담사 발언을 선택하면 질문도 함께 보존해 안내 대상이 사라지지 않게 한다.
                if (message.role() == ChatMessage.Role.ASSISTANT) {
                    ChatContextMessage question = candidates.values().stream()
                            .filter(value -> value.role() == ChatMessage.Role.USER
                                    && value.sequenceNo() == message.sequenceNo() - 1)
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("요약 답변의 질문이 없습니다."));
                    selected.putIfAbsent(question.messageId(), question);
                }
            }
            // 생략된 대상이 있는 발언만 남기면 다음 요약에서 원래 상담 주제를 복구할 수 없다.
            // 이때는 제한된 후보 범위의 앞선 고객 원문을 함께 보존한다. 모델이 새 사실을 합성하지 않는다.
            List<ChatContextMessage> dependent = selected.values().stream()
                    .filter(message -> message.role() == ChatMessage.Role.USER && message.content() != null
                            && CONTEXT_DEPENDENT.matcher(message.content()).find()).toList();
            for (ChatContextMessage message : dependent) {
                candidates.values().stream()
                        .filter(value -> value.role() == ChatMessage.Role.USER
                                && value.sequenceNo() < message.sequenceNo())
                        .forEach(value -> selected.putIfAbsent(value.messageId(), value));
            }
            List<ChatContextMessage> messages = selected.values().stream()
                    .sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo)).toList();
            String legacySummary = legacy(previous);
            if (!fits(messages, legacySummary, memoryTokens, estimator)) {
                // 최신 발언부터 보존하되 답변의 질문과 생략된 대상은 함께 남긴다.
                // 한 문장의 일부를 잘라 부정이나 조건을 바꾸지 않는다.
                messages = fitOriginals(messages, candidates, memoryTokens, estimator);
                if (legacySummary != null) {
                    log.warn("상담 요약 예산으로 미검증 이전 요약 제외");
                    legacySummary = null;
                }
            }
            return mapper.writeValueAsString(new Memory(FORMAT, messages, legacySummary));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("요약 선택 결과를 읽을 수 없습니다.", exception);
        }
    }

    private List<ChatContextMessage> fitOriginals(List<ChatContextMessage> selected,
            Map<Long, ChatContextMessage> candidates, int budget, ChatTokenEstimator estimator) {
        Map<Long, ChatContextMessage> retained = new LinkedHashMap<>();
        for (int index = selected.size() - 1; index >= 0; index--) {
            ChatContextMessage message = selected.get(index);
            if (retained.containsKey(message.messageId())) {
                continue;
            }
            List<ChatContextMessage> group = dependencyGroup(message, candidates);
            if (!fits(group, null, budget, estimator) && message.role() == ChatMessage.Role.ASSISTANT) {
                ChatContextMessage question = group.stream()
                        .filter(value -> value.role() == ChatMessage.Role.USER
                                && value.sequenceNo() == message.sequenceNo() - 1)
                        .findFirst().orElseThrow();
                group = dependencyGroup(question, candidates);
                log.warn("상담 요약 저장 예산으로 상담사 원문 제외: messageId={}, sequenceNo={}",
                        message.messageId(), message.sequenceNo());
            }
            Map<Long, ChatContextMessage> proposed = new LinkedHashMap<>(retained);
            group.forEach(value -> proposed.put(value.messageId(), value));
            List<ChatContextMessage> ordered = proposed.values().stream()
                    .sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo)).toList();
            if (!fits(ordered, null, budget, estimator)) {
                // 긴 최신 정정을 빼고 옛 조건만 다시 남기지 않도록 더 오래된 선택도 중단한다.
                log.warn("상담 요약 저장 예산으로 오래된 선택 중단: messageId={}, sequenceNo={}, budget={}",
                        message.messageId(), message.sequenceNo(), budget);
                break;
            }
            retained = proposed;
        }
        return retained.values().stream().sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo)).toList();
    }

    private List<ChatContextMessage> dependencyGroup(ChatContextMessage message,
            Map<Long, ChatContextMessage> candidates) {
        List<ChatContextMessage> group = new ArrayList<>();
        group.add(message);
        ChatContextMessage question = message;
        if (message.role() == ChatMessage.Role.ASSISTANT) {
            question = candidates.values().stream()
                    .filter(value -> value.role() == ChatMessage.Role.USER
                            && value.sequenceNo() == message.sequenceNo() - 1)
                    .findFirst().orElseThrow();
            group.add(question);
        }
        if (question.content() != null && CONTEXT_DEPENDENT.matcher(question.content()).find()) {
            int sequenceNo = question.sequenceNo();
            candidates.values().stream()
                    .filter(value -> value.role() == ChatMessage.Role.USER && value.sequenceNo() < sequenceNo)
                    .forEach(group::add);
        }
        return group.stream().sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo)).toList();
    }

    private boolean fits(List<ChatContextMessage> messages, String legacySummary,
            int budget, ChatTokenEstimator estimator) {
        String prefix = legacySummary == null ? ""
                : "[미검증 이전 요약: 최신 고객 원문을 우선]\n" + legacySummary + "\n";
        String text = prefix + messages.stream().map(this::line)
                .collect(java.util.stream.Collectors.joining("\n"));
        int tokens = estimator.estimatePromptPart(text) + messages.stream()
                .mapToInt(message -> estimator.estimatePromptPart(message.storeResults())).sum();
        return messages.size() <= MAX_MEMORY_MESSAGES && tokens <= budget;
    }

    public List<ChatContextMessage> candidatesFor(String previous, List<ChatContextMessage> incoming) {
        return new ArrayList<>(candidates(previous, incoming).values());
    }

    private Map<Long, ChatContextMessage> candidates(String previous, List<ChatContextMessage> incoming) {
        Map<Long, ChatContextMessage> result = new LinkedHashMap<>();
        for (ChatContextMessage message : sources(previous)) {
            result.put(message.messageId(), message);
        }
        for (ChatContextMessage message : incoming) {
            ChatContextMessage old = result.put(message.messageId(), message);
            if (old != null && !Objects.equals(old, message)) {
                throw new IllegalArgumentException("같은 메시지 ID의 요약 원문이 다릅니다.");
            }
        }
        result.values().removeIf(message -> ChatContextFormatter.isSocial(message.content()));
        return result;
    }

    private String line(ChatContextMessage message) {
        String role = message.role() == ChatMessage.Role.USER ? "고객 원문" : "상담사 이력(정책 근거 아님)";
        return "[%d/%d %s] %s".formatted(message.messageId(), message.sequenceNo(), role,
                message.content() == null ? "매장 결과가 있는 응답" : message.content());
    }

    private String legacy(String previous) {
        if (previous == null || previous.isBlank()) {
            return null;
        }
        if (!isMemory(previous)) {
            return previous.strip();
        }
        try {
            return mapper.readTree(previous).path("legacySummary").isTextual()
                    ? mapper.readTree(previous).path("legacySummary").asText() : null;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("기존 요약을 읽을 수 없습니다.", exception);
        }
    }

    private boolean isMemory(String text) {
        if (text == null || !text.stripLeading().startsWith("{")) return false;
        try {
            return FORMAT.equals(mapper.readTree(text).path("format").asText());
        } catch (JsonProcessingException invalid) {
            return false;
        }
    }

    public record Memory(String format, List<ChatContextMessage> messages, String legacySummary) {}
}
