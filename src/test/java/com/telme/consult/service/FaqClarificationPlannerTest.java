package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqClarificationPlannerTest {

    private static final String ANSWER =
            "요금제에 따라 다릅니다. 5G 요금제는 앱에서, LTE 요금제는 고객센터에서 신청하실 수 있습니다.";

    private final LlmClient client = mock(LlmClient.class);
    private final FaqClarificationPlanner planner =
            new FaqClarificationPlanner(new ConditionExtractor(client, new ObjectMapper()));

    private ClarificationPlan plan() {
        return planner.plan(1L, "로밍 신청하고 싶어요", List.of(
                new FaqSearchResponse(1L, null, "로밍", "로밍은 어떻게 신청하나요?", ANSWER,
                        0.9, 1, null, 1, null)));
    }

    @Test
    @DisplayName("조건을 뽑으면 먼저 물을 조건을 돌려준다")
    void 되물을_조건을_정한다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":["5G","LTE"],"evidence":"5G 요금제는 앱에서, LTE 요금제는 고객센터에서"}]}""");

        ClarificationPlan plan = plan();

        assertThat(plan.needsClarification()).isTrue();
        assertThat(plan.first().key()).isEqualTo("plan_type");
        assertThat(plan.first().options()).containsExactly("5G", "LTE");
    }

    @Test
    @DisplayName("조건이 없으면 되묻지 않는다")
    void 조건이_없으면_되묻지_않는다() {
        when(client.generate(any())).thenReturn("{\"conditions\":[]}");

        assertThat(plan().needsClarification()).isFalse();
    }

    @Test
    @DisplayName("모델 호출이 실패해도 되묻지 않고 답변으로 넘어간다")
    void 호출_실패에도_답변을_막지_않는다() {
        when(client.generate(any())).thenThrow(new RuntimeException("연결 실패"));

        assertThat(plan().needsClarification()).isFalse();
    }

    @Test
    @DisplayName("되물을 조건이 없는데 꺼내면 막는다")
    void 빈_계획에서_조건을_못_꺼낸다() {
        assertThatThrownBy(() -> ClarificationPlan.none().first())
                .isInstanceOf(IllegalStateException.class);
    }
}
