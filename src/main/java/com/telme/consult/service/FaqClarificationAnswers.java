package com.telme.consult.service;

import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.intent.dto.res.FollowUpRouteResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * FAQ 되묻기 조건을 채운다 (매장 조건은 라우팅 담당). 선택지와 같은 답이나,
 * 예·아니요 선택지의 같은 뜻 짧은 답("네", "없어요")만 채운다.
 */
public final class FaqClarificationAnswers {

    private static final Set<String> ROUTED_KEYS =
            Set.of(FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY);

    private static final Pattern YES = Pattern.compile(
            "^(네|넵|넹|예|옙|응|그래|맞아|맞습니다|그럼|물론이?|당연하?|있어|있습니다)[요오은는죠\\s.!~]*$");
    private static final Pattern NO = Pattern.compile(
            "^(아니|아뇨|아니에|아닙니다|아냐|없어|없습니다)[요오은는야다\\s.!~]*$");

    private FaqClarificationAnswers() {}

    public static Resolution fill(Resolution resolution, String reply) {
        if (resolution == null) {
            return resolution;
        }
        var candidate = resolution.candidate();
        String answer = asOption(reply == null ? "" : reply.strip(), candidate.options());
        if (ROUTED_KEYS.contains(candidate.field())
                || candidate.options().stream().noneMatch(answer::equals)) {
            return resolution;
        }
        // 선택지와 같은 답은 값이다. 라우팅은 "아니요"를 값 제공 거부로 읽어 DECLINED로 넘긴다
        var current = resolution.updates().get(candidate.field());
        if (current != null && current.status() != ConditionStatus.DECLINED) {
            return resolution;
        }
        var updates = new HashMap<>(resolution.updates());
        updates.put(candidate.field(), Condition.filled(answer));
        return new Resolution(candidate, updates);
    }

    private static String asOption(String answer, List<String> options) {
        if (options.contains("예") && YES.matcher(answer).matches()) {
            return "예";
        }
        if (options.contains("아니요") && NO.matcher(answer).matches()) {
            return "아니요";
        }
        return answer;
    }
}
