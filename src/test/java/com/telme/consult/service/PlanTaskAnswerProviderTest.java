package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
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

class PlanTaskAnswerProviderTest {

    private static final long USER_ID = 7L;

    private AnswerProvider faq;
    private PlanChangeAnswerer changes;
    private PlanCancelAnswerer cancels;
    private PlanTaskAnswerProvider provider;

    @BeforeEach
    void setUp() {
        faq = mock(AnswerProvider.class);
        changes = mock(PlanChangeAnswerer.class);
        when(changes.task()).thenReturn(PlanTask.CHANGE);
        cancels = mock(PlanCancelAnswerer.class);
        when(cancels.task()).thenReturn(PlanTask.CANCEL);
        provider = provider(true);
    }

    @Test
    @DisplayName("업무 요청이 아니면 기존 답변 경로에 그대로 맡긴다")
    void 업무_요청_아님() {
        Prepared searched = Prepared.none();
        when(faq.prepare(any(), anyBoolean())).thenReturn(searched);

        assertThat(provider.prepare(input(Purpose.GENERAL_FAQ, "요금제 변경은 어떻게 해요?"), true)).isSameAs(searched);
        verify(changes, never()).answer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("업무 요청이면 기존 답변 경로를 부르지 않고 맡은 처리기의 안내와 링크를 요금제 데이터 답변으로 낸다")
    void 업무_안내() {
        when(changes.answer(USER_ID, "5G 라이트로 바꿔줘", "5G 라이트로 바꿔줘", null))
                .thenReturn(PlanTaskOutcome.reply("요금제 변경 화면에서 골라 주세요.", PlanTaskLinks.CHANGE));

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true).answer();

        assertThat(answer.answer().content()).isEqualTo("요금제 변경 화면에서 골라 주세요.");
        assertThat(answer.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(answer.answer().followUps()).containsExactly(PlanTaskLinks.CHANGE);
        assertThat(provider.generate(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘")).answer().content())
                .isEqualTo("요금제 변경 화면에서 골라 주세요.");
        verify(faq, never()).prepare(any(), anyBoolean());
        verify(faq, never()).generate(any());
    }

    @Test
    @DisplayName("업무 판정은 이번 원문으로만 한다. 문맥으로 풀어쓴 문장에 섞인 이전 변경 요청은 다시 잡지 않는다")
    void 이전_발언의_요청() {
        Prepared searched = Prepared.none();
        when(faq.prepare(any(), anyBoolean())).thenReturn(searched);
        String resolved = "[대상을 확인할 이전 고객 발언]\n5G 라이트로 바꿔줘\n[현재 후속 질문]\n그건 데이터 몇 기가예요?";
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "그건 데이터 몇 기가예요?",
                "그건 데이터 몇 기가예요?", Map.of(), resolved, null);

        assertThat(provider.prepare(input, true)).isSameAs(searched);
        verify(changes, never()).answer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이번 원문이 업무 요청이면 풀어쓴 문장은 처리기가 대상 요금제를 보충하는 데 넘긴다")
    void 대상_보충() {
        String resolved = "[대상을 확인할 이전 고객 발언]\n5G 라이트 얼마예요?\n[현재 후속 질문]\n그걸로 바꿔줘";
        when(changes.answer(USER_ID, "그걸로 바꿔줘", resolved, null))
                .thenReturn(PlanTaskOutcome.reply("5G 라이트로 바꾸려면 변경 화면에서 골라 주세요.", PlanTaskLinks.CHANGE));

        GeneratedAnswer answer = provider.prepare(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "그걸로 바꿔줘",
                "그걸로 바꿔줘", Map.of(), resolved, null), true).answer();

        assertThat(answer.answer().content()).startsWith("5G 라이트로 바꾸려면");
    }

    @Test
    @DisplayName("채팅방의 직전 추천 답변 첫 문장을 처리기가 대상 요금제를 보충하는 데 넘긴다")
    void 직전_추천_보충() {
        String recommendation = "한 달에 데이터를 8GB 쓰신다면 LTE 플러스(월 49,000원, 데이터 15GB) 요금제를 추천해요.";
        String content = recommendation + " 5G로 쓰시려면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제가 가장 저렴해요. 로그인하고 가입하세요.";
        when(changes.answer(USER_ID, "그 요금제로 바꿔줘", "그 요금제로 바꿔줘", recommendation))
                .thenReturn(PlanTaskOutcome.reply("LTE 플러스로 바꾸려면 변경 화면에서 신청해 주세요.", PlanTaskLinks.CHANGE));
        provider = new PlanTaskAnswerProvider(faq, List.of(changes, cancels), sessionId -> Optional.of(USER_ID),
                sessionId -> sessionId == 2L ? Optional.of(content) : Optional.empty(), ExecutionTrace.noop(), true);

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "그 요금제로 바꿔줘"), true).answer();

        assertThat(answer.answer().content()).startsWith("LTE 플러스로 바꾸려면");
    }

    @Test
    @DisplayName("직전 추천은 대상 요금제를 보충하는 변경·신청만 조회하고, 해지는 조회하지 않는다")
    void 해지는_직전_추천을_조회하지_않는다() {
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
        provider = new PlanTaskAnswerProvider(faq, List.of(changes, cancels), sessionId -> Optional.of(USER_ID),
                sessionId -> {
                    lookups.incrementAndGet();
                    return Optional.empty();
                }, ExecutionTrace.noop(), true);
        when(cancels.answer(any(), any(), any(), any())).thenReturn(PlanTaskOutcome.reply("해지하면 바로 끊겨요.", PlanTaskLinks.CANCEL));
        when(changes.answer(any(), any(), any(), any())).thenReturn(PlanTaskOutcome.reply("변경 화면에서 골라 주세요.", PlanTaskLinks.CHANGE));

        provider.prepare(input(Purpose.GENERAL_FAQ, "요금제 해지해줘"), true);
        assertThat(lookups.get()).isZero();

        provider.prepare(input(Purpose.GENERAL_FAQ, "그 요금제로 바꿔줘"), true);
        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("직전 추천은 첫 문장만 쓴다. 문장 끝 표시가 없으면 본문 전체를 그대로 넘긴다")
    void 첫_문장만_쓴다() {
        assertThat(PlanTaskAnswerProvider.targetSentence(
                "한 달에 데이터를 40GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요. 뒤 문장"))
                .isEqualTo("한 달에 데이터를 40GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요.");
        assertThat(PlanTaskAnswerProvider.targetSentence("표시 없는 본문")).isEqualTo("표시 없는 본문");
    }

    @Test
    @DisplayName("업무마다 맡은 처리기로 보낸다. 해지 요청은 변경 처리기를 부르지 않는다")
    void 업무별_처리기() {
        when(cancels.answer(any(), any(), any(), any())).thenReturn(PlanTaskOutcome.reply("해지하면 바로 끊겨요.", PlanTaskLinks.CANCEL));

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "요금제 해지해줘"), true).answer();

        assertThat(answer.answer().content()).isEqualTo("해지하면 바로 끊겨요.");
        verify(changes, never()).answer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("링크 문구가 채팅으로 들어오면 그 화면으로 가는 길을 글로 알려준다")
    void 링크_문구() {
        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, PlanTaskLinks.CANCEL), true).answer();

        assertThat(answer.answer().content()).isEqualTo("마이페이지의 내 요금제 화면에서 진행해 주세요.");
        assertThat(answer.answer().followUps()).isEmpty();
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    @DisplayName("매장 찾기 질문과 요금제 답변을 끈 경우에는 업무 요청도 기존 경로가 답한다")
    void 맡지_않는_경우() {
        provider(false).prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true);
        provider.prepare(input(Purpose.NEARBY_STORE, "5G 라이트로 바꿔줘"), true);

        verify(changes, never()).answer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("안내 중 오류가 나도 FAQ 답으로 넘기지 않고 화면에서 진행하도록 알린다")
    void 오류() {
        when(changes.answer(any(), any(), any(), any())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        GeneratedAnswer answer = provider.prepare(input(Purpose.GENERAL_FAQ, "5G 라이트로 바꿔줘"), true).answer();

        assertThat(answer.answer().content()).contains("지금은 안내하지 못했어요");
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    @DisplayName("추천 답변은 업무 처리기가 후속 버튼을 더하지 않고 그대로 낸다")
    void 추천_답변_그대로() {
        GeneratedAnswer recommended = answer("LTE 맥스를 추천해요.", List.of("요금제 변경은 어떻게 해요?", "LTE 맥스 변경하러 가기"));
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.answered(recommended));

        assertThat(provider.prepare(input(Purpose.GENERAL_FAQ, "나한테 맞는 요금제 추천해줘"), true).answer().answer().followUps())
                .containsExactly("요금제 변경은 어떻게 해요?", "LTE 맥스 변경하러 가기");
    }

    @Test
    @DisplayName("같은 업무 처리기가 둘이면 만들 때 실패한다")
    void 처리기_중복() {
        PlanChangeAnswerer other = mock(PlanChangeAnswerer.class);
        when(other.task()).thenReturn(PlanTask.CHANGE);

        assertThatThrownBy(() -> new PlanTaskAnswerProvider(faq, List.of(changes, other), sessionId -> Optional.empty(),
                ExecutionTrace.noop(), true)).isInstanceOf(IllegalArgumentException.class);
    }

    private PlanTaskAnswerProvider provider(boolean enabled) {
        return new PlanTaskAnswerProvider(faq, List.of(changes, cancels), sessionId -> Optional.of(USER_ID),
                ExecutionTrace.noop(), enabled);
    }

    private static AnswerInput input(Purpose purpose, String question) {
        return new AnswerInput(1L, 2L, 3L, purpose, question, question, Map.of());
    }

    private static GeneratedAnswer answer(String content, List<String> followUps) {
        return GeneratedAnswer.withoutSources(new ChatAnswer(ChatMessage.MessageType.ANSWER, content,
                ChatMessage.AnswerBasis.GROUNDED, followUps, null));
    }
}
