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

    // 내용이 바뀌어도 유지되는 식별자(원본 JSON의 slot_id). 원본에 없는 FAQ는 null
    @Column(name = "slot_id", length = 50, unique = true)
    private String slotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    // member 도메인의 User를 직접 참조하지 않고 id만 보관한다 (도메인 간 결합 최소화)
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    // slot_id 도입 전에 적재된 행에 식별자를 채울 때만 쓴다. 이미 있는 slot_id는 바꾸지 않는다
    public void assignSlotId(String slotId) {
        if (this.slotId != null) {
            throw new IllegalStateException("이미 slot_id가 있는 FAQ: " + faqId);
        }
        this.slotId = slotId;
    }
}
