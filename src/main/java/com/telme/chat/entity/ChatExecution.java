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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "chat_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatExecution {

    public enum Status { RUNNING, COMPLETED, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_message_id", nullable = false)
    private ChatMessage inputMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_message_id")
    private ChatMessage outputMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "started_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    public boolean isRunning() {
        return this.status == Status.RUNNING;
    }

    public void attachOutput(ChatMessage outputMessage) {
        this.outputMessage = outputMessage;
    }

    public void complete(ChatMessage outputMessage, Instant endedAt) {
        this.outputMessage = outputMessage;
        this.status = Status.COMPLETED;
        this.endedAt = endedAt;
    }

    public void completeWithoutOutput(Instant endedAt) {
        this.status = Status.COMPLETED;
        this.endedAt = endedAt;
    }

    public void fail(Status status, String errorCode, ChatMessage outputMessage, Instant endedAt) {
        this.outputMessage = outputMessage;
        this.status = status;
        this.errorCode = errorCode;
        this.endedAt = endedAt;
    }
}
