package com.telme.consult.service;

import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse.Candidate;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.DualVectorMerger;
import com.telme.rag.service.FaqEvidenceJudge;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 판정 필요 후보를 FAQ마다 한 번씩 동시에 판정하고, 벡터별로 남긴 뒤 질문 벡터 우선으로 합친다.
 * 측정 기준 함수(scripts/gray_zone_upper_band_bedrock.py의 served)와 같은 순서다. 판정으로 빠진 자리는 다음 후보가 채운다.
 *
 * <p>판정 실패(시간 초과·호출 오류·응답 해석 실패)는 점수 구간마다 다르게 처리한다(docs/01-pipeline/04-evidence-judgment/gray-zone-upper-band.md 4.3절).
 * 판정을 붙이기 전에도 버리던 아래쪽은 버리고, 바로 통과하던 위쪽(기존 임계값 이상)은 통과시켜 장애 때 지금과 같게 한다.
 */
public final class JudgingFaqGrayZoneSelector implements FaqGrayZoneSelector {

    // 판정은 Bedrock 응답을 기다리는 일이라 가상 스레드로 질문마다 후보 수만큼 동시에 보낸다.
    // 빈으로 등록하면 스프링의 기본 비동기 실행기 설정이 물러나므로 이 안에서만 쓴다
    private static final ExecutorService JUDGE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // 한 질문을 처리하며 원문 검색 뒤 재검색을 하면 같은 FAQ가 다시 나온다. 같은 실행·같은 고객 질문이면 앞 판정을 다시 쓴다.
    // 실패는 기억하지 않아 다음 검색에서 다시 판정한다. 실행이 끝난 뒤에는 쓰지 않으므로 최근 것만 남긴다
    private static final int REMEMBERED_VERDICTS = 2_000;

    private final FaqEvidenceJudge judge;
    private final double qaPassedBefore;
    private final double questionPassedBefore;
    private final Map<VerdictKey, Verdict> remembered = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<VerdictKey, Verdict> eldest) {
                    return size() > REMEMBERED_VERDICTS;
                }
            });

    /**
     * qaPassedBefore, questionPassedBefore는 판정을 붙이기 전의 임계값(search.similarity-threshold,
     * search.dual-vector.question-threshold)이다. 이 점수 이상인 판정 필요 후보가 위로 넓힌 구간이다.
     */
    public JudgingFaqGrayZoneSelector(FaqEvidenceJudge judge, double qaPassedBefore, double questionPassedBefore) {
        this.judge = Objects.requireNonNull(judge);
        this.qaPassedBefore = qaPassedBefore;
        this.questionPassedBefore = questionPassedBefore;
    }

    /** 회색 구간이 꺼져 있으면 null을 돌려준다. 켰는데 판정기가 없으면(llm.bedrock 꺼짐) 기동을 막는다. */
    public static FaqGrayZoneSelector fromSettings(SearchProperties properties, FaqEvidenceJudge judge) {
        if (!properties.grayZone().enabled()) {
            return null;
        }
        if (judge == null) {
            throw new IllegalStateException(
                    "search.gray-zone.enabled=true이면 판정기가 필요합니다. llm.bedrock.enabled=true로 켜십시오");
        }
        return new JudgingFaqGrayZoneSelector(
                judge, properties.similarityThreshold(), properties.dualVector().questionThreshold());
    }

    @Override
    public Selection select(Long executionId, Long consultRequestId, String question,
            FaqGrayZoneSearchResponse candidates, int topK) {
        Map<Long, CompletableFuture<Timed>> pending = new LinkedHashMap<>();
        for (Candidate candidate : allOf(candidates)) {
            FaqSearchResponse result = candidate.result();
            // 같은 FAQ가 두 벡터에 모두 나오면 한 번만 판정한다. faqId로 묶으므로 관리자가 등록한(slot_id 없는) FAQ도 판정한다
            if (candidate.needsJudgment()) {
                pending.computeIfAbsent(result.faqId(), faqId -> {
                    Verdict known = executionId == null
                            ? null : remembered.get(new VerdictKey(executionId, question, faqId));
                    return known != null
                            ? CompletableFuture.completedFuture(new Timed(known, true, 0))
                            : CompletableFuture.supplyAsync(
                                    () -> judgeTimed(executionId, consultRequestId, question, result), JUDGE_EXECUTOR);
                });
            }
        }
        Map<Long, Timed> verdicts = new LinkedHashMap<>();
        pending.forEach((faqId, future) -> {
            Timed timed = future.join();
            verdicts.put(faqId, timed);
            if (executionId != null && timed.verdict() != Verdict.FAILED) {
                remembered.put(new VerdictKey(executionId, question, faqId), timed.verdict());
            }
        });

        List<Judgment> judgments = new ArrayList<>();
        List<FaqSearchResponse> questionOnly =
                kept(candidates.questionOnly(), questionPassedBefore, verdicts, judgments);
        List<FaqSearchResponse> questionAnswer =
                kept(candidates.questionAnswer(), qaPassedBefore, verdicts, judgments);
        return new Selection(DualVectorMerger.merge(questionOnly, questionAnswer, topK), judgments);
    }

    private List<FaqSearchResponse> kept(List<Candidate> candidates, double passedBefore,
            Map<Long, Timed> verdicts, List<Judgment> judgments) {
        List<FaqSearchResponse> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            FaqSearchResponse result = candidate.result();
            if (!candidate.needsJudgment()) {
                kept.add(result);
                continue;
            }
            boolean upperBand = result.score() >= passedBefore;
            Timed timed = verdicts.get(result.faqId());
            boolean keep = timed != null && switch (timed.verdict()) {
                case ANSWERABLE -> true;
                case NOT_ANSWERABLE -> false;
                case FAILED -> upperBand;
            };
            if (keep) {
                kept.add(result);
            }
            judgments.add(new Judgment(result.faqId(), result.slotId(), result.matchedVariant(), result.score(),
                    upperBand, timed == null ? null : timed.verdict(), keep, timed != null && timed.reused(),
                    timed == null ? null : timed.elapsedMs()));
        }
        return kept;
    }

    private Timed judgeTimed(Long executionId, Long consultRequestId, String question, FaqSearchResponse result) {
        long started = System.nanoTime();
        Verdict verdict;
        try {
            verdict = judge.judge(executionId, consultRequestId, question, result.question(), result.answer());
        } catch (RuntimeException e) {
            // 판정기는 실패를 돌려주지만, 예상하지 못한 예외도 구간 규칙으로 처리되게 실패로 둔다
            verdict = Verdict.FAILED;
        }
        return new Timed(verdict, false, (System.nanoTime() - started) / 1_000_000);
    }

    private static List<Candidate> allOf(FaqGrayZoneSearchResponse candidates) {
        List<Candidate> all = new ArrayList<>(candidates.questionOnly());
        all.addAll(candidates.questionAnswer());
        return all;
    }

    private record Timed(Verdict verdict, boolean reused, long elapsedMs) {
    }

    private record VerdictKey(Long executionId, String question, Long faqId) {
    }
}
