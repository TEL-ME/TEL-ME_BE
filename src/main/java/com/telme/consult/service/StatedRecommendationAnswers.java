package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// 요금제 추천 질문에 고객이 직접 말한 나이대·한 달 데이터 사용량. 되묻기 답과 같은 조건 이름과 값 글자로 돌려준다.
// 다른 뜻으로 읽히지 않도록 분명한 표현만 읽는다(사용량은 단위가 붙고 쓴다는 말이 함께 있을 때, 나이는 나이·나이대·구분을 말했을 때)
final class StatedRecommendationAnswers {

    private static final Pattern USAGE_VERB = Pattern.compile("쓰|써|사용|이용");

    private StatedRecommendationAnswers() {
    }

    static Map<String, String> of(String question) {
        if (question == null || question.isBlank()) {
            return Map.of();
        }
        Map<String, String> stated = new LinkedHashMap<>();
        if (USAGE_VERB.matcher(question).find()) {
            DataUsageReplyParser.Result usage = DataUsageReplyParser.parseStated(question);
            if (usage.kind() == DataUsageReplyParser.Kind.AMOUNT) {
                stated.put(MemberRecommendationAnswerer.DATA_KEY, usage.canonical());
            }
        }
        Plan.TargetGroup age = AgeReplyParser.parse(question);
        if (age != null) {
            stated.put(MemberRecommendationAnswerer.AGE_KEY, MemberRecommendationAnswerer.ageOption(age));
        }
        return stated;
    }
}
