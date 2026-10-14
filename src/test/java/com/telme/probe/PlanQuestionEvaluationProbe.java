package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.consult.service.PlanQuestionClassifier;
import com.telme.consult.service.PlanQuestionPromptTemplates;
import com.telme.consult.service.PlanQuestionRules;
import com.telme.consult.service.PlanQuestionType;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 실제 모델을 부르므로 LLM_PROVIDER=ollama 일 때만 돈다. 프롬프트의 예시 문장은 평가셋에서 뺐다
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LLM_PROVIDER", matches = "ollama")
class PlanQuestionEvaluationProbe {

    @Autowired LlmClient llmClient;

    @Test
    void 개발용_평가셋으로_채점한다() {
        score("개발", cases());
    }

    // 프롬프트를 고칠 때 보지 않은 질문이다. 이 결과를 보고 프롬프트에 문장을 추가하지 않는다
    @Test
    void 새_질문_평가셋으로_채점한다() {
        score("검증", holdoutCases());
    }

    // FAQ 질문처럼 사정을 설명하는 문체를 포함한 두 번째 검증 질문이다
    @Test
    void 두번째_새_질문_평가셋으로_채점한다() {
        score("검증2", holdoutCases2());
    }

    // 요금제 목록 질문과 같은 단어가 든 절차·자격 질문을 가르는 능력을 보는 세 번째 검증 질문이다
    @Test
    void 세번째_새_질문_평가셋으로_채점한다() {
        score("검증3", holdoutCases3());
    }

    private void score(String name, List<Case> cases) {
        Recording recording = new Recording(llmClient);
        PlanQuestionClassifier classifier =
                new PlanQuestionClassifier(recording, new ObjectMapper(), new PlanQuestionProperties(true, true));
        System.out.println("EVAL|" + name + "|" + PlanQuestionPromptTemplates.PROMPT_VERSION);

        Map<PlanQuestionType, int[]> perType = new LinkedHashMap<>();
        int hit = 0;
        int total = 0;
        long elapsed = 0;
        for (Case each : cases) {
            recording.raw = null;
            long startedAt = System.currentTimeMillis();
            PlanQuestionType actual = classifier.classify(null, each.question());
            long took = System.currentTimeMillis() - startedAt;
            elapsed += took;
            boolean ok = actual == each.expected();
            perType.computeIfAbsent(each.expected(), key -> new int[2])[ok ? 0 : 1]++;
            total++;
            hit += ok ? 1 : 0;
            System.out.printf("EVAL|%s|기대 %s|실제 %s|규칙 %s|%dms|%s|%s%n", ok ? "맞음" : "틀림", each.expected(), actual,
                    PlanQuestionRules.classify(each.question()), took, each.question(), recording.raw);
        }
        perType.forEach((type, counts) ->
                System.out.printf("EVAL_TYPE|%s|맞음 %d|틀림 %d%n", type, counts[0], counts[1]));
        System.out.printf("EVAL_SUM|%s|맞음 %d/%d|정확도 %.0f%%|평균 %dms%n", name, hit, total, 100.0 * hit / total, elapsed / total);
    }

    @Test
    void 기존_FAQ_질문을_요금제_질문으로_가져가는지_확인한다() throws Exception {
        Recording recording = new Recording(llmClient);
        PlanQuestionClassifier classifier =
                new PlanQuestionClassifier(recording, new ObjectMapper(), new PlanQuestionProperties(true, true));
        JsonNode faqs = new ObjectMapper().readTree(new File("scripts/data/faq_full_1150.json"));

        int captured = 0;
        int calls = 0;
        long elapsed = 0;
        List<String> lines = new ArrayList<>();
        for (JsonNode faq : faqs) {
            String question = faq.get("question").asText();
            int before = recording.calls;
            long startedAt = System.currentTimeMillis();
            PlanQuestionType type = classifier.classify(null, question);
            if (recording.calls > before) {
                calls++;
                elapsed += System.currentTimeMillis() - startedAt;
            }
            if (type != PlanQuestionType.NONE) {
                captured++;
                lines.add("EVAL_FAQ|" + type + "|" + faq.get("policy_ref").asText() + "|" + question);
            }
        }
        lines.forEach(System.out::println);
        System.out.printf("EVAL_FAQ_SUM|FAQ %d건|모델 호출 %d건|가져간 질문 %d건|호출 평균 %dms%n",
                faqs.size(), calls, captured, calls == 0 ? 0 : elapsed / calls);
    }

    private List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        add(cases, PlanQuestionType.RECOMMENDATION,
                "저한테 맞는 요금제 있을까요", "통화를 많이 하는 사람한테 괜찮은 요금제 알려주세요",
                "요금 아끼고 싶은데 뭐로 바꾸면 좋을까요", "가성비 좋은 요금제 하나만 골라주세요",
                "영상을 많이 보는데 어떤 요금제를 쓰면 될까요", "5G 쓰는데 더 싼 걸로 갈아타고 싶어요 뭐가 좋을까요",
                "요금제 좀 추천해 주세요", "제 사용 패턴에 맞는 요금제로 바꾸고 싶어요");
        add(cases, PlanQuestionType.MY_SUBSCRIPTION,
                "지금 무슨 요금제 쓰고 있죠", "제가 가입한 요금제 알려주세요", "현재 요금제 이름이랑 월 요금 알려줘",
                "나 어떤 요금제야", "내가 쓰는 요금제 월 얼마예요", "제 요금제 확인 좀 해주세요");
        add(cases, PlanQuestionType.MY_PENALTY,
                "제 약정 언제까지예요", "약정 남은 기간 알려줘", "내 위약금 알려줘", "오늘 해지하면 위약금 내야 해요",
                "지금 해지하면 얼마 물어야 하나요", "제 약정 아직 남았어요");
        add(cases, PlanQuestionType.MY_USAGE,
                "이번 달 데이터 얼마나 쓴 거예요", "남은 데이터 알려줘", "이번 달 요금 얼마나 나올까요",
                "내 통화량 알려줘", "문자 몇 건 보냈는지 궁금해요", "데이터 얼마 남았어요", "이번 달 사용량 확인해 주세요");
        add(cases, PlanQuestionType.PLAN_CATALOG,
                "요금제 뭐뭐 있어요", "청소년 요금제 있나요", "제일 저렴한 요금제가 뭐예요", "5G 스탠다드 얼마예요",
                "시니어 요금제 가격 알려주세요", "알뜰 요금제는 뭐가 있어요", "LTE 요금제 종류가 어떻게 돼요");
        add(cases, PlanQuestionType.NONE,
                "위약금은 어떻게 계산돼요", "약정 기간은 보통 얼마예요", "요금제 변경은 어떻게 해요",
                "요금제는 한 달에 몇 번 바꿀 수 있어요", "남은 데이터는 다음 달로 이월되나요",
                "청소년 요금제는 몇 살까지 쓸 수 있어요", "로밍 요금 얼마예요", "신촌 매장 어디 있어요", "안녕하세요",
                "유심 재발급 비용이 얼마예요", "번호이동하면 위약금 면제돼요", "가입하려면 뭐가 필요해요",
                "일시 정지는 며칠까지 돼요", "요금 납부는 어떻게 해요", "오늘 날씨 어때요",
                "약정 없이 가입할 수 있나요", "해지 신청은 어디서 해요", "데이터 다 쓰면 어떻게 돼요");
        return cases;
    }

    private List<Case> holdoutCases3() {
        List<Case> cases = new ArrayList<>();
        add(cases, PlanQuestionType.RECOMMENDATION,
                "요즘 데이터가 모자라는데 뭘로 바꾸는 게 좋을까요", "저한테 어울리는 요금제 하나 알려주세요",
                "통화를 거의 안 하는데 맞는 요금제가 있을까요", "제가 쓰기에 가성비 좋은 요금제가 뭐예요");
        add(cases, PlanQuestionType.MY_SUBSCRIPTION,
                "내가 지금 쓰는 요금제가 뭐더라", "제 요금제 한 달에 얼마 나가죠", "가입해 둔 요금제 이름 좀 알려줘");
        add(cases, PlanQuestionType.MY_PENALTY,
                "지금 해지하면 위약금 얼마나 되죠", "제 약정 기간이 얼마나 남았나요", "해지하려는데 내 위약금이 얼마인지 알려주세요");
        add(cases, PlanQuestionType.MY_USAGE,
                "이번 달 문자 얼마나 보냈어요", "내가 데이터를 얼마나 썼는지 궁금해요", "이번 달 요금은 얼마쯤 나와요");
        add(cases, PlanQuestionType.PLAN_CATALOG,
                "5G 요금제는 어떤 게 있나요", "LTE 요금제 월 요금 알려주세요", "시니어 요금제 월 요금이 얼마예요",
                "가장 비싼 요금제는 뭐예요", "5G 프리미엄은 데이터를 얼마나 주나요", "알뜰 요금제 목록 보여주세요");
        add(cases, PlanQuestionType.NONE,
                "시니어 요금제는 어떻게 가입해요", "LTE 요금제에서 5G 요금제로 옮길 수 있나요", "청소년 요금제 가입 연령이 어떻게 돼요",
                "알뜰 요금제는 무슨 뜻이에요", "요금제를 고르는 요령이 있나요", "5G 요금제로 바꾸면 위약금이 있나요",
                "요금제 변경은 언제 적용돼요", "가족과 요금제를 같이 쓸 수 있나요", "약정은 어떻게 신청해요",
                "데이터 무제한 요금제도 속도 제한이 있나요", "해지는 어떻게 해요", "요금 납부일을 바꾸고 싶어요",
                "단말 할부는 어떻게 되나요", "일시 정지는 어떻게 신청해요");
        return cases;
    }

    private List<Case> holdoutCases2() {
        List<Case> cases = new ArrayList<>();
        add(cases, PlanQuestionType.RECOMMENDATION,
                "혼자 사는 대학생인데 어떤 요금제가 좋을까요", "요금제 하나만 추천해 주세요 부탁드려요",
                "저한테 맞는 요금제가 뭔지 알려주세요", "지금 요금제보다 더 나은 게 있을까요");
        add(cases, PlanQuestionType.MY_SUBSCRIPTION,
                "나 지금 어떤 요금제에 가입돼 있어", "제 요금제가 월 얼마였죠", "가입한 요금제 좀 알려줘요");
        add(cases, PlanQuestionType.MY_PENALTY,
                "제 위약금 얼마예요", "지금 해지하면 얼마 내요", "내 약정은 언제 끝나나요");
        add(cases, PlanQuestionType.MY_USAGE,
                "이번 달 데이터 몇 기가 썼는지 알려줘", "지금 남은 통화 시간이 얼마예요", "오늘까지 문자 몇 개 보냈죠");
        add(cases, PlanQuestionType.PLAN_CATALOG,
                "5G 라이트 요금제는 월 얼마예요", "요금제 전체 목록 보여주세요", "알뜰 세이브 데이터 얼마 줘요");
        add(cases, PlanQuestionType.NONE,
                "군대 가는데 위약금 어떻게 되나요", "단말 할부금이 남았는데 해지하면 어떻게 돼요",
                "약정 끝나기 전에 번호이동하면 위약금 있어요", "청소년 요금제인데 만 19세 되면 어떻게 되나요",
                "알뜰 요금제로도 5G 쓸 수 있나요", "요금제 바꾸면 그 달 요금은 어떻게 계산돼요",
                "정지하면 요금이 얼마나 나와요", "데이터 남으면 선물할 수 있나요", "가족끼리 요금제 합칠 수 있나요",
                "해외 나갈 때 요금제 그대로 써도 돼요", "요금제 가입하려면 나이 제한 있나요",
                "시니어 요금제 가입 조건이 뭐예요", "요금 얼마 나왔는지 어디서 봐요", "위약금 얼마인지 어떻게 확인해요");
        return cases;
    }

    private List<Case> holdoutCases() {
        List<Case> cases = new ArrayList<>();
        add(cases, PlanQuestionType.RECOMMENDATION,
                "주로 유튜브만 보는데 월 얼마짜리 요금제가 적당할까요", "부모님께 해드릴 요금제 하나 골라주실래요",
                "지금보다 저렴하게 쓰려면 어떤 걸로 옮기는 게 나아요", "데이터를 적게 쓰는 편인데 맞는 요금제 있나요");
        add(cases, PlanQuestionType.MY_SUBSCRIPTION,
                "저 지금 어떤 요금제 쓰는지 기억이 안 나요", "현재 가입돼 있는 요금제가 뭔지 알려줘요",
                "제가 쓰는 요금제의 월 요금이 얼마인가요");
        add(cases, PlanQuestionType.MY_PENALTY,
                "지금 당장 해지하면 위약금이 얼마나 되나요", "저 약정 몇 개월 남았어요", "약정이 끝나려면 얼마나 더 있어야 하죠");
        add(cases, PlanQuestionType.MY_USAGE,
                "저번 달보다 이번 달 요금이 얼마나 더 나올까요", "이번 달에 통화 몇 분 했는지 알려주세요",
                "데이터 얼마나 남았는지 확인하고 싶어요", "지금까지 문자 몇 건 썼어요");
        add(cases, PlanQuestionType.PLAN_CATALOG,
                "5G 프리미엄 요금제는 한 달에 얼마인가요", "무제한 데이터 요금제 어떤 게 있어요",
                "알뜰폰 요금제 종류 좀 알려주세요", "청소년이 쓸 수 있는 요금제는 뭐가 있나요");
        add(cases, PlanQuestionType.NONE,
                "위약금을 안 내도 되는 경우가 있어요", "약정 중에 요금제 바꾸면 위약금 있나요", "로밍 데이터 요금이 얼마인가요",
                "데이터를 다 쓰면 속도가 얼마나 느려지나요", "해지하면 남은 데이터는 어떻게 되나요",
                "요금제 바꾸면 약정도 새로 시작돼요", "이번 달 요금이 두 번 빠져나갔어요", "가까운 매장 알려줘",
                "기기 변경하고 싶어요", "해외에서 문자는 어떻게 보내요");
        return cases;
    }

    private void add(List<Case> cases, PlanQuestionType expected, String... questions) {
        for (String question : questions) {
            cases.add(new Case(question, expected));
        }
    }

    private record Case(String question, PlanQuestionType expected) {
    }

    private static final class Recording implements LlmClient {
        private final LlmClient delegate;
        String raw;
        int calls;

        Recording(LlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public String generate(LlmRequest request) {
            calls++;
            raw = delegate.generate(request);
            return raw;
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
