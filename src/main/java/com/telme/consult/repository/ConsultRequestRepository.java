package com.telme.consult.repository;

import com.telme.consult.entity.ConsultRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultRequestRepository extends JpaRepository<ConsultRequest, Long> {

    List<ConsultRequest> findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(Long messageId);

    Optional<ConsultRequest> findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
            Long sessionId, ConsultRequest.Status status);
}
