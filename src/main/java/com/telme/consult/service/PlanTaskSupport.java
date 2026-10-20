package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// 요금제 업무들이 함께 쓰는 요금제 이름 찾기, 조사, 시각 표기
final class PlanTaskSupport {

    static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    static final String MY_PLAN_QUESTION = "내 요금제 알려줘";
    static final String LOGIN_REQUIRED = "로그인한 뒤 마이페이지의 내 요금제 화면에서 할 수 있어요.";
    static final String FAILED = "지금은 안내하지 못했어요. 마이페이지의 내 요금제 화면에서 진행해 주세요.";

    // 직전 추천을 가리키는 말. 띄어쓰기를 지운 문장에서 찾는다
    private static final Pattern REFERENCE = Pattern.compile("[그이저](요금제|플랜|걸|거|것)|추천(해준|한|받은)(걸|거|것|요금제)|방금|아까");
    private static final Pattern OTHER_PLAN = Pattern.compile("다른|딴");

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("M월 d일 H시", Locale.KOREAN);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);

    private PlanTaskSupport() {
    }

    // 문맥으로 풀어쓴 문장에는 앞 대화의 다른 요금제가 섞일 수 있어 사용자가 쓴 원문에 이름이 없을 때만 본다
    static List<Plan> namedPlans(List<Plan> selling, String original, String resolved) {
        List<Plan> named = mentionedPlans(selling, original);
        return named.isEmpty() ? mentionedPlans(selling, resolved) : named;
    }

    // 띄어쓰기와 대소문자를 무시하고, 음성 인식이 소리 나는 대로 적은 "오지"·"엘티이"도 5G·LTE로 읽는다
    // 원문에 이름이 없을 때 "그 요금제", "그걸로"처럼 추천을 가리키면 직전 추천 요금제를 풀어쓴 문장보다 먼저 쓴다.
    // "다른 요금제로"는 추천과 다른 요금제를 고르겠다는 말이라 대상을 정하지 않는다. 그 밖에는 풀어쓴 문장, 직전 추천 순서로 찾는다
    static List<Plan> namedPlans(List<Plan> selling, String original, String resolved, String recommended) {
        List<Plan> named = mentionedPlans(selling, original);
        if (!named.isEmpty()) {
            return named;
        }
        String asked = original == null ? "" : original.replaceAll("\\s+", "");
        boolean refersToRecommendation = REFERENCE.matcher(asked).find();
        if (!refersToRecommendation && OTHER_PLAN.matcher(asked).find()) {
            return List.of();
        }
        if (refersToRecommendation) {
            List<Plan> fromRecommendation = mentionedPlans(selling, recommended);
            if (!fromRecommendation.isEmpty()) {
                return fromRecommendation;
            }
        }
        List<Plan> fromContext = mentionedPlans(selling, resolved);
        return fromContext.isEmpty() ? mentionedPlans(selling, recommended) : fromContext;
    }

    static List<Plan> mentionedPlans(List<Plan> selling, String... texts) {
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (text != null) {
                joined.append(normalize(text)).append('|');
            }
        }
        String haystack = joined.toString();
        List<Plan> found = new ArrayList<>();
        for (Plan plan : selling) {
            if (haystack.contains(normalize(plan.getName()))) {
                found.add(plan);
            }
        }
        // "5G 라이트"를 말했을 때 이름이 그 안에 들어가는 다른 요금제까지 함께 잡히지 않도록 가장 긴 이름만 남긴다
        Set<Plan> distinct = new LinkedHashSet<>(found);
        distinct.removeIf(plan -> found.stream().anyMatch(other -> other != plan
                && normalize(other.getName()).contains(normalize(plan.getName()))));
        return List.copyOf(distinct);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).replace("오지", "5g").replace("엘티이", "lte");
    }

    // 받침이 없거나 ㄹ 받침이면 "로", 그 밖의 받침이면 "으로". 한글이 아닌 글자로 끝나면 "로"
    static String directionParticle(String word) {
        Integer finalConsonant = finalConsonant(word);
        return finalConsonant == null || finalConsonant == 0 || finalConsonant == 8 ? "로" : "으로";
    }

    // 받침이 있으면 "은", 없으면 "는"("5G 프리미엄은", "LTE 맥스는"). 한글이 아닌 글자로 끝나면 "는"
    static String topicParticle(String word) {
        Integer finalConsonant = finalConsonant(word);
        return finalConsonant == null || finalConsonant == 0 ? "는" : "은";
    }

    // 받침이 있으면 "을", 없으면 "를". 한글이 아닌 글자로 끝나면 "를"
    static String objectParticle(String word) {
        Integer finalConsonant = finalConsonant(word);
        return finalConsonant == null || finalConsonant == 0 ? "를" : "을";
    }

    private static Integer finalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        return last < '가' || last > '힣' ? null : (last - '가') % 28;
    }

    static String hour(Instant at) {
        return HOUR.format(at.atZone(KOREA));
    }

    static String day(Instant at) {
        return DAY.format(at.atZone(KOREA));
    }
}
