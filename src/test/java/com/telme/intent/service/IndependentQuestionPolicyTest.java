package com.telme.intent.service;

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
