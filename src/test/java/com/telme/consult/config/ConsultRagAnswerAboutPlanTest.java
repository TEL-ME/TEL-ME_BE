package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultRagAnswerAboutPlanTest {

    @Test
    void 판매_중인_요금제_이름이나_요금제라는_말이_있으면_요금제를_가리킨다() {
        PlanRepository plans = mock(PlanRepository.class);
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE))
                .thenReturn(List.of(Plan.builder().code("FIVE_G_STANDARD").name("5G 스탠다드").build()));

        assertThat(ConsultRagAnswerConfiguration.aboutPlan("5g스탠다드 해지 위약금", plans)).isTrue();
        assertThat(ConsultRagAnswerConfiguration.aboutPlan("키즈 요금제 개통 서류", plans)).isTrue();
        assertThat(ConsultRagAnswerConfiguration.aboutPlan("컬러링 해지 방법", plans)).isFalse();
        assertThat(ConsultRagAnswerConfiguration.aboutPlan("데이터 쉐어링 미성년자 신청 가능 여부", plans)).isFalse();
        assertThat(ConsultRagAnswerConfiguration.aboutPlan(" ", plans)).isFalse();
    }
}
