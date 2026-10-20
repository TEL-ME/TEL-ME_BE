package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryClosingRemarksTest {

    @ParameterizedTest
    @ValueSource(strings = {"네 감사합니다 수고하세요~", "감사합니다!", "아 네 알겠습니다 감사해요", "좋은 하루 되세요",
            "덕분에 해결됐어요 고맙습니다", "ㄳㄳ", "됐어요 ㅋㅋ 바이바이", "알겠어 땡큐",
            "넵 덕분에 해결했어요 고생하세요!", "네 애쓰셨어요 들어가세요", "고마워요 담에 또 올게요 ㅂㅂ",
            "넵 덕분에 다 해결했어요 감사합니다~ 수고하세요!!",
            "고마워요 수고 많으세요 들어가요"})
    @DisplayName("감사·작별 인사만 있는 발화는 마무리 인사다")
    void 마무리_인사(String content) {
        assertThat(ChatEndSummaryClosingRemarks.isClosingRemark(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"감사합니다 그런데 로밍은요?", "네 알겠습니다", "감사합니다 요금제 변경도 해주세요",
            "수고하세요 위약금은 얼마죠", "주식 오를까요", "", "다른 요금제도 알려주세요 감사합니다",
            // 짧은 낱말(잘·또·좋은·정말)은 접두 일치라 "잘못·또는·좋은데"에도 맞지만, 다른 낱말이 섞이면 인사가 아니다
            "잘못 청구된 것 같아요 감사합니다", "또는 요금제 변경도 돼요 감사합니다", "정말 요금이 두 번 나갔어요 감사합니다"})
    @DisplayName("다른 낱말이나 물음이 있거나 감사·작별 낱말이 없으면 마무리 인사가 아니다")
    void 마무리_인사가_아님(String content) {
        assertThat(ChatEndSummaryClosingRemarks.isClosingRemark(content)).isFalse();
    }
}
