package com.telme.consult.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StoreHoursQuestion {

    private static final Pattern HOURS_WORD = Pattern.compile(
            "영업시간|영업일|영업(?:중|해|하|합)|몇시|열어|열려|열지|열었|열까|닫아|닫는|닫았|닫혀|휴무|쉬는|쉬어|언제까지|마감|문(?:여|닫)");
    private static final Pattern WEEKDAY_NAME = Pattern.compile("([월화수목금토일])요일");
    private static final Pattern NOW_WORD = Pattern.compile("지금|현재|당장|열었|열려있|영업중|영업해요");

    // days가 비면 지금 영업 여부
    record Request(List<DayOfWeek> days, boolean now, String label) {
    }

    private StoreHoursQuestion() {
    }

    static Optional<Request> parse(String question, LocalDate today) {
        if (question == null) {
            return Optional.empty();
        }
        String text = question.replaceAll("\\s+", "");
        if (!HOURS_WORD.matcher(text).find()) {
            return Optional.empty();
        }
        List<DayOfWeek> named = new ArrayList<>();
        Matcher matcher = WEEKDAY_NAME.matcher(text);
        while (matcher.find()) {
            DayOfWeek day = dayOf(matcher.group(1).charAt(0));
            if (!named.contains(day)) {
                named.add(day);
            }
        }
        if (text.contains("주말")) {
            addAll(named, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
        if (text.contains("평일")) {
            addAll(named, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        if (!named.isEmpty()) {
            named.sort(java.util.Comparator.naturalOrder());
            return Optional.of(new Request(List.copyOf(named), false, label(named)));
        }
        if (text.contains("모레")) {
            return Optional.of(single(today.plusDays(2).getDayOfWeek(), "모레"));
        }
        if (text.contains("내일")) {
            return Optional.of(single(today.plusDays(1).getDayOfWeek(), "내일"));
        }
        if (NOW_WORD.matcher(text).find()) {
            return Optional.of(new Request(List.of(), true, "지금"));
        }
        return Optional.of(single(today.getDayOfWeek(), "오늘"));
    }

    private static Request single(DayOfWeek day, String label) {
        return new Request(List.of(day), false, label);
    }

    private static void addAll(List<DayOfWeek> days, DayOfWeek... added) {
        for (DayOfWeek day : added) {
            if (!days.contains(day)) {
                days.add(day);
            }
        }
    }

    private static DayOfWeek dayOf(char name) {
        return switch (name) {
            case '월' -> DayOfWeek.MONDAY;
            case '화' -> DayOfWeek.TUESDAY;
            case '수' -> DayOfWeek.WEDNESDAY;
            case '목' -> DayOfWeek.THURSDAY;
            case '금' -> DayOfWeek.FRIDAY;
            case '토' -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    static String dayName(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private static String label(List<DayOfWeek> days) {
        if (days.size() == 1) {
            return dayName(days.getFirst()) + "요일";
        }
        if (days.equals(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) {
            return "주말";
        }
        if (days.equals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))) {
            return "평일";
        }
        return String.join("·", days.stream().map(StoreHoursQuestion::dayName).toList()) + "요일";
    }
}
