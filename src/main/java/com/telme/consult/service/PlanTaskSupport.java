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

// 요금제 업무들이 함께 쓰는 요금제 이름 찾기, 조사, 시각 표기
final class PlanTaskSupport {

    static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    static final String MY_PLAN_QUESTION = "내 요금제 알려줘";
    static final String LOGIN_REQUIRED = "로그인한 뒤 마이페이지의 내 요금제 화면에서 할 수 있어요.";
    static final String FAILED = "지금은 안내하지 못했어요. 마이페이지의 내 요금제 화면에서 진행해 주세요.";

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
