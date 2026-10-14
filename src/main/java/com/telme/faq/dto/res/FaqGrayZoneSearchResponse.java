package com.telme.faq.dto.res;

import java.util.List;

// 회색 구간 검색 결과. 벡터별 상위 결과를 점수 구간으로 나눈 채 돌려준다(아래 선 미만은 이미 뺐다)
// 호출하는 쪽이 판정 필요 후보를 판정한 뒤 DualVectorMerger로 합친다. 측정과 같은 순서다(docs/GRAY_ZONE_UPPER_BAND.md)
public record FaqGrayZoneSearchResponse(List<Candidate> questionOnly, List<Candidate> questionAnswer) {

    public FaqGrayZoneSearchResponse {
        questionOnly = List.copyOf(questionOnly);
        questionAnswer = List.copyOf(questionAnswer);
    }

    // needsJudgment가 false면 점수가 통과 선 이상이라 판정 없이 근거로 쓴다
    public record Candidate(FaqSearchResponse result, boolean needsJudgment) {
    }

    // 점수와 관계없이 모든 후보를 판정 필요로 바꾼다. 아래 선 미만은 이미 빠져 있어 그대로다
    public FaqGrayZoneSearchResponse judgingAll() {
        return new FaqGrayZoneSearchResponse(judgingAll(questionOnly), judgingAll(questionAnswer));
    }

    private static List<Candidate> judgingAll(List<Candidate> candidates) {
        return candidates.stream().map(candidate -> new Candidate(candidate.result(), true)).toList();
    }

    // 판정 없이 모두 통과로 볼 결과. 회색 구간을 지원하지 않는 검색 구현이 쓴다
    public static FaqGrayZoneSearchResponse allPassed(List<FaqSearchResponse> results) {
        return new FaqGrayZoneSearchResponse(List.of(),
                results.stream().map(result -> new Candidate(result, false)).toList());
    }
}
