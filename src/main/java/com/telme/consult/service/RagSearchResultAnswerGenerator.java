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
    /**
     * FAQ 검색 결과가 하나도 없을 때 보내는 안내다. 통신 질문인데 FAQ에 없는 경우와 통신과 무관한
     * 질문이 FAQ로 분류된 경우를 구분하지 않고, 어느 쪽이든 다음 질문 방법을 알려준다.
     */
    public static final String NO_SEARCH_RESULT_ANSWER =
            "관련 안내 정보를 찾지 못했습니다. 통신 서비스나 매장 관련 질문이라면 조금 더 구체적으로 알려주세요.";

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
        LlmStreamHandler stream =
                input.streamTokens()
                        ? Objects.requireNonNull(
                                streamHandlers.create(input.executionId()), "streamHandler")
                        : discardedTokens();
        // 단일 질문에서 검색 결과가 없으면 RAG의 기존 처리(근거 없음 기록)는 그대로 거치고,
        // 사용자에게 보내는 문구만 바꾼다. 복합 질문은 하위 질문별 안내를 따로 만든다.
        boolean noSearchResult = input.streamTokens() && results.isEmpty();
        AnswerResult result =
                Objects.requireNonNull(
                        answers.generate(
                                request,
                                tokenOnlyHandler(noSearchResult ? discardedTokens() : stream)),
                        "answerResult");
        String content = result.answer();
        if (noSearchResult) {
            content = NO_SEARCH_RESULT_ANSWER;
            stream.onToken(content);
        }
        return new GeneratedAnswer(
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        content,
                        result.answerBasis(),
                        List.of(),
                        null),
                result.sources());
    }

    private LlmStreamHandler discardedTokens() {
        return new LlmStreamHandler() {
            @Override
            public void onToken(String token) {}

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable error) {}
        };
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
