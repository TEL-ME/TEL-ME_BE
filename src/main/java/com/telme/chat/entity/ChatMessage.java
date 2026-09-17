package com.telme.chat.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_message_seq", columnNames = {"session_id", "sequence_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    public enum MessageType { QUESTION, ANSWER, CLARIFICATION, STORE_RESULT, ERROR }

    public enum Status { GENERATING, COMPLETED, FAILED, TIMEOUT, CANCELLED }

    public enum AnswerBasis { GROUNDED, NO_EVIDENCE, OUT_OF_SCOPE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private MessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_basis", length = 20)
    private AnswerBasis answerBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_ups", columnDefinition = "jsonb")
    private String followUps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "store_results", columnDefinition = "jsonb")
    private String storeResults;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
