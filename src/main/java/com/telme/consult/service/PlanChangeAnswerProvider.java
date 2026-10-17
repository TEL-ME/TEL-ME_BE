package com.telme.consult.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import lombok.extern.slf4j.Slf4j;

// 기존 상담 답변 경로를 바꾸지 않고 바깥에서 감싼다. 요금제 변경 요청만 먼저 처리하고 나머지는 그대로 넘긴다
@Slf4j
public final class PlanChangeAnswerProvider implements AnswerProvider {

    // 요금제 추천 답변(MemberRecommendationAnswerer)이 더 싸거나 큰 요금제를 권할 때 다는 버튼. 이 버튼 옆에 변경 버튼을 더한다
    static final String RECOMMENDATION_CHANGE_QUESTION = "요금제 변경은 어떻게 해요?";
    private static final String FAILED = "지금은 요금제를 바꾸지 못했어요. 잠시 후 다시 시도하거나 요금제 화면에서 바꿔 주세요.";

    private final AnswerProvider delegate;
    private final PlanChangeAnswerer changes;
    private final LongFunction<Optional<Long>> memberOfSession;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanChangeAnswerProvider(AnswerProvider delegate, PlanChangeAnswerer changes,
            LongFunction<Optional<Long>> memberOfSession, ExecutionTrace trace, boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate);
        this.changes = Objects.requireNonNull(changes);
        this.memberOfSession = Objects.requireNonNull(memberOfSession);
        this.trace = Objects.requireNonNull(trace);
        this.enabled = enabled;
    }

    @Override
    public boolean clarifies() {
        return delegate.clarifies();
    }

    @Override
    public Prepared prepare(AnswerInput input, boolean plansClarification) {
        if (!handles(input)) {
            Prepared prepared = delegate.prepare(input, plansClarification);
            return prepared == null || prepared.answer() == null ? prepared
                    : new Prepared(prepared.searchResults(), prepared.plan(), withChangeButton(input, prepared.answer()));
        }
        PlanChangeAnswerer.Outcome outcome = change(input, plansClarification);
        if (outcome.asks() != null) {
            trace.stage(input.executionId(), "planChange",
                    Map.of("clarify", outcome.asks().stream().map(MissingCondition::key).toList()));
            return new Prepared(List.of(), new ClarificationPlan(outcome.asks()));
        }
        return Prepared.answered(answered(input, outcome));
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        return withChangeButton(input, delegate.generate(input, prepared));
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        if (!handles(input)) {
            return withChangeButton(input, delegate.generate(input));
        }
        // 이 경로에서는 확인 질문을 할 수 없어 바꾸지 않고 안내만 한다
        return answered(input, change(input, false));
    }

    // 사용자가 쓴 원문과 문맥으로 풀어쓴 문장 중 하나라도 분명한 변경 요청이면 맡는다
    private boolean handles(AnswerInput input) {
        if (!enabled || input == null || input.purpose() != Purpose.GENERAL_FAQ) {
            return false;
        }
        return PlanChangeRequestRules.requestsPlanChange(input.originalUserQuery())
                || PlanChangeRequestRules.requestsPlanChange(input.resolvedUserQuery());
    }

    private PlanChangeAnswerer.Outcome change(AnswerInput input, boolean mayAsk) {
        try {
            return changes.answer(memberOf(input), input.originalUserQuery(), input.resolvedUserQuery(),
                    input.confirmedConditions(), mayAsk);
        } catch (RuntimeException exception) {
            // 기존 FAQ 경로로 넘기면 변경 요청에 엉뚱한 답이 나가서 바꾸지 못했다고만 알린다
            log.error("[채팅 요금제 변경] 처리 중 오류 executionId={}", input.executionId(), exception);
            return new PlanChangeAnswerer.Outcome(FAILED, List.of(), null);
        }
    }

    private GeneratedAnswer answered(AnswerInput input, PlanChangeAnswerer.Outcome outcome) {
        trace.stage(input.executionId(), "planChange", Map.of("answered", true));
        return GeneratedAnswer.withoutSources(new ChatAnswer(ChatMessage.MessageType.ANSWER, outcome.content(),
                ChatMessage.AnswerBasis.GROUNDED, outcome.followUps(), null));
    }

    // 추천 답변이 다른 요금제를 권하면 "○○로 바꿔줘" 버튼을 더한다. 버튼을 못 만들어도 원래 답변은 그대로 낸다
    private GeneratedAnswer withChangeButton(AnswerInput input, GeneratedAnswer generated) {
        if (!enabled || input == null || generated == null || generated.answer() == null
                || generated.answer().followUps() == null
                || !generated.answer().followUps().contains(RECOMMENDATION_CHANGE_QUESTION)) {
            return generated;
        }
        try {
            Optional<String> button = changes.changeButton(memberOf(input), generated.answer().content());
            if (button.isEmpty() || generated.answer().followUps().contains(button.get())) {
                return generated;
            }
            List<String> followUps = new ArrayList<>(generated.answer().followUps());
            followUps.add(button.get());
            ChatAnswer answer = generated.answer();
            return new GeneratedAnswer(new ChatAnswer(answer.messageType(), answer.content(), answer.answerBasis(),
                    List.copyOf(followUps), answer.storeResults(), answer.storeSearchContext()), generated.sources());
        } catch (RuntimeException exception) {
            log.warn("[채팅 요금제 변경] 변경 버튼을 만들지 못했습니다. executionId={}", input.executionId(), exception);
            return generated;
        }
    }

    private Long memberOf(AnswerInput input) {
        return memberOfSession.apply(input.sessionId()).orElse(null);
    }
}
