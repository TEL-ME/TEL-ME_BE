package com.telme.store.service;

import com.telme.store.converter.StoreCommonConverter;
import com.telme.store.dto.res.StoreHoursResponse;
import com.telme.store.entity.StoreHours;
import com.telme.store.repository.StoreRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DB에 있는 요일만 반환(빠진 요일은 휴무가 아님)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreHoursQueryService {

    private final StoreRepository storeRepository;
    private final StoreCommonConverter converter;

    public Map<Long, List<StoreHoursResponse>> hoursOf(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<StoreHours>> grouped = new LinkedHashMap<>();
        for (StoreHours hours : storeRepository.findHoursByStoreIds(storeIds)) {
            grouped.computeIfAbsent(hours.getId().getStoreId(), id -> new java.util.ArrayList<>()).add(hours);
        }
        Map<Long, List<StoreHoursResponse>> result = new LinkedHashMap<>();
        grouped.forEach((storeId, rows) -> result.put(storeId, converter.toHours(rows)));
        return result;
    }
}
