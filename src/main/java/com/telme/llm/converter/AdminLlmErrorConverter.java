package com.telme.llm.converter;

import com.telme.llm.dto.res.AdminLlmErrorListItemResponse;
import com.telme.llm.dto.res.AdminLlmErrorListResponse;
import com.telme.llm.entity.LlmGeneration;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AdminLlmErrorConverter {
    
    public AdminLlmErrorListResponse toListResponse(Page<LlmGeneration> page) {
        return new AdminLlmErrorListResponse(
                page.getContent().stream().map(this::toListItem).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    
    private AdminLlmErrorListItemResponse toListItem(LlmGeneration generation) {
        return new AdminLlmErrorListItemResponse(
                generation.getGenerationId(),
                generation.getCreatedAt(),
                generation.getStatus().name(),
                generation.getTaskType().name(),
                generation.getAttempt(),
                generation.getModel(),
                generation.getTotalMs(),
                generation.getErrorMessage(),
                generation.getExecution().getExecutionId());
    }
}
