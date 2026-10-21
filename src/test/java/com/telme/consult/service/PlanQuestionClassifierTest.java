package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlanQuestionClassifierTest {

    LlmClient llm = mock(LlmClient.class);
    PlanQuestionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PlanQuestionClassifier(llm, new ObjectMapper(), new PlanQuestionProperties(true, true, false));
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
    void 모델이_추천이라고_해도_요금제와_상관없는_추천_질문은_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");

        for (String question : new String[] {"요즘 볼만한 영화 추천해 주세요", "노트북 하나 추천해 주세요", "점심 메뉴 추천해줘",
                "좋은 노래 추천해줘", "맛집 추천해줘", "저렴한 노트북 추천해줘", "이어폰 추천해줘"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.NONE);
        }
    }

    @Test
    void 요금제라는_말이_없어도_요금제를_고르는_추천은_모델_분류를_따른다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"RECOMMENDATION\"}");

        for (String question : new String[] {"데이터 많이 쓰는데 추천해줘", "한 달에 20GB 쓰는데 추천해줘", "청소년인데 추천해줘",
                "싼 거 추천해줘", "저한테 맞는 거 추천해주세요", "5만원 이하 추천해줘", "어머니가 70세인데 추천해줘",
                "뭐가 좋을까요 추천 좀 해줘요", "통신비 줄이고 싶은데 추천해줘"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.RECOMMENDATION);
        }
    }

    @Test
    void 요금제나_제공량_말_없이_하루_요금을_물으면_모델이_목록이라_해도_NONE이다() {
        when(llm.generate(any())).thenReturn("{\"type\":\"PLAN_CATALOG\"}");

        for (String question : new String[] {"요금은 하루에 얼마정도에요", "하루 요금이 얼마예요", "일일 비용이 얼마나 나와요"}) {
            assertThat(classifier.classify(1L, question)).as(question).isEqualTo(PlanQuestionType.NONE);
        }
        // 요금제나 제공량을 함께 말하면 요금제 표로 답한다
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
        classifier = new PlanQuestionClassifier(llm, new ObjectMapper(), new PlanQuestionProperties(false, true, false));

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
