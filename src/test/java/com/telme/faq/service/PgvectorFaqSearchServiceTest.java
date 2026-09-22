package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PgvectorFaqSearchServiceTest {

    private static final float[] QUERY_VECTOR = {1f, 0f, 0f};
    private static final double THRESHOLD = 0.5;
    private static final String MODEL = "bge-m3";

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final FaqEmbeddingRepository repository = mock(FaqEmbeddingRepository.class);
    private final EmbeddingProperties embeddingProperties =
            new EmbeddingProperties(MODEL, 3, Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(120));
    private final PgvectorFaqSearchService service =
            new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties, THRESHOLD);

    @Test
    @DisplayName("여러 candidates가 모두 임계값 이상이면 전체를 score·rank와 함께 반환한다")
    void 정상_검색이면_score와_rank를_매겨_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        FaqEmbedding same = embeddingOf(1L, "BILLING", "요금제 질문", new float[]{1f, 0f, 0f}); // 코사인 1.0
        FaqEmbedding similar = embeddingOf(2L, "USIM", "유심 질문", new float[]{1f, 1f, 0f}); // 코사인 ≈0.707
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(same, similar));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
        assertThat(result.get(0).score()).isEqualTo(1.0);
        assertThat(result.get(0).searchRank()).isEqualTo(1);
        assertThat(result.get(1).faqId()).isEqualTo(2L);
        assertThat(result.get(1).score()).isCloseTo(0.7071, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result.get(1).searchRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("임계값 미만인 순위부터는 결과에서 제외한다")
    void 임계값_미만_순위는_제외하고_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        FaqEmbedding same = embeddingOf(1L, "BILLING", "요금제 질문", new float[]{1f, 0f, 0f}); // 코사인 1.0
        FaqEmbedding orthogonal = embeddingOf(2L, "USIM", "유심 질문", new float[]{0f, 1f, 0f}); // 코사인 0.0 < 0.5
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(same, orthogonal));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
        assertThat(result.get(0).searchRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환한다")
    void candidates가_없으면_빈_리스트를_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of());

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최고 유사도가 임계값 미만이면 빈 리스트를 반환한다")
    void 최고_유사도가_임계값_미만이면_빈_리스트를_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        // 코사인 0.0 < 0.5
        FaqEmbedding orthogonal = embeddingOf(1L, "BILLING", "무관 질문", new float[]{0f, 1f, 0f});
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(orthogonal));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("후보가 영벡터여서 score가 NaN이면 결과에서 제외한다")
    void score가_NaN이면_결과에서_제외한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        // 후보 벡터의 norm이 0이면 분모가 0이 되어 score가 NaN
        FaqEmbedding zero = embeddingOf(1L, "BILLING", "빈 벡터", new float[]{0f, 0f, 0f});
        when(repository.findNearest(QUERY_VECTOR, 1, MODEL)).thenReturn(List.of(zero));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 1));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("정상 후보 뒤에 영벡터(NaN)가 와도 그 지점에서 끊고 앞선 결과는 반환한다")
    void 하위_순위의_NaN도_제외하고_그_앞은_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        FaqEmbedding same = embeddingOf(1L, "BILLING", "요금제 질문", new float[]{1f, 0f, 0f}); // 코사인 1.0
        FaqEmbedding zero = embeddingOf(2L, "USIM", "빈 벡터", new float[]{0f, 0f, 0f}); // NaN
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(same, zero));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
        assertThat(result.get(0).searchRank()).isEqualTo(1);
    }

    private FaqEmbedding embeddingOf(long faqId, String category, String question, float[] vector) {
        Faq faq = Faq.builder()
                .faqId(faqId)
                .category(category)
                .question(question)
                .answer("답변")
                .version(1)
                .updatedAt(Instant.parse("2026-09-21T00:00:00Z"))
                .build();
        return FaqEmbedding.builder()
                .faqId(faqId)
                .faq(faq)
                .embedding(vector)
                .modelName("bge-m3")
                .faqVersion(1)
                .build();
    }
}
