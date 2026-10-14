package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;

/** 요금제 목록과 내 요금제·위약금 질문은 FAQ 검색 없이 데이터로 답하고, 그 밖의 질문은 FAQ 답변 경로에 맡긴다. */
public final class PlanAnswerProvider implements AnswerProvider {
    private final AnswerProvider faqAnswers;
    private final PlanQuestionClassifier classifier;
    private final PlanCatalogAnswerer catalog;
    private final MemberPlanAnswerer member;
    private final MemberRecommendationAnswerer recommendation;
    private final LongFunction<Optional<Long>> memberOfSession;
    private final ComparisonEvidenceResolver comparisonEvidence;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanAnswerProvider(AnswerProvider faqAnswers, PlanQuestionClassifier classifier,
            PlanCatalogAnswerer catalog, MemberPlanAnswerer member, MemberRecommendationAnswerer recommendation,
            LongFunction<Optional<Long>> memberOfSession,
            ComparisonEvidenceResolver comparisonEvidence, ExecutionTrace trace, boolean enabled) {
        this.faqAnswers = Objects.requireNonNull(faqAnswers);
        this.classifier = Objects.requireNonNull(classifier);
        this.catalog = Objects.requireNonNull(catalog);
        this.member = Objects.requireNonNull(member);
        this.recommendation = Objects.requireNonNull(recommendation);
        this.memberOfSession = Objects.requireNonNull(memberOfSession);
        this.comparisonEvidence = Objects.requireNonNull(comparisonEvidence);
        this.trace = Objects.requireNonNull(trace);
        this.enabled = enabled;
    }

    @Override
    public boolean clarifies() {
        return faqAnswers.clarifies();
    }

    @Override
    public Prepared prepare(AnswerInput input, boolean plansClarification) {
        Outcome outcome = outcome(input, plansClarification);
        if (outcome.asks() != null) {
            return new Prepared(List.of(), new ClarificationPlan(outcome.asks()));
        }
        return outcome.answer() != null ? Prepared.answered(outcome.answer()) : faqAnswers.prepare(input, plansClarification);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        return faqAnswers.generate(input, prepared);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        Outcome outcome = outcome(input, false);
        return outcome.answer() != null ? outcome.answer() : faqAnswers.generate(input);
    }

    // answer와 ask 중 하나만 있거나 둘 다 없다(기존 FAQ 경로에 맡김)
    private record Outcome(GeneratedAnswer answer, List<MissingCondition> asks) {
        static final Outcome NONE = new Outcome(null, null);
    }

    private Outcome outcome(AnswerInput input, boolean mayAsk) {
        // 비교 질문은 기존 비교 답변 경로가 양쪽 FAQ 근거를 확인해 답한다
        if (!enabled || comparisonEvidence.applies(input.originalUserQuery())) {
            return Outcome.NONE;
        }
        String question = input.resolvedUserQuery() == null || input.resolvedUserQuery().isBlank()
                ? input.originalUserQuery() : input.resolvedUserQuery();
        PlanQuestionType type = classifier.classify(input.executionId(), question);
        String content;
        List<String> followUps = List.of();
        if (type == PlanQuestionType.PLAN_CATALOG) {
            content = catalog.answer(question);
        } else if (type == PlanQuestionType.MY_SUBSCRIPTION || type == PlanQuestionType.MY_PENALTY) {
            // 개인 정보는 이 채팅방 주인(회원)의 것만 읽는다. 회원이 아니면 로그인 안내를 준다
            MemberPlanAnswerer.MemberAnswer memberAnswer = member.answer(type, memberOf(input));
            content = memberAnswer == null ? null : memberAnswer.content();
            followUps = memberAnswer == null ? List.of() : memberAnswer.followUps();
        } else if (type == PlanQuestionType.RECOMMENDATION) {
            MemberRecommendationAnswerer.Outcome result = recommendation.recommend(memberOf(input),
                    input.confirmedConditions(), mayAsk);
            if (result.asks() != null) {
                trace.stage(input.executionId(), "planAnswer", Map.of("type", type.name(), "clarify",
                        result.asks().stream().map(MissingCondition::key).toList()));
                return new Outcome(null, result.asks());
            }
            content = result.content();
            followUps = result.followUps();
        } else {
            return Outcome.NONE;
        }
        if (content == null) {
            return Outcome.NONE;
        }
        trace.stage(input.executionId(), "planAnswer", Map.of("type", type.name()));
        return new Outcome(GeneratedAnswer.withoutSources(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, content, ChatMessage.AnswerBasis.GROUNDED, followUps, null)), null);
    }

    private Long memberOf(AnswerInput input) {
        return memberOfSession.apply(input.sessionId()).orElse(null);
    }
}
