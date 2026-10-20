package com.telme.consult.service;

import static com.telme.consult.service.PlanTaskOutcome.reply;
import static com.telme.consult.service.PlanTaskSupport.CATALOG_QUESTION;
import static com.telme.consult.service.PlanTaskSupport.MY_PLAN_QUESTION;
import static com.telme.consult.service.PlanTaskSupport.topicParticle;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.service.ContractPolicy;
import com.telme.subscription.service.SubscriptionQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 신규 신청. 채팅에서 가입시키지 않고, 가입 조건(약정·개통 시점)을 알려주고 요금제 가입 화면으로 안내한다.
// 이미 가입했으면 신청이 아니라 변경이라 변경 화면으로 안내하고 섞지 않는다
@Component
@RequiredArgsConstructor
public class PlanSubscribeAnswerer implements PlanTaskHandler {

    private final PlanRepository planRepository;
    private final SubscriptionQueryService queryService;
    private final SubscriptionProperties subscriptionProperties;

    @Override
    public PlanTask task() {
        return PlanTask.SUBSCRIBE;
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved) {
        return answer(userId, original, resolved, null);
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved, String recommended) {
        if (userId == null) {
            return reply("요금제 신청은 " + PlanTaskSupport.LOGIN_REQUIRED);
        }
        List<Plan> named = PlanTaskSupport.namedPlans(
                planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE), original, resolved, recommended);
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (mine.subscribed()) {
            var current = mine.subscription().currentPlan() != null ? mine.subscription().currentPlan()
                    : mine.subscription().pendingActivation() == null ? null : mine.subscription().pendingActivation().plan();
            if (named.size() == 1 && current != null && current.planId().equals(named.getFirst().getPlanId())) {
                return reply("%s%s 이미 가입하신 요금제예요.".formatted(current.name(), topicParticle(current.name())),
                        MY_PLAN_QUESTION);
            }
            return reply("이미 가입한 요금제가 있어서 새로 신청할 수 없어요. 다른 요금제로 바꾸려면 요금제 변경 화면에서 진행해 주세요.",
                    PlanTaskLinks.CHANGE);
        }
        if (named.size() != 1) {
            return reply("요금제 가입 화면에서 원하는 요금제를 골라 신청해 주세요. " + terms(), PlanTaskLinks.SUBSCRIBE,
                    CATALOG_QUESTION);
        }
        Plan target = named.getFirst();
        return reply("%s(월 %s, 데이터 %s)%s 요금제 가입 화면에서 신청할 수 있어요. %s%s".formatted(target.getName(),
                        PlanFormat.money(target.getMonthlyFee()), PlanFormat.data(target.isUnlimited(), target.getDataGb()),
                        topicParticle(target.getName()), terms(), PlanChangeAnswerer.ageNote(target)),
                PlanTaskLinks.subscribeTo(target.getName()));
    }

    private String terms() {
        String activation = subscriptionProperties.demoMode() ? "신청하면 바로 개통돼요." : "신청하면 다음 날 오전 9시에 개통돼요.";
        return "약정 없이 가입하거나 %d개월 약정으로 월정액 %d%% 할인을 받을 수 있고, %s"
                .formatted(ContractPolicy.CONTRACT_MONTHS, ContractPolicy.DISCOUNT_PERCENT, activation);
    }
}
