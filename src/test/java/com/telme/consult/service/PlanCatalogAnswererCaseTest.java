package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// DB 없이 입력의 영문 대소문자가 요금제 안내를 바꾸지 않는지 확인한다
class PlanCatalogAnswererCaseTest {

    private final PlanRepository repository = mock(PlanRepository.class);
    private final PlanCatalogAnswerer answerer = new PlanCatalogAnswerer(repository);

    PlanCatalogAnswererCaseTest() {
        when(repository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(
                plan("5G_STANDARD", "5G 스탠다드", Plan.NetworkType.FIVE_G, 65_000, 1),
                plan("5G_LITE", "5G 라이트", Plan.NetworkType.FIVE_G, 45_000, 2),
                plan("LTE_PLUS", "LTE 플러스", Plan.NetworkType.LTE, 49_000, 3),
                plan("BUDGET_MINI", "알뜰 미니", Plan.NetworkType.BUDGET, 15_000, 4)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5G 요금제 알려줘", "5g 요금제 알려줘", "5g요금제알려줘", "5 G 요금제 알려줘"})
    void fiveGQuestionListsOnlyFiveGPlansRegardlessOfCase(String question) {
        assertThat(answerer.answer(question))
                .contains("[5G]", "5G 스탠다드", "5G 라이트")
                .doesNotContain("[LTE]", "[알뜰]", "LTE 플러스", "알뜰 미니");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LTE 요금제 알려줘", "lte 요금제 알려줘", "Lte 요금제 알려줘"})
    void lteQuestionListsOnlyLtePlansRegardlessOfCase(String question) {
        assertThat(answerer.answer(question))
                .contains("[LTE]", "LTE 플러스")
                .doesNotContain("[5G]", "[알뜰]", "5G 스탠다드", "알뜰 미니");
    }

    @Test
    void planNameIsFoundRegardlessOfCase() {
        assertThat(answerer.answer("5g 스탠다드 얼마예요")).contains("5G 스탠다드", "월정액 65,000원")
                .doesNotContain("5G 라이트");
        assertThat(answerer.answer("lte 플러스 가격 알려줘")).contains("LTE 플러스", "월정액 49,000원");
    }

    private static Plan plan(String code, String name, Plan.NetworkType network, int fee, int order) {
        return Plan.builder().code(code).name(name).networkType(network).targetGroup(Plan.TargetGroup.GENERAL)
                .monthlyFee(fee).dataGb(new BigDecimal("50.0")).voiceUnlimited(true).smsUnlimited(true)
                .sortOrder(order).status(Plan.Status.ACTIVE).build();
    }
}
