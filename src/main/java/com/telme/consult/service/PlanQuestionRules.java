package com.telme.consult.service;

import java.util.regex.Pattern;

// 일반 정책 질문을 가로채지 않도록 표현이 분명할 때만 NONE이 아닌 값을 주는 규칙 기반 분류
public final class PlanQuestionRules {

    private static final Pattern COMPARISON = Pattern.compile("랑|비교|차이|중에|달라|나아요|나은");

    private static final Pattern RECOMMENDATION = Pattern.compile(
            "요금제.*(추천|맞는|어울리는|알맞은)|(추천|맞는|어울리는|알맞은).*요금제"
                    + "|어떤요금제.{0,3}(좋|맞|나을|써야|갈아|바꿔)|요금제.{0,12}(갈아타야|바꿔야|옮겨야)"
                    + "|요금제.{0,6}나한테(맞|어울)");

    private static final Pattern MY_SUBSCRIPTION = Pattern.compile(
            "(내요금제|제요금제|내가(가입한|쓰는|쓰고있는|이용하는)요금제"
                    + "|지금(쓰는|쓰고있는|가입한|이용중인|사용중인)요금제"
                    + "|현재(쓰는|가입한|이용중인|사용중인)?요금제|가입한요금제|쓰고있는요금제)"
                    + ".{0,10}(뭐|뭔|무엇|어떤|얼마|알려|확인|조회|이름)");
    private static final Pattern SUBSCRIPTION_EXCLUDED = Pattern.compile("변경|바꾸|해지|신청");

    private static final Pattern MY_PENALTY = Pattern.compile(
            "내위약금|제위약금|내약정|제약정|(지금|현재|오늘|바로|당장)해지(하면|할때|시|하는경우|한다면)"
                    + "|해지하면.{0,6}(얼마|위약금)나오");
    private static final Pattern PENALTY_EXCLUDED = Pattern.compile("면제|어떻게알");

    private static final Pattern MY_USAGE = Pattern.compile(
            "(내|제)(사용량|데이터|통화|문자)|이번달(사용량|데이터|통화|문자|요금.{0,6}(얼마|나올|예상|나와)|예상)"
                    + "|남은(데이터|통화|문자)(가|는|량)?(얼마|몇)|(데이터|통화|문자).{0,4}(얼마나(썼|사용했|남았|남아)|확인|조회)"
                    + "|사용량(을|좀)?(알려|확인|조회|얼마)|예상요금|청구될요금");
    private static final Pattern USAGE_EXCLUDED = Pattern.compile(
            "이월|넘길|넘어가|넘겨|다음달|선물|양도|공유|손주|이전|못쓰면|로밍|하루|보낼");

    private static final Pattern PLAN_CATALOG = Pattern.compile(
            "요금제.{0,8}(종류|몇가지|몇개|몇종류|목록|뭐가있|뭐있|어떤게있|얼마|가격|월정액)"
                    + "|(제일|가장)(싼|저렴한|비싼).{0,4}요금제|요금제(가|도|는)?(있나요|있어요)");

    private static final Pattern HOW_OR_IF = Pattern.compile(
            "어떻게(해|하|바꿔|바꾸|가입|신청|변경|골라|고르|알아|봐|받|신고|등록|확인)|어디서|어디에서|방법|절차|요령|기준|조건"
                    + "|자격|누가|몇살|가능|무슨뜻|란무엇|이란|라는게|[는할]법|되는거");
    private static final Pattern CAN_DO = Pattern.compile("수있(어요|나요|을까요|습니까|는지|을지|죠)");
    // 데이터·통화·문자를 얼마나 쓸 수 있는지는 가능 여부가 아니라 제공량을 묻는 질문이다
    private static final Pattern ALLOWANCE_AMOUNT = Pattern.compile("(통화|문자|데이터).{0,6}(얼마나|몇)|(얼마나|몇).{0,6}(통화|문자|데이터)");
    private static final Pattern COMPARES = Pattern.compile(
            "비교|차이|중에|중뭐|중어느|중어떤|달라|것과|것중|[와과랑].{1,15}중(에서)?(요금|어느|어떤|뭐|어디|가장|더|무엇)");
    // 요금제 표로 답할 수 없는 다른 서비스의 요금·사용 질문
    private static final Pattern OTHER_SERVICE = Pattern.compile("로밍|해외|국제전화");
    private static final Pattern CHANGES = Pattern.compile(
            "려면|변경|바꾸|바꿔|바꿀|옮기|옮겨|갈아|전환|가입|신청|해지|할인|약정|로밍|자동|갑자기|올랐|부족|계속|안쓰고|따로"
                    + "|될까|나이|연령");
    private static final Pattern PRONOUN = Pattern.compile("^(내|내가|제|제가|저|저는|저도|저의|나|나는|나도|우리|우리는|우리가)$");
    private static final Pattern OWN_STATE =
            Pattern.compile("지금|현재|오늘|이번\\s*달|가입한\\s*요금제|가입돼|가입되어|쓰고\\s*있|사용\\s*중|당장");
    private static final Pattern OWN_USAGE = Pattern.compile("남은|남았|남아|썼|사용했|보냈|했는지");
    private static final Pattern OWN_CONTRACT = Pattern.compile("약정.*남(은|았|아)");
    private static final Pattern GENERAL_COMPARISON = Pattern.compile("비교|차이|중에|달라|나아요|나은|것과|것\\s*중");
    // 요금제를 고르는 말에 함께 나오는 단어. "추천"처럼 다른 물건에도 쓰는 말과 "쓰", "싼"처럼 흔한 말은 넣지 않는다
    private static final Pattern PLAN_WORD = Pattern.compile(
            "(?i)요금|플랜|데이터|통화|문자|사용량|gb|기가|무제한|5g|lte|알뜰|청소년|시니어|어르신|약정|결합|통신비|통신사|월정액");
    // 요금제라는 말 없이 고르는 말. 물건 이름 없이 "싼 거", "맞는 걸", 금액, 나이로 말하는 경우만 넣어 "저렴한 노트북"은 걸리지 않게 한다
    private static final Pattern PLAN_CHOICE_CUE = Pattern.compile(
            "(싼|저렴한|비싼|좋은|맞는|알맞은|어울리는|나은|가장|제일)\\s*(거|걸|것|게)|(나|저|제|우리)(한테|에게|게|가족)\\s*(맞|어울|좋)"
                    + "|\\d[\\d,.]*\\s*만\\s*원|원\\s*(이하|이상|대|정도|미만)|\\d+\\s*(세|살)|(뭐|어떤\\s*게|뭘)\\s*(가\\s*)?(좋|나을|맞)");

    private PlanQuestionRules() {
    }

    // 로밍처럼 요금제 표 밖의 서비스를 묻는 질문이면 FAQ가 답한다
    public static boolean asksOtherService(String question) {
        return question != null && OTHER_SERVICE.matcher(question.replaceAll("\\s+", "")).find();
    }

    // 영화·노트북처럼 요금제와 상관없는 추천 질문을 요금제 추천으로 보내지 않도록, 요금제를 고르는 말이 있는지 본다
    public static boolean mentionsPlan(String question) {
        return question != null && (PLAN_WORD.matcher(question).find() || PLAN_CHOICE_CUE.matcher(question).find());
    }

    // 요금제 목록·추천 질문이 아니라 방법, 자격, 가능 여부, 뜻, 비교를 묻는 질문이면 FAQ가 답한다
    public static boolean asksProcedure(PlanQuestionType type, String question) {
        String text = question.replaceAll("\\s+", "");
        if (HOW_OR_IF.matcher(text).find() || COMPARES.matcher(text).find()) {
            return true;
        }
        if (CAN_DO.matcher(text).find() && !ALLOWANCE_AMOUNT.matcher(text).find()) {
            return true;
        }
        return type == PlanQuestionType.PLAN_CATALOG && CHANGES.matcher(text).find();
    }

    // 모델이 내 정보 질문이라고 해도 본인을 가리키는 표현이 없으면 일반 질문으로 본다
    public static boolean hasPersonalMarker(PlanQuestionType type, String question) {
        if (question == null || GENERAL_COMPARISON.matcher(question).find()) {
            return false;
        }
        for (String token : question.trim().split("\\s+")) {
            if (PRONOUN.matcher(token).matches()) {
                return true;
            }
        }
        if (OWN_STATE.matcher(question).find()) {
            return true;
        }
        return switch (type) {
            case MY_USAGE -> OWN_USAGE.matcher(question).find();
            case MY_PENALTY -> OWN_CONTRACT.matcher(question.replaceAll("\\s+", "")).find();
            default -> false;
        };
    }

    public static PlanQuestionType classify(String question) {
        if (question == null) {
            return PlanQuestionType.NONE;
        }
        String text = question.replaceAll("\\s+", "");
        if (asksOtherService(text)) {
            return PlanQuestionType.NONE;
        }
        if (RECOMMENDATION.matcher(text).find() && !asksProcedure(PlanQuestionType.RECOMMENDATION, text)) {
            return PlanQuestionType.RECOMMENDATION;
        }
        if (MY_SUBSCRIPTION.matcher(text).find()
                && !COMPARISON.matcher(text).find() && !SUBSCRIPTION_EXCLUDED.matcher(text).find()) {
            return PlanQuestionType.MY_SUBSCRIPTION;
        }
        if (MY_PENALTY.matcher(text).find()
                && !COMPARISON.matcher(text).find() && !PENALTY_EXCLUDED.matcher(text).find()) {
            return PlanQuestionType.MY_PENALTY;
        }
        if (MY_USAGE.matcher(text).find() && !USAGE_EXCLUDED.matcher(text).find()) {
            return PlanQuestionType.MY_USAGE;
        }
        if (PLAN_CATALOG.matcher(text).find() && !asksProcedure(PlanQuestionType.PLAN_CATALOG, text)) {
            return PlanQuestionType.PLAN_CATALOG;
        }
        return PlanQuestionType.NONE;
    }
}
