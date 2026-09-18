package com.telme.intent.repository;

import com.telme.intent.entity.QueryRouting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryRoutingRepository extends JpaRepository<QueryRouting, Long> {

    Optional<QueryRouting> findByMessage_MessageId(Long messageId);
}
