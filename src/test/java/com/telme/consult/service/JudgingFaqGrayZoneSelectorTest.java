package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.consult.service.FaqGrayZoneSelector.Judgment;
import com.telme.consult.service.FaqGrayZoneSelector.Selection;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse.Candidate;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.FaqGrayZoneOutcome;
import com.telme.rag.service.FaqEvidenceJudge;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JudgingFaqGrayZoneSelectorTest {

    private static final String QUESTION = "고객 질문";
    private static final String QO = "QUESTION_ONLY";
    private static final String QA = "Q_A";

    private final FaqEvidenceJudge judge = mock(FaqEvidenceJudge.class);
    // 판정을 붙이기 전 임계값: Q_A 0.72, 질문 0.88
    private final JudgingFaqGrayZoneSelector selector = new JudgingFaqGrayZoneSelector(judge, 0.72, 0.88);

    @Test
    @DisplayName("판정 필요 후보가 없으면 판정하지 않고 질문 벡터 우선으로 합친다")
    void 판정_없이_합친다() {
        Selection selection = select(
                List.of(pass(1L, 0.92, QO)),
                List.of(pass(2L, 0.85, QA), pass(1L, 0.83, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(1L, 2L);
        assertThat(selection.judgments()).isEmpty();
        verifyNoInteractions(judge);
    }

    @Test
    @DisplayName("판정으로 빠진 자리는 다음 후보가 채운다")
    void 빠진_자리는_다음_후보가_채운다() {
        verdicts(Map.of(2L, Verdict.NOT_ANSWERABLE, 3L, Verdict.ANSWERABLE, 4L, Verdict.ANSWERABLE));

        Selection selection = select(
                List.of(pass(1L, 0.90, QO)),
                List.of(judgeNeeded(2L, 0.78, QA), judgeNeeded(3L, 0.76, QA), judgeNeeded(4L, 0.70, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(1L, 3L, 4L);
    }

    @Test
    @DisplayName("판정 실패는 위로 넓힌 구간(기존 임계값 이상)만 통과시키고 아래쪽은 버린다")
    void 실패는_위쪽만_통과() {
        verdicts(Map.of(5L, Verdict.FAILED, 6L, Verdict.FAILED, 7L, Verdict.FAILED));

        Selection selection = select(
                List.of(judgeNeeded(7L, 0.85, QO)),
                List.of(judgeNeeded(5L, 0.75, QA), judgeNeeded(6L, 0.70, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(5L);
        assertThat(selection.judgments())
                .extracting(Judgment::faqId, Judgment::upperBand, Judgment::verdict, Judgment::kept)
                .containsExactly(
                        tuple(7L, false, Verdict.FAILED, false),
                        tuple(5L, true, Verdict.FAILED, true),
                        tuple(6L, false, Verdict.FAILED, false));
    }

    @Test
    @DisplayName("slot_id 없는 후보(관리자 등록 FAQ)도 판정하고, 실패하면 위쪽 구간만 통과시킨다")
    void slot_없는_후보도_판정() {
        verdicts(Map.of(8L, Verdict.ANSWERABLE, 10L, Verdict.FAILED, 11L, Verdict.FAILED));
        Candidate answerable = new Candidate(response(8L, null, 0.70, QA), true);
        Candidate failedUpper = new Candidate(response(10L, null, 0.75, QA), true);
        Candidate failedLower = new Candidate(response(11L, null, 0.70, QA), true);

        Selection selection = selector.select(1L, 2L, QUESTION,
                new FaqGrayZoneSearchResponse(List.of(), List.of(answerable, failedUpper, failedLower)), 3);

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(8L, 10L);
        assertThat(selection.judgments())
                .extracting(Judgment::faqId, Judgment::slotId, Judgment::verdict, Judgment::kept)
                .containsExactly(
                        tuple(8L, null, Verdict.ANSWERABLE, true),
                        tuple(10L, null, Verdict.FAILED, true),
                        tuple(11L, null, Verdict.FAILED, false));
        verify(judge, times(3)).judge(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 FAQ가 두 벡터에 모두 판정 필요로 나오면 한 번만 판정한다")
    void 같은_FAQ는_한_번만_판정() {
        verdicts(Map.of(9L, Verdict.ANSWERABLE));

        Selection selection = select(List.of(judgeNeeded(9L, 0.84, QO)), List.of(judgeNeeded(9L, 0.74, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(9L);
        verify(judge, times(1)).judge(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 실행에서 같은 고객 질문으로 다시 검색하면 앞 판정을 다시 쓴다")
    void 같은_실행의_재검색은_판정을_다시_쓴다() {
        verdicts(Map.of(15L, Verdict.NOT_ANSWERABLE));

        select(List.of(), List.of(judgeNeeded(15L, 0.75, QA)));
        Selection again = select(List.of(), List.of(judgeNeeded(15L, 0.73, QA)));

        verify(judge, times(1)).judge(any(), any(), anyString(), anyString(), anyString());
        assertThat(again.judgments()).singleElement().satisfies(judgment -> {
            assertThat(judgment.reused()).isTrue();
            assertThat(judgment.verdict()).isEqualTo(Verdict.NOT_ANSWERABLE);
            assertThat(judgment.kept()).isFalse();
        });
    }

    @Test
    @DisplayName("다른 실행이거나 고객 질문이 다르거나 앞 판정이 실패였으면 다시 판정한다")
    void 다시_판정하는_경우() {
        when(judge.judge(any(), any(), anyString(), anyString(), anyString())).thenReturn(Verdict.ANSWERABLE);
        FaqGrayZoneSearchResponse candidates = new FaqGrayZoneSearchResponse(List.of(),
                List.of(judgeNeeded(16L, 0.75, QA)));

        selector.select(1L, 2L, QUESTION, candidates, 3);
        selector.select(99L, 2L, QUESTION, candidates, 3);
        selector.select(1L, 2L, "다른 질문", candidates, 3);
        selector.select(null, null, QUESTION, candidates, 3);
        selector.select(null, null, QUESTION, candidates, 3);
        verify(judge, times(5)).judge(any(), any(), anyString(), anyString(), anyString());

        when(judge.judge(any(), any(), anyString(), eq("FAQ 질문 17"), anyString())).thenReturn(Verdict.FAILED);
        FaqGrayZoneSearchResponse failing = new FaqGrayZoneSearchResponse(List.of(),
                List.of(judgeNeeded(17L, 0.75, QA)));
        selector.select(5L, 2L, QUESTION, failing, 3);
        selector.select(5L, 2L, QUESTION, failing, 3);
        verify(judge, times(2)).judge(any(), any(), anyString(), eq("FAQ 질문 17"), anyString());
    }

    @Test
    @DisplayName("통과 선 이상인데 판정에 넘긴 후보(재검색)는 안 됨이면 빼고, 실패하면 예전처럼 남긴다")
    void 통과_선_이상_후보도_판정_결과를_따른다() {
        verdicts(Map.of(18L, Verdict.NOT_ANSWERABLE, 19L, Verdict.FAILED));

        Selection selection = select(List.of(judgeNeeded(19L, 0.90, QO)), List.of(judgeNeeded(18L, 0.85, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(19L);
        assertThat(selection.judgments()).extracting(Judgment::faqId, Judgment::upperBand, Judgment::kept)
                .containsExactly(tuple(19L, true, true), tuple(18L, true, false));
    }

    @Test
    @DisplayName("판정에는 고객 질문과 FAQ 질문·답변을 넘긴다")
    void 판정_입력() {
        verdicts(Map.of(10L, Verdict.ANSWERABLE));

        select(List.of(), List.of(judgeNeeded(10L, 0.75, QA)));

        verify(judge).judge(1L, 2L, QUESTION, "FAQ 질문 10", "FAQ 답변 10");
    }

    @Test
    @DisplayName("한 질문의 판정 후보들은 동시에 판정한다")
    void 동시에_판정한다() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        when(judge.judge(any(), any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            bothStarted.countDown();
            // 차례로 판정하면 두 번째가 시작되지 않아 여기서 기다리다 거절로 끝난다
            return bothStarted.await(5, TimeUnit.SECONDS) ? Verdict.ANSWERABLE : Verdict.NOT_ANSWERABLE;
        });

        Selection selection = select(List.of(), List.of(judgeNeeded(11L, 0.75, QA), judgeNeeded(12L, 0.70, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("판정기가 예외를 던져도 실패로 보고 구간 규칙을 따른다")
    void 판정기_예외는_실패() {
        when(judge.judge(any(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("예상 못 한 오류"));

        Selection selection = select(List.of(), List.of(judgeNeeded(13L, 0.75, QA), judgeNeeded(14L, 0.70, QA)));

        assertThat(selection.results()).extracting(FaqSearchResponse::faqId).containsExactly(13L);
    }

    @Test
    @DisplayName("회색 구간이 꺼져 있으면 쓰지 않고, 켰는데 판정기가 없으면 기동을 막는다")
    void 설정으로_만든다() {
        SearchProperties off = properties(SearchProperties.GrayZone.DISABLED);
        SearchProperties on = properties(new SearchProperties.GrayZone(true, 0.80, 0.69, 0.88, 0.80));

        assertThat(JudgingFaqGrayZoneSelector.fromSettings(off, judge)).isNull();
        assertThat(JudgingFaqGrayZoneSelector.fromSettings(on, judge)).isInstanceOf(JudgingFaqGrayZoneSelector.class);
        assertThatThrownBy(() -> JudgingFaqGrayZoneSelector.fromSettings(on, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("llm.bedrock.enabled");
        verify(judge, never()).judge(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("판정 뒤 결과: 근거에 바로 통과한 FAQ가 있으면 판정 거절이 섞여도 바로 통과다")
    void 결과_바로_통과() {
        verdicts(Map.of(2L, Verdict.NOT_ANSWERABLE));

        assertThat(outcomeOf(List.of(), List.of(pass(1L, 0.85, QA), judgeNeeded(2L, 0.75, QA))))
                .isEqualTo(FaqGrayZoneOutcome.PASSED_DIRECTLY);
    }

    @Test
    @DisplayName("판정 뒤 결과: 같은 FAQ가 다른 벡터에서 바로 통과했으면 판정으로 살렸어도 바로 통과다")
    void 결과_같은_FAQ가_바로_통과() {
        verdicts(Map.of(1L, Verdict.ANSWERABLE));

        assertThat(outcomeOf(List.of(judgeNeeded(1L, 0.85, QO)), List.of(pass(1L, 0.82, QA))))
                .isEqualTo(FaqGrayZoneOutcome.PASSED_DIRECTLY);
    }

    @Test
    @DisplayName("판정 뒤 결과: 판정기가 살린 FAQ가 근거에 있으면 판정 실패로 남긴 후보가 섞여도 판정기가 통과시킨 것이다")
    void 결과_판정기가_통과() {
        verdicts(Map.of(2L, Verdict.ANSWERABLE, 3L, Verdict.FAILED));

        assertThat(outcomeOf(List.of(), List.of(judgeNeeded(3L, 0.76, QA), judgeNeeded(2L, 0.70, QA))))
                .isEqualTo(FaqGrayZoneOutcome.PASSED_BY_JUDGE);
    }

    @Test
    @DisplayName("판정 뒤 결과: 판정이 실패했는데 점수가 높아 남긴 후보뿐이면 판정기가 살린 것으로 세지 않는다")
    void 결과_판정_실패로_통과() {
        verdicts(Map.of(5L, Verdict.FAILED, 6L, Verdict.NOT_ANSWERABLE));

        assertThat(outcomeOf(List.of(), List.of(judgeNeeded(5L, 0.75, QA), judgeNeeded(6L, 0.70, QA))))
                .isEqualTo(FaqGrayZoneOutcome.PASSED_ON_JUDGE_FAILURE);
    }

    @Test
    @DisplayName("판정 뒤 결과: 판정기가 모두 거절하면 판정 거절이다")
    void 결과_판정기가_거절() {
        verdicts(Map.of(5L, Verdict.NOT_ANSWERABLE, 6L, Verdict.NOT_ANSWERABLE));

        assertThat(outcomeOf(List.of(), List.of(judgeNeeded(5L, 0.75, QA), judgeNeeded(6L, 0.70, QA))))
                .isEqualTo(FaqGrayZoneOutcome.REJECTED_BY_JUDGE);
    }

    @Test
    @DisplayName("판정 뒤 결과: 판정 실패로 버린 후보가 있으면 다른 후보를 거절했어도 판정 거절로 세지 않는다")
    void 결과_판정_실패로_버림() {
        verdicts(Map.of(5L, Verdict.NOT_ANSWERABLE, 6L, Verdict.FAILED));

        assertThat(outcomeOf(List.of(), List.of(judgeNeeded(5L, 0.75, QA), judgeNeeded(6L, 0.70, QA))))
                .isEqualTo(FaqGrayZoneOutcome.DROPPED_ON_JUDGE_FAILURE);
    }

    @Test
    @DisplayName("판정 뒤 결과: 판정할 후보도 없이 비면 아래 선 미만이다")
    void 결과_아래_선_미만() {
        assertThat(outcomeOf(List.of(), List.of())).isEqualTo(FaqGrayZoneOutcome.BELOW_FLOOR);
    }

    private FaqGrayZoneOutcome outcomeOf(List<Candidate> questionOnly, List<Candidate> questionAnswer) {
        FaqGrayZoneSearchResponse candidates = new FaqGrayZoneSearchResponse(questionOnly, questionAnswer);
        return selector.select(1L, 2L, QUESTION, candidates, 3).outcome(candidates);
    }

    private Selection select(List<Candidate> questionOnly, List<Candidate> questionAnswer) {
        return selector.select(1L, 2L, QUESTION, new FaqGrayZoneSearchResponse(questionOnly, questionAnswer), 3);
    }

    private void verdicts(Map<Long, Verdict> byFaqId) {
        byFaqId.forEach((faqId, verdict) -> when(judge.judge(any(), any(), anyString(),
                eq("FAQ 질문 " + faqId), anyString())).thenReturn(verdict));
    }

    private static Candidate pass(long faqId, double score, String vector) {
        return new Candidate(response(faqId, "SLOT-" + faqId, score, vector), false);
    }

    private static Candidate judgeNeeded(long faqId, double score, String vector) {
        return new Candidate(response(faqId, "SLOT-" + faqId, score, vector), true);
    }

    private static FaqSearchResponse response(long faqId, String slotId, double score, String vector) {
        return new FaqSearchResponse(faqId, slotId, "TEST", "FAQ 질문 " + faqId, "FAQ 답변 " + faqId,
                score, 1, null, 1, vector);
    }

    private static SearchProperties properties(SearchProperties.GrayZone zone) {
        return new SearchProperties(0.72, new SearchProperties.DualVector(true, 0.88),
                new SearchProperties.ScoreRecording(false, Duration.ofDays(90)), zone);
    }
}
