package com.telme.store.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.dto.res.StoreHoursResponse;
import com.telme.store.dto.res.StoreServiceTypeResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreHours;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreCommonConverterTest {

    private final StoreCommonConverter converter = new StoreCommonConverter();
    private final Store store = Store.builder().storeId(101L).build();

    @Test
    @DisplayName("영업시간은 월요일부터 순서대로 바꾸고 휴무일은 시간 없이 closed로 둔다")
    void 영업시간_변환() {
        List<StoreHoursResponse> hours = converter.toHours(List.of(
                hours(DayOfWeek.SUNDAY, null, null, true),
                hours(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(19, 0), false)));

        assertThat(hours).containsExactly(
                new StoreHoursResponse("MONDAY", LocalTime.of(10, 0), LocalTime.of(19, 0), false),
                new StoreHoursResponse("SUNDAY", null, null, true));
    }

    @Test
    @DisplayName("DB에 없는 요일은 휴무로 채우지 않는다")
    void 없는_요일은_채우지_않는다() {
        List<StoreHoursResponse> hours = converter.toHours(List.of(
                hours(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false)));

        assertThat(hours).extracting(StoreHoursResponse::dayOfWeek).containsExactly("WEDNESDAY");
    }

    @Test
    @DisplayName("업무는 enum 선언 순서가 아니라 업무 ID 순서로 바꾼다")
    void 업무_ID_순서() {
        List<StoreServiceTypeResponse> services = converter.toServiceTypes(List.of(
                service(StoreServiceType.Code.NEW_LINE, "신규가입", 4L),
                service(StoreServiceType.Code.USIM_REISSUE, "유심재발급", 1L)));

        assertThat(services).containsExactly(
                new StoreServiceTypeResponse("USIM_REISSUE", "유심재발급"),
                new StoreServiceTypeResponse("NEW_LINE", "신규가입"));
    }

    private StoreHours hours(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        return StoreHours.builder()
                .id(new StoreHours.Id(store.getStoreId(), (short) day.getValue()))
                .store(store)
                .openTime(open)
                .closeTime(close)
                .closed(closed)
                .build();
    }

    private StoreService service(StoreServiceType.Code code, String name, long serviceTypeId) {
        StoreServiceType type = StoreServiceType.builder()
                .serviceTypeId(serviceTypeId)
                .code(code)
                .name(name)
                .build();
        return StoreService.builder()
                .id(new StoreService.Id(store.getStoreId(), serviceTypeId))
                .store(store)
                .serviceType(type)
                .build();
    }
}
