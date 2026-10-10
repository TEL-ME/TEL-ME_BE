package com.telme.consult.repository;

import com.telme.consult.entity.ConsultRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultRequestRepository extends JpaRepository<ConsultRequest, Long> {

    List<ConsultRequest> findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(Long messageId);

    // 답이 붙을 대상은 가장 최근에 되물은 상담이다. 오래된 대기가 먼저 가져가면 방이 막힌다
    @Query("""
            select r from ConsultRequest r
            where r.session.sessionId = :sessionId and r.status = :status
            order by (select max(m.sequenceNo) from ConsultCondition c join c.askedMessage m
                      where c.consultRequest = r) desc nulls last,
                     r.subqueryOrder asc, r.consultRequestId asc
            """)
    List<ConsultRequest> findByLatestAskedQuestion(
            @Param("sessionId") Long sessionId, @Param("status") ConsultRequest.Status status);
}
