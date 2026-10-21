package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 선택지 대신 직접 입력한 나이 답을 요금제 대상 구분으로 읽는다. 둘 이상에 걸치거나 알 수 없으면 null
public final class AgeReplyParser {

    // 요금제 추천이 나이대를 되물을 때 쓰는 조건 이름
    public static final String CONDITION_KEY = "age_group";

    private static final int MAX_AGE = 120;

    // 나이 하나, 범위(30~40세), 범위 표현(65세 미만·이하·이상·초과)
    private static final Pattern EXACT_AGE = Pattern.compile(
            "(\\d{1,3})\\s*(?:(?:세|살)?\\s*(?:~|-|에서|부터)\\s*(\\d{1,3}))?\\s*(?:세|살)(?:\\s*(미만|이하|이상|초과))?");
    private static final Pattern DECADE = Pattern.compile("(\\d{1,2})\\s*대");
    private static final Pattern YOUTH_WORD = Pattern.compile("청소년|중학생|고등학생|중딩|고딩");
    private static final Pattern SENIOR_WORD = Pattern.compile("시니어|어르신|실버");
    private static final Pattern GENERAL_WORD = Pattern.compile("성인|대학생|직장인");

    private AgeReplyParser() {
    }

    public static Plan.TargetGroup parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        Set<Plan.TargetGroup> groups = new HashSet<>();
        Matcher exact = EXACT_AGE.matcher(reply);
        boolean found = false;
        while (exact.find()) {
            found = true;
            groups.add(groupOfAges(exact));
        }
        if (!found) {
            Matcher decade = DECADE.matcher(reply);
            while (decade.find()) {
                found = true;
                groups.add(groupOfDecade(Integer.parseInt(decade.group(1))));
            }
        }
        if (found) {
            return groups.size() == 1 ? groups.iterator().next() : null;
        }
        // 단어는 서로 다른 구분이 함께 나오면 어느 쪽인지 알 수 없어 읽지 않는다
        Set<Plan.TargetGroup> words = EnumSet.noneOf(Plan.TargetGroup.class);
        if (YOUTH_WORD.matcher(reply).find()) {
            words.add(Plan.TargetGroup.YOUTH);
        }
        if (SENIOR_WORD.matcher(reply).find()) {
            words.add(Plan.TargetGroup.SENIOR);
        }
        if (GENERAL_WORD.matcher(reply).find()) {
            words.add(Plan.TargetGroup.GENERAL);
        }
        return words.size() == 1 ? words.iterator().next() : null;
    }

    private static Plan.TargetGroup groupOfAges(Matcher exact) {
        int first = Integer.parseInt(exact.group(1));
        if (exact.group(2) != null) {
            int second = Integer.parseInt(exact.group(2));
            return groupOfRange(Math.min(first, second), Math.max(first, second));
        }
        if (exact.group(3) == null) {
            return groupOfRange(first, first);
        }
        return switch (exact.group(3)) {
            case "미만" -> groupOfRange(0, first - 1);
            case "이하" -> groupOfRange(0, first);
            case "이상" -> groupOfRange(first, MAX_AGE);
            default -> groupOfRange(first + 1, MAX_AGE);
        };
    }

    private static Plan.TargetGroup groupOfAge(int age) {
        if (age < SubscriptionEligibilityChecker.YOUTH_MIN_AGE) {
            return null;
        }
        if (age <= SubscriptionEligibilityChecker.YOUTH_MAX_AGE) {
            return Plan.TargetGroup.YOUTH;
        }
        return age >= SubscriptionEligibilityChecker.SENIOR_MIN_AGE ? Plan.TargetGroup.SENIOR : Plan.TargetGroup.GENERAL;
    }

    // 범위의 양 끝이 같은 구분일 때만 읽는다(구분은 나이순으로 이어져 있어 양 끝이 같으면 사이도 같다)
    private static Plan.TargetGroup groupOfRange(int from, int to) {
        if (from > to) {
            return null;
        }
        Plan.TargetGroup first = groupOfAge(from);
        return first != null && first == groupOfAge(to) ? first : null;
    }

    // 10살 단위가 한 구분 안에 모두 들어갈 때만 읽는다(10대는 청소년과 일반에, 60대는 일반과 시니어에 걸친다)
    private static Plan.TargetGroup groupOfDecade(int decade) {
        if (decade % 10 != 0 || decade < 10) {
            return null;
        }
        return groupOfRange(decade, decade + 9);
    }
}
