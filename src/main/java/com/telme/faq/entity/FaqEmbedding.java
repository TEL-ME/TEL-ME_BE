package com.telme.faq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * embedding(vector(1024))은 pgvector 전용 타입이라 순수 JPA로 매핑이 안 된다.
 * float[]는 컴파일 통과용 임시 타입이며, 실제 DB 연동 시 pgvector-java 의존성과
 * Hibernate vector 타입 등록이 별도로 필요하다 (확인 필요).
 */
@Entity
@Table(name = "faq_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FaqEmbedding {

    public enum SyncStatus { PENDING, SYNCED, FAILED }

    @Id
    @Column(name = "faq_id")
    private Long faqId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "faq_id")
    private Faq faq;

    @Column(name = "embedding", columnDefinition = "vector(1024)", nullable = false)
    private float[] embedding;

    @Column(name = "model_name", nullable = false, length = 50)
    @Builder.Default
    private String modelName = "bge-m3";

    @Column(name = "faq_version", nullable = false)
    private Integer faqVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Column(name = "embedded_at", nullable = false, updatable = false, insertable = false)
    private Instant embeddedAt;
}
