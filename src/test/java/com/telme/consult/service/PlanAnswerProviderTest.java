package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanAnswerProviderTest {

    static final String QUESTION = "요금제 종류 알려줘";
    static final GeneratedAnswer FAQ_ANSWER = GeneratedAnswer.withoutSources(
            new ChatAnswer(ChatMessage.MessageType.ANSWER, "FAQ 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), null));

    AnswerProvider faq = mock(AnswerProvider.class);
    PlanQuestionClassifier classifier = mock(PlanQuestionClassifier.class);
    PlanCatalogAnswerer catalog = mock(PlanCatalogAnswerer.class);
    MemberPlanAnswerer member = mock(MemberPlanAnswerer.class);
    AtomicInteger sessionLookups = new AtomicInteger();
    Optional<Long> sessionOwner = Optional.of(77L);
    ComparisonEvidenceResolver comparison = mock(ComparisonEvidenceResolver.class);
    ExecutionTrace trace = mock(ExecutionTrace.class);
    PlanAnswerProvider provider;

    @BeforeEach
    void setUp() {
        provider = provider(true);
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.none());
        when(faq.generate(any())).thenReturn(FAQ_ANSWER);
        when(faq.generate(any(), any())).thenReturn(FAQ_ANSWER);
        when(classifier.classify(anyLong(), any())).thenReturn(PlanQuestionType.NONE);
        when(catalog.answer(any())).thenReturn("요금제 안내예요.");
    }

    @Test
    void 요금제_목록_질문은_FAQ_검색_없이_근거_카드_없는_GROUNDED_답변을_확정한다() {
        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.PLAN_CATALOG);

        Prepared prepared = provider.prepare(input(QUESTION), true);

        assertThat(prepared.answer().answer().content()).isEqualTo("요금제 안내예요.");
        assertThat(prepared.answer().answer().messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(prepared.answer().answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(prepared.answer().answer().followUps()).isEmpty();
        assertThat(prepared.answer().sources()).isEmpty();
        assertThat(prepared.plan().needsClarification()).isFalse();
        verify(faq, never()).prepare(any(), anyBoolean());
        verify(trace).stage(eq(1L), eq("planAnswer"), any());
    }

    @Test
    void 한_번에_처리하는_경로도_같은_답변을_주고_FAQ_경로를_부르지_않는다() {
        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.PLAN_CATALOG);

        GeneratedAnswer answer = provider.generate(input(QUESTION));

        assertThat(answer.answer().content()).isEqualTo("요금제 안내예요.");
        verify(faq, never()).generate(any());
    }

    @Test
    void 요금제_목록이_아닌_질문은_기존_FAQ_경로에_맡긴다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.NONE, PlanQuestionType.MY_USAGE,
                PlanQuestionType.RECOMMENDATION)) {
            when(classifier.classify(1L, QUESTION)).thenReturn(type);

            assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
            assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
        }
        verifyNoInteractions(catalog, member);
    }

    @Test
    void 내_요금제와_위약금_질문은_채팅방_주인으로_답을_만들어_FAQ_검색_없이_확정한다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.MY_SUBSCRIPTION, PlanQuestionType.MY_PENALTY)) {
            when(classifier.classify(1L, QUESTION)).thenReturn(type);
            when(member.answer(type, 77L)).thenReturn(new MemberPlanAnswerer.MemberAnswer(type + " 답변", List.of("요금제 종류 알려줘")));

            Prepared prepared = provider.prepare(input(QUESTION), true);

            assertThat(prepared.answer().answer().content()).isEqualTo(type + " 답변");
            assertThat(prepared.answer().answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
            assertThat(prepared.answer().answer().followUps()).containsExactly("요금제 종류 알려줘");
            assertThat(prepared.answer().sources()).isEmpty();
        }
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    void 회원이_아닌_채팅방이면_회원_없이_답을_만든다() {
        sessionOwner = Optional.empty();
        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.MY_SUBSCRIPTION);
        when(member.answer(PlanQuestionType.MY_SUBSCRIPTION, null))
                .thenReturn(new MemberPlanAnswerer.MemberAnswer("로그인 안내", List.of("요금제 종류 알려줘")));

        assertThat(provider.generate(input(QUESTION)).answer().content()).isEqualTo("로그인 안내");
        verify(member).answer(PlanQuestionType.MY_SUBSCRIPTION, null);
    }

    @Test
    void 채팅방_주인은_내_요금제와_위약금_질문일_때만_조회한다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.NONE, PlanQuestionType.PLAN_CATALOG,
                PlanQuestionType.MY_USAGE, PlanQuestionType.RECOMMENDATION)) {
            when(classifier.classify(1L, QUESTION)).thenReturn(type);
            provider.generate(input(QUESTION));
        }
        assertThat(sessionLookups).hasValue(0);

        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.MY_PENALTY);
        provider.generate(input(QUESTION));
        assertThat(sessionLookups).hasValue(1);
    }

    @Test
    void 개인_답변을_만들지_못하면_기존_FAQ_경로에_맡긴다() {
        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.MY_PENALTY);

        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
    }

    @Test
    void 비교_질문은_분류하지_않고_기존_비교_답변_경로에_맡긴다() {
        when(comparison.applies(QUESTION)).thenReturn(true);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);

        verifyNoInteractions(classifier, catalog, member);
    }

    @Test
    void 꺼져_있으면_분류하지_않고_기존_FAQ_경로에_맡긴다() {
        provider = provider(false);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);

        verifyNoInteractions(classifier, catalog, member);
    }

    @Test
    void 요금제_답변을_만들_수_없으면_기존_FAQ_경로에_맡긴다() {
        when(classifier.classify(1L, QUESTION)).thenReturn(PlanQuestionType.PLAN_CATALOG);
        when(catalog.answer(QUESTION)).thenReturn(null);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
        verify(trace, never()).stage(any(), any(), any());
    }

    @Test
    void 이전_대화를_반영해_풀어_쓴_질문으로_분류한다() {
        AnswerInput followUp = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "그건 얼마예요", "그건 얼마예요", Map.of(),
                "5G 스탠다드 요금제 얼마예요", null, true, null, Map.of());
        when(classifier.classify(1L, "5G 스탠다드 요금제 얼마예요")).thenReturn(PlanQuestionType.PLAN_CATALOG);
        when(catalog.answer("5G 스탠다드 요금제 얼마예요")).thenReturn("5G 스탠다드");

        assertThat(provider.generate(followUp).answer().content()).isEqualTo("5G 스탠다드");
    }

    @Test
    void 준비된_답변과_되묻기_여부는_기존_경로의_판단을_그대로_따른다() {
        assertThat(provider.clarifies()).isFalse();
        when(faq.clarifies()).thenReturn(true);
        assertThat(provider.clarifies()).isTrue();

        Prepared prepared = Prepared.none();
        assertThat(provider.generate(input(QUESTION), prepared)).isEqualTo(FAQ_ANSWER);
        verify(faq).generate(any(), eq(prepared));
        verifyNoInteractions(classifier);
    }

    private PlanAnswerProvider provider(boolean enabled) {
        return new PlanAnswerProvider(faq, classifier, catalog, member, sessionId -> {
            sessionLookups.incrementAndGet();
            return sessionOwner;
        }, comparison, trace, enabled);
    }

    private static AnswerInput input(String question) {
        return new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, question, question, Map.of());
    }
}
