package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryClosingRemarksTest {

    @ParameterizedTest
    @ValueSource(strings = {"네 감사합니다 수고하세요~", "감사합니다!", "아 네 알겠습니다 감사해요", "좋은 하루 되세요",
            "덕분에 해결됐어요 고맙습니다", "ㄳㄳ", "됐어요 ㅋㅋ 바이바이", "알겠어 땡큐"})
    @DisplayName("감사·작별 인사만 있는 발화는 마무리 인사다")
    void 마무리_인사(String content) {
        assertThat(ChatEndSummaryClosingRemarks.isClosingRemark(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"감사합니다 그런데 로밍은요?", "네 알겠습니다", "감사합니다 요금제 변경도 해주세요",
            "수고하세요 위약금은 얼마죠", "주식 오를까요", ""})
    @DisplayName("다른 낱말이나 물음이 있거나 감사·작별 낱말이 없으면 마무리 인사가 아니다")
    void 마무리_인사가_아님(String content) {
        assertThat(ChatEndSummaryClosingRemarks.isClosingRemark(content)).isFalse();
    }
}
