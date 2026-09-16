package com.telme.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    // faq 도메인의 Faq id. 답변 당시 스냅샷이라 FK보다 값 복사가 더 맞고, 도메인도 달라 id만 보관한다.
    @Column(name = "faq_id")
    private Long faqId;

    @Column(name = "title_snapshot", length = 200)
    private String titleSnapshot;

    @Column(name = "faq_version")
    private Integer faqVersion;

    @Column(name = "faq_updated_at")
    private LocalDate faqUpdatedAt;

    @Column(name = "search_rank")
    private Short searchRank;

    @Column(name = "score", precision = 5, scale = 4)
    private BigDecimal score;
}
