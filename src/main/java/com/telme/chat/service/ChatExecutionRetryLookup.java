package com.telme.chat.service;

import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.llm.service.UserRetryLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 실행의 시도 횟수가 2 이상이면 사용자가 다시 시도한 실행이다
@Component
@RequiredArgsConstructor
public class ChatExecutionRetryLookup implements UserRetryLookup {

    private final ChatExecutionRepository executions;

    @Override
    public boolean afterUserRetry(Long executionId) {
        return executions.findAttempt(executionId).map(attempt -> attempt > 1).orElse(false);
    }
}
