package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.service.QueryRoutingService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class QueryRoutingFollowupAnalysisProviderTest {
    private final QueryRoutingService routing = mock(QueryRoutingService.class);
    private final QueryRoutingFollowupAnalysisProvider provider =
            new QueryRoutingFollowupAnalysisProvider(routing);

    @Test
    void convertsFilledLocationAndKeepsExistingConsultRequest() {
        when(routing.analyzeFollowUp(3L, "강남역이요"))
                .thenReturn(
                        new FollowUpRouteResponse(
                                11L,
                                Map.of("location", "강남역"),
                                Set.of(),
                                QueryRouting.Method.LLM));

        var result = provider.analyze(context("강남역이요"));

        assertThat(result.followup().consultRequestId()).isEqualTo(11L);
        assertThat(result.followup().extractedConditions())
                .containsEntry("location", "강남역");
        assertThat(result.locationStatus()).isEqualTo(LocationStatus.AVAILABLE);
    }

    @Test
    void preservesExplicitLocationDecline() {
        when(routing.analyzeFollowUp(3L, "말하고 싶지 않아요"))
                .thenReturn(
                        new FollowUpRouteResponse(
                                11L,
                                Map.of(),
                                Set.of("location"),
                                QueryRouting.Method.RULE));

        var result = provider.analyze(context("말하고 싶지 않아요"));

        assertThat(result.followup().declinedKeys()).containsExactly("location");
        assertThat(result.locationStatus()).isEqualTo(LocationStatus.DECLINED);
    }

    @Test
    void staleWaitingContextDoesNotCreateOrSelectAnotherConsultation() {
        when(routing.analyzeFollowUp(3L, "강남역이요"))
                .thenReturn(FollowUpRouteResponse.noTarget());

        assertThatThrownBy(() -> provider.analyze(context("강남역이요")))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void newQuestionRequestsInitialRouting() {
        when(routing.analyzeFollowUp(3L, "5G 요금제는 얼마예요?"))
                .thenReturn(
                        new FollowUpRouteResponse(
                                11L,
                                Map.of(),
                                Set.of(),
                                QueryRouting.Method.LLM,
                                FollowUpRouteResponse.Disposition.NEW_QUESTION));

        var result = provider.analyze(context("5G 요금제는 얼마예요?"));

        assertThat(result.reroute()).isTrue();
    }

    private Context context(String message) {
        return new Context(
                3L,
                8L,
                message,
                List.of(
                        new Candidate(
                                11L,
                                "location",
                                6L,
                                "어느 지역인가요?",
                                "가까운 매장 알려줘",
                                "가까운 매장",
                                "STORE")));
    }
}
