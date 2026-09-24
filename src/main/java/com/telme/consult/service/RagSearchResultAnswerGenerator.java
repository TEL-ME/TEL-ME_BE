package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.FaqSearchAnswerProvider.SearchResultAnswerGenerator;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.service.AnswerGenerator;

import java.util.List;
import java.util.Objects;

/** FAQ 검색 결과와 확정된 상담 조건을 RAG 답변 요청으로 변환한다. */
public final class RagSearchResultAnswerGenerator implements SearchResultAnswerGenerator {
    private final AnswerGenerator answers;
    private final StreamHandlerFactory streamHandlers;

    public RagSearchResultAnswerGenerator(
            AnswerGenerator answers, StreamHandlerFactory streamHandlers) {
        this.answers = Objects.requireNonNull(answers);
        this.streamHandlers = Objects.requireNonNull(streamHandlers);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, List<FaqSearchResponse> searchResults) {
        Objects.requireNonNull(input, "input");
        List<FaqSearchResponse> results =
                List.copyOf(Objects.requireNonNull(searchResults, "searchResults"));
        AnswerRequest request =
                AnswerRequest.builder()
                        .executionId(input.executionId())
                        .userQuery(input.originalUserQuery())
                        .conditions(input.confirmedConditions())
                        .searchResults(results)
                        .build();
        AnswerResult result =
                Objects.requireNonNull(
                        answers.generate(
                                request,
                                tokenOnlyHandler(
                                        Objects.requireNonNull(
                                                streamHandlers.create(input.executionId()),
                                                "streamHandler"))),
                        "answerResult");
        return new GeneratedAnswer(
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        result.answer(),
                        result.answerBasis(),
                        List.of(),
                        null),
                result.sources());
    }

    private LlmStreamHandler tokenOnlyHandler(LlmStreamHandler delegate) {
        return new LlmStreamHandler() {
            @Override
            public void onToken(String token) {
                delegate.onToken(token);
            }

            @Override
            public void onComplete() {
                // 답변·상담 상태 저장 뒤 ConsultChatProcessingService가 완료 이벤트를 보낸다.
            }

            @Override
            public void onError(Throwable error) {
                // 실패 상태 저장 뒤 ConsultChatProcessingService가 오류 이벤트를 보낸다.
            }

            @Override
            public void onRetry(int attempt, Throwable cause) {
                delegate.onRetry(attempt, cause);
            }
        };
    }

    /** SSE 구현과의 경계다. 최종 완료 이벤트는 답변과 상담 상태 저장 이후에 전송한다. */
    public interface StreamHandlerFactory {
        LlmStreamHandler create(long executionId);
    }
}
