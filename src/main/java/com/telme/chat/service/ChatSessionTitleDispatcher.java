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
class ChatSessionTitleDispatcher {

    private final ChatSessionTitleService titleService;
    private final ChatBackgroundTaskExecutor backgroundTaskExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSessionTitleRequested request) {
        try {
            backgroundTaskExecutor.execute(() -> generate(request));
        } catch (TaskRejectedException exception) {
            log.warn("세션 제목 생성 요청을 대기열에 추가하지 못함: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }

    private void generate(ChatSessionTitleRequested request) {
        try {
            titleService.generateIfMissing(request);
        } catch (RuntimeException exception) {
            log.warn("세션 제목 자동 생성 실패: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }
}
