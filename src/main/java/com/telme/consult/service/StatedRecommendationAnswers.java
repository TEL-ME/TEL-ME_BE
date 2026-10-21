package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// 요금제 추천 질문에 고객이 직접 말한 나이대·한 달 데이터 사용량과, 더 싼 요금제를 찾는다는 말.
// 나이대·사용량은 되묻기 답과 같은 조건 이름과 값 글자로 돌려준다.
// 자유 문장은 되묻기 답과 달리 요금제 크기, 기간, 소진, 제3자 이야기가 섞여 오탐이 곧 잘못된 추천이 되므로,
// 본인의 한 달 사용량과 나이가 분명할 때만 읽고 그 밖에는 읽지 않아 지금처럼 되묻는다
final class StatedRecommendationAnswers {

    private static final Pattern USAGE_VERB = Pattern.compile("쓰|써|사용|이용");
    // 본인이 아닌 사람의 이야기
    private static final Pattern THIRD_PARTY = Pattern.compile(
            "친구|지인|동료|남편|아내|와이프|배우자|아들|딸|자녀|손주|손자|손녀|엄마|어머니|아빠|아버지|부모|동생|누나|오빠|언니|가족|아이(?!폰|패드|디)");
    // 사용량이 아니라 요금제 크기를 말한 것("10GB 요금제 쓰고 있는데", "10GB 쓰는 요금제인데")
    private static final Pattern PLAN_SIZE = Pattern.compile(
            "(?i)\\d\\s*(?:gb|기가|메가|mb|tb|테라|g)(?:바이트)?\\s*(?:짜리|요금제|플랜|정액|상품)"
                    + "|(?:쓰는|쓰던|쓰고\\s*있는|사용하는|이용하는)\\s*(?:요금제|플랜|상품)|요금제\\s*\\d");
    // 한 달이 아닌 기간이나 지난 때
    private static final Pattern PERIOD = Pattern.compile(
            "일주일|한\\s*주|[0-9]+\\s*주|이틀|사흘|나흘|(?<![0-9])(?:[2-9]|[1-9][0-9]+)\\s*일|어제|그제|오늘|이번\\s*주|지난|개월|분기|[0-9]+\\s*년|올해|작년");
    // 정확한 양이 아닌 말(다 써서 모자람, 이상·이하, 안 씀)
    private static final Pattern NOT_EXACT = Pattern.compile(
            "다\\s*(?:써|썼|쓰)|모자라|부족|넘|초과|이상|이하|이내|안\\s*(?:써|쓰)|못\\s*(?:써|쓰)|도\\s*안|남아|남았");
    // 본인의 현재 나이를 말한 것이 아닌 표현(부정·비교·지난 때·자격 질문, 나이를 알 수 없는 학생 구분)
    private static final Pattern NOT_OWN_AGE = Pattern.compile(
            "말고|빼고|제외|아닌|아니고|아니라|비교|차이|때(?!문)|예전|옛날|과거|졸업|(?:요금제|플랜)\\s*(?:있어요|있나요|있을까요|있는지)"
                    + "|(?:가입|이용)\\s*(?:가능|할\\s*수|돼|되)|중학생|초등학생|유치원|중딩|초딩");

    // 방금 추천한 것보다 더 싼 요금제를 찾는 말
    private static final Pattern CHEAPER = Pattern.compile("더\\s*(?:싼|싸|싸게|저렴|쌈)|(?:좀|조금)\\s*(?:싼|싸|저렴)");
    // 사용량과 상관없이 싼 요금제를 찾는 말
    private static final Pattern REGARDLESS_OF_USAGE = Pattern.compile(
            "상관\\s*없|관계\\s*없|무관|(?:사용량|데이터)\\s*(?:말고|빼고|무시)|(?:적어도|안\\s*써도)");

    private StatedRecommendationAnswers() {
    }

    static Map<String, String> of(String question) {
        if (question == null || question.isBlank()) {
            return Map.of();
        }
        Map<String, String> stated = new LinkedHashMap<>();
        if (CHEAPER.matcher(question).find()) {
            stated.put(MemberRecommendationAnswerer.PRICE_KEY, REGARDLESS_OF_USAGE.matcher(question).find()
                    ? MemberRecommendationAnswerer.CHEAPEST : MemberRecommendationAnswerer.CHEAPER);
        }
        if (THIRD_PARTY.matcher(question).find()) {
            return stated;
        }
        if (USAGE_VERB.matcher(question).find() && !PLAN_SIZE.matcher(question).find()
                && !PERIOD.matcher(question).find() && !NOT_EXACT.matcher(question).find()) {
            DataUsageReplyParser.Result usage = DataUsageReplyParser.parseStated(question);
            if (usage.kind() == DataUsageReplyParser.Kind.AMOUNT) {
                stated.put(MemberRecommendationAnswerer.DATA_KEY, usage.canonical());
            }
        }
        if (!NOT_OWN_AGE.matcher(question).find()) {
            Plan.TargetGroup age = AgeReplyParser.parse(question);
            if (age != null) {
                stated.put(MemberRecommendationAnswerer.AGE_KEY, MemberRecommendationAnswerer.ageOption(age));
            }
        }
        return stated;
    }
}
