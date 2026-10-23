package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import java.util.List;
import java.util.stream.Collectors;

public final class PlanQuestionPromptTemplates {

    private PlanQuestionPromptTemplates() {}

    // v5: 판매 중인 요금제 목록을 주고 질문이 가리키는 요금제와 목록에 없는 요금제 여부를 함께 받는다(TELME-217)
    public static final String PROMPT_VERSION = "plan-question-v5";

    private static final String TYPE_RULES = """
        당신은 통신사 챗봇의 질문 분류기입니다.
        고객 질문이 아래 중 어디에 속하는지 type 하나만 고르십시오.

        - RECOMMENDATION: 질문한 고객에게 맞는 요금제를 골라 달라는 질문
          예) "요금제 추천해줘", "데이터 많이 쓰는데 어떤 요금제가 좋아요?"
        - MY_SUBSCRIPTION: 질문한 고객이 지금 가입한 요금제의 이름이나 월 요금을 묻는 질문
          예) "내 요금제 뭐야?", "지금 쓰는 요금제 알려줘"
        - MY_PENALTY: 질문한 고객이 지금 해지하면 내야 할 금액, 본인 약정의 남은 기간이나 만료일을 묻는 질문
          예) "지금 해지하면 위약금 얼마예요?", "내 약정 언제 끝나요?"
        - MY_USAGE: 질문한 고객의 이번 달 데이터·통화·문자 사용량이나 이번 달 예상 요금을 묻는 질문
          예) "이번 달 데이터 얼마나 썼어?", "이번 달 요금 얼마 나와?"
        - PLAN_CATALOG: 판매 중인 요금제 상품의 종류, 월 요금, 데이터·통화·문자 제공량을 묻는 질문
          예) "5G 요금제 종류 알려줘", "제일 싼 요금제 얼마예요?"
        - NONE: 위에 해당하지 않는 모든 질문

        [판단 기준]
        - MY_로 시작하는 값은 질문하는 고객 본인의 계약·사용 내역을 직접 알려 달라는 경우만 고릅니다.
          고객이 상황을 설명하며 어떻게 되는지, 가능한지, 있는지 묻는 질문은 제도 문의이므로 NONE입니다.
          예) "해지하면 위약금이 있나요?", "약정이 뭐예요?", "위약금 면제 조건이 뭐예요?" -> NONE
          예) "일 때문에 해외에 나가는데 해지하면 어떻게 돼요?", "할부가 남은 폰을 해지하려면 어떻게 해요?" -> NONE
          예) "기본 데이터를 소진하면 속도가 어떻게 되나요?", "남은 데이터를 넘길 수 있나요?" -> NONE
        - PLAN_CATALOG는 어떤 요금제가 있는지, 요금제 상품의 월 요금과 데이터·통화·문자 제공량을 묻는 질문만 해당합니다.
          요금제 이름을 대고 그 요금제가 얼마나 주는지 묻는 질문은 사용량 조회(MY_USAGE)가 아니라 PLAN_CATALOG입니다.
          가입·변경 자격, 방법, 절차, 가능 여부, 용어 뜻을 묻는 질문은 NONE입니다.
          로밍, 유심, 부가서비스, 단말기, 수수료 같은 다른 요금도 NONE입니다.
          예) "청소년 요금제는 몇 살까지 가입할 수 있나요?", "해외에서 데이터 로밍을 쓰면 요금이 얼마나 나와요?" -> NONE
          어떤 혜택(OTT, 음악, 워치 등)이 들어 있는 요금제나 특정 대상(군인, 어린이, 어르신 등) 전용 요금제가 있는지, 몇 종류인지 묻는 질문은
          요금제 상품을 묻는 것이므로 PLAN_CATALOG입니다. 판매 중인 목록에 없으면 unknownPlan을 true로 둡니다.
          예) "왓챠 들어간 요금제 있어요?", "대학생 전용 요금제 있나요?" -> PLAN_CATALOG
        - RECOMMENDATION은 고객에게 맞는 요금제를 정해 달라는 경우만 해당합니다.
          요금제를 고르는 방법·절차를 묻는 질문이나 요금제 종류끼리 비교해 설명해 달라는 질문은 NONE입니다.
          예) "요금제를 고르는 방법을 알려주세요", "일반 요금제와 무제한 요금제는 뭐가 달라요?" -> NONE
        - 판단이 애매하면 NONE을 고르십시오.
        """;

    private static final String PLAN_RULES = """

        [요금제 고르기]
        type이 PLAN_CATALOG일 때만 아래 판매 중인 요금제 목록을 보고 plans와 unknownPlan을 채웁니다. 그 밖의 type이면 plans는 [], unknownPlan은 false입니다.
        - plans: 고객이 이름으로 가리킨 요금제의 코드. 망 이름을 뺀 이름("스탠다드")으로 불러도 그 요금제입니다.
          5G, 청소년, 가장 싼 요금제처럼 조건으로만 말하면 []입니다. 이름이 목록의 요금제와 정확히 같지 않으면 고르지 않습니다.
        - 판매 중인 요금제는 아래 목록이 전부입니다. 요금제마다 망(5G, LTE, 알뜰), 대상(일반, 청소년 만 14~18세, 시니어 만 65세 이상),
          월정액, 데이터·통화·문자 제공량만 있고 그 밖의 혜택이나 전용 대상은 없습니다.
        - unknownPlan: 고객이 목록에 없는 요금제 이름을 대거나, 목록에 없는 혜택(OTT, 음악, 워치, 가족 결합 등)이나
          대상(군인, 대학생, 어린이 등)을 조건으로 요금제를 물으면 true입니다. 이때 plans는 []입니다.
          망, 일반·청소년·시니어 대상(어르신, 중고생처럼 다른 말로 불러도 같음), 가격, 데이터 양을 조건으로 물으면 false입니다.
          예) "5G 스탠다드 플러스는 얼마예요?" -> 목록에 그런 이름이 없으므로 unknownPlan true
          예) "디즈니 플러스 들어간 요금제 있어요?", "대학생 전용 요금제 있나요?" -> unknownPlan true
          예) "고등학생이 쓸 수 있는 요금제 있어요?" -> 청소년 요금제가 있으므로 unknownPlan false
        """;

    private static final String OUTPUT = """

        JSON만 출력하십시오.
        {"type": "RECOMMENDATION | MY_SUBSCRIPTION | MY_PENALTY | MY_USAGE | PLAN_CATALOG | NONE 중 하나", "plans": ["요금제 코드"], "unknownPlan": false}
        """;

    // 요금제 이름을 고르지 않은 요금제 표 질문에만 따로 부른다. 분류 응답에 조건 칸을 붙이면 모델이 이름을 조건으로 옮기거나
    // 목록에 없는 요금제 질문을 NONE으로 바꾸는 일이 늘었다(TELME-227 측정)
    // v2: 묶음의 가격·데이터를 묻는 질문("시니어 요금제는 얼마예요")도 그 묶음을 조건으로 받는다. v1은 목록·개수만 말해 조건을 비웠다
    public static final String CONDITIONS_PROMPT_VERSION = "plan-list-conditions-v2";

    private static final String CONDITION_RULES = """
        당신은 통신사 챗봇에서 요금제 목록, 개수, 가격, 데이터를 묻는 질문의 조건을 읽는 역할입니다.
        고객이 알고 싶어 하는 요금제 묶음의 조건만 고릅니다. 묶음의 가격이나 데이터를 물어도 그 묶음이 조건입니다.
          예) "청소년 요금제는 데이터 몇 기가씩 줘요?" -> targetGroups [YOUTH]
        - networks: 고객이 보고 싶어 하는 망(FIVE_G, LTE, BUDGET). BUDGET은 알뜰 요금제입니다. 말하지 않았으면 [].
        - excludedNetworks: 고객이 빼 달라거나 필요 없다고 한 망.
        - targetGroups: 고객이 보고 싶어 하는 대상(GENERAL 일반, YOUTH 청소년·중고생, SENIOR 시니어·어르신). 말하지 않았으면 [].
        - excludedTargetGroups: 고객이 빼 달라거나 필요 없다고 한 대상.
        - unlimited: 데이터 무제한 요금제만 원하면 ONLY, 무제한 요금제를 빼 달라면 EXCLUDE, 그 밖에는 ANY.
        - excludedPlans: 고객이 이름으로 빼 달라고 한 요금제의 코드. 망 이름을 뺀 이름("스탠다드")으로 불러도 그 요금제입니다.
        - 가격 조건은 고르지 않습니다.
        - 이유나 사정으로 꺼낸 말은 조건이 아닙니다. 고객이 실제로 보고 싶어 하는 것만 고릅니다.
          예) "LTE 요금제 몇 개예요? 5G는 안 터지는 동네라서요" -> networks [LTE], excludedNetworks []
          예) "무제한은 너무 비싸서요 5G 언리미티드 아닌 걸로 5G 요금제 뭐 있어요?" -> networks [FIVE_G], excludedPlans [5G 언리미티드의 코드], unlimited ANY
          예) "알뜰 같은 건 됐고요 요금제 종류 쭉 알려주실래요?" -> excludedNetworks [BUDGET]
          예) "LTE 베이직이나 LTE 플러스 말고 다른 LTE 요금제" -> networks [LTE], excludedPlans [두 요금제의 코드]
        """;

    private static final String CONDITION_OUTPUT = """

        JSON만 출력하십시오.
        {"networks": [], "excludedNetworks": [], "targetGroups": [], "excludedTargetGroups": [], "unlimited": "ANY", "excludedPlans": []}
        """;

    public static String conditionsSystemPrompt(List<Plan> plans) {
        return CONDITION_RULES + catalog(plans) + CONDITION_OUTPUT;
    }

    private static String catalog(List<Plan> plans) {
        return plans.stream()
                .map(plan -> "- " + plan.getCode() + ": " + plan.getName())
                .collect(Collectors.joining("\n", "\n[판매 중인 요금제]\n", "\n"));
    }

    // 판매 중인 요금제가 없으면 요금제 고르기 없이 type만 고른다
    public static String systemPrompt(List<Plan> plans) {
        if (plans.isEmpty()) {
            return TYPE_RULES + OUTPUT;
        }
        return TYPE_RULES + PLAN_RULES + catalog(plans) + OUTPUT;
    }
}
