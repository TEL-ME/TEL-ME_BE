package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.AdminLlmErrorConverter;
import com.telme.llm.dto.req.AdminLlmErrorSearchRequest;
import com.telme.llm.dto.res.AdminLlmErrorListResponse;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.repository.LlmGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLlmErrorQueryService {

    private final LlmGenerationRepository llmGenerationRepository;
    private final AdminLlmErrorConverter converter;
    
    public AdminLlmErrorListResponse getErrors(AdminLlmErrorSearchRequest request) {
        // 시작이 끝보다 뒤면 결과가 늘 비어 조건을 잘못 넣은 것을 알아채기 어렵다
        if (request.periodReversed()) {
            throw new GeneralException(LlmErrorCode.INVALID_PERIOD);
        }
        return converter.toListResponse(llmGenerationRepository.findAdminErrors(
                request.errorType().toStatuses(), 
                request.taskTypes(), 
                request.fromOrMin(),
                request.toOrMax(),
                PageRequest.of(request.page(), request.size())));
    }
}
