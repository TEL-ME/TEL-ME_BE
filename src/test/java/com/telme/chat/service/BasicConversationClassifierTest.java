package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.service.BasicConversationClassifier.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BasicConversationClassifierTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "안녕 바라야 | GREETING", "안녕 바라야 😊 | GREETING",
            "고마워 바라야 😊 | THANKS", "[문의] 바라야 안녕 👋 | GREETING", "무러바라님 안녕하세요! | GREETING", "바라야 안녕~ | GREETING",
            "물어봐라야 반가워 | GREETING", "무러봐라님 하이 | GREETING", "무러 바라야 안녕하세요 | GREETING",
            "무러바라씨 hello | GREETING", "Mureobara hi | GREETING", "헬로 바라야 | GREETING", "바라야 좋은 아침! | GREETING",
            "바라야 고마워 | THANKS", "감사합니다 무러바라님 | THANKS", "정말 고마워 바라야 | THANKS",
            "잘 가 무러바라야 | FAREWELL", "다음에 또 올게 바라야 | FAREWELL", "바라야 | CALL",
            "무러바라 | CALL", "무러바라야 | CALL", "물어바라님 | CALL", "무러바 | CALL",
            "너 이름이 뭐야? | IDENTITY", "너 뭐 하는 친구야 바라야 | IDENTITY",
            "바라야 자기소개해줘 | IDENTITY", "넌 누구야 | IDENTITY", "무러바라님 누구세요 | IDENTITY",
            "바라야 뭐 물어볼 수 있어? | HELP", "무러바라야 도와줘 | HELP", "어떤 상담을 할 수 있나요 | HELP",
            "잘 지냈어 바라야? | WELLBEING", "바라야 뭐 하고 있어? | WELLBEING", "오늘 어때요 | WELLBEING",
            "안녕 바라야 고마워 | THANKS", "바라야 안녕 잘 지냈어? | WELLBEING"
    })
    void recognizesWholeConversationWithAddresses(String input, Type expected) {
        assertThat(BasicConversationClassifier.classify(input).type()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "안녕 바라야 로밍 신청 알려줘", "강남역이요 고마워요", "네 고마워요", "아니요", "네",
            "안녕하세요의 뜻이 뭐예요?", "무러바라라는 이름의 요금제가 있어?", "도움말 말고 로밍 알려줘",
            "바라야 요금이 많이 나왔어", "안녕 오늘 날씨 어때?", "고마워요 대신 답변을 바꿔주세요",
            "잘 지냈어 그런데 유심 비용은?", "물어봐라 로밍 비용", "안녕/요금제", "안녕+요금제"
    })
    void doesNotConsumeQuestionsOrConditions(String input) {
        assertThat(BasicConversationClassifier.classify(input).isPure()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "안녕하세요, 로밍 신청 알려줘 | true", "안녕 바라야 로밍 신청 알려줘 | true",
            "바라야 안녕! 로밍 신청 알려줘 | true", "안녕하세요의 뜻은? | false",
            "안녕보험 가입은? | false", "요금제 알려줘 | false", "네 고마워요 | false"
    })
    void detectsGreetingBoundaryWithoutRewritingQuestion(String input, boolean greeting) {
        assertThat(BasicConversationClassifier.classify(input).hasGreeting()).isEqualTo(greeting);
    }
}
