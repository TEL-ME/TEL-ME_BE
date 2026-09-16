package com.telme.intent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

import com.telme.chat.entity.ChatMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "query_routings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class QueryRouting {

    public enum Intent {
        FAQ, STORE, BOTH, UNKNOWN
    }

    public enum Method {
        RULE, LLM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "routing_id")
    private Long routingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private ChatMessage message;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, length = 20)
    private Intent intent;

    @Column(name = "refined_query", columnDefinition = "TEXT")
    private String refinedQuery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_conditions", columnDefinition = "jsonb")
    private String extractedConditions;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private Method method;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
