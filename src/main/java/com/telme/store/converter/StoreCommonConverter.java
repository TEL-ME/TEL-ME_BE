package com.telme.store.converter;

import com.telme.store.dto.res.StoreHoursResponse;
import com.telme.store.dto.res.StoreServiceTypeResponse;
import com.telme.store.entity.StoreHours;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

// 관리자·지역 검색·사용자 API가 같은 업무·영업시간 표기를 쓰도록 변환을 한곳에 둔다
@Component
public class StoreCommonConverter {

    // 조회할 때마다 업무 표시 순서가 바뀌지 않도록 id 순으로 고정한다
    public List<StoreServiceTypeResponse> toServiceTypes(Collection<StoreService> services) {
        return services.stream()
                .map(StoreService::getServiceType)
                .sorted(Comparator.comparing(StoreServiceType::getServiceTypeId))
                .map(this::toServiceType)
                .toList();
    }

    public StoreServiceTypeResponse toServiceType(StoreServiceType type) {
        return new StoreServiceTypeResponse(type.getCode().name(), type.getName());
    }

    // DB에 있는 요일만 반환한다. 빠진 요일을 휴무로 채우면 실제 휴무와 데이터 없음이 구분되지 않는다.
    // 7일 보장은 매장 등록·수정 API에서 검증한다.
    public List<StoreHoursResponse> toHours(Collection<StoreHours> hours) {
        return hours.stream()
                .sorted(Comparator.comparing((StoreHours day) -> day.getId().getDayOfWeek()))
                .map(day -> new StoreHoursResponse(
                        DayOfWeek.of(day.getId().getDayOfWeek()).name(),
                        day.getOpenTime(),
                        day.getCloseTime(),
                        day.isClosed()))
                .toList();
    }
}
