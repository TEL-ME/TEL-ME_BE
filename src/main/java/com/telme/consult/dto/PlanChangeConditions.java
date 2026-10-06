package com.telme.consult.dto;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 가입월·당월 변경 여부만 다룬다. 불명확한 경과 기간을 달력상 가입월로 추정하지 않는다. */
public final class PlanChangeConditions {
    public static final String JOINED = "joinedThisMonth";
    public static final String CHANGED = "changedThisMonth";
    public static final Set<String> KEYS = Set.of(JOINED, CHANGED);
    public static final String YES = "예";
    public static final String NO = "아니요";

    private static final Pattern CHANGE = Pattern.compile("변경|바꿀|바꾸|바꿔|바꿨|바꾼");
    private static final Pattern OTHER_SUBJECT = Pattern.compile("친구|가족|부모|동생|고객이|다른사람");
    private static final Pattern HYPOTHETICAL =
            Pattern.compile("만약|가정|(?:가입|개통|변경|바꾸)(?:하면|한다면|했다면|이면|이라면|일경우)");
    private static final Pattern UNCERTAIN = Pattern.compile("애매|헷갈|것같|일지도|수도|인지.*인지|네[,，]아니요");
    private static final Pattern CURRENT_MONTH = Pattern.compile("이번달|이달");
    private static final Pattern OTHER_TIME = Pattern.compile("지난|저번|작년|전월|개월|30일|4주|한달");
    private static final Pattern ELAPSED_MONTHS =
            Pattern.compile("(?:가입|개통)(?:한)?지(\\d{1,3})개월(?:이)?(?:됐|되었|지났)");

    private PlanChangeConditions() {}

    private static boolean isPolicyTopic(String query) {
        String text = compact(query);
        return text.contains("요금제")
                && CHANGE.matcher(text).find()
                && !Pattern.compile("적용|반영|계산|일할|부과|요금은|요금이|시니어|청소년|약정|위약금").matcher(text).find();
    }

    public static boolean isPersonalQuestion(String query) {
        String text = compact(query);
        if (!isPolicyTopic(query) || HYPOTHETICAL.matcher(text).find()) {
            return false;
        }
        if (OTHER_SUBJECT.matcher(text).find()
                && !Pattern.compile("제가|저는|나는").matcher(text).find()) {
            return false;
        }
        boolean eligibility =
                Pattern.compile("바꿀수|변경할수|변경가능|바꿔도|변경해도|변경할조건|바꿀조건").matcher(text).find();
        return eligibility
                && Pattern.compile("제가|나는|저는|지금|이번달|이달|가입했").matcher(text).find()
                && !Pattern.compile("각각|경우별|나눠").matcher(text).find();
    }

    public static boolean isCriteriaQuestion(String query) {
        return isPolicyTopic(query)
                && !isPersonalQuestion(query)
                && Pattern.compile("기준|조건|횟수|몇번|몇회|언제|변경가능|변경할수|바꿀수")
                        .matcher(compact(query))
                        .find();
    }

    public static boolean isDeferred(String reply) {
        return Pattern.compile("잠깐|잠시|이따|나중|기다|보류").matcher(compact(reply)).find();
    }

    public static boolean isAmbiguous(String reply) {
        String text = compact(reply);
        return UNCERTAIN.matcher(text).find()
                || OTHER_SUBJECT.matcher(text).find()
                || HYPOTHETICAL.matcher(text).find();
    }

    public static boolean isUnavailable(String reply) {
        return Pattern.compile("모르|몰라|모름|안알려|알려주고싶지않|말하고싶지않|알려주기싫" + "|말하기싫|거절|건너뛰|생략|스킵")
                .matcher(compact(reply))
                .find();
    }

    private static String[] clauses(String reply) {
        return (reply == null ? "" : reply)
                .split("[.!?]|(?<!아니)[,，]|(?=아니[,，])|(?=정정)|하지만|그런데|(?<=했)지만|(?<=했)고|(?<=했)는데");
    }

    public static Set<String> unavailableKeys(String reply, Set<String> pendingKeys) {
        return parseReply(reply, pendingKeys).unavailable();
    }

    public static Map<String, String> extract(String reply, Set<String> pendingKeys) {
        return parseReply(reply, pendingKeys).values();
    }

    private static ParsedReply parseReply(String reply, Set<String> pendingKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> unavailable = new LinkedHashSet<>();
        Set<String> conflicts = new HashSet<>();
        String topic = null;
        boolean otherSubject = false;
        for (String clause : clauses(reply)) {
            String text = compact(clause);
            if (OTHER_SUBJECT.matcher(text).find() || HYPOTHETICAL.matcher(text).find()) {
                otherSubject = true;
                continue;
            }
            if (Pattern.compile("제가|저는|나는|저의|제가입|제변경").matcher(text).find()) {
                otherSubject = false;
            }
            if (otherSubject) {
                continue;
            }
            if (isUnavailable(text)) {
                Set<String> keys = mentionedKeys(text);
                if (keys.isEmpty()) {
                    keys = topic == null ? pendingKeys : Set.of(topic);
                }
                for (String key : keys) {
                    if (KEYS.contains(key)) {
                        values.remove(key);
                        unavailable.add(key);
                        conflicts.remove(key);
                    }
                }
                if (keys.size() == 1) {
                    topic = keys.iterator().next();
                }
                continue;
            }
            if (UNCERTAIN.matcher(text).find()) {
                Set<String> keys = mentionedKeys(text);
                if (keys.isEmpty()) {
                    keys = topic == null ? pendingKeys : Set.of(topic);
                }
                for (String key : keys) {
                    values.remove(key);
                    conflicts.add(key);
                }
                continue;
            }
            boolean correction =
                    text.startsWith("아니,") || text.startsWith("아니，") || text.contains("정정");
            if ((text.startsWith("아니,") || text.startsWith("아니，"))
                    && topic != null
                    && !Pattern.compile("가입|개통|변경|바꿨|바꿔|바꾸|바꾼").matcher(text).find()) {
                text = (JOINED.equals(topic) ? "가입은" : "이번달변경은") + text.substring(3);
            }
            Map<String, String> extracted = extractClause(text, pendingKeys);
            for (var entry : extracted.entrySet()) {
                String key = entry.getKey();
                if (!correction
                        && (conflicts.contains(key)
                                || values.containsKey(key)
                                        && !values.get(key).equals(entry.getValue()))) {
                    values.remove(key);
                    unavailable.remove(key);
                    conflicts.add(key);
                } else {
                    if (correction) {
                        conflicts.remove(key);
                    }
                    unavailable.remove(key);
                    values.put(key, entry.getValue());
                }
            }
            if (extracted.size() == 1) {
                topic = extracted.keySet().iterator().next();
            }
        }
        return new ParsedReply(Map.copyOf(values), Set.copyOf(unavailable), Set.copyOf(conflicts));
    }

    private static Set<String> mentionedKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (Pattern.compile("가입|개통|지난달").matcher(text).find()) {
            keys.add(JOINED);
        }
        if (CHANGE.matcher(text).find()) {
            keys.add(CHANGED);
        }
        return keys;
    }

    private record ParsedReply(
            Map<String, String> values, Set<String> unavailable, Set<String> conflicts) {}

    private static Map<String, String> extractClause(String text, Set<String> pendingKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        if (Pattern.compile("가능|수있|해도|바꿔도|되나요|기준|방법|적용|계산|예정|계획|신청|접수|무슨|뜻|의미").matcher(text).find()
                || Pattern.compile("유심|매장|청구|로밍|명의|번호이동|가상계좌|소액결제").matcher(text).find()
                        && !text.contains("요금제")) {
            return Map.of();
        }
        String joined = null;
        if (pendingKeys.equals(Set.of(JOINED))
                && text.matches("(?:지난달|저번달|이전달)(?:이요|이에요|입니다|요)?")) {
            joined = NO;
        } else if (pendingKeys.equals(Set.of(JOINED))
                && text.matches("(?:이번달|이달)(?:이요|이에요|입니다|요)?")) {
            joined = YES;
        } else if (Pattern.compile(
                                "(?:이번달|이달)(?:에|은|에는)?(?:가입|개통)" + "(?:은|이|한건|한것|한달이)?(?:아니|안|하지않)")
                        .matcher(text)
                        .find()
                || Pattern.compile("(?:지난달|저번달|이전달|작년|\\d+개월전|두달전|세달전)" + "(?:에|은|에는)?(?:가입|개통)")
                        .matcher(text)
                        .find()
                || Pattern.compile("(?:가입|개통)(?:은|한건|한달은|시기는)?(?:지난달|저번달|이전달|작년)")
                        .matcher(text)
                        .find()) {
            joined = NO;
        } else if (Pattern.compile("(?:이번달|이달)(?:에|은|에는)?(?:가입|개통)(?:했|한|이|이에|해)")
                        .matcher(text)
                        .find()
                || Pattern.compile("(?:가입|개통)(?:은|한건|한달은|시기는)?(?:이번달|이달)").matcher(text).find()) {
            joined = YES;
        }
        Matcher elapsed = ELAPSED_MONTHS.matcher(text);
        if (elapsed.find() && Integer.parseInt(elapsed.group(1)) >= 2) {
            if (CURRENT_MONTH.matcher(text).find()) {
                return Map.of();
            }
            // 두 달 이상이라는 하한만 사용한다. 30일·한 달을 달력상 가입월과 동일시하지 않는다.
            joined = NO;
        }
        if (joined != null) {
            values.put(JOINED, joined);
        }
        if ((CURRENT_MONTH.matcher(text).find() || pendingKeys.equals(Set.of(CHANGED)))
                && (!OTHER_TIME.matcher(text).find() || CURRENT_MONTH.matcher(text).find())) {
            if (Pattern.compile(
                            "안(?:바꿨|바꿔|바꾸|변경)|(?:변경|바꾼|바꿔|바꾸).*?(?:안했|하지않|않았|없)"
                                    + "|(?:변경|바꾼|바꿔|바꾸)(?:은|한적|한건|을)?(?:아직)?(?:안(?:했|하|한)|않)")
                    .matcher(text)
                    .find()) {
                values.put(CHANGED, NO);
            } else if (Pattern.compile("변경했|변경한|바꿨|바꾼|바꿨어").matcher(text).find()) {
                values.put(CHANGED, YES);
            }
        }
        if (pendingKeys.size() == 1 && KEYS.containsAll(pendingKeys)) {
            String field = pendingKeys.iterator().next();
            if (text.matches("(?:네|예|응|맞아요|맞습니다)(?:고마워요|감사합니다|감사해요)?[.!~]*")) {
                values.put(field, YES);
            }
            if (text.matches("(?:아니|아니요|아닙니다)[.!~]*")) {
                values.put(field, NO);
            }
        }
        return Map.copyOf(values);
    }

    /** 규칙이 놓친 표현도 발화의 주체·시점·동작·극성이 확인된 경우에만 LLM 값을 받는다. */
    public static boolean supportsLlmValue(
            String reply, Set<String> pendingKeys, String key, String value) {
        if (key == null
                || value == null
                || pendingKeys == null
                || !KEYS.contains(key)
                || !valid(key, value)
                || !pendingKeys.equals(Set.of(key))
                || isAmbiguous(reply)
                || isUnavailable(reply)
                || isDeferred(reply)) {
            return false;
        }
        String compact = compact(reply);
        if (Pattern.compile("안내|설명|문의|상담|신청|접수").matcher(compact).find()) {
            return false;
        }
        if (Pattern.compile("가능|수있|해도|바꿔도|되나요|기준|방법|적용|계산|예정|계획|신청|접수|무슨|뜻|의미")
                        .matcher(compact)
                        .find()
                || Pattern.compile("유심|매장|청구|로밍|명의|번호이동|가상계좌|소액결제").matcher(compact).find()
                        && !compact.contains("요금제")
                || CURRENT_MONTH.matcher(compact).find()
                        && ELAPSED_MONTHS.matcher(compact).find()) {
            return false;
        }
        var parsed = parseReply(reply, pendingKeys);
        if (parsed.conflicts().contains(key) || parsed.unavailable().contains(key)) {
            return false;
        }
        if (parsed.values().containsKey(key)) {
            return value.equals(parsed.values().get(key));
        }
        String acknowledgement = compact.replaceAll("[.!~,，]", "");
        if (acknowledgement.matches("(?:넵|넹|네용|그렇습니다)(?:고마워요|감사합니다)?")) {
            return YES.equals(value);
        }
        if (acknowledgement.matches("(?:아뇨|아니오|아니용|아니에요)(?:고마워요|감사합니다)?")) {
            return NO.equals(value);
        }
        for (String clause : clauses(reply)) {
            String text = compact(clause);
            boolean current = CURRENT_MONTH.matcher(text).find();
            if (!current && OTHER_TIME.matcher(text).find()) {
                continue;
            }
            if (JOINED.equals(key)
                    && current
                    && !CHANGE.matcher(text).find()
                    && Pattern.compile("가입|개통").matcher(text).find()) {
                boolean negative = Pattern.compile("안했|하지않|아니").matcher(text).find();
                boolean completed = Pattern.compile("했|완료|마쳤|되었").matcher(text).find();
                if ((NO.equals(value) && negative)
                        || (YES.equals(value) && completed && !negative)) {
                    return true;
                }
            }
            if (CHANGED.equals(key) && CHANGE.matcher(text).find()) {
                boolean negative = Pattern.compile("안|않|없").matcher(text).find();
                boolean completed = Pattern.compile("했|바꿨|바꿨어|마쳤|완료").matcher(text).find();
                if ((NO.equals(value) && negative)
                        || (YES.equals(value) && completed && !negative)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean valid(String key, String value) {
        // 공용 변환기의 매장 조건 계약을 유지한다. 요금제 키만 예/아니요를 제한한다.
        return !KEYS.contains(key) || YES.equals(value) || NO.equals(value);
    }

    public static boolean blocks(Map<String, DialogueInput.Condition> conditions) {
        return KEYS.stream()
                .anyMatch(
                        key -> {
                            var condition = conditions.get(key);
                            return condition != null
                                    && condition.status() == DialogueInput.ConditionStatus.FILLED
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
