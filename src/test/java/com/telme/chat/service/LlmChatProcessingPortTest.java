package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class LlmChatProcessingPortTest {

    private static final Long EXECUTION_ID = 157L;
    private static final Long SESSION_ID = 42L;

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
    private final ChatEmitterRegistry emitterRegistry = mock(ChatEmitterRegistry.class);

    private final LlmChatProcessingPort port =
            new LlmChatProcessingPort(llmClient, chatExecutionService, emitterRegistry);

    private final ChatProcessingCommand command =
            new ChatProcessingCommand(EXECUTION_ID, SESSION_ID, 501L, "가까운 매장 알려줘");

    @BeforeEach
    void stubStartAnswer() {
        when(chatExecutionService.startAnswer(EXECUTION_ID)).thenReturn(
                new ChatOutputMessage(SESSION_ID, EXECUTION_ID, 502L, 2,
                        ChatMessage.MessageType.ANSWER, ChatMessage.Status.GENERATING));
        when(emitterRegistry.sendEvent(any(), any(), any())).thenReturn(true);
    }

    @Test
    void startsAnswerBeforeStreamingBeginsAndPushesStartEvent() {
        stubStreamThatEmits("강남");

        port.request(command);

        InOrder inOrder = inOrder(chatExecutionService, llmClient);
        inOrder.verify(chatExecutionService).startAnswer(EXECUTION_ID);
        inOrder.verify(llmClient).stream(any(), any());
        verify(emitterRegistry).sendEvent(eq(EXECUTION_ID), eq("start"), any(ChatOutputMessage.class));
    }

    @Test
    void buildsLlmRequestFromCommandContent() {
        stubStreamThatCompletesWithoutTokens();

        port.request(command);

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).stream(requestCaptor.capture(), any());
        LlmRequest sent = requestCaptor.getValue();
        assertThat(sent.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(sent.taskType()).isEqualTo(TaskType.RAG_ANSWER);
        assertThat(sent.userPrompt()).isEqualTo("가까운 매장 알려줘");
    }

    @Test
    void pushesEachTokenAsItArrives() {
        stubStreamThatEmits("강남", "역점입니다");

        port.request(command);

        verify(emitterRegistry).sendEvent(EXECUTION_ID, "token", "강남");
        verify(emitterRegistry).sendEvent(EXECUTION_ID, "token", "역점입니다");
    }

    @Test
    void completesAnswerWithAccumulatedContentAndNotifiesRegistry() {
        stubStreamThatEmits("강남", "역점입니다");
        ChatOutputMessage completed = new ChatOutputMessage(SESSION_ID, EXECUTION_ID, 502L, 2,
                ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED);
        when(chatExecutionService.completeAnswer(eq(EXECUTION_ID), any(ChatAnswer.class)))
                .thenReturn(completed);

        port.request(command);

        ArgumentCaptor<ChatAnswer> answerCaptor = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(chatExecutionService).completeAnswer(eq(EXECUTION_ID), answerCaptor.capture());
        assertThat(answerCaptor.getValue().content()).isEqualTo("강남역점입니다");
        verify(emitterRegistry).complete(EXECUTION_ID, completed);
    }

    @Test
    void failsExecutionAndNotifiesRegistryOnLlmError() {
        GeneralException timeout = new GeneralException(LlmErrorCode.TIMEOUT);
        stubStreamThatFails(timeout);

        port.request(command);

        ArgumentCaptor<ChatFailure> failureCaptor = ArgumentCaptor.forClass(ChatFailure.class);
        verify(chatExecutionService).fail(eq(EXECUTION_ID), failureCaptor.capture());
        assertThat(failureCaptor.getValue().status()).isEqualTo(ChatMessage.Status.FAILED);
        assertThat(failureCaptor.getValue().errorCode()).isEqualTo(LlmErrorCode.TIMEOUT.getCode());
        verify(emitterRegistry).fail(EXECUTION_ID, timeout.getMessage());
    }

    @Test
    void treatsEmptyAnswerAsFailureInsteadOfCrashing() {
        // LLM이 토큰을 하나도 내지 않고 onComplete만 부르면 ChatAnswer 생성 자체가 막힌다 — 이걸 실패로 전환해야 한다.
        stubStreamThatCompletesWithoutTokens();

        port.request(command);

        verify(chatExecutionService).fail(eq(EXECUTION_ID), any(ChatFailure.class));
        verify(emitterRegistry).fail(eq(EXECUTION_ID), any());
        verify(chatExecutionService, never()).completeAnswer(any(), any());
    }

    @Test
    void throwsLlmStreamCancelledExceptionWhenSseClientDisconnects() {
        when(emitterRegistry.sendEvent(eq(EXECUTION_ID), eq("token"), any())).thenReturn(false);

        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            try {
                handler.onToken("강남");
            } catch (com.telme.llm.exception.LlmStreamCancelledException e) {
                // 이 예외가 발생하면 성공
                handler.onError(e);
            }
            return null;
        }).when(llmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));

        port.request(command);

        verify(chatExecutionService).fail(eq(EXECUTION_ID), any(ChatFailure.class));
        verify(emitterRegistry).fail(eq(EXECUTION_ID), any());
    }

    @Test
    void clearsAccumulatedTokensWhenOnRetryIsCalled() {
        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            handler.onToken("실패할 ");
            handler.onToken("내용");
            handler.onRetry(1, new RuntimeException("Ollama Timeout"));
            handler.onToken("성공한 ");
            handler.onToken("답변");
            handler.onComplete();
            return null;
        }).when(llmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));

        ChatOutputMessage completed = new ChatOutputMessage(SESSION_ID, EXECUTION_ID, 502L, 2,
                ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED);
        when(chatExecutionService.completeAnswer(eq(EXECUTION_ID), any(ChatAnswer.class)))
                .thenReturn(completed);

        port.request(command);

        ArgumentCaptor<ChatAnswer> answerCaptor = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(chatExecutionService).completeAnswer(eq(EXECUTION_ID), answerCaptor.capture());
        
        // 재시도 이전에 모였던 "실패할 내용"은 지워지고 "성공한 답변"만 남아야 함
        assertThat(answerCaptor.getValue().content()).isEqualTo("성공한 답변");
    }

    private void stubStreamThatEmits(String... tokens) {
        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            for (String token : tokens) {
                handler.onToken(token);
            }
            handler.onComplete();
            return null;
        }).when(llmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));
    }

    private void stubStreamThatCompletesWithoutTokens() {
        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            handler.onComplete();
            return null;
        }).when(llmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));
    }

    private void stubStreamThatFails(Throwable error) {
        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            handler.onError(error);
            return null;
        }).when(llmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));
    }
}
