package com.telme.store.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telme.store.entity.StoreServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

//등록과 수정이 같은 항목을 받는다. 상태는 받지 않는다 — 등록은 항상 OPEN, 폐점은 삭제 API로만 한다.
public record AdminStoreSaveRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 20) String phone,
        @NotNull @Pattern(regexp = "\\d{10}", message = "법정동코드는 숫자 10자리입니다.") String regionCode,
        @NotNull @DecimalMin("33.0") @DecimalMax("38.7") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("124.6") @DecimalMax("132.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @NotNull @Valid List<Hours> hours,
        @NotEmpty List<StoreServiceType.Code> serviceCodes,
        Integer lockVersion
        ) {
    public AdminStoreSaveRequest {
        name = name == null ? null : name.strip();
        address = address == null ? null : address.strip();
        phone = phone == null || phone.isBlank() ? null : phone.strip();
        regionCode = regionCode == null ? null : regionCode.strip();
    }
    
    // 여러 값을 함께 보는 규칙은 @AssertTrue로 둔다. 생성자에서 예외를 던지면 400이 나간다.
    // null이면 @NotNull이 알리도록 통과시킨다
    @JsonIgnore
    @AssertTrue(message = "영업시간은 월요일부터 일요일까지 7일을 한 번씩 입력해야 합니다.")
    public boolean isWeekComplete() {
        return hours == null || (hours.size() == 7
                                    && hours.stream().map(Hours::dayOfWeek).filter(Objects::nonNull).distinct().count() == 7);
    }
    
    @JsonIgnore
    @AssertTrue(message = "취급업무가 중복되었습니다.")
    public boolean isServiceCodesUnique() {
        return serviceCodes == null || serviceCodes.size() == new HashSet<>(serviceCodes).size();
    }
    
    public record Hours(
            @NotNull DayOfWeek dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed) {
        @JsonIgnore
        @AssertTrue(message = "휴무일은 시간이 없어야 하고, 영업일은 여는 시간이 닫는 시간보다 빨라야 합니다.")
        public boolean isTimeValid() {
            if(closed) {
                return openTime == null && closeTime == null;
            }
            return openTime != null && closeTime != null && openTime.isBefore(closeTime);
        }
    }
}
