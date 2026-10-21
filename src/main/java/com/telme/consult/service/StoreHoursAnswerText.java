package com.telme.consult.service;

import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.store.dto.res.StoreHoursResponse;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StoreHoursAnswerText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private StoreHoursAnswerText() {
    }

    // 영업시간을 읽은 매장이 없으면 빈 문자열
    static String of(StoreHoursQuestion.Request request, List<ChatStoreResponse> stores,
            Map<Long, List<StoreHoursResponse>> hoursByStore, LocalDateTime now) {
        StringBuilder lines = new StringBuilder();
        boolean anyHours = false;
        for (ChatStoreResponse store : stores) {
            List<StoreHoursResponse> hours = hoursByStore.getOrDefault(store.storeId(), List.of());
            anyHours |= !hours.isEmpty();
            lines.append("\n- ").append(store.name()).append(": ").append(describe(request, hours, now));
        }
        if (!anyHours) {
            return "";
        }
        String header = request.now() ? "지금 영업 여부예요." : request.label() + " 영업시간이에요.";
        return "\n\n" + header + lines;
    }

    private static String describe(StoreHoursQuestion.Request request, List<StoreHoursResponse> hours, LocalDateTime now) {
        if (hours.isEmpty()) {
            return "영업시간 정보가 없어요";
        }
        if (request.now()) {
            return nowStatus(find(hours, now.getDayOfWeek()), now.toLocalTime());
        }
        if (request.days().size() == 1) {
            return dayText(find(hours, request.days().getFirst()));
        }
        return String.join(", ", request.days().stream()
                .map(day -> StoreHoursQuestion.dayName(day) + " " + dayText(find(hours, day))).toList());
    }

    private static Optional<StoreHoursResponse> find(List<StoreHoursResponse> hours, DayOfWeek day) {
        return hours.stream().filter(row -> day.name().equals(row.dayOfWeek())).findFirst();
    }

    private static String dayText(Optional<StoreHoursResponse> day) {
        if (day.isEmpty()) {
            return "정보가 없어요";
        }
        StoreHoursResponse row = day.get();
        if (row.closed() || row.openTime() == null || row.closeTime() == null) {
            return "휴무";
        }
        return row.openTime().format(TIME) + "~" + row.closeTime().format(TIME);
    }

    private static String nowStatus(Optional<StoreHoursResponse> day, LocalTime time) {
        if (day.isEmpty()) {
            return "오늘 영업시간 정보가 없어요";
        }
        StoreHoursResponse row = day.get();
        if (row.closed() || row.openTime() == null || row.closeTime() == null) {
            return "오늘은 휴무예요";
        }
        boolean open = !time.isBefore(row.openTime()) && time.isBefore(row.closeTime());
        return open ? "지금 영업 중이에요(" + row.closeTime().format(TIME) + "까지)"
                : "지금은 영업 시간이 아니에요(오늘 " + row.openTime().format(TIME) + "~" + row.closeTime().format(TIME) + ")";
    }
}
