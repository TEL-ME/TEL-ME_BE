package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoutingQuestionNormalizerTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "번호이동 하고싶어요", "번호 이동하고싶어요", "번호 이동 하고 싶어요",
            "번호이동하고싶어요", "번호\t이동\u00a0하고\u3000싶어요"
    })
    void koreanSpacingVariantsHaveSameAnalysisInput(String question) {
        assertThat(RoutingQuestionNormalizer.normalize(question)).isEqualTo("번호이동하고싶어요");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "5G 와 LTE 비교 | 5G와LTE비교",
            "1 2 개월 조건 | 1 2개월조건",
            "LTE 5G 요금제 | LTE 5G요금제",
            "U+ 로 번호 이동 | U+로번호이동",
            "미납 요금이 있는데 번호 이동 | 미납요금이있는데번호이동",
            "번호 이동은 안 하고 요금제만 변경 | 번호이동은안하고요금제만변경"
    })
    void preservesNumbersLatinWordBoundariesConditionsAndNegation(String question, String expected) {
        assertThat(RoutingQuestionNormalizer.normalize(question)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"로밍 요금 알려줘\n유심 비용 알려줘", "로밍 요금 알려줘\r\n유심 비용 알려줘"})
    void preservesIndependentSentenceBoundary(String question) {
        assertThat(RoutingQuestionNormalizer.normalize(question)).isEqualTo("로밍요금알려줘\n유심비용알려줘");
    }
}
