package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.*;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.*;
import com.telme.consult.service.StoreSearchAssessment.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** 다른 팀의 검색 구현 없이 실행하는 상담 연결 시나리오. 실제 지도 API 통합 테스트는 아니다. */
class StoreConsultationScenarioTest {
    private final DialogueService dialogue = new DialogueService(p -> p.fallbackText());
    private final StoreSearchAssessment assessment = new StoreSearchAssessment();

    @Test
    void missingRegionThenTypedReplyCanProceedToSearch() {
        var first =
                dialogue.decide(
                        new DialogueInput(
                                1L,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of(),
                                LocationStatus.MISSING));
        assertEquals(Action.ASK, first.action());
        var second =
                dialogue.decide(
                        new DialogueInput(
                                1L,
                                Purpose.NEARBY_STORE,
                                first.conditions(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.MISSING));
        assertEquals(Action.PROCEED, second.action());
        assertEquals(
                NextStep.SEARCH_REQUIRED,
                assessment.assess(new SearchOutcome(SearchStatus.NOT_RUN, 0)));
    }

    @Test
    void gpsDenialDoesNotPreventTextRegionSearch() {
        var result =
                dialogue.decide(
                        new DialogueInput(
                                1L,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.DECLINED));
        assertEquals(Action.PROCEED, result.action());
        assertEquals(
                NextStep.USE_RESULTS,
                assessment.assess(new SearchOutcome(SearchStatus.SUCCESS, 2)));
    }

    @Test
    void emptyResultsAndFailureMustNotBeTreatedAsMissingRegion() {
        assertEquals(
                NextStep.OFFER_CONDITION_CHANGE,
                assessment.assess(new SearchOutcome(SearchStatus.SUCCESS, 0)));
        assertEquals(
                NextStep.SEARCH_FAILURE,
                assessment.assess(new SearchOutcome(SearchStatus.FAILED, 0)));
    }

    @Test
    void changingRegionKeepsRequestedService() {
        var result =
                dialogue.decide(
                        new DialogueInput(
                                1L,
                                Purpose.NEARBY_STORE,
                                Map.of(
                                        "location",
                                        Condition.filled("강남역"),
                                        "serviceType",
                                        Condition.filled("USIM_REISSUE")),
                                Map.of("location", Condition.filled("역삼역")),
                                LocationStatus.MISSING));
        assertEquals("역삼역", result.conditions().get("location").value());
        assertEquals("USIM_REISSUE", result.conditions().get("serviceType").value());
        assertEquals(Action.PROCEED, result.action());
    }

    @Test
    void refusingRegionDoesNotPromiseAnUnimplementedStoreList() {
        var result =
                dialogue.decide(
                        new DialogueInput(
                                1L,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of("location", Condition.declined()),
                                LocationStatus.DECLINED));
        assertEquals(Action.ALTERNATIVE_GUIDANCE, result.action());
        assertFalse(result.message().contains("전체 매장 목록"));
    }

    @Test
    void searchCannotClaimResultsOnFailure() {
        assertThrows(
                IllegalArgumentException.class, () -> new SearchOutcome(SearchStatus.FAILED, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new SearchOutcome(SearchStatus.NOT_RUN, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new SearchOutcome(SearchStatus.SUCCESS, -1));
    }
}
