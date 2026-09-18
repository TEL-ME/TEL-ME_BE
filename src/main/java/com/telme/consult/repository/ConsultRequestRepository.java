package com.telme.consult.repository;

import com.telme.consult.entity.ConsultRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultRequestRepository extends JpaRepository<ConsultRequest, Long> {

    List<ConsultRequest> findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(Long messageId);
}
