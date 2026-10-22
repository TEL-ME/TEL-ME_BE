package com.telme.consult.service;

import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.MissingCondition;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.Objects;

/** 검색된 FAQ 근거로 되물을 조건을 정한다. 뽑지 못하면 되묻지 않고 답변으로 넘어간다. */
public class FaqClarificationPlanner {

    private final ConditionExtractor extractor;

    public FaqClarificationPlanner(ConditionExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor);
    }

    private FaqClarificationPlanner() {
        this.extractor = null;
    }

    // FAQ 조건은 묻지 않는다. 근거 답을 그대로 전해 답하는 정책(TELME-218)이라 지금 FAQ에는 물어야 할 조건이 거의 없다.
    // 되묻기 경로는 남아 요금제 추천의 나이·사용량 되묻기는 그대로 나간다
    public static FaqClarificationPlanner withoutConditions() {
        return new FaqClarificationPlanner();
    }

    // 조건은 질문에 가장 잘 맞는 1위 FAQ에서만 뽑는다. 2위나 3위 FAQ에서 뽑은 조건은 물은 것과 무관한 경우가 많았다
    // (Judge 점검 앱 문제 51턴 재생에서 되물은 조건의 근거 절반이 2위나 3위, TELME-218)
    public ClarificationPlan plan(Long executionId, String userQuery, List<FaqSearchResponse> sources) {
        if (extractor == null) {
            return ClarificationPlan.none();
        }
        List<FaqSearchResponse> top = sources == null || sources.isEmpty() ? List.of() : List.of(sources.getFirst());
        List<MissingCondition> conditions = extractor.extract(executionId, userQuery, top);
        return conditions.isEmpty() ? ClarificationPlan.none() : new ClarificationPlan(conditions);
    }
}
