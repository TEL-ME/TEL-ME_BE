package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.llm.exception.LlmStreamCancelledException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// ChatProcessingPort의 실제 구현체. LlmClient(재시도·기록 체인)를 호출해 답변을 만들고,
// 진행 상황을 ChatExecutionService(DB 상태)와 ChatEmitterRegistry(구독 중인 SSE 연결)에 함께 반영한다.
// 라우팅/검색(RAG)이 아직 없어 모든 질문을 taskType=RAG_ANSWER로 고정 호출한다 — 실제 인텐트 분류가
// 붙으면 이 부분을 교체해야 한다.
@Slf4j
@RequiredArgsConstructor
public class LlmChatProcessingPort implements ChatProcessingPort {

    private static final String PROCESSING_ERROR_CODE = "AI_PROCESSING_ERROR";

    private final LlmClient llmClient;
    private final ChatExecutionService chatExecutionService;
    private final ChatEmitterRegistry emitterRegistry;

    @Override
    public void request(ChatProcessingCommand command) {
        Long executionId = command.executionId();
        try {
            ChatOutputMessage started = chatExecutionService.startAnswer(executionId);
            emitterRegistry.sendEvent(executionId, "start", started);

            LlmRequest request = LlmRequest.builder()
                    .executionId(executionId)
                    // 라우팅 / 검색기능 추가후 반영 필요
                    .taskType(TaskType.RAG_ANSWER)
                    .userPrompt(command.content())
                    .build();

            StringBuilder content = new StringBuilder();
            llmClient.stream(request, new LlmStreamHandler() {
                @Override
                public void onToken(String token) {
                    content.append(token);
                    boolean isConnected = emitterRegistry.sendEvent(executionId, "token", token);
                    if (!isConnected) {
                        throw new LlmStreamCancelledException();
                    }
                }

                @Override
                public void onRetry(int attempt, Throwable cause) {
                    content.setLength(0);
                }

                @Override
                public void onComplete() {
                    try {
                        ChatAnswer answer = new ChatAnswer(
                                ChatMessage.MessageType.ANSWER, content.toString(), null, null, null);
                        ChatOutputMessage output = chatExecutionService.completeAnswer(executionId, answer);
                        emitterRegistry.complete(executionId, output);
                    } catch (RuntimeException exception) {
                        // 빈 답변 등으로 ChatAnswer 생성 자체가 실패하면 실패 처리로 이어간다.
                        onError(exception);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("AI 응답 생성 실패: executionId={}", executionId, error);
                    try {
                        chatExecutionService.fail(executionId,
                                new ChatFailure(ChatMessage.Status.FAILED, toErrorCode(error)));
                    } finally {
                        // DB 업데이트 중 에러가 나더라도 클라이언트 연결은 반드시 종료
                        emitterRegistry.fail(executionId, error.getMessage());
                    }
                }
            });
        } catch (RuntimeException exception) {
            log.error("AI 요청 초기화 중 동기적 오류: executionId={}", executionId, exception);
            // 디스패처로 예외가 전파되기 전에 SSE 클라이언트 연결부터 정리
            emitterRegistry.fail(executionId, exception.getMessage());
            throw exception;
        }
    }

    private String toErrorCode(Throwable error) {
        if (error instanceof GeneralException general) {
            return general.getErrorCode().getCode();
        }
        return PROCESSING_ERROR_CODE;
    }
}
