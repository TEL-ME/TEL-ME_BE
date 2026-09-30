package com.telme.faq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * embedding·embeddingQuestion(vector(1024))은 Hibernate 6.4+ 내장 vector 지원(hibernate-vector 모듈)으로
 * 매핑
 * @JdbcTypeCode(SqlTypes.VECTOR) + @Array(length=1024)가 DDL의 vector(1024)와 대응
 * 차원 수를 바꾸면 두 필드의 값과 마이그레이션의 vector(1024)를 함께 바꿔야 한다.
 *
 * PK(faq_id)를 애플리케이션이 정하므로(@MapsId) Persistable을 구현해 save()가 INSERT/UPDATE를
 * 제대로 고르게 한다 (Guest 엔티티와 같은 방식).
 */
@Entity
@Table(name = "faq_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FaqEmbedding implements Persistable<Long> {

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

    // 질문만(QUESTION_ONLY) 구성으로 만든 벡터
    // embedding과 같은 트랜잭션에서 갱신
    // 아직 채우지 않은 행이 있을 수 있어 nullable(조회 쪽에서 IS NOT NULL로 걸러야 함)
    @Column(name = "embedding_question")
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] embeddingQuestion;

    @Column(name = "model_name", nullable = false, length = 50)
    @Builder.Default
    private String modelName = "bge-m3";

    @Column(name = "faq_version", nullable = false)
    private Integer faqVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    // INSERT는 DB default(now()), 재임베딩 때는 refresh()가 갱신
    @Column(name = "embedded_at", nullable = false, insertable = false)
    private Instant embeddedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    // 두 벡터와 faqs.version으로 갱신(재임베딩)
    public void refresh(float[] embedding, float[] embeddingQuestion, String modelName, Integer faqVersion) {
        this.embedding = embedding;
        this.embeddingQuestion = embeddingQuestion;
        this.modelName = modelName;
        this.faqVersion = faqVersion;
        this.syncStatus = SyncStatus.SYNCED;
        this.embeddedAt = Instant.now();
    }

    @Override
    public Long getId() {
        return faqId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
