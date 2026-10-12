package com.telme.rag.service;

import com.telme.chat.entity.ChatMessage.AnswerBasis;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.exception.AnswerGuardException;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import com.telme.chat.service.ExecutionTrace;
import com.telme.chat.service.ChatTokenEstimator;
import com.telme.llm.config.LlmProperties;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RagAnswerGenerator implements AnswerGenerator {

    private final LlmClient llmClient;
    private final AnswerContextConverter contextConverter;
    private final AnswerGuard answerGuard;
    private final EvidenceRelevanceChecker relevanceChecker;
    private final LlmGenerationRecorder recorder;
    private final ExecutionTrace trace;
    private final int contextSize;

    @Autowired
    public RagAnswerGenerator(LlmClient client, AnswerContextConverter converter, AnswerGuard guard,
            EvidenceRelevanceChecker checker, LlmGenerationRecorder recorder, ExecutionTrace trace,
            LlmProperties properties) {
        this(client, converter, guard, checker, recorder, trace, properties.contextSize());
    }

    public RagAnswerGenerator(LlmClient client, AnswerContextConverter converter, AnswerGuard guard,
            EvidenceRelevanceChecker checker, LlmGenerationRecorder recorder, ExecutionTrace trace) {
        this(client, converter, guard, checker, recorder, trace, 8192);
    }

    private RagAnswerGenerator(LlmClient client, AnswerContextConverter converter, AnswerGuard guard,
            EvidenceRelevanceChecker checker, LlmGenerationRecorder recorder, ExecutionTrace trace, int size) {
        this.llmClient = client;
        this.contextConverter = converter;
        this.answerGuard = guard;
        this.relevanceChecker = checker;
        this.recorder = recorder;
        this.trace = trace;
        this.contextSize = size;
    }

    @Override
    public AnswerResult generate(AnswerRequest request, LlmStreamHandler handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        var generationInput = new LinkedHashMap<String, Object>(Map.of(
                "userQuery", request.userQuery(), "sources", request.searchResults(),
                "promptVersion", AnswerPromptTemplates.promptVersionFor(request.userQuery()),
                "guardEvidenceScope", "FAQ_ANSWERS_ONLY"));
        generationInput.put("consultRequestId", request.consultRequestId());
        generationInput.put("resolvedQuery", request.resolvedQuery());
        if (request.chatContext() != null) {
            generationInput.put("historyMessageIds", request.chatContext().history().stream()
                    .map(com.telme.chat.service.ChatContextMessage::messageId).toList());
            generationInput.put("summaryMessageIds", request.chatContext().summarySources().stream()
                    .map(com.telme.chat.service.ChatContextMessage::messageId).toList());
        }
        trace.append(request.executionId(), "generationInputs", generationInput);

        // 근거 없이 호출하면 모델이 지어냄. 검색 결과 없음의 guard 기록은 호출 전에 FaqSearchAnswerProvider가 남긴다
        if (request.searchResults().isEmpty()) {
            return answerWithoutEvidence(request, handler);
        }

        String context = contextConverter.toContext(request.searchResults());
        String answerEvidence = request.searchResults().stream()
                .map(result -> result.answer() == null ? "" : result.answer())
                .collect(Collectors.joining("\n"));

        // 검색은 문장 유사도로만 걸러 묻는 항목이 근거에 없는 질문도 통과시킨다
        if (!relevanceChecker.canAnswer(request.executionId(), request.resolvedQuery(), context)) {
            trace.stage(request.executionId(), "guard", Map.of("outcome", "NOT_RUN",
                    "reason", "EVIDENCE_NOT_RELEVANT"));
            log.info("[RagAnswerGenerator] 근거가 질문에 답하지 않아 생성을 건너뛴다 executionId={}",
                    request.executionId());
            return answerWithoutEvidence(request, handler);
        }

        String system = AnswerPromptTemplates.systemPromptFor(request.userQuery())
                + (request.chatContext() == null ? "" : AnswerPromptTemplates.MULTITURN_RULES);
        ChatTokenEstimator estimator = new ChatTokenEstimator();
        int historyBudget = Math.max(0, contextSize - 1024 - 256
                - estimator.estimatePromptPart(system)
                - estimator.estimatePromptPart(AnswerPromptTemplates.buildUserPrompt(request, context, 0)));
        String prompt = AnswerPromptTemplates.buildUserPrompt(request, context, historyBudget);
        if (estimator.estimatePromptPart(system) + estimator.estimatePromptPart(prompt) + 1024 + 256 > contextSize) {
            throw new IllegalArgumentException("FAQ 근거와 현재 질문이 모델의 입력 예산을 초과합니다.");
        }
        LlmRequest llmRequest = LlmRequest.builder()
                .executionId(request.executionId())
                .consultRequestId(request.consultRequestId())
                .taskType(TaskType.RAG_ANSWER)
                .systemPrompt(system)
                .userPrompt(prompt)
                .contextCount(request.searchResults().size())
                .promptVersion(AnswerPromptTemplates.promptVersionFor(request.userQuery()))
                .build();

        CollectingHandler collector =
                new CollectingHandler(handler, answerGuard, answerEvidence, request.resolvedQuery(),
                        String.join(" ", request.conditions().values()),
                        trace, request.executionId(), request.consultRequestId());
        try {
            llmClient.stream(llmRequest, collector);
            collector.rethrowIfFailed();
        } catch (AnswerGuardException rejection) {
            if (collector.rejection != rejection) {
                throw rejection;
            }
            // 검사 예외는 기록 클라이언트까지 전달해 실패로 남긴 뒤 기존 안전 안내로 완료한다.
            log.warn("[RagAnswerGenerator] Guard 차단으로 안전 안내 반환 executionId={} reason={}",
                    request.executionId(), rejection.getMessage());
            collector.deliverSafeAnswer();
        }

        String answer = collector.answer();

        return AnswerResult.builder()
                .answer(answer)
                .answerBasis(toAnswerBasis(answer))
                // 모델이 실제로 참고한 근거는 알 수 없어 전달한 검색 결과 전부를 기록
                .sources(contextConverter.toSources(request.searchResults()))
                .build();
    }

    // 잘라낸 답변은 문구로 끝난다. 앞에 "죄송합니다." 같은 서두가 남을 수 있어 포함 여부로 본다
    private AnswerBasis toAnswerBasis(String answer) {
        return answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                ? AnswerBasis.NO_EVIDENCE
                : AnswerBasis.GROUNDED;
    }

    private AnswerResult answerWithoutEvidence(AnswerRequest request, LlmStreamHandler handler) {
        // LLM을 안 거쳐 RecordingLlmClient가 남길 수 없는 경로
        recordNoEvidence(request);

        handler.onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        handler.onComplete();

        return AnswerResult.builder()
                .answer(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                .answerBasis(AnswerBasis.NO_EVIDENCE)
                .build();
    }

    // 기록 저장 실패가 답변을 막지 않도록 차단
    private void recordNoEvidence(AnswerRequest request) {
        LlmRequest llmRequest = LlmRequest.builder()
                .executionId(request.executionId())
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt(request.userQuery())
                .contextCount(0)
                .promptVersion(AnswerPromptTemplates.PROMPT_VERSION)
                .build();
        try {
            recorder.record(llmRequest, null, LlmGenerationRecorder.Result.noEvidence());
        } catch (RuntimeException e) {
            log.warn("[RagAnswerGenerator] 근거 없음 기록 저장 실패 executionId={}", request.executionId(), e);
        }
    }

    // 원문 토큰은 보관만 하고 Guard가 검증한 최종 답변만 외부로 전달한다.
    private static final class CollectingHandler implements LlmStreamHandler {

        private final LlmStreamHandler delegate;
        private final AnswerGuard answerGuard;
        private final String context;
        private final String userQuery;
        private final String confirmedValues;
        private final StringBuilder collected = new StringBuilder();
        private String answer = "";
        private RuntimeException failure;
        private AnswerGuardException rejection;
        private boolean terminal;
        private final ExecutionTrace trace;
        private final Long executionId;
        private final Long consultRequestId;

        private CollectingHandler(
                LlmStreamHandler delegate, AnswerGuard answerGuard, String context, String userQuery,
                String confirmedValues, ExecutionTrace trace, Long executionId, Long consultRequestId) {
            this.delegate = delegate;
            this.answerGuard = answerGuard;
            this.context = context;
            this.userQuery = userQuery;
            this.confirmedValues = confirmedValues;
            this.trace = trace;
            this.executionId = executionId;
            this.consultRequestId = consultRequestId;
        }

        @Override
        public void onToken(String token) {
            if (terminal) {
                return;
            }
            delegate.onProgress();
            collected.append(token);
        }

        // 여기서 검사해야 호출 기록이 실패로 남는다. stream()이 끝난 뒤에 막으면 SUCCESS가 이미 들어간다
        @Override
        public void onComplete() {
            if (terminal) {
                return;
            }
            try {
                answer = answerGuard.applyEvidencePolicy(
                        collected.toString().strip(), context, userQuery, confirmedValues);
                // 상담 경로의 delegate.onToken은 진행 신호(onProgress)로만 전달된다.
                // 최종 답변은 저장 후 별도로 전송하므로 여기의 기록은 전송을 늦추지 않는다.
                recordGuard(
                        answer.equals(collected.toString()) ? "KEPT"
                                : answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                                        ? "REPLACED" : "MODIFIED",
                        "EVIDENCE_POLICY_RESULT");
            } catch (AnswerGuardException exception) {
                recordGuard("REPLACED", "GUARD_EXCEPTION");
                rejection = exception;
                throw exception;
            }
            delegate.onToken(answer);
            delegate.onComplete();
            terminal = true;
        }

        @Override
        public void onError(Throwable error) {
            if (terminal) {
                return;
            }
            terminal = true;
            // 여기서 바로 던지면 LLM 클라이언트 내부에서 터짐. 보관 후 stream() 종료 뒤 전달
            failure = error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(error);
            if (error != rejection) {
                recordGuard("NOT_RUN", "GENERATION_FAILED");
                delegate.onError(error);
            }
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            if (terminal) {
                return;
            }
            // 재시도는 처음부터 다시 생성. 앞서 모은 토큰 폐기
            collected.setLength(0);
            delegate.onRetry(attempt, cause);
        }

        private void deliverSafeAnswer() {
            terminal = true;
            collected.setLength(0);
            answer = AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
            delegate.onToken(answer);
            delegate.onComplete();
        }

        private String answer() {
            return answer;
        }

        private void recordGuard(String outcome, String reason) {
            var metadata = new LinkedHashMap<String, Object>(Map.of("outcome", outcome, "reason", reason));
            metadata.put("consultRequestId", consultRequestId);
            trace.append(executionId, "guardResults", metadata);
            trace.stage(executionId, "guard", metadata);
        }

        private void rethrowIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
