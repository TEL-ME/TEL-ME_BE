package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PlanAnswerProviderTest {

    static final String QUESTION = "요금제 종류 알려줘";
    static final GeneratedAnswer FAQ_ANSWER = GeneratedAnswer.withoutSources(
            new ChatAnswer(ChatMessage.MessageType.ANSWER, "FAQ 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), null));

    AnswerProvider faq = mock(AnswerProvider.class);
    PlanQuestionClassifier classifier = mock(PlanQuestionClassifier.class);
    PlanCatalogAnswerer catalog = mock(PlanCatalogAnswerer.class);
    MemberPlanAnswerer member = mock(MemberPlanAnswerer.class);
    MemberRecommendationAnswerer recommendation = mock(MemberRecommendationAnswerer.class);
    AtomicInteger sessionLookups = new AtomicInteger();
    Optional<Long> sessionOwner = Optional.of(77L);
    Map<String, String> previous = Map.of();
    ComparisonEvidenceResolver comparison = mock(ComparisonEvidenceResolver.class);
    ExecutionTrace trace = mock(ExecutionTrace.class);
    PlanAnswerProvider provider;

    @BeforeEach
    void setUp() {
        provider = provider(true);
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.none());
        when(faq.generate(any())).thenReturn(FAQ_ANSWER);
        when(faq.generate(any(), any())).thenReturn(FAQ_ANSWER);
        when(classifier.classifyQuestion(anyLong(), any())).thenReturn(PlanQuestion.of(PlanQuestionType.NONE));
        when(catalog.answer(any())).thenReturn("요금제 안내예요.");
    }

    @Test
    void 요금제_목록_질문은_FAQ_검색_없이_근거_카드_없는_GROUNDED_답변을_확정한다() {
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));

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
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));

        GeneratedAnswer answer = provider.generate(input(QUESTION));

        assertThat(answer.answer().content()).isEqualTo("요금제 안내예요.");
        verify(faq, never()).generate(any());
    }

    @Test
    void 요금제_목록이_아닌_질문은_기존_FAQ_경로에_맡긴다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.NONE)) {
            when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(type));

            assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
            assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
        }
        verifyNoInteractions(catalog, member, recommendation);
    }

    @Test
    void 내_요금제와_위약금과_사용량_질문은_채팅방_주인으로_답을_만들어_FAQ_검색_없이_확정한다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.MY_SUBSCRIPTION, PlanQuestionType.MY_PENALTY,
                PlanQuestionType.MY_USAGE)) {
            when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(type));
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
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_SUBSCRIPTION));
        when(member.answer(PlanQuestionType.MY_SUBSCRIPTION, null))
                .thenReturn(new MemberPlanAnswerer.MemberAnswer("로그인 안내", List.of("요금제 종류 알려줘")));

        assertThat(provider.generate(input(QUESTION)).answer().content()).isEqualTo("로그인 안내");
        verify(member).answer(PlanQuestionType.MY_SUBSCRIPTION, null);
    }

    @Test
    void 채팅방_주인은_내_정보와_추천_질문일_때만_조회한다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.NONE, PlanQuestionType.PLAN_CATALOG)) {
            when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(type));
            provider.generate(input(QUESTION));
        }
        assertThat(sessionLookups).hasValue(0);

        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_PENALTY));
        provider.generate(input(QUESTION));
        assertThat(sessionLookups).hasValue(1);

        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_USAGE));
        provider.generate(input(QUESTION));
        assertThat(sessionLookups).hasValue(2);

        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(any(), any(), any(), anyBoolean()))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("추천", List.of(), null));
        provider.generate(input(QUESTION));
        assertThat(sessionLookups).hasValue(3);
    }

    @Test
    void 개인_답변을_만들지_못하면_기존_FAQ_경로에_맡긴다() {
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_PENALTY));

        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
    }

    @Test
    void 요금제_추천_질문은_채팅방_주인의_추천_답변을_FAQ_검색_없이_확정한다() {
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, Map.of(), Map.of(), true)).thenReturn(
                new MemberRecommendationAnswerer.Outcome("추천 답변", List.of("요금제 변경은 어떻게 해요?"), null));

        Prepared prepared = provider.prepare(input(QUESTION), true);

        assertThat(prepared.answer().answer().content()).isEqualTo("추천 답변");
        assertThat(prepared.answer().answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(prepared.answer().answer().followUps()).containsExactly("요금제 변경은 어떻게 해요?");
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    void 나이대를_알아야_하면_되묻기_계획만_주고_답변은_확정하지_않는다() {
        MissingCondition ask = new MissingCondition("age_group", "나이대가 어떻게 되세요?",
                List.of("만 14~18세", "만 19~64세", "만 65세 이상"), null);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, Map.of(), Map.of(), true)).thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(ask)));

        Prepared prepared = provider.prepare(input(QUESTION), true);

        assertThat(prepared.answer()).isNull();
        assertThat(prepared.plan().needsClarification()).isTrue();
        assertThat(prepared.plan().first()).isEqualTo(ask);
        verify(faq, never()).prepare(any(), anyBoolean());
    }

    @Test
    void 되묻기를_이미_마친_턴에는_되묻지_않고_확정된_나이대로_추천한다() {
        AnswerInput answered = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION,
                Map.of("age_group", "만 65세 이상"));
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, Map.of("age_group", "만 65세 이상"), Map.of(), true)).thenReturn(
                new MemberRecommendationAnswerer.Outcome("시니어 추천", List.of(), null));

        Prepared prepared = provider.prepare(answered, false);

        assertThat(prepared.answer().answer().content()).isEqualTo("시니어 추천");
    }

    @Test
    void 되묻기를_마친_턴에도_통화_문자는_처음_묻는_경우만_되묻는다() {
        MissingCondition voice = new MissingCondition("monthly_voice_usage", "통화는 몇 분 쓰세요?", List.of(), null);
        MissingCondition sms = new MissingCondition("monthly_sms_usage", "문자는 몇 건 보내세요?", List.of(), null);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        Map<String, String> confirmed = Map.of("age_group", "만 19~64세", "monthly_data_usage", "1GB");
        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(voice, sms)));

        Prepared first = provider.prepare(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION,
                confirmed), false);

        assertThat(first.answer()).isNull();
        assertThat(first.plan().conditions()).containsExactly(voice, sms);
    }

    @Test
    void 되묻기를_마친_턴에는_통화_문자_중_아직_묻지_않은_항목만_되묻는다() {
        MissingCondition voice = new MissingCondition("monthly_voice_usage", "통화는 몇 분 쓰세요?", List.of(), null);
        MissingCondition sms = new MissingCondition("monthly_sms_usage", "문자는 몇 건 보내세요?", List.of(), null);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        Map<String, String> confirmed = Map.of("age_group", "만 19~64세", "monthly_data_usage", "2GB");
        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(voice, sms)));
        AnswerInput voiceAsked = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION, confirmed,
                QUESTION, null, true, null, Map.of("monthly_voice_usage", "통화는 몇 분 쓰세요?"));

        Prepared prepared = provider.prepare(voiceAsked, false);

        assertThat(prepared.answer()).isNull();
        assertThat(prepared.plan().conditions()).containsExactly(sms);
    }

    @Test
    void 나이대나_데이터가_섞인_되묻기는_되묻기를_마친_턴에_통화_문자와_함께_묻지_않는다() {
        MissingCondition age = new MissingCondition("age_group", "나이대가 어떻게 되세요?", List.of(), null);
        MissingCondition voice = new MissingCondition("monthly_voice_usage", "통화는 몇 분 쓰세요?", List.of(), null);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        Map<String, String> confirmed = Map.of("monthly_data_usage", "2GB");
        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(age, voice)));
        when(recommendation.recommend(77L, confirmed, Map.of(), false))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("안내만", List.of(), null));

        Prepared prepared = provider.prepare(
                new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION, confirmed), false);

        assertThat(prepared.answer().answer().content()).isEqualTo("안내만");
    }

    @Test
    void 질문에_직접_말한_나이대와_사용량은_앞서_저장된_답보다_우선해_되묻지_않고_추천한다() {
        String question = "저 17살인데 한 달에 50GB 쓰는데 요금제 추천해줘";
        Map<String, String> stated = Map.of("age_group", "만 14~18세", "monthly_data_usage", "50GB");
        previous = Map.of("age_group", "만 19~64세", "monthly_data_usage", "3GB");
        provider = provider(true);
        when(classifier.classifyQuestion(1L, question)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, stated, previous, true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("청소년 추천", List.of(), null));

        Prepared prepared = provider.prepare(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, question, question, Map.of()), true);

        assertThat(prepared.answer().answer().content()).isEqualTo("청소년 추천");
    }

    @Test
    void 되묻기로_확정한_값이_있으면_질문에_말한_값보다_우선한다() {
        String question = "한 달에 50GB 쓰는데 요금제 추천해줘";
        Map<String, String> confirmed = Map.of("monthly_data_usage", "20GB");
        when(classifier.classifyQuestion(1L, question)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("확정값 추천", List.of(), null));

        Prepared prepared = provider.prepare(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, question, question, confirmed), false);

        assertThat(prepared.answer().answer().content()).isEqualTo("확정값 추천");
    }

    @Test
    void 이미_물은_통화_문자나_나이대_데이터는_되묻기를_마친_턴에_다시_묻지_않고_추천한다() {
        MissingCondition voice = new MissingCondition("monthly_voice_usage", "통화는 몇 분 쓰세요?", List.of(), null);
        MissingCondition data = new MissingCondition("monthly_data_usage", "데이터는 얼마나 쓰세요?", List.of(), null);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        Map<String, String> confirmed = Map.of("age_group", "만 19~64세", "monthly_data_usage", "많이 써요");
        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(voice)));
        when(recommendation.recommend(77L, confirmed, Map.of(), false))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("안내만", List.of(), null));
        AnswerInput voiceAsked = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION, confirmed,
                QUESTION, null, true, null, Map.of("monthly_voice_usage", "통화는 몇 분 쓰세요?"));

        assertThat(provider.prepare(voiceAsked, false).answer().answer().content()).isEqualTo("안내만");

        when(recommendation.recommend(77L, confirmed, Map.of(), true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(data)));
        AnswerInput noneAsked = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, QUESTION, QUESTION, confirmed);

        assertThat(provider.prepare(noneAsked, false).answer().answer().content()).isEqualTo("안내만");
    }

    @Test
    void 같은_채팅방에서_앞서_한_답을_추천_답변기에_함께_넘긴다() {
        previous = Map.of("age_group", "만 65세 이상", "monthly_data_usage", "40GB");
        provider = provider(true);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, Map.of(), previous, true))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("이어받은 추천", List.of(), null));

        assertThat(provider.prepare(input(QUESTION), true).answer().answer().content()).isEqualTo("이어받은 추천");
    }

    @Test
    void 한_번에_처리하는_경로에서는_되묻지_않는다() {
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(77L, Map.of(), Map.of(), false)).thenReturn(
                new MemberRecommendationAnswerer.Outcome("일반 요금제 추천", List.of(), null));

        assertThat(provider.generate(input(QUESTION)).answer().content()).isEqualTo("일반 요금제 추천");
        verify(recommendation).recommend(77L, Map.of(), Map.of(), false);
    }

    @Test
    void 비교_질문은_분류하지_않고_기존_비교_답변_경로에_맡긴다() {
        when(comparison.applies(QUESTION)).thenReturn(true);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);

        verifyNoInteractions(classifier, catalog, member, recommendation);
    }

    @Test
    void 꺼져_있으면_분류하지_않고_기존_FAQ_경로에_맡긴다() {
        provider = provider(false);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);

        verifyNoInteractions(classifier, catalog, member, recommendation);
    }

    @Test
    void 요금제_답변을_만들_수_없으면_기존_FAQ_경로에_맡긴다() {
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));
        when(catalog.answer(QUESTION)).thenReturn(null);

        assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
        verify(trace, never()).stage(any(), any(), any());
    }

    @Test
    void 이전_대화를_반영해_풀어_쓴_질문으로_분류한다() {
        AnswerInput followUp = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "그건 얼마예요", "그건 얼마예요", Map.of(),
                "5G 스탠다드 요금제 얼마예요", null, true, null, Map.of());
        when(classifier.classifyQuestion(1L, "5G 스탠다드 요금제 얼마예요")).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));
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
        return new PlanAnswerProvider(faq, classifier, catalog, member, recommendation, sessionId -> {
            sessionLookups.incrementAndGet();
            return sessionOwner;
        }, sessionId -> previous, comparison, trace, enabled);
    }

    @Test
    void 답변을_만들다_예외가_나면_채팅을_실패시키지_않고_FAQ_경로에_맡긴다() {
        for (PlanQuestionType type : List.of(PlanQuestionType.PLAN_CATALOG, PlanQuestionType.MY_SUBSCRIPTION,
                PlanQuestionType.MY_USAGE, PlanQuestionType.RECOMMENDATION)) {
            when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(type));
            doThrow(new IllegalStateException("catalog")).when(catalog).answer(any());
            doThrow(new IllegalStateException("member")).when(member).answer(any(), any());
            doThrow(new IllegalStateException("recommend")).when(recommendation).recommend(any(), any(), any(), anyBoolean());

            assertThat(provider.prepare(input(QUESTION), true)).isEqualTo(Prepared.none());
            assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
        }
    }

    @Test
    void 채팅방_주인을_찾다_예외가_나도_FAQ_경로에_맡긴다() {
        sessionLookups.set(0);
        provider = new PlanAnswerProvider(faq, classifier, catalog, member, recommendation,
                sessionId -> {
                    throw new IllegalStateException("db");
                }, sessionId -> Map.of(), comparison, trace, true);
        when(classifier.classifyQuestion(1L, QUESTION)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_SUBSCRIPTION));

        assertThat(provider.generate(input(QUESTION))).isEqualTo(FAQ_ANSWER);
    }

    @Test
    void 후속_질문_버튼_문구는_추천이나_내_정보_답변으로_가져가지_않는다() throws IOException {
        String button = planButton();
        for (PlanQuestionType type : List.of(PlanQuestionType.RECOMMENDATION, PlanQuestionType.MY_USAGE,
                PlanQuestionType.MY_SUBSCRIPTION)) {
            when(classifier.classifyQuestion(anyLong(), any())).thenReturn(PlanQuestion.of(type));

            assertThat(provider.prepare(input(button), true)).isEqualTo(Prepared.none());
            assertThat(provider.generate(input(button))).isEqualTo(FAQ_ANSWER);
            assertThat(provider.generate(input(button.replace(" ", "  ") + " "))).isEqualTo(FAQ_ANSWER);
        }
        verifyNoInteractions(recommendation, member);
    }

    @Test
    void 후속_질문_버튼_문구도_요금제_목록_질문이면_요금제_표로_답한다() throws IOException {
        String button = planButton();
        when(classifier.classifyQuestion(1L, button)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));

        assertThat(provider.generate(input(button)).answer().content()).isEqualTo("요금제 안내예요.");
    }

    @Test
    void 버튼_문구가_아닌_추천_질문은_그대로_처리한다() {
        when(classifier.classifyQuestion(1L, "나에게 맞는 요금제 찾기")).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        when(recommendation.recommend(any(), any(), any(), anyBoolean()))
                .thenReturn(new MemberRecommendationAnswerer.Outcome("추천", List.of(), null));

        assertThat(provider.generate(input("나에게 맞는 요금제 찾기")).answer().content()).isEqualTo("추천");
    }

    @Test
    void 문맥으로_풀어쓴_문장이_달라도_사용자가_쓴_원문으로_먼저_분류한다() {
        String original = "나 요금제 뭐야?";
        String rewritten = "만 19~64세 대상 요금제 종류";
        when(classifier.classifyQuestion(1L, original)).thenReturn(PlanQuestion.of(PlanQuestionType.MY_SUBSCRIPTION));
        when(classifier.classifyQuestion(1L, rewritten)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));
        when(member.answer(PlanQuestionType.MY_SUBSCRIPTION, 77L))
                .thenReturn(new MemberPlanAnswerer.MemberAnswer("내 요금제 답변", List.of()));
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, original, rewritten, Map.of(),
                rewritten, null);

        assertThat(provider.prepare(input, true).answer().answer().content()).isEqualTo("내 요금제 답변");
        verify(classifier, never()).classifyQuestion(1L, rewritten);
        verifyNoInteractions(catalog);
    }

    // 원문에 요금제 이름이 없어 모델이 요금제를 고르지 못하면 풀어쓴 문장으로 다시 고른다(리뷰: 이어받기 질문)
    @Test
    void 원문에서_고른_요금제가_없으면_풀어쓴_문장으로_다시_고른다() {
        String original = "아까 그 요금제 통화는 무제한이에요?";
        String rewritten = "5G 스탠다드 요금제 통화는 무제한이에요?";
        PlanQuestion.Mention standard = new PlanQuestion.Mention(List.of("FIVE_G_STANDARD"), false);
        when(classifier.classifyQuestion(1L, original))
                .thenReturn(new PlanQuestion(PlanQuestionType.PLAN_CATALOG, new PlanQuestion.Mention(List.of(), false)));
        when(classifier.classifyQuestion(1L, rewritten)).thenReturn(new PlanQuestion(PlanQuestionType.PLAN_CATALOG, standard));
        when(catalog.answer(rewritten, standard)).thenReturn("5G 스탠다드 안내");
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, original, rewritten, Map.of(),
                rewritten, null);

        assertThat(provider.prepare(input, true).answer().answer().content()).isEqualTo("5G 스탠다드 안내");
    }

    // 가리키는 요금제가 원래 없는 질문("거기서 제일 싼 건")은 다시 고른 결과가 목록 질문이 아니면 원문 분류를 쓴다
    @Test
    void 다시_고른_결과가_요금제_목록이_아니면_원문_분류를_쓴다() {
        String original = "거기서 제일 싼 건 얼마예요?";
        String rewritten = "5G 요금제 중에서 제일 싼 건 얼마예요?";
        PlanQuestion.Mention none = new PlanQuestion.Mention(List.of(), false);
        when(classifier.classifyQuestion(1L, original)).thenReturn(new PlanQuestion(PlanQuestionType.PLAN_CATALOG, none));
        when(classifier.classifyQuestion(1L, rewritten)).thenReturn(PlanQuestion.of(PlanQuestionType.NONE));
        when(catalog.answer(rewritten, none)).thenReturn("5G 라이트 안내");
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, original, rewritten, Map.of(),
                rewritten, null);

        assertThat(provider.prepare(input, true).answer().answer().content()).isEqualTo("5G 라이트 안내");
    }

    @Test
    void 원문이_해당_없음이면_풀어쓴_문장으로_다시_분류해_후속_질문을_처리한다() {
        String original = "그건 얼마예요";
        String rewritten = "5G 스탠다드 얼마예요";
        when(classifier.classifyQuestion(1L, original)).thenReturn(PlanQuestion.of(PlanQuestionType.NONE));
        when(classifier.classifyQuestion(1L, rewritten)).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));
        when(catalog.answer(rewritten)).thenReturn("5G 스탠다드 안내");
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, original, rewritten, Map.of(),
                rewritten, null);

        assertThat(provider.prepare(input, true).answer().answer().content()).isEqualTo("5G 스탠다드 안내");
    }

    private static AnswerInput input(String question) {
        return new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, question, question, Map.of());
    }

    // 대표 질문은 재측정 때 바뀌므로 실제 버튼 데이터에서 요금제 대표 질문을 읽는다
    private static String planButton() throws IOException {
        try (InputStream in = new ClassPathResource("suggested-question/policy-links.json").getInputStream()) {
            return new ObjectMapper().readTree(in).path("representativeQuestions").path("PLAN-01")
                    .path("question").asText();
        }
    }
}
