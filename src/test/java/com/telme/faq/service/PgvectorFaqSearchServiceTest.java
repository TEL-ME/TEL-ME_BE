package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqNearestMatch;
import com.telme.faq.repository.FaqSearchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PgvectorFaqSearchServiceTest {

    private static final float[] QUERY_VECTOR = {1f, 0f, 0f};
    private static final double THRESHOLD = 0.5;
    private static final double QUESTION_THRESHOLD = 0.88;
    private static final String MODEL = "bge-m3";
    private static final SearchProperties.ScoreRecording RECORDING_ON = 
            new SearchProperties.ScoreRecording(true, Duration.ofDays(90));

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final FaqEmbeddingRepository repository = mock(FaqEmbeddingRepository.class);
    private final FaqSearchScoreRepository scoreRepository = mock(FaqSearchScoreRepository.class);
    private final EmbeddingProperties embeddingProperties =
            new EmbeddingProperties(MODEL, 3, Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(120));
    private final PgvectorFaqSearchService service =
            new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties,
                    new SearchProperties(THRESHOLD, new SearchProperties.DualVector(false, 0.88), RECORDING_ON,
                            SearchProperties.GrayZone.DISABLED),
                    new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);
    private final PgvectorFaqSearchService dualVectorService =
            new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties,
                    new SearchProperties(THRESHOLD, new SearchProperties.DualVector(true, QUESTION_THRESHOLD), RECORDING_ON,
                            SearchProperties.GrayZone.DISABLED),
                    new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);
    
    @Test
    @DisplayName("임계값 미달로 결과가 비어도 Q_A 1위 점수와 통과 여부를 남긴다")
    void 임계값_미달이어도_1위_점수를_남긴다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.75)));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        assertThat(result).isEmpty();
        verify(scoreRepository).save(FaqSearchKind.ORIGINAL, 0.25, false, THRESHOLD);
    }
    
    @Test
    @DisplayName("점수 기록을 끄면 검색만 하고 기록하지 않는다")
    void 기록을_끄면_남기지_않는다() {
        PgvectorFaqSearchService recordingOff = new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties,
                new SearchProperties(THRESHOLD, new SearchProperties.DualVector(false, 0.88),
                        new SearchProperties.ScoreRecording(false, Duration.ofDays(90)), SearchProperties.GrayZone.DISABLED),
                new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));

        recordingOff.search(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        verifyNoInteractions(scoreRepository);
    }

    @Test
    @DisplayName("측정용 검색(kind 없음)은 기록하지 않는다")
    void 측정용_검색은_남기지_않는다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));

        service.search(new FaqSearchRequest("질문", 3, FaqSearchVector.QA));

        verifyNoInteractions(scoreRepository);
    }

    @Test
    @DisplayName("점수 기록이 실패해도 검색 결과는 그대로 돌려준다")
    void 점수_기록_실패가_검색을_막지_않는다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));
        doThrow(new IllegalStateException("db down")).when(scoreRepository).save(any(), any(), anyBoolean(), anyDouble());

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(1L);
    }

    @Test
    @DisplayName("vector=QA면 이중 벡터가 켜져 있어도 질문+답변 벡터만 조회한다")
    void QA를_고르면_질문_벡터를_조회하지_않는다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));

        List<FaqSearchResponse> result = dualVectorService.search(new FaqSearchRequest("질문", 3, FaqSearchVector.QA));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(1L);
        assertThat(result.getFirst().matchedVariant()).isEqualTo(FaqEmbeddingTextVariant.Q_A.name());
        verify(repository, never()).findNearestByQuestionVector(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("vector=QUESTION이면 이중 벡터가 꺼져 있어도 질문 벡터만 질문 벡터 임계값으로 조회한다")
    void QUESTION을_고르면_질문_벡터만_조회한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(3L, "PLAN", "요금제 종류 질문", 0.05),  // score 0.95
                matchOf(4L, "USIM", "유심 질문", 0.2)));        // score 0.8, 질문 벡터 임계값(0.88) 미만

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3, FaqSearchVector.QUESTION));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(3L);
        assertThat(result.getFirst().matchedVariant()).isEqualTo(FaqEmbeddingTextVariant.QUESTION_ONLY.name());
        assertThat(result.getFirst().searchRank()).isEqualTo(1);
        verify(repository, never()).findNearest(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("vector=DUAL이면 이중 벡터가 꺼져 있어도 두 결과를 합친다")
    void DUAL을_고르면_설정과_상관없이_합친다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL))
                .thenReturn(List.of(matchOf(3L, "PLAN", "요금제 종류 질문", 0.05)));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3, FaqSearchVector.DUAL));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(3L, 1L);
        verify(embeddingClient, times(1)).embed("질문");
    }

    @Test
    @DisplayName("이중 벡터가 꺼져 있으면 질문 벡터를 조회하지 않는다")
    void 이중_벡터가_꺼져_있으면_질문_벡터를_조회하지_않는다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.0)));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(1L);
        verify(repository, never()).findNearestByQuestionVector(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("이중 벡터가 켜지면 질문 벡터 결과를 앞에 두고 합치며, 임베딩은 한 번만 호출한다")
    void 이중_벡터가_켜지면_질문_벡터_결과를_앞에_두고_합친다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(1L, "BILLING", "요금제 질문", 0.2),   // score 0.8
                matchOf(2L, "USIM", "유심 질문", 0.3)));       // score 0.7
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(3L, "PLAN", "요금제 종류 질문", 0.05), // score 0.95
                matchOf(1L, "BILLING", "요금제 질문", 0.1)));  // score 0.9, Q_A 쪽과 중복

        List<FaqSearchResponse> result = dualVectorService.search(new FaqSearchRequest("질문", 3));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(3L, 1L, 2L);
        assertThat(result).extracting(FaqSearchResponse::matchedVariant).containsExactly(
                FaqEmbeddingTextVariant.QUESTION_ONLY.name(), FaqEmbeddingTextVariant.QUESTION_ONLY.name(),
                FaqEmbeddingTextVariant.Q_A.name());
        assertThat(result).extracting(FaqSearchResponse::searchRank).containsExactly(1, 2, 3);
        verify(embeddingClient, times(1)).embed("질문");
    }

    @Test
    @DisplayName("질문 벡터에는 질문 벡터 임계값을 따로 적용한다")
    void 질문_벡터에는_별도_임계값을_적용한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        // 같은 score 0.8이라도 Q_A 임계값(0.5)은 넘고 질문 벡터 임계값(0.88)은 못 넘는다
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "요금제 질문", 0.2)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL))
                .thenReturn(List.of(matchOf(2L, "USIM", "유심 질문", 0.2)));

        List<FaqSearchResponse> result = dualVectorService.search(new FaqSearchRequest("질문", 3));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(1L);
        assertThat(result.getFirst().matchedVariant()).isEqualTo(FaqEmbeddingTextVariant.Q_A.name());
    }

    @Test
    @DisplayName("합친 결과는 요청한 topK로 자른다")
    void 합친_결과는_topK로_자른다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(
                matchOf(1L, "BILLING", "요금제 질문", 0.2), matchOf(2L, "USIM", "유심 질문", 0.3)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(
                matchOf(3L, "PLAN", "요금제 종류 질문", 0.05), matchOf(4L, "ROAMING", "로밍 질문", 0.1)));

        List<FaqSearchResponse> result = dualVectorService.search(new FaqSearchRequest("질문", 2));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("여러 candidates가 모두 임계값 이상이면 전체를 slotId·score·rank·찾은 벡터와 함께 반환한다")
    void 정상_검색이면_score와_rank를_매겨_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        FaqNearestMatch same = matchOf(1L, "BILLING", "요금제 질문", 0.0); // distance 0 → score 1.0
        FaqNearestMatch similar = matchOf(2L, "USIM", "유심 질문", 0.2929); // distance ≈0.2929 → score ≈0.7071
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(same, similar));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
        assertThat(result.get(0).slotId()).isEqualTo("BILLING-0001");
        assertThat(result.get(0).matchedVariant()).isEqualTo(FaqEmbeddingTextVariant.Q_A.name());
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
        FaqNearestMatch same = matchOf(1L, "BILLING", "요금제 질문", 0.0); // distance 0 → score 1.0
        FaqNearestMatch orthogonal = matchOf(2L, "USIM", "유심 질문", 1.0); // distance 1 → score 0.0 < 0.5
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
        // distance 1 → score 0.0 < 0.5
        FaqNearestMatch orthogonal = matchOf(1L, "BILLING", "무관 질문", 1.0);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(orthogonal));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 3));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("후보 검색은 임계값 미달 결과도 순위를 유지해 반환한다")
    void 후보_검색은_임계값을_적용하지_않는다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(
                matchOf(1L, "PLAN", "첫 후보", 0.4),
                matchOf(2L, "PLAN", "임계값 미달 후보", 0.6)));

        List<FaqSearchResponse> result = service.searchCandidates(new FaqSearchRequest("질문", 2));

        assertThat(result).extracting(FaqSearchResponse::faqId).containsExactly(1L, 2L);
        assertThat(result).extracting(FaqSearchResponse::searchRank).containsExactly(1, 2);
        assertThat(result.get(1).score()).isEqualTo(0.4);
        verify(repository, never()).findNearestByQuestionVector(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("후보가 영벡터여서 score가 NaN이면 결과에서 제외한다")
    void score가_NaN이면_결과에서_제외한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        // 후보 벡터의 norm이 0이면 pgvector의 cosine_distance 자체가 NaN을 반환(실측 확인됨)
        FaqNearestMatch zero = matchOf(1L, "BILLING", "빈 벡터", Double.NaN);
        when(repository.findNearest(QUERY_VECTOR, 1, MODEL)).thenReturn(List.of(zero));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 1));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("정상 후보 뒤에 영벡터(NaN)가 와도 그 지점에서 끊고 앞선 결과는 반환한다")
    void 하위_순위의_NaN도_제외하고_그_앞은_반환한다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        FaqNearestMatch same = matchOf(1L, "BILLING", "요금제 질문", 0.0); // distance 0 → score 1.0
        FaqNearestMatch zero = matchOf(2L, "USIM", "빈 벡터", Double.NaN);
        when(repository.findNearest(QUERY_VECTOR, 2, MODEL)).thenReturn(List.of(same, zero));

        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("질문", 2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
        assertThat(result.get(0).searchRank()).isEqualTo(1);
    }

    // 경계값을 이진수로 정확히 나타낼 수 있는 선(0.875, 0.75, 0.5)으로 둔다
    private final PgvectorFaqSearchService grayZoneService =
            new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties,
                    new SearchProperties(THRESHOLD, new SearchProperties.DualVector(true, QUESTION_THRESHOLD), RECORDING_ON,
                            new SearchProperties.GrayZone(true, 0.75, 0.5, 0.875, 0.75)),
                    new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);

    @Test
    @DisplayName("회색 구간 검색은 벡터별로 통과 선 이상은 통과, 아래 선 이상은 판정 필요로 두고 그 아래는 뺀다")
    void 회색_구간은_점수로_나눈다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(1L, "BILLING", "통과 선과 같음", 0.25),    // 0.75
                matchOf(2L, "USIM", "아래 선과 같음", 0.5),        // 0.5
                matchOf(3L, "PLAN", "아래 선 미만", 0.625)));       // 0.375
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(4L, "ROAMING", "통과 선과 같음", 0.125),   // 0.875
                matchOf(5L, "DEVICE", "판정 구간", 0.1875),        // 0.8125
                matchOf(6L, "PORTING", "아래 선 미만", 0.5)));      // 0.5

        FaqGrayZoneSearchResponse result = grayZoneService.searchGrayZone(new FaqSearchRequest("질문", 3));

        assertThat(result.questionOnly()).extracting(c -> c.result().faqId(), FaqGrayZoneSearchResponse.Candidate::needsJudgment)
                .containsExactly(tuple(4L, false), tuple(5L, true));
        assertThat(result.questionAnswer()).extracting(c -> c.result().faqId(), FaqGrayZoneSearchResponse.Candidate::needsJudgment)
                .containsExactly(tuple(1L, false), tuple(2L, true));
        assertThat(result.questionOnly()).extracting(c -> c.result().matchedVariant())
                .containsOnly(FaqEmbeddingTextVariant.QUESTION_ONLY.name());
        verify(embeddingClient, times(1)).embed("질문");
    }

    @Test
    @DisplayName("기본 선에서는 기존 질문 임계값(0.88) 위라도 0.90 미만이면 판정 필요다(질문 벡터 위로 넓히기)")
    void 기본_선은_질문_벡터_0_88에서_0_90도_판정한다() {
        PgvectorFaqSearchService defaults = new PgvectorFaqSearchService(embeddingClient, repository,
                embeddingProperties,
                new SearchProperties(0.72, new SearchProperties.DualVector(true, 0.88), RECORDING_ON,
                        new SearchProperties.GrayZone(true, 0.80, 0.69, 0.90, 0.80)),
                new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of());
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(
                matchOf(1L, "USIM", "0.905", 0.095),
                matchOf(2L, "USIM", "0.895", 0.105),
                matchOf(3L, "USIM", "0.85", 0.15)));

        FaqGrayZoneSearchResponse result = defaults.searchGrayZone(new FaqSearchRequest("질문", 3));

        assertThat(result.questionOnly()).extracting(c -> c.result().faqId(), FaqGrayZoneSearchResponse.Candidate::needsJudgment)
                .containsExactly(tuple(1L, false), tuple(2L, true), tuple(3L, true));
    }

    @Test
    @DisplayName("이중 벡터가 꺼져 있으면 회색 구간 검색도 질문 벡터를 조회하지 않는다")
    void 이중_벡터가_꺼지면_회색_구간도_QA만() {
        PgvectorFaqSearchService qaOnly = new PgvectorFaqSearchService(embeddingClient, repository, embeddingProperties,
                new SearchProperties(THRESHOLD, new SearchProperties.DualVector(false, QUESTION_THRESHOLD), RECORDING_ON,
                        new SearchProperties.GrayZone(true, 0.75, 0.5, 0.875, 0.75)),
                new FaqEmbeddingTextProperties(FaqEmbeddingTextVariant.Q_A), scoreRepository);
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "질문", 0.375)));

        FaqGrayZoneSearchResponse result = qaOnly.searchGrayZone(new FaqSearchRequest("질문", 3));

        assertThat(result.questionOnly()).isEmpty();
        assertThat(result.questionAnswer()).singleElement().satisfies(c -> assertThat(c.needsJudgment()).isTrue());
        verify(repository, never()).findNearestByQuestionVector(any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("회색 구간 검색도 관리자 점수 기록은 판정 전 기존 임계값 기준으로 남긴다")
    void 회색_구간_점수_기록은_기존_임계값_기준() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        // Q_A 0.375 < 0.5, 질문 0.875 < 0.88: 기존 검색이었다면 결과가 없다
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "질문", 0.625)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL))
                .thenReturn(List.of(matchOf(2L, "USIM", "질문", 0.125)));

        grayZoneService.searchGrayZone(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        verify(scoreRepository).save(FaqSearchKind.ORIGINAL, 0.375, false, THRESHOLD);
    }

    @Test
    @DisplayName("회색 구간 검색은 판정 결과를 채울 수 있게 점수 기록 번호를 함께 돌려준다")
    void 회색_구간_검색은_기록_번호를_돌려준다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "질문", 0.25)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of());
        when(scoreRepository.save(any(), any(), anyBoolean(), anyDouble())).thenReturn(7L);

        FaqGrayZoneSearchResponse result = grayZoneService.searchGrayZone(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        assertThat(result.searchScoreId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("점수 기록이 실패하면 기록 번호 없이 회색 구간 결과만 돌려준다")
    void 회색_구간_기록_실패면_번호가_없다() {
        when(embeddingClient.embed("질문")).thenReturn(QUERY_VECTOR);
        when(repository.findNearest(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of(matchOf(1L, "BILLING", "질문", 0.25)));
        when(repository.findNearestByQuestionVector(QUERY_VECTOR, 3, MODEL)).thenReturn(List.of());
        doThrow(new IllegalStateException("db down")).when(scoreRepository).save(any(), any(), anyBoolean(), anyDouble());

        FaqGrayZoneSearchResponse result = grayZoneService.searchGrayZone(new FaqSearchRequest("질문", 3, FaqSearchKind.ORIGINAL));

        assertThat(result.searchScoreId()).isNull();
        assertThat(result.questionAnswer()).extracting(c -> c.result().faqId()).containsExactly(1L);
    }

    @Test
    @DisplayName("판정 결과는 검색 때 남긴 같은 기록에 채운다")
    void 판정_결과를_같은_기록에_채운다() {
        grayZoneService.recordGrayZoneOutcome(7L, true, false);

        verify(scoreRepository).markOutcome(7L, true, false);
    }

    @Test
    @DisplayName("기록 번호가 없으면 판정 결과를 남기지 않는다")
    void 기록_번호가_없으면_판정_결과를_남기지_않는다() {
        grayZoneService.recordGrayZoneOutcome(null, true, true);

        verifyNoInteractions(scoreRepository);
    }

    @Test
    @DisplayName("판정 결과 기록이 실패해도 예외를 밖으로 내보내지 않는다")
    void 판정_결과_기록_실패를_삼킨다() {
        doThrow(new IllegalStateException("db down")).when(scoreRepository).markOutcome(anyLong(), anyBoolean(), anyBoolean());

        assertThatCode(() -> grayZoneService.recordGrayZoneOutcome(7L, true, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회색 구간을 지원하지 않는 구현은 기존 검색 결과를 모두 통과로 돌려준다")
    void 기본_구현은_모두_통과() {
        FaqSearchResponse found = new FaqSearchResponse(1L, "BILLING-0001", "BILLING", "질문", "답변", 0.9, 1,
                null, 1, null);
        FaqSearchService dummy = request -> List.of(found);

        FaqGrayZoneSearchResponse result = dummy.searchGrayZone(new FaqSearchRequest("질문", 3));

        assertThat(result.questionOnly()).isEmpty();
        assertThat(result.questionAnswer()).containsExactly(new FaqGrayZoneSearchResponse.Candidate(found, false));
    }

    private FaqNearestMatch matchOf(long faqId, String category, String question, double distance) {
        Faq faq = Faq.builder()
                .faqId(faqId)
                .slotId(String.format("%s-%04d", category, faqId))
                .category(category)
                .question(question)
                .answer("답변")
                .version(1)
                .updatedAt(Instant.parse("2026-09-21T00:00:00Z"))
                .build();
        FaqEmbedding embedding = FaqEmbedding.builder()
                .faqId(faqId)
                .faq(faq)
                .embedding(new float[]{1f, 0f, 0f})
                .modelName("bge-m3")
                .faqVersion(1)
                .build();
        return new FaqNearestMatch(embedding, distance);
    }
}
