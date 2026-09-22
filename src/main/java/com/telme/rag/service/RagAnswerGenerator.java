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
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagAnswerGenerator implements AnswerGenerator {

    private final LlmClient llmClient;
    private final AnswerContextConverter contextConverter;
    private final AnswerGuard answerGuard;
    private final LlmGenerationRecorder recorder;

    @Override
    public AnswerResult generate(AnswerRequest request, LlmStreamHandler handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");

        // 근거 없이 호출하면 모델이 지어냄
        if (request.searchResults().isEmpty()) {
            return answerWithoutEvidence(request, handler);
        }

        String context = contextConverter.toContext(request.searchResults());

        // temperature, maxTokens는 TaskType별 기본값 사용
        LlmRequest llmRequest = LlmRequest.builder()
                .executionId(request.executionId())
                .taskType(TaskType.RAG_ANSWER)
                .systemPrompt(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT)
                .userPrompt(AnswerPromptTemplates.buildUserPrompt(request, context))
                .contextCount(request.searchResults().size())
                .promptVersion(AnswerPromptTemplates.PROMPT_VERSION)
                .build();

        CollectingHandler collector = new CollectingHandler(handler);
        llmClient.stream(llmRequest, collector);
        collector.rethrowIfFailed();

        String answer = answerGuard.trimAfterNoEvidence(collector.answer());
        answerGuard.verifyAmounts(answer, context, request.userQuery());

        return AnswerResult.builder()
                .answer(answer)
                .answerBasis(toAnswerBasis(answer))
                // 모델이 실제로 참고한 근거는 알 수 없어 전달한 검색 결과 전부를 기록
                .sources(contextConverter.toSources(request.searchResults()))
                .build();
    }

    private AnswerBasis toAnswerBasis(String answer) {
        return answer.startsWith(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
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

    // stream()이 값을 반환하지 않아 최종 답변을 모으기 위한 래퍼
    private static final class CollectingHandler implements LlmStreamHandler {

        private final LlmStreamHandler delegate;
        private final StringBuilder collected = new StringBuilder();
        private RuntimeException failure;

        private CollectingHandler(LlmStreamHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onToken(String token) {
            collected.append(token);
            delegate.onToken(token);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            // 여기서 바로 던지면 LLM 클라이언트 내부에서 터짐. 보관 후 stream() 종료 뒤 전달
            failure = error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(error);
            delegate.onError(error);
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            // 재시도는 처음부터 다시 생성. 앞서 모은 토큰 폐기
            collected.setLength(0);
            delegate.onRetry(attempt, cause);
        }

        private String answer() {
            return collected.toString();
        }

        private void rethrowIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
