package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndependentQuestionPolicyTest {
    @Test
    void acceptsGroundedSearchQuestionWhenItsQuoteWasMisCopied() {
        var subQueries = java.util.List.of(
                new LlmRoutingPayload.SubQueryPayload((short) 1, ConsultRequest.Intent.FAQ,
                        "로밍 요금", Map.of(), "로밍요금"),
                new LlmRoutingPayload.SubQueryPayload((short) 2, ConsultRequest.Intent.FAQ,
                        "유심 재발급 방법", Map.of(), "유심재발급받고싶어요"));
        var result = new LlmRoutingPayload(Intent.FAQ, BigDecimal.ONE, "", Map.of(), subQueries);
        assertThatCode(() -> IndependentQuestionPolicy.validate(
                result, "로밍요금과 유심재발급방법 알려줘")).doesNotThrowAnyException();
        assertThat(IndependentQuestionPolicy.verifiedQuote(subQueries.get(1),
                "로밍요금과 유심재발급방법 알려줘")).isEqualTo("유심 재발급 방법");
    }

    @Test
    void doesNotTreatWordsSplicedFromDifferentRequestsAsOneQuote() {
        var sub = new LlmRoutingPayload.SubQueryPayload((short) 3, ConsultRequest.Intent.FAQ,
                "로밍 해지 서류", Map.of(), "로밍해지서류");

        assertThatThrownBy(() -> IndependentQuestionPolicy.verifiedQuote(sub,
                "유심재발급비용과로밍신청방법그리고해지서류알려줘"))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
    }

    @Test
    void acceptsSharedSubjectWhenBothRequestsHaveSeparateOriginalAnchors() {
        assertThatCode(() -> IndependentQuestionPolicy.validate(
                payload("부가서비스 가입", "부가서비스 해지"),
                "부가서비스 가입이랑 해지는 어떻게 해요?"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsListedCategoriesWhenSharedDescriptionAppearsOnce() {
        assertThatCode(() -> IndependentQuestionPolicy.validate(
                payload("5G 요금제 구성 안내", "LTE 요금제 구성 안내", "알뜰요금제 구성 안내"),
                "5G, LTE, 알뜰 요금제 구성을 순서대로 안내해 주세요."))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInventedAnswerFacetEvenWhenEachRequestHasAnOriginalAnchor() {
        assertThatThrownBy(() -> IndependentQuestionPolicy.validate(
                payload("부가서비스 가입 비용", "부가서비스 해지 필요 서류"),
                "부가서비스 가입이랑 해지는 어떻게 해요?"))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
    }

    @Test
    void rejectsTwoRephrasingsAnchoredToOnlyOneActualRequest() {
        assertThatThrownBy(() -> IndependentQuestionPolicy.validate(
                payload("번호이동 신청", "번호이동 처리"),
                "번호이동 신청하고 싶어요"))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
    }

    @Test
    void acceptsSeparateExplicitRequestsWithSharedSubjectAndPredicate() {
        assertThatCode(() -> IndependentQuestionPolicy.validate(
                payload("번호이동 비용", "신청 방법", "필요 서류"),
                "번호 이동 비용과 신청 방법 그리고 필요 서류 알려줘")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "번호이동 절차 | 필요 서류",
            "번호이동 | 번호이동",
            "번호이동하고 | 이동하고싶어요",
            "싶어요 | 번호이동",
            "번호이동 | 이전 질문에 있던 요금제"
    })
    void rejectsInventedDuplicateOverlappingOrReorderedEvidence(String first, String second) {
        assertThatThrownBy(() -> IndependentQuestionPolicy.validate(
                payload(first, second), "번호 이동하고 싶어요"))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
    }

    @Test
    void rejectsMissingQuoteRatherThanTrustingGeneratedSubQuestions() {
        assertThatThrownBy(() -> IndependentQuestionPolicy.validate(
                payload(null, "로밍 요금"), "유심 비용과 로밍 요금 알려줘"))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
    }

    private LlmRoutingPayload payload(String... quotes) {
        var subQueries = Arrays.stream(quotes)
                .map(quote -> new LlmRoutingPayload.SubQueryPayload(
                        (short) 1, ConsultRequest.Intent.FAQ, "검색 질문", Map.of(), quote))
                .toList();
        return new LlmRoutingPayload(Intent.FAQ, BigDecimal.ONE, "검색 질문", Map.of(), subQueries);
    }
}
