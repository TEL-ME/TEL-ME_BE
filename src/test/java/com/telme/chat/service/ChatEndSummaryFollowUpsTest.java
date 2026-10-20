package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryFollowUpsTest {

    @ParameterizedTest
    @ValueSource(strings = {"아니 그러니까 폰 없어졌을때 정지는 어디서 하냐구요", "그니까 돈 안내면 언제부터 폰 끊기냐구요",
            "제 말은 해지하면 위약금이 얼마냐는 거예요",
            "다시 여쭤볼게요. 제 데이터를 다른 사람한테 나눠주는 방법이요"})
    @DisplayName("같은 문의를 다시 묻는 발화")
    void 다시_묻기(String content) {
        assertThat(ChatEndSummaryFollowUps.isReask(content)).isTrue();
        assertThat(ChatEndSummaryFollowUps.isCorrection(content)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"아 죄송 카드 말고 계좌로요", "아 서면 말고 해운대요", "아 1년 아니고 1년 6개월이요 공군이라",
            "아니 그냥 5만원 이하면 돼요 데이터 많은걸로", "아 아니다 일주일 가요",
            "잘못 말했어요 계좌 자동이체요", "정정할게요 15만원이요", "아 헷갈렸다 엄마 아니고 아빠꺼요", "14세가 아니라 만 12세에요"})
    @DisplayName("앞 문의의 값을 고쳐 말하는 발화")
    void 정정(String content) {
        assertThat(ChatEndSummaryFollowUps.isCorrection(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"유심 말고 eSIM은 얼마예요", "카드 말고 다른 결제 수단도 되나요?", "아니 근데 해지하면 위약금 있어요",
            "아니요", "로밍 요금 알려주세요", "잘못 청구된 것 같아요", "정정 신청은 어디서 해요?", "어머니 유심도 대신 받아 올 수 있어요?", "",
            // "그니까"로 시작해도 새로 묻는 말이다
            "그니까 아예 차단하는 방법은요?"})
    @DisplayName("새로 묻는 말·조건 답·일반 질문은 이어받는 발화가 아니다")
    void 이어받지_않음(String content) {
        assertThat(ChatEndSummaryFollowUps.isFollowUp(content)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"아 그건 됐고 폰 잃어버렸을때 위치 찾는 서비스 있어요?", "매장은 됐고 요금제나 알려줘",
            "그거는 필요 없고 해지 방법이요"})
    @DisplayName("바로 앞 문의를 거두는 발화")
    void 거두기(String content) {
        assertThat(ChatEndSummaryFollowUps.isWithdrawal(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"그건 괜찮아요?", "요금은 얼마예요", "됐어요 감사합니다", "로밍은 어떻게 신청해요", ""})
    @DisplayName("앞 문의를 거두는 말이 아니다")
    void 거두지_않음(String content) {
        assertThat(ChatEndSummaryFollowUps.isWithdrawal(content)).isFalse();
    }
}
