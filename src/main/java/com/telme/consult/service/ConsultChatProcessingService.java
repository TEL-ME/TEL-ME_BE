package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatCoordinates;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.repository.AskedQuestions;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.service.AnswerPromptTemplates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/** 분석·검색 어댑터를 받은 뒤 Chat 처리 지점에 등록한다. 모델 호출 중에는 DB 잠금을 잡지 않는다. */
@Slf4j
public final class ConsultChatProcessingService implements ChatProcessingPort {
    private static final String PROCESSING_ERROR_CODE = "AI_PROCESSING_ERROR";

    private final TurnAnalyzer analyzer;
    private final AnswerProvider answers;
    private final ConsultChatPersistenceService persistence;
    private final ConfirmedConditionConverter conditionConverter;
    private final ConsultChatEvents events;
    private final ExecutionTrace trace;
    private final AskedQuestions askedQuestions;

    public ConsultChatProcessingService(
            TurnAnalyzer analyzer,
            AnswerProvider answers,
            ConsultChatPersistenceService persistence,
            ConfirmedConditionConverter conditionConverter) {
        this(
                analyzer,
                answers,
                persistence,
                conditionConverter,
                ConsultChatEvents.noop());
    }

    public ConsultChatProcessingService(
            TurnAnalyzer analyzer,
            AnswerProvider answers,
            ConsultChatPersistenceService persistence,
            ConfirmedConditionConverter conditionConverter,
            ConsultChatEvents events) {
        this(analyzer, answers, persistence, conditionConverter, events, ExecutionTrace.noop());
    }

    public ConsultChatProcessingService(
            TurnAnalyzer analyzer,
            AnswerProvider answers,
            ConsultChatPersistenceService persistence,
            ConfirmedConditionConverter conditionConverter,
            ConsultChatEvents events,
            ExecutionTrace trace) {
        this(analyzer, answers, persistence, conditionConverter, events, trace, AskedQuestions.none());
    }

    public ConsultChatProcessingService(
            TurnAnalyzer analyzer,
            AnswerProvider answers,
            ConsultChatPersistenceService persistence,
            ConfirmedConditionConverter conditionConverter,
            ConsultChatEvents events,
            ExecutionTrace trace,
            AskedQuestions askedQuestions) {
        this.askedQuestions = Objects.requireNonNull(askedQuestions);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.answers = Objects.requireNonNull(answers);
        this.persistence = Objects.requireNonNull(persistence);
        this.conditionConverter = Objects.requireNonNull(conditionConverter);
        this.events = Objects.requireNonNull(events);
        this.trace = Objects.requireNonNull(trace);
    }

    @Override
    public void request(ChatProcessingCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            process(command);
        } catch (RuntimeException exception) {
            fail(command, exception);
        }
    }

    private void process(ChatProcessingCommand command) {
        trace.stage(command.executionId(), "processing", Map.of(
                "handler", "ConsultChatProcessingService", "traceVersion", 1));
        AnalyzedTurn turn = analyzer.analyze(command);
        if (turn.directAnswer() == null && turn.preparation() != null) {
            trace.stage(command.executionId(), "analysis", Map.of(
                    "purpose", turn.purpose().name(), "originalQuery", turn.originalUserQuery(),
                    "resolvedQuery", turn.resolvedUserQuery(),
                    "refinedQuery", turn.searchQuery(), "action",
                    turn.preparation().waitingForReply() ? "WAITING"
                            : turn.preparation().prepared().decision().action().name()));
        } else {
            trace.stage(command.executionId(), "analysis", Map.of("action",
                    turn.directAnswer() != null ? "DIRECT" : "ADAPTER_BRANCH"));
        }
        if (turn.directAnswer() != null) {
            var completed =
                    persistence.persistDirectAnswer(
                            command.executionId(), command.sessionId(), turn.directAnswer());
            events.completed(command.executionId(), completed);
            return;
        }
        if (turn.faqTurns() != null) {
            processMultipleFaq(command, turn.faqTurns(), turn.context());
            return;
        }
        var result = turn.preparation();
        if (result.waitingForReply()) {
            var completed =
                    persistence.persistWaiting(command.executionId(), command.sessionId(), result);
            events.completed(command.executionId(), completed);
            return;
        }
        var prepared = result.prepared();
        if (prepared.sessionId() != command.sessionId()) {
            throw new IllegalArgumentException("분석 결과의 채팅방이 일치하지 않습니다.");
        }
        if (prepared.decision().action() == Action.ASK) {
            var clarification =
                    persistence.persistClarification(
                            command.executionId(), prepared, turn.answeredField());
            events.completed(command.executionId(), clarification);
            return;
        }
        ClarificationPlan storedPlan = prepared.clarificationPlan();
        ClarificationPlan storedRemaining =
                storedPlan.remaining(prepared.decision().conditions());
        if (storedRemaining.needsClarification()) {
            var asking = new ConsultService.PreparedTurn(
                    prepared.sessionId(),
                    prepared.expectedVersion(),
                    FaqClarificationDecisions.ask(
                            prepared.decision().consultRequestId(),
                            storedRemaining,
                            prepared.decision().conditions()),
                    storedRemaining);
            var clarification = persistence.persistClarification(
                    command.executionId(), asking, turn.answeredField());
            events.completed(command.executionId(), clarification);
            return;
        }
        AnswerInput answerInput = new AnswerInput(
                command.executionId(),
                command.sessionId(),
                prepared.decision().consultRequestId(),
                turn.purpose(),
                turn.originalUserQuery(),
                turn.searchQuery(),
                conditionConverter.convert(prepared.decision().conditions()),
                turn.resolvedUserQuery(),
                turn.context(),
                command.coordinates(),
                askedQuestions.of(prepared.decision().consultRequestId()));
        // 저장된 계획을 모두 처리한 뒤에는 재추출 결과로 새 조건을 추가하지 않는다.
        // 같은 질문에서 모델 결과가 바뀌어 되묻기가 끝없이 늘어나는 것을 막는다.
        // 버릴 결과라 조건 추출도 부르지 않는다. 마지막 답변이 그만큼 늦어진다
        boolean plansClarification = !storedPlan.needsClarification();
        // 되묻기는 답변 메시지를 열기 전에 정해야 한다. 열고 나서 되물으면 빈 답변이 남는다
        Prepared searched = answers.clarifies() && prepared.decision().action() == Action.PROCEED
                ? answers.prepare(answerInput, plansClarification)
                : Prepared.none();
        var plan = plansClarification
                ? searched.plan().remaining(prepared.decision().conditions())
                : ClarificationPlan.none();
        if (plan.needsClarification()) {
            var asking = new ConsultService.PreparedTurn(
                    prepared.sessionId(),
                    prepared.expectedVersion(),
                    FaqClarificationDecisions.ask(
                            prepared.decision().consultRequestId(),
                            plan,
                            prepared.decision().conditions()),
                    plan);
            var clarification = persistence.persistClarification(
                    command.executionId(), asking, turn.answeredField());
            events.completed(command.executionId(), clarification);
            return;
        }
        persistence.persistReadyTurn(command.executionId(), prepared, turn.answeredField());
        GeneratedAnswer generated;
        if (prepared.decision().action() == Action.ALTERNATIVE_GUIDANCE) {
            generated =
                    GeneratedAnswer.withoutSources(
                            new ChatAnswer(
                                    ChatMessage.MessageType.ANSWER,
                                    prepared.decision().message(),
                                    null,
                                    List.of(),
                                    null));
        } else {
            var started = persistence.startAnswer(command.executionId(), command.sessionId());
            events.started(started);
            // 되묻기를 쓰지 않는 경로는 아직 검색을 안 했다. 그때는 예전처럼 한 번에 처리한다
            generated = answers.clarifies()
                    ? answer(answerInput, searched)
                    : answers.generate(answerInput);
        }
        var completed =
                persistence.persistFinalAnswer(
                        command.executionId(),
                        command.sessionId(),
                        prepared.decision().consultRequestId(),
                        prepared.expectedVersion() + 1,
                        generated.answer(),
                        generated.sources());
        if (prepared.decision().action() == Action.PROCEED
                && generated.answer().messageType() == ChatMessage.MessageType.ANSWER) {
            // 트랜잭션이 완료된 동일 답변만 전송한다. 연결 실패가 완료된 DB 상태를 되돌리지 않는다.
            // 추적 기록은 전송 뒤에 남긴다. 기록용 DB 쓰기가 최종 답변 전송을 늦추지 않게 한다.
            try {
                events.stream(command.executionId()).onToken(generated.answer().content());
                trace.stage(command.executionId(), "finalTransmission", Map.of(
                        "outputMessageId", completed.outputMessage().messageId(),
                        "status", "DISPATCH_RETURNED"));
            } catch (RuntimeException deliveryFailure) {
                trace.stage(command.executionId(), "finalTransmission", Map.of(
                        "outputMessageId", completed.outputMessage().messageId(),
                        "status", "DISPATCH_ERROR"));
                log.warn("최종 답변 토큰 전달 실패: executionId={}",
                        command.executionId(), deliveryFailure);
            }
        }
        try {
            events.completed(command.executionId(), completed);
        } catch (RuntimeException deliveryFailure) {
            log.warn("최종 답변 완료 이벤트 전달 실패: executionId={}",
                    command.executionId(), deliveryFailure);
        }
    }

    private void processMultipleFaq(ChatProcessingCommand command, List<FaqTurn> faqTurns, ChatContext context) {
        List<ConsultService.PreparedTurn> preparedTurns = faqTurns.stream()
                .map(faq -> faq.preparation().prepared())
                .toList();
        if (preparedTurns.stream().anyMatch(prepared -> prepared.sessionId() != command.sessionId()
                || prepared.decision().action() != Action.PROCEED)) {
            throw new IllegalArgumentException("여러 FAQ 질문에는 진행 가능한 상담 결과만 사용할 수 있습니다.");
        }
        persistence.persistReadyTurns(command.executionId(), command.sessionId(), preparedTurns);
        var started = persistence.startAnswer(command.executionId(), command.sessionId());
        events.started(started);

        List<String> sections = new ArrayList<>();
        List<AnswerSource> sources = new ArrayList<>();
        Set<Long> sourceFaqIds = new HashSet<>();
        boolean hasGroundedAnswer = false;
        for (int i = 0; i < faqTurns.size(); i++) {
            FaqTurn faq = faqTurns.get(i);
            ConsultService.PreparedTurn prepared = preparedTurns.get(i);
            GeneratedAnswer generated = answers.generate(new AnswerInput(
                    command.executionId(), command.sessionId(), prepared.decision().consultRequestId(),
                    Purpose.GENERAL_FAQ, faq.queryText(), faq.queryText(),
                    conditionConverter.convert(prepared.decision().conditions()),
                    faq.queryText(), context, false, command.coordinates(), Map.of()));
            if (generated.answer().messageType() != ChatMessage.MessageType.ANSWER) {
                throw new IllegalStateException("FAQ 답변 유형이 올바르지 않습니다.");
            }
            boolean grounded = generated.answer().answerBasis() == ChatMessage.AnswerBasis.GROUNDED;
            String content = grounded ? generated.answer().content() : AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
            sections.add("%d. %s\n%s".formatted(i + 1, faq.queryText(), content));
            if (grounded) {
                hasGroundedAnswer = true;
                // 같은 FAQ가 두 하위 질문의 근거가 되면 인용 횟수가 두 번 쌓이므로 한 번만 남긴다
                generated.sources().stream()
                        .filter(source -> source.faqId() == null || sourceFaqIds.add(source.faqId()))
                        .forEach(sources::add);
            }
        }

        ChatAnswer combined = new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                String.join("\n\n", sections),
                hasGroundedAnswer ? ChatMessage.AnswerBasis.GROUNDED : ChatMessage.AnswerBasis.NO_EVIDENCE,
                List.of(), null);
        List<ConsultChatPersistenceService.ConsultCompletion> completions = preparedTurns.stream()
                .map(prepared -> new ConsultChatPersistenceService.ConsultCompletion(
                        prepared.decision().consultRequestId(), prepared.expectedVersion() + 1))
                .toList();
        var completed = persistence.persistFinalAnswers(
                command.executionId(), command.sessionId(), completions, combined, sources);
        // 여러 모델 호출의 중간 토큰은 저장된 최종 답변과 다를 수 있어 완료 후 한 번만 전송한다.
        try {
            events.stream(command.executionId()).onToken(combined.content());
            trace.stage(command.executionId(), "finalTransmission", Map.of(
                    "outputMessageId", completed.outputMessage().messageId(),
                    "status", "DISPATCH_RETURNED"));
        } catch (RuntimeException deliveryFailure) {
            trace.stage(command.executionId(), "finalTransmission", Map.of(
                    "outputMessageId", completed.outputMessage().messageId(),
                    "status", "DISPATCH_ERROR"));
            log.warn("복합 FAQ 최종 토큰 전달 실패: executionId={}",
                    command.executionId(), deliveryFailure);
        }
        try {
            events.completed(command.executionId(), completed);
        } catch (RuntimeException deliveryFailure) {
            log.warn("복합 FAQ 완료 이벤트 전달 실패: executionId={}",
                    command.executionId(), deliveryFailure);
        }
    }

    private void fail(ChatProcessingCommand command, RuntimeException exception) {
        log.error("상담 AI 처리 실패: executionId={}, sessionId={}",
                command.executionId(), command.sessionId(), exception);
        ChatFailure failure = failure(exception);
        RuntimeException persistenceFailure = null;
        try {
            persistence.failAnswer(
                    command.executionId(), command.sessionId(), failure);
        } catch (RuntimeException failureException) {
            failureException.addSuppressed(exception);
            persistenceFailure = failureException;
        }
        try {
            events.failed(command.executionId(), failure);
        } catch (RuntimeException eventFailure) {
            if (persistenceFailure == null) {
                throw eventFailure;
            }
            persistenceFailure.addSuppressed(eventFailure);
        }
        if (persistenceFailure != null) {
            throw persistenceFailure;
        }
    }

    private ChatFailure failure(RuntimeException exception) {
        if (exception instanceof LlmStreamCancelledException) {
            return new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED");
        }
        if (exception instanceof FaqAnswerSearchException) {
            return new ChatFailure(ChatMessage.Status.FAILED, FaqAnswerSearchException.ERROR_CODE);
        }
        if (exception instanceof GeneralException general) {
            return new ChatFailure(ChatMessage.Status.FAILED, general.getErrorCode().getCode());
        }
        return new ChatFailure(ChatMessage.Status.FAILED, PROCESSING_ERROR_CODE);
    }

    public interface TurnAnalyzer {
        AnalyzedTurn analyze(ChatProcessingCommand command);
    }

    public interface AnswerProvider {
        GeneratedAnswer generate(AnswerInput input);

        /** 되묻기를 판단하는 경로인지. 아니면 검색 시점을 앞당기지 않는다. */
        default boolean clarifies() {
            return false;
        }

        /** 검색과 되묻기 판단만 한다. 되묻는 경로가 아니면 기본값을 쓴다.
         * plansClarification이 false면 검색만 하고 조건 추출은 부르지 않는다. */
        default Prepared prepare(AnswerInput input, boolean plansClarification) {
            return Prepared.none();
        }

        default GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
            return generate(input);
        }
    }

    /** 검색 결과와 되묻기 계획. 답변을 만들 때 검색을 다시 하지 않으려고 함께 들고 다닌다. */
    /** answer가 있으면 준비 단계에서 이미 답을 확정한 것이다. 비교·후보 근거 검증이 그 경우다. */
    public record Prepared(
            List<FaqSearchResponse> searchResults, ClarificationPlan plan, GeneratedAnswer answer) {
        private static final Prepared NONE = new Prepared(List.of(), ClarificationPlan.none(), null);

        public Prepared(List<FaqSearchResponse> searchResults, ClarificationPlan plan) {
            this(searchResults, plan, null);
        }

        public Prepared {
            searchResults = List.copyOf(searchResults);
            Objects.requireNonNull(plan, "plan");
        }

        public static Prepared answered(GeneratedAnswer answer) {
            return new Prepared(List.of(), ClarificationPlan.none(), Objects.requireNonNull(answer));
        }

        public static Prepared none() {
            return NONE;
        }
    }

    public record GeneratedAnswer(ChatAnswer answer, List<AnswerSource> sources) {
        public GeneratedAnswer {
            Objects.requireNonNull(answer, "answer");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }

        public static GeneratedAnswer withoutSources(ChatAnswer answer) {
            return new GeneratedAnswer(answer, List.of());
        }
    }

    /** HTTP DTO가 아닌 분석 연결 결과다. 최초 질문은 answeredField가 없다. */
    public record AnalyzedTurn(
            ConsultService.PreparationResult preparation,
            String answeredField,
            Purpose purpose,
            String originalUserQuery,
            String searchQuery,
            ChatAnswer directAnswer,
            ChatContext context,
            String resolvedUserQuery,
            List<FaqTurn> faqTurns) {
        public AnalyzedTurn(ConsultService.PreparationResult preparation, String answeredField, Purpose purpose,
                String originalUserQuery, String searchQuery, ChatAnswer directAnswer) {
            this(preparation, answeredField, purpose, originalUserQuery, searchQuery, directAnswer,
                    null, directAnswer == null ? originalUserQuery : null, null);
        }
        public AnalyzedTurn(
                ConsultService.PreparationResult preparation,
                String answeredField,
                Purpose purpose,
                String originalUserQuery,
                String searchQuery) {
            this(preparation, answeredField, purpose, originalUserQuery, searchQuery, null);
        }

        public static AnalyzedTurn direct(ChatAnswer answer) {
            return new AnalyzedTurn(null, null, null, null, null, Objects.requireNonNull(answer));
        }

        public static AnalyzedTurn multipleFaq(List<FaqTurn> faqTurns) {
            return new AnalyzedTurn(null, null, null, null, null, null, null, null, faqTurns);
        }

        public AnalyzedTurn withContext(ChatContext value, String resolved) {
            return new AnalyzedTurn(preparation, answeredField, purpose, originalUserQuery, searchQuery,
                    directAnswer, value, resolved, faqTurns);
        }

        public AnalyzedTurn {
            if (directAnswer != null) {
                if (preparation != null
                        || answeredField != null
                        || purpose != null
                        || originalUserQuery != null
                        || searchQuery != null
                        || context != null || resolvedUserQuery != null || faqTurns != null) {
                    throw new IllegalArgumentException("직접 답변에는 상담 분석 결과를 함께 넣을 수 없습니다.");
                }
            } else if (faqTurns != null) {
                faqTurns = List.copyOf(faqTurns);
                if (faqTurns.size() < 2 || preparation != null || answeredField != null
                        || purpose != null || originalUserQuery != null || searchQuery != null) {
                    throw new IllegalArgumentException("여러 FAQ 상담 결과가 필요합니다.");
                }
                if (resolvedUserQuery != null && resolvedUserQuery.isBlank()) {
                    throw new IllegalArgumentException("문맥을 반영한 상담 질문이 필요합니다.");
                }
                if (resolvedUserQuery != null) {
                    resolvedUserQuery = resolvedUserQuery.strip();
                }
            } else {
                Objects.requireNonNull(preparation, "preparation");
                Objects.requireNonNull(purpose, "purpose");
                if (answeredField != null && answeredField.isBlank()) {
                    throw new IllegalArgumentException("후속 조건 이름은 비어 있을 수 없습니다.");
                }
                if (originalUserQuery == null || originalUserQuery.isBlank()) {
                    throw new IllegalArgumentException("상담 원문이 필요합니다.");
                }
                if (searchQuery == null || searchQuery.isBlank()) {
                    throw new IllegalArgumentException("검색할 질문이 필요합니다.");
                }
                originalUserQuery = originalUserQuery.strip();
                searchQuery = searchQuery.strip();
                if (resolvedUserQuery == null || resolvedUserQuery.isBlank()) {
                    throw new IllegalArgumentException("문맥을 반영한 상담 질문이 필요합니다.");
                }
                resolvedUserQuery = resolvedUserQuery.strip();
            }
        }
    }

    public record FaqTurn(ConsultService.PreparationResult preparation, String queryText) {
        public FaqTurn {
            if (preparation == null || preparation.waitingForReply()
                    || preparation.prepared() == null
                    || queryText == null || queryText.isBlank()) {
                throw new IllegalArgumentException("진행 가능한 FAQ 하위 질문이 필요합니다.");
            }
            queryText = queryText.strip();
        }
    }

    /** 실제 답변 생성 어댑터에 넘길 상담 입력이다. */
    public record AnswerInput(
            long executionId,
            long sessionId,
            long consultRequestId,
            Purpose purpose,
            String originalUserQuery,
            String searchQuery,
            Map<String, String> confirmedConditions,
            String resolvedUserQuery,
            ChatContext context,
            boolean streamTokens,
            ChatCoordinates coordinates,
            Map<String, String> askedQuestions) {
        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, originalUserQuery, null, true, null, Map.of());
        }

        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions,
                boolean streamTokens) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, originalUserQuery, null, streamTokens, null, Map.of());
        }

        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions,
                ChatCoordinates coordinates) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, originalUserQuery, null, true, coordinates, Map.of());
        }

        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions,
                String resolvedUserQuery, ChatContext context) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, resolvedUserQuery, context, true, null, Map.of());
        }

        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions,
                String resolvedUserQuery, ChatContext context, ChatCoordinates coordinates) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, resolvedUserQuery, context, true, coordinates, Map.of());
        }

        public AnswerInput(long executionId, long sessionId, long consultRequestId, Purpose purpose,
                String originalUserQuery, String searchQuery, Map<String, String> confirmedConditions,
                String resolvedUserQuery, ChatContext context, ChatCoordinates coordinates,
                Map<String, String> askedQuestions) {
            this(executionId, sessionId, consultRequestId, purpose, originalUserQuery, searchQuery,
                    confirmedConditions, resolvedUserQuery, context, true, coordinates, askedQuestions);
        }

        @Override
        public String toString() {
            return "AnswerInput[executionId=%d, sessionId=%d, consultRequestId=%d]"
                    .formatted(executionId, sessionId, consultRequestId);
        }

        public AnswerInput {
            if (executionId <= 0 || sessionId <= 0 || consultRequestId <= 0) {
                throw new IllegalArgumentException("답변 생성에 필요한 상담 참조가 없습니다.");
            }
            Objects.requireNonNull(purpose, "purpose");
            if (originalUserQuery == null || originalUserQuery.isBlank()) {
                throw new IllegalArgumentException("답변 생성에 사용할 사용자 원문이 필요합니다.");
            }
            if (searchQuery == null || searchQuery.isBlank()) {
                throw new IllegalArgumentException("검색에 사용할 정제 질문이 필요합니다.");
            }
            originalUserQuery = originalUserQuery.strip();
            searchQuery = searchQuery.strip();
            if (resolvedUserQuery == null || resolvedUserQuery.isBlank()) {
                throw new IllegalArgumentException("문맥을 반영한 답변 질문이 필요합니다.");
            }
            if (context != null && context.sessionId() != sessionId) {
                throw new IllegalArgumentException("답변 문맥의 세션이 일치하지 않습니다.");
            }
            resolvedUserQuery = resolvedUserQuery.strip();
            confirmedConditions =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    confirmedConditions, "confirmedConditions"));
            askedQuestions = askedQuestions == null ? Map.of() : Map.copyOf(askedQuestions);
        }
    }

    // 준비 단계에서 답이 확정됐으면 모델을 다시 부르지 않는다
    private GeneratedAnswer answer(AnswerInput input, Prepared prepared) {
        return prepared.answer() != null ? prepared.answer() : answers.generate(input, prepared);
    }
}
