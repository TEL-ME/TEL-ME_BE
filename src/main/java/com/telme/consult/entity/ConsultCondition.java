package com.telme.consult.entity;

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

import com.telme.chat.entity.ChatMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * consult_requests의 자식 엔티티. 부모(ConsultRequest)를 통해서만 접근하며
 * 별도 Repository는 두지 않는다.
 */
@Entity
@Table(name = "consult_conditions", uniqueConstraints = @UniqueConstraint(name = "uk_condition_key", columnNames = {
        "consult_request_id", "condition_key" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ConsultCondition {

    public enum Source {
        EXTRACTED, ASKED
    }

    public enum Status {
        PENDING, FILLED, DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condition_id")
    private Long conditionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consult_request_id", nullable = false)
    private ConsultRequest consultRequest;

    @Column(name = "condition_key", nullable = false, length = 50)
    private String conditionKey;

    @Column(name = "condition_value", length = 255)
    private String conditionValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asked_message_id")
    private ChatMessage askedMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_message_id")
    private ChatMessage answeredMessage;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
