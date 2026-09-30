package com.telme.faq.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 이중 벡터 검색 결과 합치기: 중복 제거 → 질문만 벡터(QUESTION_ONLY) 결과 우선 → topK 컷 → 순위 재부여
// 두 벡터는 점수 분포가 달라 점수로 섞어 정렬하지 않고 우선순위로 합친다 (docs/EVAL_SET_SUPPLEMENT.md 6절)
// topK로 다시 잘라야 LLM에 넘어가는 근거 수가 단일 벡터 검색과 같아진다
public final class DualVectorMerger {

    private DualVectorMerger() {
    }

    // 입력은 벡터별로 임계값을 거쳐 순위가 매겨진 결과. 같은 FAQ가 양쪽에 있으면 먼저 넣는 질문만 쪽 점수를 쓴다
    public static List<FaqSearchResponse> merge(
            List<FaqSearchResponse> questionOnly, List<FaqSearchResponse> questionAnswer, int topK) {
        List<FaqSearchResponse> merged = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (List<FaqSearchResponse> results : List.of(questionOnly, questionAnswer)) {
            for (FaqSearchResponse result : results) {
                if (merged.size() == topK) {
                    return merged;
                }
                if (seen.add(result.faqId())) {
                    merged.add(withRank(result, merged.size() + 1));
                }
            }
        }
        return merged;
    }

    private static FaqSearchResponse withRank(FaqSearchResponse result, int rank) {
        return new FaqSearchResponse(
                result.faqId(),
                result.slotId(),
                result.category(),
                result.question(),
                result.answer(),
                result.score(),
                result.version(),
                result.updatedAt(),
                rank,
                result.matchedVariant());
    }
}
