package com.telme.faq.dto.res;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 회색 구간 검색 결과. 벡터별 상위 결과를 점수 구간으로 나눈 채 돌려준다(아래 선 미만은 이미 뺐다)
// 호출하는 쪽이 판정 필요 후보를 판정한 뒤 DualVectorMerger로 합친다. 측정과 같은 순서다(docs/01-pipeline/04-evidence-judgment/gray-zone-upper-band.md)
// searchScoreId는 이 검색의 점수 기록 번호다. 판정이 끝난 뒤 같은 기록에 결과를 채운다. 기록을 끄거나 실패하면 null
public record FaqGrayZoneSearchResponse(List<Candidate> questionOnly, List<Candidate> questionAnswer, Long searchScoreId) {

    public FaqGrayZoneSearchResponse {
        questionOnly = List.copyOf(questionOnly);
        questionAnswer = List.copyOf(questionAnswer);
    }

    public FaqGrayZoneSearchResponse(List<Candidate> questionOnly, List<Candidate> questionAnswer) {
        this(questionOnly, questionAnswer, null);
    }

    // needsJudgment가 false면 점수가 통과 선 이상이라 판정 없이 근거로 쓴다
    public record Candidate(FaqSearchResponse result, boolean needsJudgment) {
    }

    // 점수와 관계없이 모든 후보를 판정 필요로 바꾼다. 아래 선 미만은 이미 빠져 있어 그대로다
    public FaqGrayZoneSearchResponse judgingAll() {
        return new FaqGrayZoneSearchResponse(judgingAll(questionOnly), judgingAll(questionAnswer), searchScoreId);
    }

    private static List<Candidate> judgingAll(List<Candidate> candidates) {
        return candidates.stream().map(candidate -> new Candidate(candidate.result(), true)).toList();
    }

    // 판정 없이 통과하는 후보의 FAQ 번호. 두 벡터에 같은 FAQ가 있으면 한 쪽만 바로 통과여도 들어간다
    public Set<Long> passedWithoutJudgmentFaqIds() {
        return Stream.concat(questionOnly.stream(), questionAnswer.stream())
                .filter(candidate -> !candidate.needsJudgment())
                .map(candidate -> candidate.result().faqId())
                .collect(Collectors.toSet());
    }

    // 판정 없이 모두 통과로 볼 결과. 회색 구간을 지원하지 않는 검색 구현이 쓴다
    public static FaqGrayZoneSearchResponse allPassed(List<FaqSearchResponse> results) {
        return new FaqGrayZoneSearchResponse(List.of(),
                results.stream().map(result -> new Candidate(result, false)).toList());
    }
}
