package com.telme.rag.service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 기존 수치 검사가 읽지 않는 주 단위를 일수로 비교한다. 상품/여행 기간의 역할과 부정은 모델이 판정한다.
final class ComparisonClaimPrecheck {

    private static final Pattern PERIOD = Pattern.compile(
            "(?<![0-9])(?:일주일|한\\s*주|열흘|([1-9][0-9]{0,2})\\s*(일|주))");
    private static final Pattern WEEK = Pattern.compile("(?<![0-9])([1-9][0-9]{0,2})\\s*주");

    private ComparisonClaimPrecheck() {}

    static boolean hasUngroundedWeekMeasure(String sentence, String evidence) {
        Set<Integer> days = new HashSet<>();
        Matcher source = PERIOD.matcher(evidence);
        while (source.find()) {
            int duration = source.group().equals("열흘") ? 10
                    : source.group(1) == null ? 7
                    : Integer.parseInt(source.group(1)) * (source.group(2).equals("주") ? 7 : 1);
            days.add(duration);
        }
        Matcher answer = WEEK.matcher(sentence);
        while (answer.find()) {
            int duration = Integer.parseInt(answer.group(1)) * 7;
            if (!days.contains(duration)) {
                return true;
            }
        }
        return false;
    }
}
