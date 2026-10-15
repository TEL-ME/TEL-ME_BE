package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRepeatedInquiryResolverTest {
    private static final String FIRST = "유심을 분실했는데 재발급 비용과 무료 조건, 절차를 알려주세요.";
    private static final String REPEAT = "가입 기간은 18개월입니다. 합성 주민번호는 ***입니다. "
            + "같은 유심 재발급의 신분증 지참과 매장 방문 절차를 다시 알려주세요.";

    @Test
    @DisplayName("긴 동일 유심 재질문에 앞선 고객 발언과 현재 조건을 함께 남긴다")
    void 동일_유심_재질문은_원문으로_복원한다() {
        var result = ChatRepeatedInquiryResolver.resolve(
                REPEAT, createContext(REPEAT, createCustomerMessage(1, FIRST)));

        assertThat(result.needsClarification()).isFalse();
        assertThat(result.sourceMessageIds()).containsExactly(1L);
        assertThat(result.question()).containsSubsequence(FIRST, REPEAT);
    }

    @Test
    @DisplayName("같은 문의를 세 번 이어 물어도 이전의 가입 기간을 보존한다")
    void 반복된_이어_묻기의_조건을_남긴다() {
        String question = "같은 유심 재발급 절차를 다시 알려주세요.";
        var result = ChatRepeatedInquiryResolver.resolve(question,
                createContext(question, createCustomerMessage(1, FIRST), createCustomerMessage(3, REPEAT)));

        assertThat(result.sourceMessageIds()).containsExactly(1L, 3L);
        assertThat(result.question()).containsSubsequence(FIRST, REPEAT, question);
    }

    @Test
    @DisplayName("독립 유심 문의 둘 중 하나를 임의로 선택하지 않는다")
    void 여러_독립_문의는_확인을_요청한다() {
        var result = ChatRepeatedInquiryResolver.resolve(REPEAT,
                createContext(REPEAT, createCustomerMessage(1, FIRST), createCustomerMessage(3, "유심 교체도 별도로 문의합니다.")));

        assertThat(result.needsClarification()).isTrue();
        assertThat(result.sourceMessageIds()).isEmpty();
    }

    @Test
    @DisplayName("다른 방문자나 회선과 명시되지 않은 중간 정정을 자동으로 합치지 않는다")
    void 다른_대상과_불명확한_정정은_합치지_않는다() {
        assertThat(ChatRepeatedInquiryResolver.resolve("어머니의 같은 유심 절차를 다시 알려주세요.",
                createContext(REPEAT, createCustomerMessage(1, FIRST)))).isNull();
        var result = ChatRepeatedInquiryResolver.resolve(REPEAT,
                createContext(REPEAT, createCustomerMessage(1, FIRST),
                        createCustomerMessage(3, "가입 기간은 6개월이 아니라 18개월입니다.")));
        assertThat(result.needsClarification()).isTrue();
    }

    @Test
    @DisplayName("같은 유심이라고 해도 재발급에서 정지로 업무가 바뀌면 자동 연결하지 않는다")
    void 다른_업무로_바뀌면_확인을_요청한다() {
        String question = "같은 유심 정지 절차를 다시 알려주세요.";
        var result = ChatRepeatedInquiryResolver.resolve(
                question, createContext(question, createCustomerMessage(1, FIRST)));

        assertThat(result.needsClarification()).isTrue();
        assertThat(result.sourceMessageIds()).isEmpty();
    }

    private ChatContext createContext(String question, ChatContextMessage... history) {
        return new ChatContext(1L, 5L, null, List.of(history), question, 100);
    }

    private ChatContextMessage createCustomerMessage(long id, String text) {
        return new ChatContextMessage(id, (int) id, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, text, null);
    }
}
