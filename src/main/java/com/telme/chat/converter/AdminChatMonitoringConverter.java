package com.telme.chat.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.telme.chat.dto.res.AdminChatMonitoringDetailResponse;
import com.telme.chat.dto.res.AdminChatMonitoringListItemResponse;
import com.telme.chat.dto.res.AdminChatMonitoringListResponse;
import com.telme.chat.dto.res.AdminChatMonitoringStagesResponse;
import com.telme.chat.dto.res.AdminChatMonitoringTimelineResponse;
import com.telme.chat.dto.res.ChatExecutionTraceResponse;
import com.telme.chat.repository.AdminChatMonitoringRow;
import com.telme.chat.repository.ChatExecutionTraceRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class AdminChatMonitoringConverter {

    // 목록 질문 미리보기 길이
    private static final int QUESTION_PREVIEW_LENGTH = 40;
    private static final String BLOCKED = "BLOCKED";

    public AdminChatMonitoringListResponse toListResponse(
            List<AdminChatMonitoringListItemResponse> items, boolean hasNext) {
        Long nextBeforeId = hasNext && !items.isEmpty() ? items.get(items.size() - 1).messageId() : null;
        return new AdminChatMonitoringListResponse(items, nextBeforeId);
    }

    public AdminChatMonitoringListItemResponse toListItem(
            AdminChatMonitoringRow row, AdminChatMonitoringStagesResponse stages) {
        return AdminChatMonitoringListItemResponse.builder()
                .messageId(row.messageId())
                .executionId(row.executionId())
                .sessionId(row.sessionId())
                .userId(row.userId())
                .userName(row.userName())
                .guestId(row.userId() == null ? row.guestId() : null)
                .status(row.blocked() ? BLOCKED : row.executionStatus())
                .startedAt(startedAt(row))
                .endedAt(row.endedAt())
                .elapsedMs(elapsedMs(row))
                .questionPreview(preview(row.content()))
                .intent(row.intent())
                .answerBasis(row.answerBasis())
                .errorCode(row.errorCode())
                .lastStage(row.lastStage())
                .stages(stages)
                .build();
    }

    public AdminChatMonitoringDetailResponse toDetail(ChatExecutionTraceResponse trace,
            AdminChatMonitoringRow row, AdminChatMonitoringStagesResponse stages) {
        return AdminChatMonitoringDetailResponse.builder()
                .executionId(trace.executionId())
                .sessionId(trace.sessionId())
                .inputMessageId(trace.inputMessageId())
                .outputMessageId(trace.outputMessageId())
                .userId(row.userId())
                .userName(row.userName())
                .guestId(row.userId() == null ? row.guestId() : null)
                .status(trace.status())
                .errorCode(trace.errorCode())
                .startedAt(row.startedAt())
                .endedAt(row.endedAt())
                .elapsedMs(elapsedMs(row))
                .originalUserMessage(trace.originalUserMessage())
                .finalAnswer(trace.finalAnswer())
                .answerStatus(trace.answerStatus())
                .answerBasis(trace.answerBasis())
                .inputCheckActions(row.inputActions())
                .stages(stages)
                .timeline(timeline(trace.steps(), row.startedAt()))
                .traceRecorded(trace.traceRecorded())
                .steps(trace.steps())
                .routing(trace.routing())
                .modelAttempts(trace.modelAttempts().stream().map(this::withInstant).toList())
                .build();
    }

    List<AdminChatMonitoringTimelineResponse> timeline(JsonNode steps, Instant startedAt) {
        JsonNode entries = steps == null ? null : steps.path(ChatExecutionTraceRepository.TIMELINE);
        if (entries == null || !entries.isArray()) return List.of();
        return StreamSupport.stream(entries.spliterator(), false)
                .map(entry -> {
                    Instant at = parse(entry.path("at").asText(null));
                    Long elapsed = at == null || startedAt == null ? null : Duration.between(startedAt, at).toMillis();
                    return new AdminChatMonitoringTimelineResponse(entry.path("stage").asText(null), at, elapsed);
                })
                .toList();
    }

    // ISO 오프셋 문자열 → Instant (실패 시 null)
    private Instant parse(String at) {
        if (at == null) return null;
        try {
            return OffsetDateTime.parse(at).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // createdAt Timestamp → Instant
    private Map<String, Object> withInstant(Map<String, Object> attempt) {
        if (!(attempt.get("createdAt") instanceof Timestamp createdAt)) return attempt;
        Map<String, Object> result = new LinkedHashMap<>(attempt);
        result.put("createdAt", createdAt.toInstant());
        return result;
    }

    // 차단 행은 메시지 시각을 시작으로
    private Instant startedAt(AdminChatMonitoringRow row) {
        return row.startedAt() != null ? row.startedAt() : row.messageCreatedAt();
    }

    // 소요 시간은 관리자 응답 속도와 같은 total_ms를 쓴다. 시작·종료 시각 차이는 시계가 달라 틀릴 수 있다.
    // 진행 중이거나 측정 전 실행은 0으로 꾸미지 않고 null로 둔다
    private Long elapsedMs(AdminChatMonitoringRow row) {
        return row.totalMs() == null ? null : row.totalMs().longValue();
    }

    private String preview(String text) {
        if (text == null || text.length() <= QUESTION_PREVIEW_LENGTH) {
            return text;
        }
        // 이모지 반쪽 잘림 방지
        int end = Character.isHighSurrogate(text.charAt(QUESTION_PREVIEW_LENGTH - 1))
                ? QUESTION_PREVIEW_LENGTH - 1
                : QUESTION_PREVIEW_LENGTH;
        return text.substring(0, end) + "...";
    }
}
