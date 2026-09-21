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
    private final LlmGenerationRecorder recorder;

    @Override
    public AnswerResult generate(AnswerRequest request, LlmStreamHandler handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");

        // 근거 없이 호출하면 모델이 지어내므로 여기서 차단
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
                .build();

        CollectingHandler collector = new CollectingHandler(handler);
        llmClient.stream(llmRequest, collector);
        collector.rethrowIfFailed();

        String answer = collector.answer();

        return AnswerResult.builder()
                .answer(answer)
                .answerBasis(toAnswerBasis(answer))
                // 모델이 실제로 참고한 근거를 알 수 없어 전달한 검색 결과 전부를 기록
                .sources(contextConverter.toSources(request.searchResults()))
                .build();
    }

    // 근거를 줬어도 모델이 답변 불가 문구를 내놓으면 근거 없음으로 본다
    private AnswerBasis toAnswerBasis(String answer) {
        return answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                ? AnswerBasis.NO_EVIDENCE
                : AnswerBasis.GROUNDED;
    }

    private AnswerResult answerWithoutEvidence(AnswerRequest request, LlmStreamHandler handler) {
        // LLM을 거치지 않아 RecordingLlmClient가 남길 수 없으므로 직접 기록
        recordNoEvidence(request);

        // 화면에도 같은 문구가 나가도록 handler로 전달
        handler.onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        handler.onComplete();

        return AnswerResult.builder()
                .answer(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                .answerBasis(AnswerBasis.NO_EVIDENCE)
                .build();
    }

    // 기록 저장 실패가 답변을 막지 않도록 여기서 차단
    private void recordNoEvidence(AnswerRequest request) {
        LlmRequest llmRequest = LlmRequest.builder()
                .executionId(request.executionId())
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt(request.userQuery())
                .contextCount(0)
                .build();
        try {
            recorder.record(llmRequest, null, LlmGenerationRecorder.Result.noEvidence());
        } catch (RuntimeException e) {
            log.warn("[RagAnswerGenerator] 근거 없음 기록 저장 실패 executionId={}", request.executionId(), e);
        }
    }

    // stream()이 값을 반환하지 않아 최종 답변을 얻기 위한 래퍼
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
            // 여기서 바로 던지면 LLM 클라이언트 내부에서 터지므로 보관 후 stream() 종료 뒤 전달
            failure = error instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(error);
            delegate.onError(error);
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            // 재시도는 처음부터 다시 생성이라 앞서 모은 토큰 폐기
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
