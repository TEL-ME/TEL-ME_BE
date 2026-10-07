package com.telme.consult.repository;

import com.telme.consult.service.PolicyLinkSuggestedQuestions.FaqLookup;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** 추천 질문의 기준 FAQ 정책과 대표 FAQ의 현재 질문을 읽는다. */
public final class SuggestedQuestionFaqFinder implements FaqLookup {
    private final JdbcTemplate jdbc;
    private final String embeddingModel;

    public SuggestedQuestionFaqFinder(JdbcTemplate jdbc, String embeddingModel) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
    }

    @Override
    public String policyRef(long faqId) {
        List<String> refs = jdbc.query(
                "SELECT policy_ref FROM faqs WHERE faq_id = ?", (rs, rowNum) -> rs.getString(1), faqId);
        return refs.isEmpty() ? null : refs.getFirst();
    }

    // 검색(FaqEmbeddingRepository)과 같은 조건이어야 버튼을 눌렀을 때 그 FAQ가 다시 검색된다.
    // 모델을 바꿔 일부만 재임베딩된 동안에는 이전 모델 벡터가 검색에서 빠지므로 모델 이름도 맞춘다
    @Override
    public Map<String, String> searchableQuestions(List<String> slotIds) {
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", slotIds.stream().map(id -> "?").toList());
        Map<String, String> questions = new HashMap<>();
        jdbc.query(
                """
                SELECT f.slot_id, f.question
                FROM faqs f
                JOIN faq_embeddings e ON e.faq_id = f.faq_id
                WHERE f.slot_id IN (%s)
                  AND f.status = 'ACTIVE' AND e.sync_status = 'SYNCED' AND e.faq_version = f.version
                  AND e.model_name = ?
                """.formatted(placeholders),
                rs -> {
                    questions.put(rs.getString(1), rs.getString(2));
                },
                Stream.concat(slotIds.stream(), Stream.of(embeddingModel)).toArray());
        return questions;
    }
}
