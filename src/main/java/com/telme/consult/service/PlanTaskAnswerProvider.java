package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.repository.SessionRecommendationAnswers;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;
import lombok.extern.slf4j.Slf4j;

// 기존 상담 답변 경로를 바꾸지 않고 바깥에서 감싼다. 요금제 업무(변경·신청·일시 정지·해지) 요청만 먼저 받아 안내와 처리 화면 링크를 주고
// 나머지는 그대로 넘긴다. 보안상 채팅에서는 계약을 바꾸지 않는다
@Slf4j
public final class PlanTaskAnswerProvider implements AnswerProvider {

    // 요금제 추천 답변(MemberRecommendationAnswerer)이 더 싸거나 큰 요금제를 권할 때 다는 버튼. 이 버튼 옆에 변경 화면 링크를 더한다
    static final String RECOMMENDATION_CHANGE_QUESTION = "요금제 변경은 어떻게 해요?";

    // 대상 요금제를 직전 추천에서 보충하는 업무. 정지·해지는 쓰지 않아 저장된 메시지를 읽지 않는다
    private static final Set<PlanTask> USES_RECOMMENDATION = EnumSet.of(PlanTask.CHANGE, PlanTask.SUBSCRIBE);

    private final AnswerProvider delegate;
    private final Map<PlanTask, PlanTaskHandler> handlers;
    private final LongFunction<Optional<Long>> memberOfSession;
    private final LongFunction<Optional<String>> recommendationOfSession;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanTaskAnswerProvider(AnswerProvider delegate, List<? extends PlanTaskHandler> handlers,
            LongFunction<Optional<Long>> memberOfSession, ExecutionTrace trace, boolean enabled) {
        this(delegate, handlers, memberOfSession, sessionId -> Optional.empty(), trace, enabled);
    }

    // recommendationOfSession은 채팅방의 가장 최근 요금제 추천 답변 본문. 없으면 비어 있다
    public PlanTaskAnswerProvider(AnswerProvider delegate, List<? extends PlanTaskHandler> handlers,
            LongFunction<Optional<Long>> memberOfSession, LongFunction<Optional<String>> recommendationOfSession,
            ExecutionTrace trace, boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate);
        this.handlers = new EnumMap<>(PlanTask.class);
        for (PlanTaskHandler handler : handlers) {
            if (this.handlers.put(handler.task(), handler) != null) {
                throw new IllegalArgumentException("요금제 업무 처리기가 중복됩니다: " + handler.task());
            }
        }
        this.memberOfSession = Objects.requireNonNull(memberOfSession);
        this.recommendationOfSession = Objects.requireNonNull(recommendationOfSession);
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
            String recommended = USES_RECOMMENDATION.contains(handler.task()) ? recentRecommendation(input) : null;
            return handler.answer(memberOf(input), input.originalUserQuery(), input.resolvedUserQuery(), recommended);
        } catch (RuntimeException exception) {
            // 기존 FAQ 경로로 넘기면 업무 요청에 엉뚱한 답이 나가서 안내하지 못했다고만 알린다
            log.error("[채팅 요금제 업무] 안내 중 오류 task={}, executionId={}", handler.task(), input.executionId(), exception);
            return PlanTaskOutcome.reply(PlanTaskSupport.FAILED);
        }
    }

    // 같은 채팅방에서 가장 최근에 한 요금제 추천 답변에서 추천한 요금제를 알려주는 문장.
    // 뒤따르는 "5G로 쓰시려면 …" 같은 다른 후보와 지금 쓰는 요금제 문장은 대상이 아니라 빼고 쓴다. 없으면 null
    private String recentRecommendation(AnswerInput input) {
        return recommendationOfSession.apply(input.sessionId()).map(PlanTaskAnswerProvider::targetSentence).orElse(null);
    }

    static String targetSentence(String recommendation) {
        int end = recommendation.indexOf(SessionRecommendationAnswers.RECOMMENDATION_MARK);
        if (end >= 0) {
            return recommendation.substring(0, end + SessionRecommendationAnswers.RECOMMENDATION_MARK.length());
        }
        // 사용량이 있는 회원에게는 현재 요금제 문장 뒤에 추천한 요금제가 나온다
        String sentence = between(recommendation, SessionRecommendationAnswers.UPGRADE_MARK,
                SessionRecommendationAnswers.SHORTAGE_END, SessionRecommendationAnswers.UPGRADE_END);
        if (sentence == null) {
            sentence = between(recommendation, SessionRecommendationAnswers.CHEAPER_MARK,
                    SessionRecommendationAnswers.USE_END, SessionRecommendationAnswers.CHEAPER_END);
        }
        if (sentence == null) {
            sentence = between(recommendation, SessionRecommendationAnswers.KEEP_MARK,
                    SessionRecommendationAnswers.USE_END, SessionRecommendationAnswers.KEEP_MARK);
        }
        return sentence == null ? recommendation : sentence;
    }

    // mark가 든 문장을 start 뒤부터 end 끝까지 잘라낸다. mark가 없거나 앞뒤를 찾지 못하면 null
    private static String between(String text, String mark, String start, String end) {
        int markAt = text.indexOf(mark);
        if (markAt < 0) {
            return null;
        }
        int from = text.lastIndexOf(start, markAt);
        int to = text.indexOf(end, markAt);
        return from < 0 || to < 0 ? null : text.substring(from + start.length(), to + end.length());
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
