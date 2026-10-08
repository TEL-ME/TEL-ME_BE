package com.telme.dashboard.service;

import com.telme.dashboard.converter.AdminIntentDistributionConverter;
import com.telme.dashboard.dto.req.AdminIntentDistributionSearchRequest;
import com.telme.dashboard.dto.res.AdminIntentDistributionResponse;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.repository.QueryRoutingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminIntentDistributionQueryService {

    private final QueryRoutingRepository queryRoutingRepository;
    private final AdminIntentDistributionConverter converter;

    public AdminIntentDistributionResponse getDistribution(AdminIntentDistributionSearchRequest request) {
        if (request.periodReversed()) {
            throw new GeneralException(DashboardErrorCode.INVALID_PERIOD);
        }
        Instant from = request.fromOrMin();
        Instant to = request.toOrMax();
        // 한 번의 그룹 집계 결과로 전체·의도·방식을 계산해 분모와 분자의 조회 시점을 맞춘다.
        return converter.toResponse(from, to, queryRoutingRepository.countDistribution(from, to));
    }
}
