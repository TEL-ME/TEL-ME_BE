package com.telme.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.telme.chat.entity.ChatMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * userId/guestId는 member 도메인 엔티티를 직접 참조하지 않고 id만 보관한다.
 * DB의 ck_feedback_actor CHECK와 부분 유니크 인덱스(uk_feedback_user/uk_feedback_guest)는
 * JPA로 표현이 안 되므로 마이그레이션 SQL에만 존재하고, 생성 시점 검증은
 * 서비스 레이어 책임이다 (확인 필요).
 */
@Entity
@Table(name = "message_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MessageFeedback {

    public enum Rating {
        LIKE, DISLIKE
    }

    public enum ReasonCode {
        WRONG_INFO, NOT_RELATED, HARD_TO_READ
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_id")
    private UUID guestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private Rating rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    private ReasonCode reasonCode;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    // trg_message_feedback_updated_at이 채우므로 JPA가 낡은 값을 같이 보내지 않게 막는다
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    // 관리자가 조치를 끝낸 시각. 비어 있으면 아직 처리하지 않은 건이다
    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_note", columnDefinition = "TEXT")
    private String handledNote;

    public boolean isHandled() {
        return handledAt != null;
    }

    // 시각 의존 테스트가 가능하도록 Clock을 받는다 (Guest.issue와 같은 이유)
    public void markHandled(Long adminId, String note, Clock clock) {
        this.handledAt = clock.instant();
        this.handledBy = adminId;
        this.handledNote = note;
    }

    // 잘못 눌렀을 때 되돌린다. ck_feedback_handled_pair가 시각과 처리자를 함께 보므로 셋을 같이 비운다
    public void markUnhandled() {
        this.handledAt = null;
        this.handledBy = null;
        this.handledNote = null;
    }
}
