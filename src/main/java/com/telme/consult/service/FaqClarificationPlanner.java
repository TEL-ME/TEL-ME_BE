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

    public ClarificationPlan plan(Long executionId, String userQuery, List<FaqSearchResponse> sources) {
        List<MissingCondition> conditions = extractor.extract(executionId, userQuery, sources);
        return conditions.isEmpty() ? ClarificationPlan.none() : new ClarificationPlan(conditions);
    }
}
