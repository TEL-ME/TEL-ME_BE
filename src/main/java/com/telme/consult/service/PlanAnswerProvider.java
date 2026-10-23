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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
    private PlanQuestion classify(Long executionId, String question) {
        PlanQuestion classified = classifier.classifyQuestion(executionId, question);
        Map<String, Object> record = new HashMap<>(Map.of("question", question, "type", classified.type().name()));
        if (classified.mention() != null) {
            record.put("plans", classified.mention().planCodes());
            record.put("unknownPlan", classified.mention().unknownPlan());
            if (classified.mention().conditions() != null) {
                record.put("conditions", classified.mention().conditions());
            }
        }
        trace.append(executionId, "planQuestionClassifications", record);
        return classified;
    }

    private Outcome planOutcome(AnswerInput input, boolean mayAsk, boolean mayAskUsageFollowUp) {
        // 비교 질문은 기존 비교 답변 경로가 양쪽 FAQ 근거를 확인해 답한다
        if (!enabled || comparisonEvidence.applies(input.originalUserQuery())) {
            return Outcome.NONE;
        }
        String question = input.resolvedUserQuery() == null || input.resolvedUserQuery().isBlank()
                ? input.originalUserQuery() : input.resolvedUserQuery();
        // 문맥으로 풀어쓴 문장은 "나", "내" 같은 본인 표현이 사라질 수 있어 사용자가 쓴 원문으로 먼저 분류한다
        PlanQuestion classified = classify(input.executionId(), input.originalUserQuery());
        // 원문에 요금제 이름이 없어("그 요금제 통화는 무제한이에요?") 고른 요금제가 비면 문맥으로 풀어쓴 문장으로 다시 고른다
        if (!question.equals(input.originalUserQuery()) && (classified.type() == PlanQuestionType.NONE
                || classified.type() == PlanQuestionType.PLAN_CATALOG && pointsToNoPlan(classified))) {
            PlanQuestion resolved = classify(input.executionId(), question);
            if (classified.type() == PlanQuestionType.NONE || resolved.type() == PlanQuestionType.PLAN_CATALOG) {
                classified = resolved;
            }
        }
        PlanQuestionType type = classified.type();
        // 후속 질문 버튼의 문구에는 이전 답을 되풀이하는 추천·내 정보 답변을 하지 않는다. 요금제 표 안내는 FAQ 대신 쓴다
        if (type != PlanQuestionType.PLAN_CATALOG && FaqButtonQuestions.contains(input.originalUserQuery())) {
            return Outcome.NONE;
        }
        String content;
        List<String> followUps = List.of();
        if (type == PlanQuestionType.PLAN_CATALOG) {
            content = classified.mention() == null ? catalog.answer(question) : catalog.answer(question, classified.mention());
        } else if (type == PlanQuestionType.MY_SUBSCRIPTION || type == PlanQuestionType.MY_PENALTY
                || type == PlanQuestionType.MY_USAGE) {
            // 개인 정보는 이 채팅방 주인(회원)의 것만 읽는다. 회원이 아니면 로그인 안내를 준다
            MemberPlanAnswerer.MemberAnswer memberAnswer = member.answer(type, memberOf(input));
            content = memberAnswer == null ? null : memberAnswer.content();
            followUps = memberAnswer == null ? List.of() : memberAnswer.followUps();
        } else if (type == PlanQuestionType.RECOMMENDATION) {
            Long member = memberOf(input);
            PlanQuestionClassifier.Subscriber subscriber = subscriber(input, member);
            boolean forOther = subscriber != null && subscriber.other();
            // 다른 사람이 쓸 요금제면 이 채팅방에서 앞서 한 답(고객 본인의 나이·사용량일 수 있음)을 쓰지 않는다
            Map<String, String> previous = forOther ? Map.of() : previousAnswers.apply(input.sessionId());
            Map<String, String> stated = withStated(input.originalUserQuery(), input.confirmedConditions());
            Map<String, String> confirmed = withSubscriber(subscriber, stated);
            boolean ageFromModel = !stated.containsKey(MemberRecommendationAnswerer.AGE_KEY)
                    && confirmed.containsKey(MemberRecommendationAnswerer.AGE_KEY);
            Function<Boolean, MemberRecommendationAnswerer.Outcome> recommend = ask -> forOther
                    ? recommendation.recommendForOther(confirmed, ask, ageFromModel)
                    : ageFromModel ? recommendation.recommend(member, confirmed, previous, ask, true)
                    : recommendation.recommend(member, confirmed, previous, ask);
            MemberRecommendationAnswerer.Outcome result = recommend.apply(mayAsk || mayAskUsageFollowUp);
            if (result.asks() != null && !mayAsk) {
                List<MissingCondition> firstAsks = mayAskUsageFollowUp ? firstUsageFollowUps(result.asks(), input) : List.of();
                result = firstAsks.isEmpty()
                        ? recommend.apply(false)
                        : new MemberRecommendationAnswerer.Outcome(null, List.of(), firstAsks);
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

    private static boolean pointsToNoPlan(PlanQuestion classified) {
        return classified.mention() != null && classified.mention().planCodes().isEmpty() && !classified.mention().unknownPlan();
    }

    // 질문에 직접 말한 나이대·사용량은 앞서 저장된 답보다 우선하는 확정된 답으로 본다(이미 말했으니 되묻지 않는다).
    // 되묻기로 확정한 값이 있으면 그 값이 우선한다
    private static Map<String, String> withStated(String question, Map<String, String> confirmed) {
        Map<String, String> stated = StatedRecommendationAnswers.of(question);
        if (stated.isEmpty()) {
            return confirmed;
        }
        Map<String, String> merged = new HashMap<>(stated);
        merged.putAll(confirmed);
        return merged;
    }

    // 요금제를 쓸 사람과 그 사람의 나이대·사용량을 모델에 묻는다. 규칙처럼 저장하지 않고 되묻기 다음 턴에도 처음 질문으로 다시 읽는다.
    // 규칙이 질문에서 나이를 읽었으면 나이대는 묻지 않는다. 이때 비회원은 쓸 사람도 묻지 않는다: 규칙의 가족 낱말 목록에 없는
    // 호칭("할머니 만 75세")이어도 읽은 나이와 사용량이 그 사람의 것이라 추천이 같다. 회원은 생년월일·가입 정보가 대신 쓰일 수 있어 묻는다(#212 리뷰)
    private PlanQuestionClassifier.Subscriber subscriber(AnswerInput input, Long member) {
        boolean ruleAge = StatedRecommendationAnswers.of(input.originalUserQuery()).containsKey(MemberRecommendationAnswerer.AGE_KEY);
        if (ruleAge && member == null) {
            return null;
        }
        PlanQuestionClassifier.Subscriber subscriber = classifier.subscriber(input.executionId(), input.originalUserQuery(), !ruleAge);
        Map<String, Object> record = new HashMap<>(Map.of("question", input.originalUserQuery()));
        if (subscriber != null) {
            record.put("user", subscriber.other() ? "OTHER" : "SELF");
            // 규칙이 나이를 읽어 나이대를 묻지 않았으면 남기지 않는다(모델이 모른다고 한 것과 구분)
            if (!ruleAge) {
                record.put("targetGroup", subscriber.group() == null ? "UNKNOWN" : subscriber.group().name());
            }
            record.put("monthlyDataGb", subscriber.monthlyDataGb() == null ? 0 : subscriber.monthlyDataGb());
        }
        trace.append(input.executionId(), "planSubscribers", record);
        return subscriber;
    }

    // 모델이 읽은 나이대·사용량은 규칙과 되묻기로 받은 값이 없을 때만 쓴다
    private static Map<String, String> withSubscriber(PlanQuestionClassifier.Subscriber subscriber, Map<String, String> confirmed) {
        if (subscriber == null) {
            return confirmed;
        }
        Map<String, String> merged = new HashMap<>(confirmed);
        if (subscriber.group() != null) {
            merged.putIfAbsent(MemberRecommendationAnswerer.AGE_KEY, MemberRecommendationAnswerer.ageOption(subscriber.group()));
        }
        if (subscriber.monthlyDataGb() != null) {
            merged.putIfAbsent(MemberRecommendationAnswerer.DATA_KEY, DataUsageReplyParser.canonicalGb(subscriber.monthlyDataGb()));
        }
        return merged;
    }

    // 앞선 되묻기를 마친 뒤에는 새 질문을 붙이지 않아 같은 조건을 끝없이 묻지 않는다.
    // 다만 통화·문자는 데이터 사용량을 받은 뒤에야 알 수 있으므로 이 상담에서 아직 묻지 않은 항목만 허용한다.
    // 나이대·데이터가 섞여 있으면 새 질문이 아니라 빠진 답이라 허용하지 않는다
    private static List<MissingCondition> firstUsageFollowUps(List<MissingCondition> asks, AnswerInput input) {
        if (!asks.stream().allMatch(ask -> MemberRecommendationAnswerer.USAGE_FOLLOW_UP_KEYS.contains(ask.key()))) {
            return List.of();
        }
        return asks.stream().filter(ask -> !input.askedQuestions().containsKey(ask.key())).toList();
    }

    private Long memberOf(AnswerInput input) {
        return memberOfSession.apply(input.sessionId()).orElse(null);
    }
}
