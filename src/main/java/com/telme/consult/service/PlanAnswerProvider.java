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
import lombok.extern.slf4j.Slf4j;

/** 요금제 목록과 내 요금제·위약금 질문은 FAQ 검색 없이 데이터로 답하고, 그 밖의 질문은 FAQ 답변 경로에 맡긴다. */
@Slf4j
public final class PlanAnswerProvider implements AnswerProvider {
    private final AnswerProvider faqAnswers;
    private final PlanQuestionClassifier classifier;
    private final PlanCatalogAnswerer catalog;
    private final MemberPlanAnswerer member;
    private final MemberRecommendationAnswerer recommendation;
    private final LongFunction<Optional<Long>> memberOfSession;
    private final LongFunction<Map<String, String>> previousAnswers;
    private final ComparisonEvidenceResolver comparisonEvidence;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanAnswerProvider(AnswerProvider faqAnswers, PlanQuestionClassifier classifier,
            PlanCatalogAnswerer catalog, MemberPlanAnswerer member, MemberRecommendationAnswerer recommendation,
            LongFunction<Optional<Long>> memberOfSession, LongFunction<Map<String, String>> previousAnswers,
            ComparisonEvidenceResolver comparisonEvidence, ExecutionTrace trace, boolean enabled) {
        this.faqAnswers = Objects.requireNonNull(faqAnswers);
        this.classifier = Objects.requireNonNull(classifier);
        this.catalog = Objects.requireNonNull(catalog);
        this.member = Objects.requireNonNull(member);
        this.recommendation = Objects.requireNonNull(recommendation);
        this.memberOfSession = Objects.requireNonNull(memberOfSession);
        this.previousAnswers = Objects.requireNonNull(previousAnswers);
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
        Outcome outcome = outcome(input, plansClarification, true);
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
        Outcome outcome = outcome(input, false, false);
        return outcome.answer() != null ? outcome.answer() : faqAnswers.generate(input);
    }

    // answer와 ask 중 하나만 있거나 둘 다 없다(기존 FAQ 경로에 맡김)
    private record Outcome(GeneratedAnswer answer, List<MissingCondition> asks) {
        static final Outcome NONE = new Outcome(null, null);
    }

    // 요금제 데이터로 답을 만들다 실패하면 채팅을 실패시키지 않고 기존 FAQ 경로에 맡긴다
    private Outcome outcome(AnswerInput input, boolean mayAsk, boolean mayAskUsageFollowUp) {
        try {
            return planOutcome(input, mayAsk, mayAskUsageFollowUp);
        } catch (RuntimeException exception) {
            log.warn("[요금제 답변] 답변을 만들지 못해 FAQ 경로로 넘깁니다. executionId={}", input.executionId(), exception);
            return Outcome.NONE;
        }
    }

    // 분류에 넣은 문장과 결과를 남겨, 같은 입력으로 분류 모델을 다시 비교할 수 있게 한다
    private PlanQuestionType classify(Long executionId, String question) {
        PlanQuestionType type = classifier.classify(executionId, question);
        trace.append(executionId, "planQuestionClassifications", Map.of("question", question, "type", type.name()));
        return type;
    }

    private Outcome planOutcome(AnswerInput input, boolean mayAsk, boolean mayAskUsageFollowUp) {
        // 비교 질문은 기존 비교 답변 경로가 양쪽 FAQ 근거를 확인해 답한다
        if (!enabled || comparisonEvidence.applies(input.originalUserQuery())) {
            return Outcome.NONE;
        }
        String question = input.resolvedUserQuery() == null || input.resolvedUserQuery().isBlank()
                ? input.originalUserQuery() : input.resolvedUserQuery();
        // 문맥으로 풀어쓴 문장은 "나", "내" 같은 본인 표현이 사라질 수 있어 사용자가 쓴 원문으로 먼저 분류한다
        PlanQuestionType type = classify(input.executionId(), input.originalUserQuery());
        if (type == PlanQuestionType.NONE && !question.equals(input.originalUserQuery())) {
            type = classify(input.executionId(), question);
        }
        // 후속 질문 버튼의 문구에는 이전 답을 되풀이하는 추천·내 정보 답변을 하지 않는다. 요금제 표 안내는 FAQ 대신 쓴다
        if (type != PlanQuestionType.PLAN_CATALOG && FaqButtonQuestions.contains(input.originalUserQuery())) {
            return Outcome.NONE;
        }
        String content;
        List<String> followUps = List.of();
        if (type == PlanQuestionType.PLAN_CATALOG) {
            content = catalog.answer(question);
        } else if (type == PlanQuestionType.MY_SUBSCRIPTION || type == PlanQuestionType.MY_PENALTY
                || type == PlanQuestionType.MY_USAGE) {
            // 개인 정보는 이 채팅방 주인(회원)의 것만 읽는다. 회원이 아니면 로그인 안내를 준다
            MemberPlanAnswerer.MemberAnswer memberAnswer = member.answer(type, memberOf(input));
            content = memberAnswer == null ? null : memberAnswer.content();
            followUps = memberAnswer == null ? List.of() : memberAnswer.followUps();
        } else if (type == PlanQuestionType.RECOMMENDATION) {
            Long member = memberOf(input);
            Map<String, String> previous = previousAnswers.apply(input.sessionId());
            MemberRecommendationAnswerer.Outcome result =
                    recommendation.recommend(member, input.confirmedConditions(), previous, mayAsk || mayAskUsageFollowUp);
            if (result.asks() != null && !mayAsk && !(mayAskUsageFollowUp && isFirstUsageFollowUp(result.asks(), input))) {
                result = recommendation.recommend(member, input.confirmedConditions(), previous, false);
            }
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

    // 앞선 되묻기를 마친 뒤에는 새 질문을 붙이지 않아 같은 조건을 끝없이 묻지 않는다.
    // 다만 통화·문자는 데이터 사용량을 받은 뒤에야 알 수 있으므로 이 상담에서 처음 묻는 경우만 허용한다
    private static boolean isFirstUsageFollowUp(List<MissingCondition> asks, AnswerInput input) {
        return asks.stream().allMatch(ask -> MemberRecommendationAnswerer.USAGE_FOLLOW_UP_KEYS.contains(ask.key())
                && !input.askedQuestions().containsKey(ask.key()));
    }

    private Long memberOf(AnswerInput input) {
        return memberOfSession.apply(input.sessionId()).orElse(null);
    }
}
