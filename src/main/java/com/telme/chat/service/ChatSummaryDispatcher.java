package com.telme.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
class ChatSummaryDispatcher {

    private final ChatSummaryService chatSummaryService;
    private final ChatBackgroundTaskExecutor backgroundTaskExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSummaryRequested request) {
        try {
            backgroundTaskExecutor.execute(() -> summarize(request));
        } catch (TaskRejectedException exception) {
            log.warn("상담 요약 요청을 대기열에 추가하지 못함: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }

    private void summarize(ChatSummaryRequested request) {
        try {
            chatSummaryService.summarizeIfNeeded(request);
        } catch (RuntimeException exception) {
            log.warn("상담 요약 생성 실패, 다음 완료 요청에서 재시도: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }
}
