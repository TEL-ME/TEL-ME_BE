package com.telme.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.converter.ChatExecutionTraceConverter;
import com.telme.chat.dto.res.ChatExecutionTraceResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionTraceRepository;
import com.telme.global.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatExecutionTraceService implements ExecutionTrace {
    private final ChatExecutionTraceRepository repository;
    private final ObjectMapper mapper;
    private final ChatSessionService sessions;
    private final ChatExecutionTraceConverter converter;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stage(Long executionId, String stage, Object value) {
        write(executionId, stage, value, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(Long executionId, String stage, Object value) {
        write(executionId, stage, value, true);
    }

    private void write(Long executionId, String stage, Object value, boolean append) {
        if (executionId == null) return;
        try {
            String json = mapper.writeValueAsString(value);
            int changed = repository.updateStage(executionId, stage, json, append);
            if (changed != 1) log.warn("Execution trace target unavailable executionId={} stage={}",
                    executionId, stage);
        } catch (RuntimeException | JsonProcessingException failure) {
            // 추적 저장 실패가 답변 처리에 영향을 주거나 사용자 값·DB 예외 본문을 로그에 남기지 않도록 한다.
            log.warn("Execution trace write failed executionId={} stage={} type={}",
                    executionId, stage, failure.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public ChatExecutionTraceResponse get(ChatActor actor, long sessionId, long executionId) {
        // 기록을 읽기 전에 SSE 구독과 동일한 회원·게스트 소유권을 확인한다.
        var execution = sessions.getExecution(actor, executionId);
        if (execution.sessionId() != sessionId) {
            throw new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND);
        }
        var row = repository.findExecution(sessionId, executionId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
        var routing = repository.findRouting((Long) row.get("inputMessageId"));
        var attempts = repository.findModelAttempts(executionId);
        return converter.toResponse(row, routing, attempts);
    }
}
