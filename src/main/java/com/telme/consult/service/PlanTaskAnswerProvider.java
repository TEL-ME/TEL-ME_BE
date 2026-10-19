package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import lombok.extern.slf4j.Slf4j;

// 기존 상담 답변 경로를 바꾸지 않고 바깥에서 감싼다. 요금제 업무(변경·신청·일시 정지·해지) 요청만 먼저 받아 안내와 처리 화면 링크를 주고
// 나머지는 그대로 넘긴다. 보안상 채팅에서는 계약을 바꾸지 않는다
@Slf4j
public final class PlanTaskAnswerProvider implements AnswerProvider {

    // 요금제 추천 답변(MemberRecommendationAnswerer)이 더 싸거나 큰 요금제를 권할 때 다는 버튼. 이 버튼 옆에 변경 화면 링크를 더한다
    static final String RECOMMENDATION_CHANGE_QUESTION = "요금제 변경은 어떻게 해요?";
    // 요금제 추천 답변(MemberRecommendationAnswerer.recommendation)의 첫 문장 끝
    private static final String RECOMMENDATION_MARK = "요금제를 추천해요.";

    private final AnswerProvider delegate;
    private final Map<PlanTask, PlanTaskHandler> handlers;
    private final LongFunction<Optional<Long>> memberOfSession;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanTaskAnswerProvider(AnswerProvider delegate, List<? extends PlanTaskHandler> handlers,
            LongFunction<Optional<Long>> memberOfSession, ExecutionTrace trace, boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate);
        this.handlers = new EnumMap<>(PlanTask.class);
        for (PlanTaskHandler handler : handlers) {
            if (this.handlers.put(handler.task(), handler) != null) {
                throw new IllegalArgumentException("요금제 업무 처리기가 중복됩니다: " + handler.task());
            }
        }
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
        Optional<GeneratedAnswer> answer = taskAnswer(input);
        if (answer.isPresent()) {
            return Prepared.answered(answer.get());
        }
        Prepared prepared = delegate.prepare(input, plansClarification);
        return prepared == null || prepared.answer() == null ? prepared
                : new Prepared(prepared.searchResults(), prepared.plan(), withChangeLink(prepared.answer()));
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        return withChangeLink(delegate.generate(input, prepared));
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        return taskAnswer(input).orElseGet(() -> withChangeLink(delegate.generate(input)));
    }

    // 업무 요청이 아니면 비어 있다. 업무 판정은 사용자가 이번에 쓴 원문으로만 한다. 문맥으로 풀어쓴 문장에는
    // "[대상을 확인할 이전 고객 발언] 5G 라이트로 바꿔줘 [현재 후속 질문] 그건 데이터 몇 기가예요?"처럼 이전 요청이 섞여 있어,
    // 처리기가 대상 요금제를 보충할 때만 쓴다
    private Optional<GeneratedAnswer> taskAnswer(AnswerInput input) {
        if (!enabled || input == null || input.purpose() != Purpose.GENERAL_FAQ) {
            return Optional.empty();
        }
        Optional<String> linkGuide = PlanTaskLinks.guideFor(input.originalUserQuery());
        if (linkGuide.isPresent()) {
            return Optional.of(answered(input, "link", new PlanTaskOutcome(linkGuide.get(), List.of())));
        }
        Optional<PlanTaskHandler> handler = PlanTaskRules.detect(input.originalUserQuery()).map(handlers::get);
        return handler.map(task -> answered(input, task.task().name(), handle(task, input)));
    }

    private PlanTaskOutcome handle(PlanTaskHandler handler, AnswerInput input) {
        try {
            return handler.answer(memberOf(input), input.originalUserQuery(), input.resolvedUserQuery(), recentRecommendation(input));
        } catch (RuntimeException exception) {
            // 기존 FAQ 경로로 넘기면 업무 요청에 엉뚱한 답이 나가서 안내하지 못했다고만 알린다
            log.error("[채팅 요금제 업무] 안내 중 오류 task={}, executionId={}", handler.task(), input.executionId(), exception);
            return PlanTaskOutcome.reply(PlanTaskSupport.FAILED);
        }
    }

    // 같은 채팅방에서 가장 최근에 한 요금제 추천 답변의 첫 문장("…쓰신다면 5G 스탠다드(월 65,000원…) 요금제를 추천해요.").
    // 뒤따르는 "5G로 쓰시려면 …" 같은 다른 후보는 대상이 아니라 첫 문장만 쓴다. 없으면 null
    static String recentRecommendation(AnswerInput input) {
        if (input.context() == null) {
            return null;
        }
        List<ChatContextMessage> history = input.context().history();
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatContextMessage message = history.get(i);
            if (message.role() != ChatMessage.Role.ASSISTANT || message.content() == null) {
                continue;
            }
            int end = message.content().indexOf(RECOMMENDATION_MARK);
            if (end >= 0) {
                return message.content().substring(0, end + RECOMMENDATION_MARK.length());
            }
        }
        return null;
    }

    private GeneratedAnswer answered(AnswerInput input, String task, PlanTaskOutcome outcome) {
        trace.stage(input.executionId(), "planTask", Map.of("task", task));
        return GeneratedAnswer.withoutSources(new ChatAnswer(ChatMessage.MessageType.ANSWER, outcome.content(),
                ChatMessage.AnswerBasis.GROUNDED, outcome.followUps(), null));
    }

    // 추천 답변이 다른 요금제를 권하면 요금제 변경 화면 링크를 더한다
    private GeneratedAnswer withChangeLink(GeneratedAnswer generated) {
        if (!enabled || generated == null || generated.answer() == null || generated.answer().followUps() == null
                || !generated.answer().followUps().contains(RECOMMENDATION_CHANGE_QUESTION)
                || generated.answer().followUps().contains(PlanTaskLinks.CHANGE)) {
            return generated;
        }
        List<String> followUps = new ArrayList<>(generated.answer().followUps());
        followUps.add(PlanTaskLinks.CHANGE);
        ChatAnswer answer = generated.answer();
        return new GeneratedAnswer(new ChatAnswer(answer.messageType(), answer.content(), answer.answerBasis(),
                List.copyOf(followUps), answer.storeResults(), answer.storeSearchContext()), generated.sources());
    }

    private Long memberOf(AnswerInput input) {
        return memberOfSession.apply(input.sessionId()).orElse(null);
    }
}
