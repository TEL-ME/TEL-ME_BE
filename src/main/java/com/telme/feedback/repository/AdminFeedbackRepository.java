package com.telme.feedback.repository;

import com.telme.feedback.entity.MessageFeedback;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 사용자 피드백 저장은 JdbcFeedbackStore가 맡는다. 여기는 관리자 조회 전용이다
public interface AdminFeedbackRepository extends JpaRepository<MessageFeedback, Long> {

    // 조건을 안 준 항목은 호출부가 전체 범위로 넓혀 넘긴다.
    // handledMode는 AdminFeedbackHandledFilter가 준다 (0 전체, 1 처리됨, 2 미처리)
    String DISLIKE_WHERE = """
            where f.rating = :dislike
              and f.reasonCode in :reasons
              and f.createdAt >= :from
              and f.createdAt < :to
              and (:handledMode = 0
                   or (:handledMode = 1 and f.handledAt is not null)
                   or (:handledMode = 2 and f.handledAt is null))
            """;

    // 질문은 답변 메시지가 가리키는 앞 메시지에 있다. 한 줄씩 다시 읽지 않도록 함께 가져온다.
    // 되묻기 없이 시작한 답변은 앞 메시지가 없을 수 있어 left join
    @Query(value = "select f from MessageFeedback f "
            + "join fetch f.message m left join fetch m.replyTo " + DISLIKE_WHERE
            + " order by f.createdAt desc, f.feedbackId desc",
            countQuery = "select count(f) from MessageFeedback f " + DISLIKE_WHERE)
    Page<MessageFeedback> findDislikes(
            @Param("dislike") MessageFeedback.Rating dislike,
            @Param("reasons") Collection<MessageFeedback.ReasonCode> reasons,
            @Param("handledMode") int handledMode,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
