package com.telme.chat.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

// 모니터링 목록 SQL 한 줄 → AdminChatMonitoringRow
@Component
@RequiredArgsConstructor
public class AdminChatMonitoringRowMapper implements RowMapper<AdminChatMonitoringRow> {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONGS = new TypeReference<>() {};
    private static final TypeReference<List<Boolean>> BOOLEANS = new TypeReference<>() {};

    private final ObjectMapper mapper;

    @Override
    public AdminChatMonitoringRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String actions = rs.getString("input_actions");
        return AdminChatMonitoringRow.builder()
                .messageId(rs.getLong("message_id"))
                .messageType(rs.getString("message_type"))
                .sessionId(rs.getLong("session_id"))
                .content(rs.getString("content"))
                .messageCreatedAt(instant(rs, "message_created_at"))
                .userId(rs.getObject("user_id", Long.class))
                .userName(rs.getString("user_name"))
                .guestId(rs.getString("guest_id"))
                .executionId(rs.getObject("execution_id", Long.class))
                .executionStatus(rs.getString("execution_status"))
                .startedAt(instant(rs, "started_at"))
                .endedAt(instant(rs, "ended_at"))
                .totalMs(rs.getObject("total_ms", Integer.class))
                .errorCode(rs.getString("error_code"))
                .answerType(rs.getString("answer_type"))
                .answerBasis(rs.getString("answer_basis"))
                .intent(rs.getString("intent"))
                .inputActions(actions == null ? List.of() : Arrays.asList(actions.split(",")))
                .analysisAction(rs.getString("analysis_action"))
                .searchRequestCount(rs.getInt("search_request_count"))
                .lastSearchGrayZone(rs.getBoolean("last_search_gray_zone"))
                .searchStatuses(list(rs, "search_statuses", STRINGS))
                .sourceFaqIds(list(rs, "source_faq_ids", LONGS))
                .refinedSearched(list(rs, "refined_searched", BOOLEANS))
                .judgmentKept(list(rs, "judgment_kept", BOOLEANS))
                .judgmentVerdicts(list(rs, "judgment_verdicts", STRINGS))
                .planAnswer(rs.getBoolean("plan_answer"))
                .comparisonAnswer(rs.getBoolean("comparison_answer"))
                .faqCandidateAnswer(rs.getBoolean("faq_candidate_answer"))
                .generationStarted(rs.getBoolean("generation_started"))
                .guardOutcome(rs.getString("guard_outcome"))
                .guardReason(rs.getString("guard_reason"))
                .outputSafetyOutcome(rs.getString("output_safety_outcome"))
                .finalTransmissionStatus(rs.getString("final_transmission_status"))
                .lastStage(rs.getString("last_stage"))
                .build();
    }

    // JSONPath 결과 배열 → List (실행 없음·형식 오류는 빈 목록)
    private <T> List<T> list(ResultSet rs, String column, TypeReference<List<T>> type) throws SQLException {
        String json = rs.getString(column);
        if (json == null) return List.of();
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
