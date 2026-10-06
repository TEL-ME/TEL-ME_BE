package com.telme.llm.service;

import com.telme.llm.converter.AdminLatencyConverter;
import com.telme.llm.dto.req.AdminLatencySearchRequest;
import com.telme.llm.dto.res.AdminLatencyResponse;
import com.telme.llm.repository.AdminLatencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLatencyQueryService {

    private final AdminLatencyRepository adminLatencyRepository;
    private final AdminLatencyConverter converter;
    
    public AdminLatencyResponse getLatency(AdminLatencySearchRequest request) {
        return converter.toResponse(
                request, 
                adminLatencyRepository.findExecutionStats(request.from(), request.to()), 
                adminLatencyRepository.findFirstTokenStats(request.from(), request.to()), 
                adminLatencyRepository.findTaskStats(request.from(), request.to()));
    }
}
