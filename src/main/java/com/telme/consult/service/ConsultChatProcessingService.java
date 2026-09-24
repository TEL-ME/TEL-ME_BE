package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        this.analyzer = Objects.requireNonNull(analyzer);
        this.answers = Objects.requireNonNull(answers);
        this.persistence = Objects.requireNonNull(persistence);
        this.conditionConverter = Objects.requireNonNull(conditionConverter);
        this.events = Objects.requireNonNull(events);
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
        AnalyzedTurn turn = analyzer.analyze(command);
        if (turn.directAnswer() != null) {
            var completed =
                    persistence.persistDirectAnswer(
                            command.executionId(), command.sessionId(), turn.directAnswer());
            events.completed(command.executionId(), completed);
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
            generated =
                    answers.generate(
                            new AnswerInput(
                                    command.executionId(),
                                    command.sessionId(),
                                    prepared.decision().consultRequestId(),
                                    turn.purpose(),
                                    turn.originalUserQuery(),
                                    turn.searchQuery(),
                                    conditionConverter.convert(
                                            prepared.decision().conditions())));
        }
        var completed =
                persistence.persistFinalAnswer(
                        command.executionId(),
                        command.sessionId(),
                        prepared.decision().consultRequestId(),
                        prepared.expectedVersion() + 1,
                        generated.answer(),
                        generated.sources());
        events.completed(command.executionId(), completed);
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
            ChatAnswer directAnswer) {
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

        public AnalyzedTurn {
            if (directAnswer != null) {
                if (preparation != null
                        || answeredField != null
                        || purpose != null
                        || originalUserQuery != null
                        || searchQuery != null) {
                    throw new IllegalArgumentException("직접 답변에는 상담 분석 결과를 함께 넣을 수 없습니다.");
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
            }
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
            Map<String, String> confirmedConditions) {
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
            confirmedConditions =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    confirmedConditions, "confirmedConditions"));
        }
    }
}
