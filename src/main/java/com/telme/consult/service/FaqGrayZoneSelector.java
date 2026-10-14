package com.telme.consult.service;

import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import java.util.List;

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
    }

    /**
     * 판정 필요 후보 하나의 결과. upperBand는 판정을 붙이기 전에는 바로 통과하던 점수 구간(위로 넓힌 구간)이다.
     * verdict가 null이면 slot_id가 없어 판정하지 않고 거부한 후보다. reused면 같은 실행의 앞 검색에서 받은 판정을 다시 썼다.
     */
    record Judgment(Long faqId, String slotId, String vector, double score, boolean upperBand,
            Verdict verdict, boolean kept, boolean reused, Long elapsedMs) {
    }
}
