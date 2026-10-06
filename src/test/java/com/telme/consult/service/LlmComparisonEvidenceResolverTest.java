package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmClient;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class LlmComparisonEvidenceResolverTest {
    private final LlmClient llm = mock(LlmClient.class);
    private final LlmComparisonEvidenceResolver resolver =
            new LlmComparisonEvidenceResolver(llm, new ObjectMapper());
    private final String question = "에이와 비요금제의 가입 조건 차이를 비교해줘";
    private final FaqSearchResponse left = source(1, "에이 가입은 만 19세부터 가능합니다.");
    private final FaqSearchResponse right = source(2, "비요금제 가입은 만 65세부터 가능합니다.");

    @ParameterizedTest
    @ValueSource(strings = {
            "청소년 요금제하고 시니어 요금제 차이가 뭐야",
            "청소년 요금제와 시니어 요금제 차이가 뭐야",
            "청소년 요금제랑 시니어 요금제 차이가 뭐야",
            "명의 변경과 번호 이동의 필요 서류를 비교해줘"
    })
    void comparisonRequestsUseTheSamePolicyRegardlessOfConnector(String question) {
        assertThat(resolver.applies(question)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "가입 성과금 지급 조건을 알려줘",
            "로밍 신청 방법 알려주고 청소년 요금제와 시니어 요금제를 비교해줘"
    })
    void ordinaryAndMixedRequestsAreNotTreatedAsStandaloneComparisons(String question) {
        assertThat(resolver.applies(question)).isFalse();
    }

    @Test
    void rejectsCriterionInventedForTheQuestionBeforeAdditionalSearch() {
        var youth = source(3, "청소년 요금제는 데이터 8GB를 제공합니다.");
        var senior = source(4, "시니어 요금제는 데이터 5GB를 제공합니다.");
        when(llm.generate(any())).thenReturn(result(true, "청소년 요금제", "시니어 요금제", "데이터 제공량",
                3, youth.answer(), 4, senior.answer()));

        var resolution = resolver.resolveDetailed(null, "청소년 요금제하고 시니어 요금제의 가격 차이가 뭐야",
                List.of(youth, senior), (query, kind) -> {
                    throw new AssertionError("질문에 없는 기준으로 검색하지 않는다");
                });

        assertThat(resolution.sources()).isEmpty();
        assertThat(resolution.answer()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입 조건 비교", "가입 조건 및 비교", "가입 조건 차이점"})
    void acceptsCriterionWordsSeparatedByParticlesInTheOriginalQuestion(String criterion) {
        when(llm.generate(any())).thenReturn(result(true, "에이", "비요금제", criterion, 1,
                left.answer(), 2, right.answer()));

        assertThat(resolver.resolveDetailed(null, question, List.of(left, right), (query, kind) -> {
            throw new AssertionError("검증된 근거가 있어 추가 검색하지 않는다");
        }).sources()).containsExactly(left, right);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입 조건 데이터", "가입 조건 및 데이터", "및", "수"})
    void rejectsCriterionWhenOnlySomeOfItsWordsAreInTheQuestion(String criterion) {
        when(llm.generate(any())).thenReturn(result(true, "에이", "비요금제", criterion, 1,
                left.answer(), 2, right.answer()));

        assertThat(resolver.resolveDetailed(null, question, List.of(left, right), (query, kind) -> {
            throw new AssertionError("질문에 없는 속성으로 검색하지 않는다");
        }).sources()).isEmpty();
    }

    @Test
    void comparisonOfPlanTypesAllowsTypeCountWithoutIntroducingAnotherAttribute() {
        var both = new FaqSearchResponse(117L, null, "test", "5G와 LTE 요금제 종류",
                "5G는 4종, LTE는 3종으로 구성이 다릅니다.", 0.9, 1, null, 1, null);
        when(llm.generate(any())).thenReturn(result(true, "5G 요금제", "LTE 요금제", "요금제 종류 수",
                117, both.answer(), 117, both.answer()));

        var resolution = resolver.resolveDetailed(null, "5G와 LTE 요금제 종류를 비교해줘",
                List.of(both), (query, kind) -> List.of());

        assertThat(resolution.sources()).containsExactly(both);
        assertThat(resolution.answer()).isEqualTo(both.answer());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void emptyModelOutputCannotCreateComparisonAnswer(String output) {
        when(llm.generate(any())).thenReturn(output);

        var resolution = resolver.resolveDetailed(null, question, List.of(left, right), (query, kind) -> {
            throw new AssertionError("빈 판정으로 추가 검색하지 않는다");
        });

        assertThat(resolution.sources()).isEmpty();
        assertThat(resolution.answer()).isNull();
    }

    @Test
    void passesOnlyVerifiedSourcesForBothSides() {
        when(llm.generate(any())).thenReturn(result(true, "에이", "비요금제", "가입 조건", 1,
                "에이 가입은 만 19세부터 가능합니다.", 2, "비요금제 가입은 만 65세부터 가능합니다."));

        assertThat(resolver.resolve(null, question, List.of(left, right), (query, kind) ->
                { throw new AssertionError("extra search"); })).containsExactly(left, right);
        assertThat(resolver.resolveDetailed(null, question, List.of(left, right), (query, kind) ->
                { throw new AssertionError("extra search"); }).answer())
                .contains("에이: 에이 가입은", "비요금제: 비요금제 가입은");
    }

    @Test
    void searchesBothSidesOnlyWhenInitialEvidenceIsIncomplete() {
        when(llm.generate(any())).thenReturn(
                result(false, "에이", "비요금제", "가입 조건", 1,
                        "에이 가입은 만 19세부터 가능합니다.", null, null),
                result(true, "에이", "비요금제", "가입 조건", 1,
                        "에이 가입은 만 19세부터 가능합니다.", 2, "비요금제 가입은 만 65세부터 가능합니다."));
        List<String> searches = new ArrayList<>();

        assertThat(resolver.resolve(null, question, List.of(left), (query, kind) -> {
            searches.add(kind + ":" + query);
            return List.of(right);
        })).containsExactly(left, right);
        assertThat(searches).containsExactly(
                "COMPARISON_LEFT:에이 가입 조건", "COMPARISON_RIGHT:비요금제 가입 조건");
    }

    @Test
    void rejectsMissingSideAndUnverifiedQuotation() {
        when(llm.generate(any())).thenReturn(
                result(true, "에이", "비요금제", "가입 조건", 1,
                        "에이 가입은 만 19세부터 가능합니다.", 2, "비요금제 가입은 만 18세부터 가능합니다."));

        assertThat(resolver.resolve(null, question, List.of(left, right), (query, kind) -> List.of()))
                .isEmpty();
    }

    @Test
    void rejectsInventedTargetBeforeAdditionalSearch() {
        when(llm.generate(any())).thenReturn(result(false, "에이", "씨요금제", "가입 조건", 1,
                "에이 가입은 만 19세부터 가능합니다.", null, null));

        assertThat(resolver.resolve(null, question, List.of(left), (query, kind) ->
                { throw new AssertionError("extra search"); })).isEmpty();
    }

    @Test
    void acceptsEllipticalAnswerWhenFaqQuestionNamesTheTarget() {
        FaqSearchResponse namedByQuestion = new FaqSearchResponse(65L, null, "test",
                "명의변경 시 필요한 서류", "양도인과 양수인의 신분증이 각각 필요합니다.",
                0.9, 1, null, 1, null);
        var otherDocuments = new FaqSearchResponse(2L, null, "test", "비요금제 필요 서류",
                "본인 신분증을 지참하시면 됩니다.", 0.9, 1, null, 2, null);
        when(llm.generate(any())).thenReturn(result(true, "명의 변경", "비요금제", "필요 서류", 65,
                "양도인과 양수인의 신분증이 각각 필요합니다.", 2,
                otherDocuments.answer()));

        assertThat(resolver.resolve(null, "명의 변경과 비요금제의 필요 서류를 비교해줘",
                List.of(namedByQuestion, otherDocuments), (query, kind) -> List.of()))
                .containsExactly(namedByQuestion, otherDocuments);
    }

    @Test
    void expandsVerifiedFragmentsToWholeFaqSentence() {
        var both = new FaqSearchResponse(297L, null, "test", "유심과 eSIM 비용",
                "물리 유심 재발급은 7,700원, eSIM 발급은 2,750원으로 eSIM이 더 저렴합니다.",
                0.9, 1, null, 1, null);
        when(llm.generate(any())).thenReturn(result(true, "물리 유심 재발급", "eSIM 발급",
                "비용", 297, "물리 유심 재발급은 7,700원", 297,
                "eSIM 발급은 2,750원으로"));

        var resolution = resolver.resolveDetailed(null, "물리 유심 재발급과 eSIM 발급 비용 차이를 비교해줘",
                List.of(both), (query, kind) -> List.of());

        assertThat(resolution.sources()).containsExactly(both);
        assertThat(resolution.answer()).isEqualTo(both.answer());
    }

    @Test
    void rejectsInventedProductEvenWhenItsNetworkNameMatches() {
        var premium = source(3, "5G 프리미엄은 월 85,000원입니다.");
        var plus = source(4, "LTE 플러스는 월 49,000원입니다.");
        when(llm.generate(any())).thenReturn(result(true, "5G 프리미엄", "LTE 플러스", "월 요금",
                3, premium.answer(), 4, plus.answer()));

        assertThat(resolver.resolve(null, "5G 라이트와 LTE 플러스의 월 요금을 비교해줘",
                List.of(premium, plus), (query, kind) -> {
                    throw new AssertionError("invented target must not be searched");
                })).isEmpty();
    }

    @Test
    void rejectsSourceForDifferentProductOnTheSameNetwork() {
        var premium = source(3, "5G 프리미엄은 월 85,000원입니다.");
        var plus = source(4, "LTE 플러스는 월 49,000원입니다.");
        when(llm.generate(any())).thenReturn(result(true, "5G 라이트", "LTE 플러스", "월 요금",
                3, premium.answer(), 4, plus.answer()));

        assertThat(resolver.resolve(null, "5G 라이트와 LTE 플러스의 월 요금을 비교해줘",
                List.of(premium, plus), (query, kind) -> List.of())).isEmpty();
    }

    @Test
    void propagatesModelTimeoutInsteadOfReportingMissingEvidence() {
        var timeout = new GeneralException(LlmErrorCode.TIMEOUT);
        when(llm.generate(any())).thenThrow(timeout);

        assertThatThrownBy(() -> resolver.resolve(null, question, List.of(left, right),
                (query, kind) -> List.of())).isSameAs(timeout);
    }

    @Test
    void invalidJsonCannotCreateComparisonAnswer() {
        when(llm.generate(any())).thenReturn("null", "[]", "{\"answerable\":\"true\"}", "{broken");

        for (int i = 0; i < 4; i++) {
            assertThat(resolver.resolveDetailed(null, question, List.of(left, right),
                    (query, kind) -> { throw new AssertionError("invalid judgment must not expand search"); })
                    .answer()).isNull();
        }
    }

    @Test
    void associatesEvidenceCallWithItsConsultation() {
        when(llm.generate(any())).thenReturn(result(true, "에이", "비요금제", "가입 조건", 1,
                left.answer(), 2, right.answer()));

        resolver.resolveDetailed(31L, 11L, question, List.of(left, right),
                (query, kind) -> List.of());

        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(requests.capture());
        assertThat(requests.getValue().consultRequestId()).isEqualTo(11L);
    }

    private static FaqSearchResponse source(long id, String answer) {
        return new FaqSearchResponse(id, null, "test", "가입 조건", answer,
                0.9, 1, null, 1, null);
    }

    private static String result(boolean answerable, String leftTarget, String rightTarget,
            String criterion, Integer leftId, String leftQuote, Integer rightId, String rightQuote) {
        String lq = leftQuote == null ? "null" : "\"" + leftQuote + "\"";
        String rq = rightQuote == null ? "null" : "\"" + rightQuote + "\"";
        return """
                {"answerable":%s,"leftTarget":"%s","rightTarget":"%s","criterion":"%s",
                "leftFaqId":%s,"leftQuote":%s,"rightFaqId":%s,"rightQuote":%s}
                """.formatted(answerable, leftTarget, rightTarget, criterion, leftId, lq, rightId, rq);
    }
}


