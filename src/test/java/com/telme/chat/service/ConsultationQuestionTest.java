package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConsultationQuestionTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "바라야 요금제 변경 알려줘|요금제 변경 알려줘",
            "[문의] 무러바라님 그럼 필요한 서류는?|그럼 필요한 서류는?",
            "[질문] 안녕 바라야, 로밍 신청 알려줘|로밍 신청 알려줘",
            "바라야😊 .5GB 요금제 있나요?|.5GB 요금제 있나요?",
            "강남역이요, 고마워 바라야 😊|강남역이요",
            "바라야 .5GB 요금제 있나요?|.5GB 요금제 있나요?",
            "안녕, .5GB는 0.5GB인가요?|.5GB는 0.5GB인가요?",
            "네, 고마워요|네",
            "성인, 고마워요|성인",
            "안녕 바라야, 요금제 변경 알려줘|요금제 변경 알려줘",
            "무러바라님 그럼 신청 방법은?|그럼 신청 방법은?",
            "안녕바라야, 로밍 신청 알려줘|로밍 신청 알려줘",
            "좋은 아침이에요, 무러바라씨, 로밍 신청 알려줘|로밍 신청 알려줘",
            "MUREOBARA, 로밍 신청 알려줘|로밍 신청 알려줘",
            "요금제 변경 알려줘. 고마워 바라야|요금제 변경 알려줘.",
            "요금제 변경 알려줘 고마워 바라야|요금제 변경 알려줘",
            "요금제는 언제 변경할 수 있나요 바라야?|요금제는 언제 변경할 수 있나요",
            "요금제 변경, 바라야, 언제 가능해?|요금제 변경, 언제 가능해?",
            "유심 비용, 고마워 바라야, 로밍 신청 알려줘|유심 비용, 로밍 신청 알려줘",
            "안녕 바라야, 유심 비용과 로밍 신청 알려줘. 고마워|유심 비용과 로밍 신청 알려줘.",
            "강남역이요, 고마워 바라야|강남역이요",
            "바라야 번호이동은 안 하고 요금제만 변경할래|번호이동은 안 하고 요금제만 변경할래",
            "바라야 5G 요금제 39,000원 맞아?|5G 요금제 39,000원 맞아?"
    })
    @DisplayName("분명한 인사·호칭·감사 구간만 제외하고 상담 본문을 유지한다")
    void extractsQuestion(String original, String expected) {
        assertThat(ConsultationQuestion.extract(original)).isEqualTo(expected);
        assertThat(ConsultationQuestion.extract(expected)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "이 메시지로 요금이 청구돼? 보낼 내용: 안녕",
            "문자 본문： 고마워 바라야", "보낼 내용은 안녕", "본문=안녕", "문자 본문: 안녕, 로밍 비용은?",
            "[5G 프리미어] 요금제 알려줘", "[상품명] 무러바라님 가입 방법은?",
            "하이패스 신청 알려줘", "하이브리드 요금제 있나요?", "바라던 요금제로 변경하고 싶어요",
            "hi-pass 요금 알려줘", "hi.example.com 접속하면 데이터 요금 나와?",
            "감사 인사를 문자로 보내면 요금이 나와?", "안녕이라는 문자를 보내면 요금이 나와?",
            "안녕 문자를 보내면 얼마인가요?", "고마워 인사를 문자로 보내면 얼마야?",
            "'안녕 바라야'라는 문자를 보내면 요금이 나와?", "로밍 중 ‘고마워’ 문자를 보내면 얼마야?",
            "요금제 이름이 \"바라\"인가요?", "유심 비용과 로밍 신청 방법 알려줘",
            "요금제 고마워 신청 방법 알려줘", "안녕 바라야", "고마워 바라야", "무러바라야"
    })
    @DisplayName("일반 단어·인용문·애매한 중간 표현·순수 기본 대화는 보존한다")
    void preservesQuestion(String question) {
        assertThat(ConsultationQuestion.extract(question)).isEqualTo(question);
    }
}
