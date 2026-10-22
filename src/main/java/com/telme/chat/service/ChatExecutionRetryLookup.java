package com.telme.chat.service;

import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.llm.service.UserRetryLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatExecutionRetryLookup implements UserRetryLookup {

    private final ChatExecutionRepository executions;

    @Override
    public boolean afterUserRetry(Long executionId) {
        return executions.findAttempt(executionId).map(attempt -> attempt > 1).orElse(false);
    }
}
