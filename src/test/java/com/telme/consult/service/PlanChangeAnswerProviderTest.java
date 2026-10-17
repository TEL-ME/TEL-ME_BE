package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanChangeAnswerProviderTest {

    private static final long USER_ID = 7L;

    private AnswerProvider faq;
    private PlanChangeAnswerer changes;
    private PlanChangeAnswerProvider provider;

    @BeforeEach
    void setUp() {
        faq = mock(AnswerProvider.class);
        changes = mock(PlanChangeAnswerer.class);
        provider = new PlanChangeAnswerProvider(faq, changes, sessionId -> Optional.of(USER_ID), ExecutionTrace.noop(), true);
    }

    @Test
    @DisplayName("변경 요청이 아니면 기존 답변 경로에 그대로 맡긴다")
    void 변경_요청_아님() {
        Prepared searched = Prepared.none();
        when(faq.prepare(any(), anyBoolean())).thenReturn(searched);
        AnswerInput input = input(Purpose.GENERAL_FAQ, "요금제 변경은 어떻게 해요?");

        assertThat(provider.prepare(input, true)).isSameAs(searched);
        verify(changes, never()).answer(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("변경 요청이면 기존 답변 경로를 부르지 않고 확인 질문을 되묻는다")
    void 확인_질문() {
        MissingCondition ask = new MissingCondition(PlanChangeAnswerer.CONFIRM_KEY, "바꿀까요?", List.of("예", "아니요"), null);
        when(changes.answer(USER_ID, "5G 라이트로 바꿔줘", "5G 라이트로 바꿔줘", Map.of(), true))
                .thenReturn(new PlanChangeAnswerer.Outcome(null, List.of(), List.of(ask)));

        Prepared prepared = provider.prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true);

        assertThat(prepared.plan()).isEqualTo(new ClarificationPlan(List.of(ask)));
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    @DisplayName("변경 결과는 요금제 데이터로 만든 답변으로 낸다")
    void 변경_결과() {
        when(changes.answer(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PlanChangeAnswerer.Outcome("5G 라이트로 바꿨어요.", List.of("내 요금제 알려줘"), null));

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true).answer();

        assertThat(answer.answer().content()).isEqualTo("5G 라이트로 바꿨어요.");
        assertThat(answer.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(answer.answer().followUps()).containsExactly("내 요금제 알려줘");
    }

    @Test
    @DisplayName("확인 질문을 할 수 없는 경로에서는 바꾸지 않도록 되묻기 없이 부른다")
    void 확인_불가_경로() {
        when(changes.answer(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PlanChangeAnswerer.Outcome("확인을 받은 뒤에 진행해요.", List.of(), null));

        provider.generate(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"));

        verify(changes).answer(USER_ID, "5G 라이트로 바꿔줘", "5G 라이트로 바꿔줘", Map.of(), false);
        verify(faq, never()).generate(any());
    }

    @Test
    @DisplayName("매장 찾기 질문과 요금제 답변을 끈 경우에는 변경 요청도 기존 경로가 답한다")
    void 맡지_않는_경우() {
        new PlanChangeAnswerProvider(faq, changes, sessionId -> Optional.of(USER_ID), ExecutionTrace.noop(), false)
                .prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true);
        provider.prepare(input(Purpose.NEARBY_STORE, "5G 라이트로 바꿔줘"), true);

        verify(changes, never()).answer(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("변경 처리 중 오류가 나도 FAQ 답으로 넘기지 않고 바꾸지 못했다고 알린다")
    void 오류() {
        when(changes.answer(any(), any(), any(), any(), anyBoolean())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true).answer();

        assertThat(answer.answer().content()).contains("지금은 요금제를 바꾸지 못했어요");
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    @DisplayName("추천 답변의 변경 방법 버튼 옆에 추천 요금제로 바꾸는 버튼을 더한다")
    void 추천_버튼() {
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.answered(answer("5G 라이트로 바꾸면 월 10,000원 아껴요.",
                List.of(PlanChangeAnswerProvider.RECOMMENDATION_CHANGE_QUESTION))));
        when(changes.changeButton(USER_ID, "5G 라이트로 바꾸면 월 10,000원 아껴요.")).thenReturn(Optional.of("5G 라이트로 바꿔줘"));

        Prepared prepared = provider.prepare(input(Purpose.GENERAL_FAQ, "나한테 맞는 요금제 추천해줘"), true);

        assertThat(prepared.answer().answer().followUps())
                .containsExactly(PlanChangeAnswerProvider.RECOMMENDATION_CHANGE_QUESTION, "5G 라이트로 바꿔줘");
    }

    @Test
    @DisplayName("변경 방법 버튼이 없는 답변이나 버튼을 만들다 실패한 답변은 그대로 낸다")
    void 추천_버튼_없음() {
        GeneratedAnswer plain = answer("FAQ 답변", List.of("요금제 종류 알려줘"));
        when(faq.generate(any())).thenReturn(plain);
        GeneratedAnswer recommended = answer("5G 라이트를 추천해요.",
                List.of(PlanChangeAnswerProvider.RECOMMENDATION_CHANGE_QUESTION));
        when(faq.generate(any(), any())).thenReturn(recommended);
        when(changes.changeButton(any(), anyString())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThat(provider.generate(input(Purpose.GENERAL_FAQ, "요금제 종류가 뭐예요"))).isSameAs(plain);
        assertThat(provider.generate(input(Purpose.GENERAL_FAQ, "요금제 추천해줘"), Prepared.none())).isSameAs(recommended);
        verify(changes).changeButton(any(), anyString());
    }

    private static AnswerInput input(Purpose purpose, String question) {
        return new AnswerInput(1L, 2L, 3L, purpose, question, question, Map.of());
    }

    private static GeneratedAnswer answer(String content, List<String> followUps) {
        return GeneratedAnswer.withoutSources(new ChatAnswer(ChatMessage.MessageType.ANSWER, content,
                ChatMessage.AnswerBasis.GROUNDED, followUps, null));
    }
}
