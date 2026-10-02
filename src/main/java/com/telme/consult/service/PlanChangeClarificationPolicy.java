package com.telme.consult.service;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.PlanChangeConditions;
import com.telme.faq.dto.res.FaqSearchResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 실제 검색된 답변의 두 제한을 확인한 상담만 되묻는다. 점수·Guard 결과는 사용하지 않는다. */
public final class PlanChangeClarificationPolicy {
    public static final String POLICY_QUERY = "요금제 변경 가입한 달 월 1회 기준";
    private static final Pattern JOIN_LIMIT = Pattern.compile("가입(?:한달|당월|월).*?(?:변경할수없|변경이불가능|변경이제한|변경불가)");
    private static final Pattern MONTH_LIMIT = Pattern.compile("(?:월1회|한달에1회|한달에한번|한달에1번)(?:만|로제한|입니다|이며|변경할수|변경이가능|변경가능|가능)");
    private PlanChangeClarificationPolicy() {}

    private static Set<String> policyFacts(List<FaqSearchResponse> sources) {
        boolean joined = false;
        boolean changed = false;
        for (var source : sources) {
            if (source.question() == null || source.answer() == null) continue;
            String subject = source.question().replaceAll("\\s+", "");
            if (!subject.contains("요금제") || !(subject.contains("변경") || subject.contains("바꿀") || subject.contains("바꾸") || subject.contains("바꿔"))) continue;
            String answer = source.answer().replaceAll("\\s+", "");
            joined |= JOIN_LIMIT.matcher(answer).find();
            changed |= MONTH_LIMIT.matcher(answer).find();
        }
        var facts = new java.util.HashSet<String>();
        if (joined) facts.add(PlanChangeConditions.JOINED);
        if (changed) facts.add(PlanChangeConditions.CHANGED);
        return Set.copyOf(facts);
    }

    public static boolean coversRequiredPolicy(String query, Map<String, Condition> previous,
            List<FaqSearchResponse> sources) {
        var conditions = userConditions(query, previous);
        var facts = policyFacts(sources);
        // 한 제한으로 이미 불가하면 답변에 필요하지 않은 다른 조건을 묻지 않는다.
        for (String key : facts) {
            var condition = conditions.get(key);
            if (condition != null && condition.status() == ConditionStatus.FILLED
                    && PlanChangeConditions.YES.equals(condition.value())) return true;
        }
        return facts.containsAll(PlanChangeConditions.KEYS);
    }

    public static DialogueDecision assess(long requestId, String query,
            Map<String, Condition> previous, List<FaqSearchResponse> sources) {
        if (!PlanChangeConditions.isPersonalQuestion(query) || !coversRequiredPolicy(query, previous, sources)) return null;
        Map<String, Condition> conditions = userConditions(query, previous);
        if (!blocks(conditions) && PlanChangeConditions.KEYS.stream().noneMatch(key -> declined(conditions.get(key)))) {
            for (String key : List.of(PlanChangeConditions.JOINED, PlanChangeConditions.CHANGED)) {
                Condition condition = conditions.get(key);
                if (condition == null || condition.status() == ConditionStatus.PENDING) {
                    return new DialogueDecision(requestId, Action.ASK, conditions, key,
                            PlanChangeConditions.question(key), MessageOrigin.TEMPLATE);
                }
            }
        }
        return new DialogueDecision(requestId, Action.PROCEED, conditions, null, null, MessageOrigin.NONE);
    }

    /** 사용자 미확인과 근거가 있는 제한을 구분한 유한 정책 응답. FAQ 근거가 없으면 구성하지 않는다. */
    public static String policyCompletion(Map<String, Condition> conditions, List<FaqSearchResponse> sources) {
        var facts = policyFacts(sources);
        String monthly = monthlyLabel(sources);
        boolean joinedBlock = facts.contains(PlanChangeConditions.JOINED) && isYes(conditions.get(PlanChangeConditions.JOINED));
        boolean changedBlock = facts.contains(PlanChangeConditions.CHANGED) && isYes(conditions.get(PlanChangeConditions.CHANGED));
        if (joinedBlock) {
            return "이번 달에 가입하셨으므로 가입한 달에는 요금제를 변경할 수 없습니다."
                    + (sources.stream().anyMatch(source -> source.answer() != null
                            && source.answer().replaceAll("\\s+", "").matches(".*다음달부터(?:변경(?:하실수있|이|하실수|할수)|가능).*"))
                            ? " 가입월 제한 기준으로는 다음 달부터 변경이 가능합니다." : "")
                    + (changedBlock ? " 이번 달에 이미 변경하신 이력도 " + monthly + " 제한에 해당합니다." : "");
        }
        if (changedBlock) {
            return "요금제 변경은 " + monthly + "입니다. 이번 달에 이미 변경하셨으므로 이번 달 추가 변경은 어렵습니다.";
        }
        boolean unknown = PlanChangeConditions.KEYS.stream().anyMatch(key -> conditions.get(key) == null
                || conditions.get(key).status() != ConditionStatus.FILLED);
        if (unknown && facts.containsAll(PlanChangeConditions.KEYS)) {
            return "가입한 달에는 요금제를 변경할 수 없습니다. 요금제 변경은 " + monthly + (monthly.endsWith("회") ? "로" : "으로") + " 제한됩니다. "
                    + "확인되지 않은 조건이 있어 개인별 변경 가능 여부는 확정할 수 없습니다.";
        }
        if (!unknown && facts.containsAll(PlanChangeConditions.KEYS)) {
            return "가입한 달이 아니며 이번 달에 아직 변경하지 않으셨으므로, 가입월과 " + monthly
                    + " 기준에 한정하면 요금제 변경이 가능합니다. 이는 두 기준에 한정한 안내입니다.";
        }
        return null;
    }

    public static String criteriaCompletion(List<FaqSearchResponse> sources) {
        var facts = policyFacts(sources);
        if (!facts.containsAll(PlanChangeConditions.KEYS)) return null;
        return "가입한 달에는 요금제를 변경할 수 없습니다. 요금제 변경은 " + monthlyLabel(sources) + (monthlyLabel(sources).endsWith("회") ? "로" : "으로") + " 제한됩니다.";
    }

    private static String monthlyLabel(List<FaqSearchResponse> sources) {
        // 한글 '한 번' 근거를 숫자 '1회'로 새로 바꾸면 기존 수치 Guard가 근거 없음으로 본다.
        return sources.stream().anyMatch(source -> source.answer() != null
                && source.answer().replaceAll("\\s+", "").matches(".*(?:월1회|한달에1회).*")) ? "월 1회" : "한 달에 한 번";
    }

    public static String generationGuidance(Map<String, Condition> conditions, List<FaqSearchResponse> sources) {
        var facts = policyFacts(sources);
        StringBuilder guidance = new StringBuilder("가입월은 달력상 가입한 달입니다. 가입 후 경과 기간(30일·한 달 경과)으로 바꾸지 마십시오. "
                + "다음 달 초·특정 신청 날짜를 덧붙이지 마십시오. 아래 상태는 계정 조회 결과가 아닌 사용자 발화입니다.\n");
        for (String key : List.of(PlanChangeConditions.JOINED, PlanChangeConditions.CHANGED)) {
            var condition = conditions.get(key);
            guidance.append(key).append(": ").append(condition != null && condition.status() == ConditionStatus.FILLED
                    ? condition.value() : "미확인(모름·거절 포함, 예/아니요로 추정하지 않음)").append("\n");
        }
        boolean knownBlock = facts.stream().anyMatch(key -> {
            var c = conditions.get(key);
            return c != null && c.status() == ConditionStatus.FILLED && PlanChangeConditions.YES.equals(c.value());
        });
        if (knownBlock) {
            guidance.append("확인된 조건과 FAQ 제한으로 이번 달 변경 제한을 안내할 수 있습니다. 다른 미확인 조건 때문에 전체를 거절하지 마십시오. ");
            if (facts.contains(PlanChangeConditions.JOINED) && isYes(conditions.get(PlanChangeConditions.JOINED))) {
                guidance.append("이번 달 가입이 확인되었으므로 가입한 달의 변경 제한을 안내하십시오. ");
            }
            if (facts.contains(PlanChangeConditions.CHANGED) && isYes(conditions.get(PlanChangeConditions.CHANGED))) {
                guidance.append("이번 달 변경 이력이 확인되었으므로 월 1회 제한에 따라 이번 달 추가 변경이 어렵다고 안내하십시오. ");
            }
        } else if (PlanChangeConditions.KEYS.stream().anyMatch(key -> conditions.get(key) == null || conditions.get(key).status() != ConditionStatus.FILLED)) {
            guidance.append("개인별 변경 가능과 변경 불가 모두 확정하지 마십시오. 이번 달 가입 여부·변경 여부를 모릅니다. "
                    + "FAQ의 '가입한 달에는 변경 불가', '월 1회'를 각각 조건별 기준으로 안내하십시오. "
                    + "'현재 변경 불가' 또는 '다음 달부터 변경 가능'으로 고객에게 적용하지 마십시오. 추가 조건 질문 없이 일반 기준으로 마무리하십시오. ");
        } else {
            guidance.append("가입월이 아니며 이번 달 변경하지 않았다는 확인된 두 조건에 대해 가입월·월 1회 기준에 한정해 안내하십시오. "
                    + "다른 요금제의 자격과 모든 변경 권한이 충족됐다고 확정하지 마십시오. ");
        }
        return guidance.append("FAQ에 실제 명시된 제한과 적용 시점만 설명하십시오. 근거 없는 사유나 안내 채널을 붙이지 마십시오.").toString();
    }

    private static boolean isYes(Condition condition) {
        return condition != null && condition.status() == ConditionStatus.FILLED && PlanChangeConditions.YES.equals(condition.value());
    }

    private static Map<String, Condition> userConditions(String query, Map<String, Condition> previous) {
        Map<String, Condition> conditions = new HashMap<>(previous);
        // 재개한 상담에서는 최신 정정을 원문의 옛 값으로 덮지 않는다.
        PlanChangeConditions.extract(query, Set.of()).forEach((key, value) -> conditions.putIfAbsent(key, Condition.filled(value)));
        if (PlanChangeConditions.isUnavailable(query)) {
            PlanChangeConditions.unavailableKeys(query, Set.of()).forEach(key -> conditions.putIfAbsent(key, Condition.declined()));
        }
        return conditions;
    }

    public static boolean blocks(Map<String, Condition> conditions) {
        return PlanChangeConditions.blocks(conditions);
    }

    private static boolean declined(Condition condition) {
        return condition != null && condition.status() == ConditionStatus.DECLINED;
    }
}
