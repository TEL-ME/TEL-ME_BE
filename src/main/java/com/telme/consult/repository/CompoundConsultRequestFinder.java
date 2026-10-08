package com.telme.consult.repository;

import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
public class CompoundConsultRequestFinder {
    private final JdbcTemplate jdbc;

    // 후속 답변으로 선택한 상담과 같은 원문에서 만들어진 요청만 함께 재개한다.
    public List<IntentSubQueryResponse> findOpenGroup(long sessionId, long requestId) {
        var rows = jdbc.query("""
                SELECT sibling.consult_request_id, sibling.subquery_order, sibling.intent,
                       sibling.query_text, sibling.status
                FROM consult_requests selected
                JOIN consult_requests sibling
                  ON sibling.origin_message_id=selected.origin_message_id
                 AND sibling.session_id=selected.session_id
                WHERE selected.consult_request_id=? AND selected.session_id=?
                ORDER BY sibling.subquery_order, sibling.consult_request_id
                """, (rs, index) -> new Member(new IntentSubQueryResponse(
                        rs.getLong(1), rs.getShort(2), ConsultRequest.Intent.valueOf(rs.getString(3)),
                        rs.getString(4), Map.of()), rs.getString(5)), requestId, sessionId);
        if (rows.size() < 2 || rows.stream().noneMatch(
                row -> row.query().intent() == ConsultRequest.Intent.STORE)) {
            return List.of();
        }
        if (rows.stream().anyMatch(row -> !Set.of("PENDING", "WAITING_CONDITION").contains(row.status()))) {
            throw new GeneralException(ConsultErrorCode.REQUEST_CLOSED);
        }
        return rows.stream().map(Member::query).toList();
    }

    private record Member(IntentSubQueryResponse query, String status) {}
}
