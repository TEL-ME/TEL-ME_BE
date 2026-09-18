package com.telme.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * userId/guestId는 member 도메인 엔티티를 직접 참조하지 않고 id만 보관한다.
 * DB에는 ck_session_owner CHECK(user_id IS NOT NULL OR guest_id IS NOT NULL)가
 * 있지만, JPA로는 표현이 안 되므로 서비스 레이어에서 생성 시점에 검증해야 한다 (확인 필요).
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatSession {

    public enum Status { ACTIVE, NEED_CLARIFICATION, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_id")
    private UUID guestId;

    @Column(name = "title", length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    @Builder.Default
    private Instant lastActiveAt = Instant.now();

    public void rename(String title) {
        this.title = title;
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public void touch(Instant activeAt) {
        this.lastActiveAt = activeAt;
    }

    public void waitForClarification() {
        if (this.status == Status.ACTIVE) {
            this.status = Status.NEED_CLARIFICATION;
        }
    }

    public void resume() {
        if (this.status == Status.NEED_CLARIFICATION) {
            this.status = Status.ACTIVE;
        }
    }
}
