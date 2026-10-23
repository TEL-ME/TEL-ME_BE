package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlanQuestionClassifierTest {

    LlmClient llm = mock(LlmClient.class);
    PlanRepository plans = mock(PlanRepository.class);
    PlanQuestionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PlanQuestionClassifier(llm, new ObjectMapper(), new PlanQuestionProperties(true, true, false), plans);
    }

    @Test
    void 모델이_분류하면_규칙이_놓치는_표현도_그_값을_쓴다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"MY_USAGE\"}");

        assertThat(PlanQuestionRules.classify("제 요금이 얼마나 나올지 궁금해요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "제 요금이 얼마나 나올지 궁금해요")).isEqualTo(PlanQuestionType.MY_USAGE);
    }

    @Test
    void 모델이_내_정보_질문이라고_해도_본인을_가리키는_표현이_없으면_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"MY_PENALTY\"}");

        assertThat(classifier.classify(1L, "폰 잃어버려서 해지하면 위약금 있어요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "약정 중간에 해지하면 위약금이 얼마나 나오나요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "지금 해지하면 위약금 얼마예요")).isEqualTo(PlanQuestionType.MY_PENALTY);
    }

    @Test
    void 모델이_요금제_목록이나_추천이라고_해도_방법이나_자격을_묻는_질문이면_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\"}");
        assertThat(classifier.classify(1L, "시니어 요금제로 바꾸려면 어떻게 해요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "알뜰 요금제로 5G를 쓸 수 있어요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "5G 요금제랑 알뜰 요금제 중 뭐가 싸요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "5G 스탠다드 얼마예요")).isEqualTo(PlanQuestionType.PLAN_CATALOG);

        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");
        assertThat(classifier.classify(1L, "식구들 요금제 어떻게 골라요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "데이터를 아끼려면 어떤 요금제가 좋아요")).isEqualTo(PlanQuestionType.RECOMMENDATION);
    }

    @Test
    void 모델이_가져가도_로밍_질문과_내_정보의_방법_질문은_NONE이다() {
        for (String type : new String[] {"PLAN_CATALOG", "MY_USAGE", "RECOMMENDATION"}) {
            when(llm.generate(any())).thenReturn("{\"type\":\"" + type + "\"}");
            assertThat(classifier.classify(1L, "해외 나갈 때 데이터 하루에 얼마예요")).as(type).isEqualTo(PlanQuestionType.NONE);
            assertThat(classifier.classify(1L, "보름 동안 로밍을 썼는데 요금이 생각보다 많이 나왔습니다")).as(type)
                    .isEqualTo(PlanQuestionType.NONE);
        }
        when(llm.generate(any())).thenReturn("{\"type\":\"MY_USAGE\"}");
        assertThat(classifier.classify(1L, "내 데이터 다 썼다고 문자 오면 어떻게 해요")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "이번 달 내 데이터 얼마나 썼어요")).isEqualTo(PlanQuestionType.MY_USAGE);
    }

    @Test
    void 요금제_추천과_목록은_본인_표현이_없어도_모델_분류를_따른다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");
        assertThat(classifier.classify(1L, "가성비 좋은 요금제 하나만 골라주세요")).isEqualTo(PlanQuestionType.RECOMMENDATION);

        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\"}");
        assertThat(classifier.classify(1L, "5G 스탠다드 얼마예요")).isEqualTo(PlanQuestionType.PLAN_CATALOG);
    }

    @Test
    void 모델이_추천이라고_해도_요금제가_아닌_물건을_골라_달라는_질문은_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");

        for (String question : new String[] {"요즘 볼만한 영화 추천해 주세요", "노트북 하나 추천해 주세요", "점심 메뉴 추천해줘",
                "좋은 노래 추천해줘", "맛집 추천해줘", "저렴한 노트북 추천해줘", "이어폰 추천해줘", "책 한 권 추천해줘", "게임 추천해줘",
                "영상 편집 프로그램 추천해줘", "유튜브 채널 추천해줘"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.NONE);
        }
    }

    @Test
    void 요금제라는_말이_없어도_습관이나_상황으로_요금제를_고르는_추천은_모델_분류를_따른다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");

        for (String question : new String[] {"데이터 많이 쓰는데 추천해줘", "한 달에 20GB 쓰는데 추천해줘", "청소년인데 추천해줘", "싼 거 추천해줘",
                "저한테 맞는 거 추천해주세요", "5만원 이하 추천해줘", "어머니가 70세인데 추천해줘", "통신비 줄이고 싶은데 추천해줘",
                "유튜브 많이 보는데 추천해줘", "영상 많이 보는데 뭐가 나아요?", "게임 많이 하는 고등학생인데 추천 좀", "엄마 폰 뭐로 해드리면 좋을까요",
                "SNS 많이 하는데 추천해줘", "아이가 게임을 많이 해요 추천해 주세요", "부모님 드릴 건데 추천해줘", "가족 4명 같이 쓸 건데 추천해줘",
                "웹툰이랑 동영상 많이 봐요 추천 좀", "출퇴근 때 영상 보는데 어떤 게 좋을까요", "재택근무라 화상회의 많이 하는데 뭐가 좋을까요"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.RECOMMENDATION);
        }
    }

    @Test
    void 영상_게임_폰_같은_습관_단어가_든_질문도_모델이_분류한다() {
        for (String question : new String[] {"영상 많이 보는데 뭐가 나아요?", "게임 많이 하는데 추천해줘", "엄마 폰 뭐로 해드리면 좋을까요",
                "와이파이 써서 거의 안 쓰는데 추천해줘", "화상회의 많이 하는데 뭐가 좋을까요"}) {
            assertThat(PlanQuestionClassifier.mentionsPlanTopic(question)).as(question).isTrue();
        }
        assertThat(PlanQuestionClassifier.mentionsPlanTopic("신촌 매장 어디 있어요")).isFalse();
    }

    @Test
    void 날짜나_남은_일수와_내_정보_질문은_하루_요금_규칙으로_막지_않는다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"MY_PENALTY\"}");
        assertThat(classifier.classify(1L, "약정 끝나기 1일 남았는데 위약금 얼마예요")).isEqualTo(PlanQuestionType.MY_PENALTY);

        when(llm.generate(any())).thenReturn("{\"type\":\"MY_USAGE\"}");
        for (String question : new String[] {"11일에 개통했는데 이번 달 요금 얼마 나와요", "오늘 하루 요금이 얼마나 나왔어요",
                "내 하루 평균 사용 요금 얼마예요"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.MY_USAGE);
        }
    }

    @Test
    void 요금제나_제공량_말_없이_하루_요금을_물으면_모델이_목록이라_해도_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\"}");

        for (String question : new String[] {"요금은 하루에 얼마정도에요", "하루 요금이 얼마예요", "일일 비용이 얼마나 나와요",
                "1일 요금이 얼마예요", "하루에 얼마 드나요"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.NONE);
        }
        assertThat(classifier.classify(1L, "하루 2GB 주는 요금제 뭐 있어요")).isEqualTo(PlanQuestionType.PLAN_CATALOG);
        assertThat(classifier.classify(1L, "5G 스탠다드 얼마예요")).isEqualTo(PlanQuestionType.PLAN_CATALOG);
    }

    @Test
    void 모델이_NONE이라고_하면_규칙이_맞다고_해도_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"NONE\"}");

        assertThat(PlanQuestionRules.classify("내 요금제 뭐야")).isEqualTo(PlanQuestionType.MY_SUBSCRIPTION);
        assertThat(classifier.classify(1L, "내 요금제 뭐야")).isEqualTo(PlanQuestionType.NONE);
    }

    @Test
    void 모델_호출이_실패하면_규칙으로_분류한다() {
        when(llm.generate(any())).thenThrow(new IllegalStateException("연결 실패"));

        assertThat(classifier.classify(1L, "내 요금제 뭐야")).isEqualTo(PlanQuestionType.MY_SUBSCRIPTION);
        assertThat(classifier.classify(1L, "요금제 변경은 어떻게 해요")).isEqualTo(PlanQuestionType.NONE);
    }

    @Test
    void 응답을_읽을_수_없으면_규칙으로_분류한다() {
        for (String broken : new String[] {null, "", "{}", "not json", "[]", "{\"type\":\"UNKNOWN\"}",
                "{\"type\":1}", "{\"type\":\"MY_USAGE\",\"extra\":true}"}) {
            when(llm.generate(any())).thenReturn(broken);

            assertThat(classifier.classify(1L, "내 요금제 뭐야")).as(String.valueOf(broken))
                    .isEqualTo(PlanQuestionType.MY_SUBSCRIPTION);
        }
    }

    @Test
    void 요금제와_관련_없는_질문은_모델을_부르지_않는다() {
        assertThat(classifier.classify(1L, "신촌 매장 알려줘")).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "안녕하세요")).isEqualTo(PlanQuestionType.NONE);

        verify(llm, never()).generate(any());
    }

    @Test
    void 빈_질문은_모델을_부르지_않는다() {
        assertThat(classifier.classify(1L, null)).isEqualTo(PlanQuestionType.NONE);
        assertThat(classifier.classify(1L, "  ")).isEqualTo(PlanQuestionType.NONE);

        verify(llm, never()).generate(any());
    }

    @Test
    void 꺼져_있으면_모델을_부르지_않고_규칙으로_분류한다() {
        classifier = new PlanQuestionClassifier(llm, new ObjectMapper(), new PlanQuestionProperties(false, true, false), plans);

        assertThat(classifier.classify(1L, "내 요금제 뭐야")).isEqualTo(PlanQuestionType.MY_SUBSCRIPTION);
        assertThat(classifier.classify(1L, "요금제 변경은 어떻게 해요")).isEqualTo(PlanQuestionType.NONE);

        verify(llm, never()).generate(any());
    }

    @Test
    void 모델에는_질문과_허용값_스키마를_보내고_호출_기록용_정보를_남긴다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"NONE\"}");

        classifier.classify(7L, "내 요금제 뭐야");

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.executionId()).isEqualTo(7L);
        assertThat(request.taskType()).isEqualTo(TaskType.ROUTING);
        assertThat(request.userPrompt()).isEqualTo("내 요금제 뭐야");
        assertThat(request.temperature()).isZero();
        assertThat(request.think()).isFalse();
        assertThat(request.promptVersion()).isEqualTo(PlanQuestionPromptTemplates.PROMPT_VERSION);
        assertThat(request.responseSchema().toString())
                .contains("RECOMMENDATION", "MY_SUBSCRIPTION", "MY_PENALTY", "MY_USAGE", "PLAN_CATALOG", "NONE");
    }

    @Test
    void 판매_중인_요금제_목록을_주고_모델이_고른_요금제를_함께_받는다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE))
                .thenReturn(List.of(plan("FIVE_G_PREMIUM", "5G 프리미엄"), plan("LTE_PLUS", "LTE 플러스")));
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\",\"plans\":[\"FIVE_G_PREMIUM\",\"GONE\"],\"unknownPlan\":false}");

        PlanQuestion classified = classifier.classifyQuestion(1L, "프리미엄 요금제 얼마예요");

        assertThat(classified.type()).isEqualTo(PlanQuestionType.PLAN_CATALOG);
        // 판매 중이 아닌 코드는 버린다
        assertThat(classified.mention().planCodes()).containsExactly("FIVE_G_PREMIUM");
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("- FIVE_G_PREMIUM: 5G 프리미엄", "- LTE_PLUS: LTE 플러스");
        assertThat(captor.getValue().responseSchema().toString()).contains("FIVE_G_PREMIUM", "unknownPlan");
    }

    @Test
    void 목록에_없는_요금제라고_하면_고른_요금제를_두지_않는다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(plan("FIVE_G_PREMIUM", "5G 프리미엄")));
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\",\"plans\":[\"FIVE_G_PREMIUM\"],\"unknownPlan\":true}");

        PlanQuestion.Mention mention = classifier.classifyQuestion(1L, "5G 프리미엄 플러스 얼마예요").mention();

        assertThat(mention.unknownPlan()).isTrue();
        assertThat(mention.planCodes()).isEmpty();
    }

    @Test
    void 요금제_목록_질문이_아니거나_규칙으로_분류하면_고른_요금제가_없다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(plan("FIVE_G_PREMIUM", "5G 프리미엄")));
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\",\"plans\":[],\"unknownPlan\":false}");
        assertThat(classifier.classifyQuestion(1L, "요금제 추천해줘").mention()).isNull();

        when(llm.generate(any())).thenThrow(new IllegalStateException("down"));
        PlanQuestion fallback = classifier.classifyQuestion(1L, "5G 프리미엄 요금제 얼마예요");
        assertThat(fallback.type()).isEqualTo(PlanQuestionType.PLAN_CATALOG);
        assertThat(fallback.mention()).isNull();
    }

    @Test
    void 요금제를_고르지_않은_목록_질문은_목록_조건을_한_번_더_받는다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE))
                .thenReturn(List.of(plan("FIVE_G_UNLIMITED", "5G 언리미티드"), plan("LTE_PLUS", "LTE 플러스")));
        when(llm.generate(any())).thenReturn(
                "{\"type\":\"PLAN_CATALOG\",\"plans\":[],\"unknownPlan\":false}",
                "{\"networks\":[\"FIVE_G\"],\"excludedNetworks\":[],\"targetGroups\":[],\"excludedTargetGroups\":[\"YOUTH\"],"
                        + "\"unlimited\":\"ANY\",\"excludedPlans\":[\"FIVE_G_UNLIMITED\",\"GONE\"]}");

        PlanQuestion.Mention mention = classifier.classifyQuestion(3L, "무제한은 비싸서요 언리미티드 말고 5G 요금제 뭐 있어요").mention();

        assertThat(mention.planCodes()).isEmpty();
        assertThat(mention.conditions().networks()).containsExactly(Plan.NetworkType.FIVE_G);
        assertThat(mention.conditions().excludedTargetGroups()).containsExactly(Plan.TargetGroup.YOUTH);
        assertThat(mention.conditions().unlimited()).isEqualTo(PlanQuestion.Unlimited.ANY);
        // 판매 중이 아닌 코드는 버린다
        assertThat(mention.conditions().excludedPlans()).containsExactly("FIVE_G_UNLIMITED");
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).generate(captor.capture());
        LlmRequest conditions = captor.getAllValues().get(1);
        assertThat(conditions.executionId()).isEqualTo(3L);
        assertThat(conditions.promptVersion()).isEqualTo(PlanQuestionPromptTemplates.CONDITIONS_PROMPT_VERSION);
        assertThat(conditions.systemPrompt()).contains("- FIVE_G_UNLIMITED: 5G 언리미티드");
        assertThat(conditions.responseSchema().toString()).contains("excludedNetworks", "BUDGET", "SENIOR", "EXCLUDE", "LTE_PLUS");
    }

    @Test
    void 요금제를_골랐거나_목록에_없는_요금제면_목록_조건을_묻지_않는다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(plan("FIVE_G_PREMIUM", "5G 프리미엄")));
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\",\"plans\":[\"FIVE_G_PREMIUM\"],\"unknownPlan\":false}");
        assertThat(classifier.classifyQuestion(1L, "프리미엄 얼마예요").mention().conditions()).isNull();

        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\",\"plans\":[],\"unknownPlan\":true}");
        assertThat(classifier.classifyQuestion(1L, "넷플릭스 들어간 요금제 있어요").mention().conditions()).isNull();

        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\",\"plans\":[],\"unknownPlan\":false}");
        assertThat(classifier.classifyQuestion(1L, "요금제 추천해줘").mention()).isNull();

        verify(llm, times(3)).generate(any());
    }

    @Test
    void 목록_조건을_받지_못하면_조건_없이_두어_답변이_낱말로_읽는다() {
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(plan("FIVE_G_PREMIUM", "5G 프리미엄")));
        String listReply = "{\"type\":\"PLAN_CATALOG\",\"plans\":[],\"unknownPlan\":false}";

        when(llm.generate(any())).thenReturn(listReply).thenThrow(new IllegalStateException("down"));
        PlanQuestion failed = classifier.classifyQuestion(1L, "LTE 요금제 뭐 있어요");
        assertThat(failed.type()).isEqualTo(PlanQuestionType.PLAN_CATALOG);
        assertThat(failed.mention().planCodes()).isEmpty();
        assertThat(failed.mention().conditions()).isNull();

        // 칸이 빠졌거나 타입이나 값이 틀리면 읽지 않는다(#212 리뷰). Bedrock은 응답 스키마를 보내지 않는다
        String full = "\"excludedNetworks\":[],\"targetGroups\":[],\"excludedTargetGroups\":[],\"excludedPlans\":[]";
        for (String broken : new String[] {"{}", "{\"networks\":\"LTE\",\"unlimited\":\"ANY\"," + full + "}",
                "{\"networks\":[\"LTE\",\"WIFI\"],\"unlimited\":\"ANY\"," + full + "}",
                "{\"networks\":[\"LTE\"],\"unlimited\":\"SOME\"," + full + "}",
                "{\"networks\":[\"LTE\"]," + full + "}"}) {
            reset(llm);
            doReturn(listReply, broken).when(llm).generate(any());
            assertThat(classifier.classifyQuestion(1L, "LTE 요금제 목록 알려줘").mention().conditions()).as(broken).isNull();
        }

        // 분류 응답 형식처럼 모르는 칸이 있으면 읽지 않는다
        reset(llm);
        doReturn(listReply, listReply).when(llm).generate(any());
        assertThat(classifier.classifyQuestion(1L, "LTE 요금제 뭐 있어요").mention().conditions()).isNull();
    }

    // 나이대와 쓸 사람·사용량은 두 호출로 동시에 묻는다
    @Test
    void 추천받을_요금제를_쓸_사람과_나이대_사용량을_모델에서_받는다() {
        replies("{\"targetGroup\":\"YOUTH\"}", "{\"user\":\"OTHER\",\"monthlyDataGb\":10}");

        assertThat(classifier.subscriber(5L, "고딩 딸 한 달에 10기가 써요 요금제 추천"))
                .isEqualTo(new PlanQuestionClassifier.Subscriber(true, Plan.TargetGroup.YOUTH, 10.0));

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).generate(captor.capture());
        assertThat(captor.getAllValues()).extracting(LlmRequest::promptVersion).containsExactlyInAnyOrder(
                PlanQuestionPromptTemplates.SUBSCRIBER_AGE_PROMPT_VERSION, PlanQuestionPromptTemplates.SUBSCRIBER_USAGE_PROMPT_VERSION);
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.executionId()).isEqualTo(5L);
            assertThat(request.userPrompt()).isEqualTo("고딩 딸 한 달에 10기가 써요 요금제 추천");
        });
    }

    @Test
    void 모르는_나이대와_사용량은_비워_두고_쓸_사람을_읽지_못하거나_꺼져_있으면_없다() {
        replies("{\"targetGroup\":\"UNKNOWN\"}", "{\"user\":\"SELF\",\"monthlyDataGb\":0}");
        assertThat(classifier.subscriber(1L, "요금제 추천")).isEqualTo(new PlanQuestionClassifier.Subscriber(false, null, null));

        // 나이대만 못 읽으면 나이대 없이 쓴다
        replies("oops", "{\"user\":\"OTHER\",\"monthlyDataGb\":0}");
        assertThat(classifier.subscriber(1L, "어머니 요금제 추천")).isEqualTo(new PlanQuestionClassifier.Subscriber(true, null, null));

        replies("{\"targetGroup\":\"SENIOR\"}", "{\"user\":\"OTHER\"}");
        assertThat(classifier.subscriber(1L, "어머니 요금제 추천")).isNull();

        // SELF, OTHER가 아니면 본인으로 보지 않고 읽지 못한 것으로 둔다(#212 리뷰)
        replies("{\"targetGroup\":\"SENIOR\"}", "{\"user\":\"UNKNOWN\",\"monthlyDataGb\":10}");
        assertThat(classifier.subscriber(1L, "어머니 요금제 추천")).isNull();

        reset(llm);
        doThrow(new IllegalStateException("down")).when(llm).generate(any());
        assertThat(classifier.subscriber(1L, "어머니 요금제 추천")).isNull();

        LlmClient off = mock(LlmClient.class);
        PlanQuestionClassifier disabled = new PlanQuestionClassifier(off, new ObjectMapper(),
                new PlanQuestionProperties(false, true, false), plans);
        assertThat(disabled.subscriber(1L, "고딩 딸 요금제 추천")).isNull();
        verify(off, never()).generate(any());
    }

    @Test
    void 규칙이_나이를_읽었으면_나이대는_묻지_않고_쓸_사람과_사용량만_묻는다() {
        replies("{\"targetGroup\":\"YOUTH\"}", "{\"user\":\"OTHER\",\"monthlyDataGb\":5}");

        assertThat(classifier.subscriber(1L, "할머니 만 75세 한 달에 5기가 쓰세요 요금제 추천해 주세요", false))
                .isEqualTo(new PlanQuestionClassifier.Subscriber(true, null, 5.0));
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(captor.capture());
        assertThat(captor.getValue().promptVersion()).isEqualTo(PlanQuestionPromptTemplates.SUBSCRIBER_USAGE_PROMPT_VERSION);
    }

    private void replies(String age, String usage) {
        reset(llm);
        when(llm.generate(any())).thenAnswer(invocation -> PlanQuestionPromptTemplates.SUBSCRIBER_AGE_PROMPT_VERSION
                .equals(invocation.<LlmRequest>getArgument(0).promptVersion()) ? age : usage);
    }

    @Test
    void 본인_사용량을_알_수_있는지_묻는_말과_전화_사용량_질문도_내_정보_질문으로_받는다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"MY_USAGE\"}");

        assertThat(classifier.classify(1L, "이번 달 데이터 아직 얼마 남았는지 알 수 있나요")).isEqualTo(PlanQuestionType.MY_USAGE);
        assertThat(classifier.classify(1L, "이번 달 전화 몇 분 했어요")).isEqualTo(PlanQuestionType.MY_USAGE);
        assertThat(classifier.classify(1L, "남은 데이터는 어떻게 확인할 수 있나요")).isEqualTo(PlanQuestionType.NONE);
    }

    @Test
    void 모델이_목록으로_판정한_사정_설명_문장은_변경_낱말이_있어도_그대로_쓴다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\"}");

        assertThat(classifier.classify(1L, "엄마 폰 바꿔드리려는데 시니어 요금제 뭐 있어요")).isEqualTo(PlanQuestionType.PLAN_CATALOG);
        assertThat(classifier.classify(1L, "시니어 요금제로 바꾸려면 어떻게 해요")).isEqualTo(PlanQuestionType.NONE);
    }

    private static Plan plan(String code, String name) {
        return Plan.builder().code(code).name(name).build();
    }

    @Test
    void 규칙이_요금제_질문으로_보는_표현은_모두_모델_호출_조건을_통과한다() {
        List<String> ruleMatched = List.of(
                "요금제 추천해줘", "나한테 맞는 요금제 알려줘", "내 요금제 뭐야", "현재 요금제 얼마야",
                "지금 해지하면 위약금 얼마예요", "내 약정 언제 끝나요", "이번 달 사용량 알려줘",
                "남은 데이터 얼마야", "통화 얼마나 썼는지 알려줘", "이번 달 예상 요금 알려줘",
                "5G 요금제 종류 알려줘", "제일 싼 요금제 얼마예요");

        for (String question : ruleMatched) {
            assertThat(PlanQuestionRules.classify(question)).as(question).isNotEqualTo(PlanQuestionType.NONE);
            assertThat(PlanQuestionClassifier.mentionsPlanTopic(question)).as(question).isTrue();
        }
    }

    @Test
    void 요금제_이름과_갈아타기_표현도_모델_호출_조건을_통과한다() {
        assertThat(PlanQuestionClassifier.mentionsPlanTopic("5G 스탠다드 얼마예요")).isTrue();
        assertThat(PlanQuestionClassifier.mentionsPlanTopic("5G 쓰는데 더 싼 걸로 갈아타고 싶어요 뭐가 좋을까요")).isTrue();
        assertThat(PlanQuestionClassifier.mentionsPlanTopic("신촌 매장 어디 있어요")).isFalse();
    }
}
