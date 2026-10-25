package com.telme.consult.service;

import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.FaqGrayZoneOutcome;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 회색 구간 검색 결과에서 판정이 필요한 후보를 판정해 답변 근거를 고른다(TELME-160). */
public interface FaqGrayZoneSelector {

    Selection select(Long executionId, Long consultRequestId, String question,
            FaqGrayZoneSearchResponse candidates, int topK);

    /** results는 질문 벡터 우선으로 합친 최종 근거, judgments는 판정한 후보마다 하나씩이다. */
    record Selection(List<FaqSearchResponse> results, List<Judgment> judgments) {

        public Selection {
            results = List.copyOf(results);
            judgments = List.copyOf(judgments);
        }

        /**
         * 관리자 점수 통계용 결과(TELME-233). candidates는 판정에 넘긴 검색 결과다.
         * Judgment.kept는 합치면서 중복·topK로 빠질 수 있어 최종 근거(results)를 직접 본다.
         * 판정 결과가 ANSWERABLE·NOT_ANSWERABLE이 아니면(실패, 판정 없음) 판정기가 정한 것으로 보지 않는다
         */
        public FaqGrayZoneOutcome outcome(FaqGrayZoneSearchResponse candidates) {
            Set<Long> passedWithoutJudgment = candidates.passedWithoutJudgmentFaqIds();
            if (results.stream().anyMatch(result -> passedWithoutJudgment.contains(result.faqId()))) {
                return FaqGrayZoneOutcome.PASSED_DIRECTLY;
            }
            if (!results.isEmpty()) {
                Set<Long> answerable = judgments.stream().filter(judgment -> judgment.verdict() == Verdict.ANSWERABLE)
                        .map(Judgment::faqId).collect(Collectors.toSet());
                // 판정 필요 후보가 근거로 남는 길은 ANSWERABLE과 판정 실패 뒤 위쪽 구간 둘뿐이다
                return results.stream().anyMatch(result -> answerable.contains(result.faqId()))
                        ? FaqGrayZoneOutcome.PASSED_BY_JUDGE : FaqGrayZoneOutcome.PASSED_ON_JUDGE_FAILURE;
            }
            if (judgments.isEmpty()) {
                return FaqGrayZoneOutcome.BELOW_FLOOR;
            }
            return judgments.stream().allMatch(judgment -> judgment.verdict() == Verdict.NOT_ANSWERABLE)
                    ? FaqGrayZoneOutcome.REJECTED_BY_JUDGE : FaqGrayZoneOutcome.DROPPED_ON_JUDGE_FAILURE;
        }
    }

    /**
     * 판정 필요 후보 하나의 결과. upperBand는 판정을 붙이기 전에는 바로 통과하던 점수 구간(위로 넓힌 구간)이다.
     * verdict가 null이면 slot_id가 없어 판정하지 않고 거부한 후보다. reused면 같은 실행의 앞 검색에서 받은 판정을 다시 썼다.
     */
    record Judgment(Long faqId, String slotId, String vector, double score, boolean upperBand,
            Verdict verdict, boolean kept, boolean reused, Long elapsedMs) {
    }
}
