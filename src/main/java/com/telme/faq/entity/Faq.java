package com.telme.faq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Faq {

    public enum Status { ACTIVE, HIDDEN, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long faqId;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "policy_ref", length = 50)
    private String policyRef;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    // member 도메인의 User를 직접 참조하지 않고 id만 보관한다 (도메인 간 결합 최소화)
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    // DB 기본값과 trg_faqs_updated_at 트리거가 채운다. 안 읽어오면 등록·수정 응답에 낡은 값이 나간다
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    // 검색이 e.faqVersion = f.version으로 걸러, 버전만 오르고 재임베딩이 빠지면 그 FAQ가 통째로 사라진다.
    // 반환값이 true면 호출부가 재임베딩까지 해야 한다
    public boolean update(
            String category, String question, String answer, String policyRef, String newHash, Long updatedBy) {
        boolean contentChanged = !newHash.equals(contentHash);

        this.category = category;
        this.question = question;
        this.answer = answer;
        this.policyRef = policyRef;
        this.updatedBy = updatedBy;
        if (contentChanged) {
            this.contentHash = newHash;
            this.version = version + 1;
        }
        return contentChanged;
    }

    // 삭제도 상태 변경이라 임베딩은 그대로 둔다. 검색이 f.status = 'ACTIVE'로 이미 거른다
    public void changeStatus(Status status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }
}
