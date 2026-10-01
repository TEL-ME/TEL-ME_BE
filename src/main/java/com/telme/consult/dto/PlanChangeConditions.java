package com.telme.consult.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 가입월·당월 변경 여부만 다룬다. 계정 조회나 날짜 추정으로 값을 채우지 않는다. */
public final class PlanChangeConditions {
    public static final String JOINED = "joinedThisMonth";
    public static final String CHANGED = "changedThisMonth";
    public static final Set<String> KEYS = Set.of(JOINED, CHANGED);
    public static final String YES = "예";
    public static final String NO = "아니요";

    private PlanChangeConditions() {}

    public static boolean isPersonalQuestion(String query) {
        String text = compact(query);
        if (Pattern.compile("만약|가정|(?:가입|개통)(?:하면|한다면|했다면)").matcher(text).find()) return false;
        if (Pattern.compile("친구|부모|가족|동생|다른사람").matcher(text).find()
                && !Pattern.compile("제가|저는|나는").matcher(text).find()) return false;
        return text.contains("요금제") && (text.contains("변경") || text.contains("바꿀") || text.contains("바꾸") || text.contains("바꿔"))
                && Pattern.compile("제가|나는|저는|지금|이번달|이달|가입했").matcher(text).find()
                && !Pattern.compile("각각|경우별|나눠|시니어|청소년|약정|위약금").matcher(text).find();
    }

    public static boolean isCriteriaQuestion(String query) {
        String text = compact(query);
        return !isPersonalQuestion(query) && text.contains("요금제")
                && Pattern.compile("변경|바꿀|바꾸|바꿔").matcher(text).find()
                && Pattern.compile("기준|조건|횟수|몇번|언제").matcher(text).find()
                && !Pattern.compile("시니어|청소년|약정|위약금|로밍|유심|명의|배송").matcher(text).find();
    }

    public static boolean isDeferred(String reply) {
        return isClarificationRequest(reply) || Pattern.compile("잠깐|잠시|이따|나중|기다|보류").matcher(compact(reply)).find();
    }

    public static boolean isAmbiguous(String reply) {
        return Pattern.compile("애매|헷갈|것같|일지도|수도|인지.*인지|네[,，]아니요|친구|가족|부모|동생|만약|가정|(?:가입|개통|변경|바꾸)(?:하면|한다면|했다면)")
                .matcher(compact(reply)).find();
    }

    // 기존 설명 생성 기능은 없다. 현재 조건 설명 요청은 새 상담·추출 실패로 소비하지 않는다.
    public static boolean isClarificationRequest(String reply) {
        String text = compact(reply);
        return Pattern.compile("가입한달|가입월|가입여부|변경이력|변경여부").matcher(text).find()
                && Pattern.compile("무슨뜻|무엇을뜻|의미|설명").matcher(text).find()
                && !Pattern.compile("유심|로밍|매장|명의|청구|배송").matcher(text).find();
    }

    public static boolean isUnavailable(String reply) {
        return Pattern.compile("모르|몰라|모름|안알려|알려주고싶지않|말하고싶지않|알려주기싫|말하기싫|거절|건너뛰|생략|스킵")
                .matcher(compact(reply)).find();
    }

    private static String[] clauses(String reply) {
        return (reply == null ? "" : reply).split("[.!?]|(?<!아니)[,，]|(?=아니[,，])|(?=정정)|하지만|그런데|(?<=했)지만|(?<=했)고|(?<=했)는데");
    }

    public static Set<String> unavailableKeys(String reply, Set<String> pendingKeys) {
        return parseReply(reply, pendingKeys).unavailable();
    }

    public static Map<String, String> extract(String reply, Set<String> pendingKeys) {
        return parseReply(reply, pendingKeys).values();
    }

    private static ParsedReply parseReply(String reply, Set<String> pendingKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> unavailable = new java.util.LinkedHashSet<>();
        String topic = null;
        boolean otherSubject = false;
        Set<String> conflicts = new java.util.HashSet<>();
        // 확정값과 미확인을 같은 순서로 판단한다. 타인·가정의 거절도 본인 조건이 아니다.
        for (String clause : clauses(reply)) {
            String text = compact(clause);
            if (Pattern.compile("친구|가족|부모|동생|고객이|다른사람|만약|가정|(?:가입|개통|변경|바꾸)(?:하면|한다면|했다면)").matcher(text).find()) {
                otherSubject = true;
                continue;
            }
            if (Pattern.compile("제가|저는|나는|저의|제가입|제변경").matcher(text).find()) otherSubject = false;
            if (otherSubject) continue;
            if (isUnavailable(text)) {
                Set<String> keys = mentionedKeys(text);
                if (keys.isEmpty()) keys = topic == null ? pendingKeys : Set.of(topic);
                for (String key : keys) {
                    if (!KEYS.contains(key)) continue;
                    values.remove(key);
                    unavailable.add(key);
                    conflicts.remove(key);
                }
                if (keys.size() == 1) topic = keys.iterator().next();
                continue;
            }
            if (Pattern.compile("모르|몰라|애매|헷갈|것같|일지도|수도|인지.*인지|네[,，]아니요").matcher(text).find()) {
                if (text.contains("가입") || text.contains("지난달")) values.remove(JOINED);
                else if (text.contains("변경") || text.contains("바꿨")) values.remove(CHANGED);
                else if (topic != null) values.remove(topic);
                continue;
            }
            boolean explicitCorrection = text.startsWith("아니,") || text.startsWith("아니，") || text.contains("정정");
            if ((text.startsWith("아니,") || text.startsWith("아니，"))
                    && topic != null && !text.contains("가입") && !text.contains("변경")) {
                text = (JOINED.equals(topic) ? "가입은" : "이번달변경은") + text.substring(3);
            }
            var extracted = extractClause(text, pendingKeys);
            boolean correction = explicitCorrection || Pattern.compile("정정|아니[,，]|잘못|실수").matcher(text).find();
            for (var entry : extracted.entrySet()) {
                String key = entry.getKey();
                if (!correction && (conflicts.contains(key)
                        || values.containsKey(key) && !values.get(key).equals(entry.getValue()))) {
                    // 순서만으로 상반된 사실 중 하나를 고르지 않는다.
                    values.remove(key);
                    unavailable.remove(key);
                    conflicts.add(key);
                } else {
                    if (correction) conflicts.remove(key);
                    unavailable.remove(key);
                    values.put(key, entry.getValue());
                }
            }
            if (extracted.size() == 1) topic = extracted.keySet().iterator().next();
        }
        return new ParsedReply(Map.copyOf(values), Set.copyOf(unavailable));
    }

    private static Set<String> mentionedKeys(String text) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (Pattern.compile("가입|개통|지난달").matcher(text).find()) keys.add(JOINED);
        if (Pattern.compile("변경|바꿨|바꿔|바꾸|바꾼").matcher(text).find()) keys.add(CHANGED);
        return keys;
    }

    private record ParsedReply(Map<String, String> values, Set<String> unavailable) {}

    private static Map<String, String> extractClause(String reply, Set<String> pendingKeys) {
        String text = compact(reply);
        Map<String, String> values = new LinkedHashMap<>();
        if (Pattern.compile("만약|가정|(?:가입|개통|변경|바꾸)(?:하면|한다면)").matcher(text).find()) {
            return Map.of();
        }
        if (pendingKeys.equals(Set.of(JOINED)) && text.matches("(?:지난달|저번달|이전달)(?:이요|이에요|입니다|요)?")) return Map.of(JOINED, NO);
        if (pendingKeys.equals(Set.of(JOINED)) && text.matches("(?:이번달|이달)(?:이요|이에요|입니다|요)?")) return Map.of(JOINED, YES);
        String joined = null;
        if (Pattern.compile("(?:이번달|이달)(?:에|은|에는)?(?:가입|개통)(?:은|이|한건|한것|한달이)?(?:아니|안|하지않)").matcher(text).find()
                || Pattern.compile("(?:지난달|저번달|이전달|작년|\\d+개월전|두달전|세달전)(?:에|은|에는)?(?:가입|개통)").matcher(text).find()
                || Pattern.compile("(?:가입|개통)(?:은|한건|한달은|시기는)?(?:지난달|저번달|이전달|작년)").matcher(text).find()) {
            joined = NO;
        } else if (Pattern.compile("(?:이번달|이달)(?:에|은|에는)?(?:가입|개통)(?:했|한|이|이에|해)").matcher(text).find()
                || Pattern.compile("(?:가입|개통)(?:은|한건|한달은|시기는)?(?:이번달|이달)").matcher(text).find()) {
            joined = YES;
        }
        if (joined != null) values.put(JOINED, joined);
        String changed = null;
        if (Pattern.compile("(?:이번달|이달).*?(?:변경한|바꾼)(?:적|이력|기록)(?:이|은|는)?없").matcher(text).find()
                || Pattern.compile("(?:이번달|이달)(?:에는|에|은)?(?:아직)?(?:요금제)?(?:를|는)?(?:변경|바꾼|바꾸|바꿔|바꾼적)(?:은|한적|한건|을)?(?:아직)?(?:안|않|없|하지않)").matcher(text).find()
                || Pattern.compile("(?:이번달|이달)(?:에는|에|은)?(?:아직)?(?:요금제)?(?:를|는)?(?:안|한번도안)(?:바꿨|바꿔|바꾸|변경)").matcher(text).find()
                || Pattern.compile("(?:이번달|이달).*변경.*(?:없어요|없습니다|안했|하지않)").matcher(text).find()) {
            changed = NO;
        } else if (Pattern.compile("(?:이번달|이달)(?:에는|에|은)?(?:이미)?(?:요금제)?(?:를|는)?(?:(?:한|두|세|\\d+)번)?(?:변경했|변경한|바꿨|바꾼|바꿨어)").matcher(text).find()) {
            changed = YES;
        }
        if (changed != null) values.put(CHANGED, changed);
        if (pendingKeys.size() == 1 && KEYS.containsAll(pendingKeys)) {
            String field = pendingKeys.iterator().next();
            if (text.matches("(?:네|예|응|맞아요|맞습니다)(?:고마워요|감사합니다|감사해요)?[.!~]*")) values.put(field, YES);
            if (text.matches("(?:아니|아니요|아닙니다)[.!~]*")) values.put(field, NO);
        }
        return Map.copyOf(values);
    }

    public static boolean valid(String key, String value) {
        return !KEYS.contains(key) || YES.equals(value) || NO.equals(value);
    }

    public static boolean blocks(Map<String, DialogueInput.Condition> conditions) {
        return KEYS.stream().anyMatch(key -> {
            var condition = conditions.get(key);
            return condition != null && condition.status() == DialogueInput.ConditionStatus.FILLED
                    && YES.equals(condition.value());
        });
    }

    public static String question(String key) {
        return switch (key) {
            case JOINED -> "이번 달에 가입하셨나요?";
            case CHANGED -> "이번 달에 이미 요금제를 변경하셨나요?";
            default -> throw new IllegalArgumentException("지원하지 않는 요금제 상담 조건입니다.");
        };
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
