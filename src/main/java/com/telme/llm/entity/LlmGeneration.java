package com.telme.llm.entity;

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

import com.telme.chat.entity.ChatExecution;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "llm_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LlmGeneration {

    public enum TaskType {
        ROUTING, RAG_ANSWER, CLARIFICATION, FOLLOW_UP, SUMMARY, SESSION_TITLE
    }

    public enum Status {
        SUCCESS, NO_EVIDENCE, TIMEOUT, CONNECTION_FAILED, MODEL_ERROR, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_id")
    private Long generationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private ChatExecution execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private TaskType taskType;

    @Column(name = "attempt", nullable = false)
    @Builder.Default
    private Short attempt = 1;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "context_count")
    private Integer contextCount;

    @Column(name = "first_token_ms")
    private Integer firstTokenMs;

    @Column(name = "total_ms")
    private Integer totalMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
