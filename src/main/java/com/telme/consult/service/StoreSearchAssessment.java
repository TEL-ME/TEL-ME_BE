package com.telme.consult.service;

import java.util.Objects;

/** 검색 안 함·결과 없음·검색 실패를 구분한다. 연결 계약은 협의 전이다. */
public final class StoreSearchAssessment {
    public enum SearchStatus {
        NOT_RUN,
        SUCCESS,
        FAILED
    }

    public enum NextStep {
        SEARCH_REQUIRED,
        USE_RESULTS,
        OFFER_CONDITION_CHANGE,
        SEARCH_FAILURE
    }

    public record SearchOutcome(SearchStatus status, int resultCount) {
        public SearchOutcome {
            Objects.requireNonNull(status, "status");
            if (resultCount < 0 || (status != SearchStatus.SUCCESS && resultCount != 0)) {
                throw new IllegalArgumentException("Only successful searches can contain results");
            }
        }
    }

    /** 검색에 필요한 조건이 채워진 상담에 사용한다. */
    public NextStep assess(SearchOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return switch (outcome.status()) {
            case NOT_RUN -> NextStep.SEARCH_REQUIRED;
            case FAILED -> NextStep.SEARCH_FAILURE;
            case SUCCESS ->
                    outcome.resultCount() == 0
                            ? NextStep.OFFER_CONDITION_CHANGE
                            : NextStep.USE_RESULTS;
        };
    }
}
