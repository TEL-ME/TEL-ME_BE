package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;

public record AdminLlmErrorSearchRequest(
        AdminLlmErrorType errorType,
        LlmGeneration.TaskType taskType,
        Instant from,
        Instant to,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final List<LlmGeneration.TaskType> EVERY_TASK_TYPE = List.of(LlmGeneration.TaskType.values());
    
    private static final Instant EVERY_TIME_FROM = Instant.EPOCH;
    private static final Instant EVERY_TIME_TO = Instant.parse("9999-12-31T23:59:59Z"); 
    
    public AdminLlmErrorSearchRequest {
        errorType = errorType == null ? AdminLlmErrorType.ALL : errorType;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
    
    public List<LlmGeneration.TaskType> taskTypes() {
        return taskType == null ? EVERY_TASK_TYPE : List.of(taskType);
    }
    
    public Instant fromOrMin() {
        return from == null ? EVERY_TIME_FROM : from;
    }

    // to는 그 시각 직전까지다
    public Instant toOrMax() {
        return to == null ? EVERY_TIME_TO : to;
    }
    
    public boolean periodReversed() {
        return from != null && to != null && from.isAfter(to);
    }
}
