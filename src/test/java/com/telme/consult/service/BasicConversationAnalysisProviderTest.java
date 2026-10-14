package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

class BasicConversationAnalysisProviderTest {
    @Test
    void repeatedClarificationDoesNotRepeatStoredGreeting() {
        var context = new Context(1, 10, "고마워 바라야", List.of(new Candidate(
                101, "location", 9, "안녕하세요! 어느 지역의 매장을 찾으시나요?", "매장 알려줘", "매장", "STORE")));

        ChatAnswer answer = BasicConversationAnalysisProvider.answerFor(context);

        assertThat(answer.content())
                .isEqualTo("천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요.\n\n어느 지역의 매장을 찾으시나요?");
    }

    @ParameterizedTest
    @MethodSource("기본_대화_사례")
    @DisplayName("대기 상담이 없는 기본 대화는 기존 분석 없이 고정 응답한다")
    void 기본_대화는_고정_응답한다(String input, String expected) {
        ChatAnswer answer = BasicConversationAnalysisProvider.answerFor(new Context(1, 10, input, List.of()));

        assertThat(answer.content()).isEqualTo(expected);
        assertThat(answer.messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(answer.answerBasis()).isNull();
        assertThat(answer.followUps()).isEmpty();
    }

    static Stream<Arguments> 기본_대화_사례() {
        return Stream.of(
                Arguments.of("안녕하세요", "안녕하세요! 무러바라예요 😊 무엇이 궁금하세요?"),
                Arguments.of("  안녕하세요!!  ", "안녕하세요! 무러바라예요 😊 무엇이 궁금하세요?"),
                Arguments.of("안녕~", "안녕하세요! 무러바라예요 😊 무엇이 궁금하세요?"),
                Arguments.of("반갑습니다.", "안녕하세요! 무러바라예요 😊 무엇이 궁금하세요?"),
                Arguments.of("감사합니다", "천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요."),
                Arguments.of("고마워요!", "천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요."),
                Arguments.of("고맙습니다", "천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요."),
                Arguments.of("감사해요…", "천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요."),
                Arguments.of("안녕히 계세요", "이용해 주셔서 감사해요 😊 궁금한 점이 생기면 다시 찾아주세요."),
                Arguments.of("잘 가요", "이용해 주셔서 감사해요 😊 궁금한 점이 생기면 다시 찾아주세요."),
                Arguments.of("수고하세요", "이용해 주셔서 감사해요 😊 궁금한 점이 생기면 다시 찾아주세요."),
                Arguments.of(
                        "도움말", "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요 😊 궁금한 내용을 말씀해 주세요."),
                Arguments.of(
                        "뭐 물어볼 수 있어요?",
                        "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요 😊 궁금한 내용을 말씀해 주세요."));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "안녕하세요, 유심 재발급 비용 알려주세요",
                "감사합니다. 요금제 변경은 언제 적용돼요?",
                "네, 고마워요",
                "네, 유심 비용도 알려주세요",
                "아니요",
                "네",
                "지난달이요",
                "가입한 달이 무슨 뜻이에요?",
                "안녕하세요의 뜻이 뭐예요?",
                "감사합니다라고 하면 되나요?",
                "고마워요 대신 답변을 바꿔주세요",
                "가까운 매장 알려줘",
                "요금제 변경 기준이 뭐예요?",
                "오늘 날씨 어때요?",
                "도움말 말고 로밍 알려줘"
            })
    @DisplayName("상담 질문과 조건 응답은 인사나 감사가 섞여도 기본 대화로 답하지 않는다")
    void 상담_발화는_가로채지_않는다(String input) {
        assertThat(BasicConversationAnalysisProvider.answerFor(new Context(1, 10, input, List.of()))).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"네, 고마워요", "강남역이요, 고마워요"})
    @DisplayName("되묻기 대기 중 실제 조건 답변은 기본 대화로 답하지 않고 후속 처리에 남긴다")
    void 대기_상담의_후속_처리를_우선한다(String input) {
        Context context = new Context(1, 10, input, List.of(
                new Candidate(101, "location", 9, "어느 지역인가요?", "매장 알려줘", "매장", "STORE")));

        assertThat(BasicConversationAnalysisProvider.answerFor(context)).isNull();
    }
}
