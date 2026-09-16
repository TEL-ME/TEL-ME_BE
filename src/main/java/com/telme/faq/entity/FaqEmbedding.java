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
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * embedding(vector(1024))은 Hibernate 6.4+ 내장 vector 지원(hibernate-vector 모듈)으로
 * 매핑
 * @JdbcTypeCode(SqlTypes.VECTOR) + @Array(length=1024)가 DDL의 vector(1024)와 대응
 * 차원 수를 바꾸면 이 값과 마이그레이션의 vector(1024)를 함께 바꿔야 한다.
 */
@Entity
@Table(name = "faq_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FaqEmbedding {

    public enum SyncStatus {
        PENDING, SYNCED, FAILED
    }

    @Id
    @Column(name = "faq_id")
    private Long faqId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "faq_id")
    private Faq faq;

    @Column(name = "embedding", nullable = false)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
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
