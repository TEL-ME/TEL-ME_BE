package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 선택지 대신 직접 입력한 나이 답을 요금제 대상 구분으로 읽는다. 둘 이상에 걸치거나 알 수 없으면 null
final class AgeReplyParser {

    private static final Pattern EXACT_AGE = Pattern.compile("(\\d{1,3})\\s*(?:세|살)");
    private static final Pattern DECADE = Pattern.compile("(\\d{1,2})\\s*대");
    private static final Pattern YOUTH_WORD = Pattern.compile("청소년|중학생|고등학생|중딩|고딩");
    private static final Pattern SENIOR_WORD = Pattern.compile("시니어|어르신|실버");
    private static final Pattern GENERAL_WORD = Pattern.compile("성인|대학생|직장인");

    private AgeReplyParser() {
    }

    static Plan.TargetGroup parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        Matcher exact = EXACT_AGE.matcher(reply);
        if (exact.find()) {
            return groupOfAge(Integer.parseInt(exact.group(1)));
        }
        Matcher decade = DECADE.matcher(reply);
        if (decade.find()) {
            return groupOfDecade(Integer.parseInt(decade.group(1)));
        }
        if (YOUTH_WORD.matcher(reply).find()) {
            return Plan.TargetGroup.YOUTH;
        }
        if (SENIOR_WORD.matcher(reply).find()) {
            return Plan.TargetGroup.SENIOR;
        }
        return GENERAL_WORD.matcher(reply).find() ? Plan.TargetGroup.GENERAL : null;
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

    // 10살 단위가 한 구분 안에 모두 들어갈 때만 읽는다(10대는 청소년과 일반에, 60대는 일반과 시니어에 걸친다)
    private static Plan.TargetGroup groupOfDecade(int decade) {
        if (decade % 10 != 0 || decade < 10) {
            return null;
        }
        Plan.TargetGroup first = groupOfAge(decade);
        Plan.TargetGroup last = groupOfAge(decade + 9);
        return first != null && first == last ? first : null;
    }
}
