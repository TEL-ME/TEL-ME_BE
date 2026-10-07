package com.telme.llm.service;

import com.telme.llm.converter.AdminLatencyConverter;
import com.telme.llm.dto.req.AdminLatencySearchRequest;
import com.telme.llm.dto.res.AdminLatencyResponse;
import com.telme.llm.repository.AdminLatencyRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminLatencyQueryService {

    private final AdminLatencyRepository adminLatencyRepository;
    private final AdminLatencyConverter converter;
    private final Clock clock;
    
    public AdminLatencyResponse getLatency(AdminLatencySearchRequest request) {
        Instant now = clock.instant();
        Instant from = request.fromOr(now);
        Instant to = request.toOr(now);
        return converter.toResponse(
                from, 
                to, 
                adminLatencyRepository.findExecutionStats(from, to), 
                adminLatencyRepository.findFirstTokenStats(from, to), 
                adminLatencyRepository.findTaskStats(from, to));
        
    }
}
