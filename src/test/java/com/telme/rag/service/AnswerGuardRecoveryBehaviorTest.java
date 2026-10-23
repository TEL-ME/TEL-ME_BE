package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.ComparisonCheckProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerGuardRecoveryBehaviorTest {

    static final String EVIDENCE = "일주일이면 7일 기간권 39,000원이 가장 유리합니다. "
            + "일 단위 요금제는 9,900원, 데이터 무제한은 하루 12,100원이라 같은 기간을 쓰시면 기간권보다 비쌉니다.";
    static final String ANSWER = "일주일(7일) 로밍을 이용하신다면 7일 기간권이 39,000원으로 가장 저렴합니다.";
    private final AnswerGuard guard = new AnswerGuard();
    private final LlmClient client = mock(LlmClient.class);

    @ParameterizedTest
    @ValueSource(strings = {
            "39,000원인 7일 기간권이 가장 저렴해요.",
            "결론부터 말씀드리면 7일 기간권 39,000원이 제일 쌉니다.",
            "39,000원짜리 7일 기간권을 쓰시는 게 제일 저렴하겠습니다.",
            "7일 기간권이 제일 저렴하고 요금은 39,000원이에요.",
            ANSWER
    })
    void supportedVerdictPreservesNaturalWording(String answer) throws Exception {
        assertThat(apply(answer, "일주일 해외여행 가는데 로밍 뭐가 제일 싸요?", true)).isEqualTo(answer);
        verify(client).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "일주일 해외여행 가는데 로밍 뭐가 제일 싸요?",
            "7일 동안 일본 가요. 로밍 어떤 게 저렴해요?",
            "한 주 정도 해외에 머물 예정인데 로밍 비용을 아끼고 싶어요.",
            "담주에 일주일 출장가는데 로밍은 뭐가 제일 싸요",
            "지난번에는 3일 여행했고 이번에는 일주일 해외여행 가는데 로밍 뭐가 제일 싸요?",
            "출국까지 3일 남았고 일주일 해외여행 가는데 로밍 뭐가 제일 싸요?",
            "예전 3일 여행 요금이 궁금한 게 아니라 일주일 로밍 뭐가 제일 싸요?"
    })
    void naturalQuestionsCanReachTheSemanticJudge(String question) throws Exception {
        assertThat(apply(ANSWER, question, true)).isEqualTo(ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7일 기간권이 29,000원으로 가장 저렴합니다.",
            "14일 기간권이 39,000원으로 가장 저렴합니다.",
            "2주 여행이시면 7일 기간권이 가장 저렴합니다."
    })
    void explicitNumericConflictsNeverReachTheJudge(String answer) throws Exception {
        assertThat(apply(answer, "일주일 로밍 뭐가 제일 싸요?", true))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(client, never()).generate(any());
    }

    @Test
    void aDifferentTripDurationRequiresSemanticJudgement() throws Exception {
        assertThat(apply(ANSWER, "2주 여행이면 로밍 뭐가 제일 싸요?", false))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(client).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7일 무제한이 39,000원으로 가장 저렴합니다.",
            "7일 기간권은 가장 저렴하지 않습니다.",
            "일 단위 요금제가 가장 저렴해요."
    })
    void productAndNegationRequireSemanticRejection(String answer) throws Exception {
        assertThat(apply(answer, "일주일 로밍 뭐가 제일 싸요?", false))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(client).generate(any());
    }

    @Test
    void productValidityNeedNotEqualTripDuration() throws Exception {
        String evidence = "일주일 여행이면 14일 기간권 39,000원이 가장 유리합니다. 같은 기간을 쓰는 다른 상품들은 이 기간권보다 비쌉니다.";
        String answer = "14일 기간권이 39,000원으로 가장 저렴합니다.";
        // 이 테스트는 기간 관계가 판정기에 도달하는지만 확인한다. 인용 존재가 의미 정답을 보장하지 않는다.
        var review = judge().newReview(1L, 2L, "7일 여행 로밍은 뭐가 싸요?", evidence);
        when(client.generate(any())).thenReturn(new ObjectMapper().writeValueAsString(Map.of("decisions", List.of(
                Map.of("id", 0, "supported", true, "evidence", evidence)))));
        assertThat(guard.applyEvidencePolicy(answer, evidence, "7일 여행 로밍은 뭐가 싸요?", "", review))
                .isEqualTo(answer);
        verify(client).generate(any());
    }

    @Test
    void unsupportedOrUnavailableVerdictStillRemovesTheComparison() throws Exception {
        assertThat(apply(ANSWER, "일주일 로밍 뭐가 제일 싸요?", false))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(guard.applyEvidencePolicy(ANSWER, EVIDENCE, "일주일 로밍 뭐가 제일 싸요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void supportedStatementsWithoutNewComparisonsNeverCallTheJudge() throws Exception {
        String answer = "7일 기간권은 39,000원입니다.";
        assertThat(apply(answer, "일주일 로밍은요?", true)).isEqualTo(answer);
        verify(client, never()).generate(any());
    }

    @Test
    void anImportantNoticePrefixIsNotMistakenForAWeek() {
        assertThat(ComparisonClaimPrecheck.hasUngroundedWeekMeasure(
                "중요한 주의사항을 먼저 말씀드리면 30일 기간권이 가장 저렴합니다.",
                "30일 기간권 99,000원이 가장 유리합니다.")).isFalse();
    }

    @Test
    void originalFaqWordingAndSurroundingConditionsArePassedWhole() throws Exception {
        String evidence = "아래 상품은 일본에서만 가능합니다. " + EVIDENCE
                .replace("일 단위 요금제는", "하루짜리 요금제는") + " 현재 판매가 종료됐습니다.";
        var review = judge().newReview(1L, 2L, "미국에서 일주일 쓸 거예요.", evidence);
        when(client.generate(any())).thenReturn("{\"decisions\":[{\"id\":0,\"supported\":false,\"evidence\":\"\"}]}");
        assertThat(guard.applyEvidencePolicy(ANSWER, evidence, "미국에서 일주일 쓸 거예요.", "", review))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        var sent = org.mockito.ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(sent.capture());
        assertThat(new ObjectMapper().readTree(sent.getValue().userPrompt()).path("evidence").asText())
                .isEqualTo(evidence);
    }

    private String apply(String answer, String query, boolean approved) throws Exception {
        when(client.generate(any())).thenReturn(new ObjectMapper().writeValueAsString(Map.of("decisions", List.of(
                Map.of("id", 0, "supported", approved, "evidence", approved ? EVIDENCE : "")))));
        return guard.applyEvidencePolicy(answer, EVIDENCE, query, "", judge().newReview(1L, 2L, query, EVIDENCE));
    }

    private ComparisonEvidenceJudge judge() {
        return new ComparisonEvidenceJudge(client, new ObjectMapper(),
                new ComparisonCheckProperties(true, 1000, 4, 12000), ExecutionTrace.noop());
    }
}
