package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.member.repository.UserRepository;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.usage.service.SubscriptionUsageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberRecommendationNoPlansTest {

    @Test
    void 판매_중인_요금제가_없으면_오류_없이_안내만_한다() {
        PlanRepository plans = mock(PlanRepository.class);
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of());
        var answerer = new MemberRecommendationAnswerer(mock(SubscriptionQueryService.class),
                mock(SubscriptionUsageService.class), mock(SubscriptionHistoryRepository.class), plans,
                mock(UserRepository.class), mock(SubscriptionCalendar.class));

        var outcome = answerer.recommend(null, Map.of("age_group", "만 19~64세", "heavy_data", "예"), false);

        assertThat(outcome.content()).contains("추천할 수 있는 요금제가 없어요");
        assertThat(outcome.asks()).isNull();
    }
}
