package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DualVectorMergerTest {

    private static final int TOP_K = 3;

    @Test
    @DisplayName("질문만 벡터 결과를 먼저 넣고 질문+답변 벡터 결과를 뒤에 붙인다")
    void 질문만_결과가_앞에_온다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(qo(10L, 0.90, 1)), List.of(qa(20L, 0.80, 1)), TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("같은 FAQ가 양쪽에 있으면 한 번만 넣고 질문만 벡터의 점수와 출처를 쓴다")
    void 중복은_한_번만_넣는다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(qo(10L, 0.90, 1)),
                List.of(qa(10L, 0.75, 1), qa(20L, 0.73, 2)),
                TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(10L, 20L);
        assertThat(merged.getFirst().score()).isEqualTo(0.90);
        assertThat(merged).extracting(FaqSearchResponse::matchedVariant)
                .containsExactly(FaqEmbeddingTextVariant.QUESTION_ONLY, FaqEmbeddingTextVariant.Q_A);
    }

    @Test
    @DisplayName("합친 결과가 topK를 넘으면 뒤를 자른다")
    void topK로_자른다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(qo(1L, 0.95, 1), qo(2L, 0.94, 2), qo(3L, 0.93, 3)),
                List.of(qa(4L, 0.80, 1), qa(5L, 0.79, 2)),
                TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("순위는 합친 순서대로 1부터 다시 매긴다")
    void 순위를_다시_매긴다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(qo(10L, 0.90, 1)),
                List.of(qa(10L, 0.75, 1), qa(20L, 0.73, 2), qa(30L, 0.72, 3)),
                TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(10L, 20L, 30L);
        assertThat(merged).extracting(FaqSearchResponse::searchRank).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("질문만 벡터 결과가 없으면 질문+답변 벡터 결과만 남는다")
    void 질문만_결과가_없으면_질문답변_결과만_쓴다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(), List.of(qa(20L, 0.80, 1), qa(30L, 0.75, 2)), TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(20L, 30L);
        assertThat(merged).extracting(FaqSearchResponse::searchRank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("질문+답변 벡터 결과가 없으면 질문만 벡터 결과만 남는다")
    void 질문답변_결과가_없으면_질문만_결과만_쓴다() {
        List<FaqSearchResponse> merged = DualVectorMerger.merge(
                List.of(qo(10L, 0.90, 1)), List.of(), TOP_K);

        assertThat(merged).extracting(FaqSearchResponse::faqId).containsExactly(10L);
    }

    @Test
    @DisplayName("양쪽 다 결과가 없으면 빈 리스트를 반환한다")
    void 양쪽_다_없으면_빈_리스트() {
        assertThat(DualVectorMerger.merge(List.of(), List.of(), TOP_K)).isEmpty();
    }

    private static FaqSearchResponse qo(long faqId, double score, int rank) {
        return result(faqId, score, rank, FaqEmbeddingTextVariant.QUESTION_ONLY);
    }

    private static FaqSearchResponse qa(long faqId, double score, int rank) {
        return result(faqId, score, rank, FaqEmbeddingTextVariant.Q_A);
    }

    private static FaqSearchResponse result(long faqId, double score, int rank, FaqEmbeddingTextVariant variant) {
        return new FaqSearchResponse(faqId, "BILLING-" + faqId, "BILLING", "질문" + faqId, "답변" + faqId,
                score, 1, LocalDate.of(2026, 9, 29), rank, variant);
    }
}
