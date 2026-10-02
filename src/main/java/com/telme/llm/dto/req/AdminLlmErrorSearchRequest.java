package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record AdminLlmErrorSearchRequest(
        AdminLlmErrorType errorType,
        LlmGeneration.TaskType taskType,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final List<LlmGeneration.TaskType> EVERY_TASK_TYPE = List.of(LlmGeneration.TaskType.values());
    
    public AdminLlmErrorSearchRequest {
        errorType = errorType == null ? AdminLlmErrorType.ALL : errorType;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
    
    public List<LlmGeneration.TaskType> taskTypes() {
        return taskType == null ? EVERY_TASK_TYPE : List.of(taskType);
    }
}
