package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComparisonQuestionPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘",
            "요금제 변경 방법 알려주세요! 5G와 LTE 요금제 종류를 비교해주세요",
            "요금제 변경 방법 알려줘\n5G와 LTE 요금제 종류를 비교해줘",
            "요금제 변경 방법 알려주세요.\n5G와 LTE 요금제 종류를 비교해주세요",
            "요금제 변경 방법은 뭐야? 5G와 LTE 요금제 종류를 비교해줘",
            "요금제 변경 방법은 뭐야. 5G와 LTE 요금제 종류를 비교해줘",
            "요금제 변경 방법은 뭐야\n5G와 LTE 요금제 종류를 비교해줘",
            "로밍은 어떻게 신청하나요? 5G와 LTE 요금제 종류를 비교해주세요"
    })
    void independentRequestBeforeComparisonPreventsWholeQuestionMerge(String question) {
        assertThat(ComparisonQuestionPolicy.isStandaloneComparison(question, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5G와 LTE 요금제 종류 비교",
            "5G와 LTE 요금제 종류 차이",
            "5G와 LTE 요금제 종류를 비교해서 알려줘.",
            "5G와 LTE 요금제 종류를 비교해서 알려주세요?!",
            "지금 5G 요금제를 쓰고 있어. LTE 요금제와 데이터 제공량을 비교해줘.",
            "지금 5G 요금제를 쓰고 있어\nLTE 요금제와 데이터 제공량을 비교해줘",
            "현재 5.5GB를 사용하고 있어. 5G와 LTE 요금제 종류를 비교해줘"
    })
    void backgroundAndSearchPhrasesRemainStandaloneComparison(String question) {
        assertThat(ComparisonQuestionPolicy.isStandaloneComparison(question, null)).isTrue();
    }
}
