package com.telme.consult.service;

import static com.telme.consult.service.PlanTaskOutcome.reply;
import static com.telme.consult.service.PlanTaskSupport.CATALOG_QUESTION;
import static com.telme.consult.service.PlanTaskSupport.MY_PLAN_QUESTION;
import static com.telme.consult.service.PlanTaskSupport.directionParticle;
import static com.telme.consult.service.PlanTaskSupport.topicParticle;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 요금제 변경. 채팅에서 바꾸지 않고, 바꿀 수 없는 상태(개통 전·일시 정지·변경 대기·가입 당월·월 1회)면 이유를, 바꿀 수 있으면 요금·적용 시점을 알려주고 요금제 변경 화면으로 안내한다
@Component
@RequiredArgsConstructor
public class PlanChangeAnswerer implements PlanTaskHandler {

    private final PlanRepository planRepository;
    private final SubscriptionQueryService queryService;
    private final SubscriptionProperties subscriptionProperties;
    private final SubscriptionHistoryRepository historyRepository;
    private final SubscriptionCalendar calendar;

    @Override
    public PlanTask task() {
        return PlanTask.CHANGE;
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved) {
        return answer(userId, original, resolved, null);
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved, String recommended) {
        if (userId == null) {
            return reply("요금제 변경은 " + PlanTaskSupport.LOGIN_REQUIRED);
        }
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed()) {
            return reply("아직 가입한 요금제가 없어요. 요금제 가입 화면에서 새로 신청할 수 있어요.", PlanTaskLinks.SUBSCRIBE);
        }
        SubscriptionResponse subscription = mine.subscription();
        if (subscription.currentPlan() == null) {
            return reply("개통 전에는 요금제를 바꿀 수 없어요. 개통된 뒤 다시 말씀해 주세요.", MY_PLAN_QUESTION);
        }
        // 확인할 수 있는 제한은 화면으로 보내기 전에 먼저 알려준다
        if (subscription.pause() != null) {
            return reply("일시 정지 중에는 요금제를 바꿀 수 없어요. 정지가 끝난 뒤 다시 말씀해 주세요.", MY_PLAN_QUESTION);
        }
        if (subscription.pendingChange() != null) {
            String pending = subscription.pendingChange().plan().name();
            return reply("이미 %s%s 변경을 신청해 두셨어요. %s부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요."
                    .formatted(pending, directionParticle(pending),
                            PlanTaskSupport.hour(subscription.pendingChange().effectiveFrom())),
                    MY_PLAN_QUESTION);
        }
        String limited = monthlyLimit(subscription);
        if (limited != null) {
            return reply(limited, MY_PLAN_QUESTION);
        }

        Long currentPlanId = subscription.currentPlan().planId();
        List<Plan> named = PlanTaskSupport.namedPlans(
                planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE), original, resolved, recommended);
        // "LTE 맥스에서 5G 라이트로 바꿔줘"처럼 지금 요금제를 함께 말하면 바꿀 요금제만 남긴다
        if (named.size() > 1) {
            named = named.stream().filter(plan -> !plan.getPlanId().equals(currentPlanId)).toList();
        }
        if (named.size() != 1) {
            return reply("요금제 변경 화면에서 바꿀 요금제를 골라 주세요. " + rules(), PlanTaskLinks.CHANGE, CATALOG_QUESTION);
        }
        Plan target = named.getFirst();
        if (currentPlanId.equals(target.getPlanId())) {
            return reply("%s%s 지금 쓰고 계신 요금제예요.".formatted(target.getName(), topicParticle(target.getName())),
                    MY_PLAN_QUESTION);
        }
        return reply("지금 쓰는 %s에서 %s(월 %s, 데이터 %s)%s 바꾸려면 요금제 변경 화면에서 %s%s 골라 주세요. %s%s"
                        .formatted(subscription.currentPlan().name(), target.getName(),
                                PlanFormat.money(target.getMonthlyFee()),
                                PlanFormat.data(target.isUnlimited(), target.getDataGb()),
                                directionParticle(target.getName()), target.getName(),
                                PlanTaskSupport.objectParticle(target.getName()), rules(), ageNote(target)),
                PlanTaskLinks.changeTo(target.getName()));
    }

    // 기존 변경 규칙(SubscriptionCommandService.changePlan)의 가입 당월·월 1회 제한을 같은 기준으로 먼저 본다.
    // 변경이 이미 적용되면 pendingChange가 없어 이력으로 확인한다. 시연 모드는 기존 규칙처럼 두 제한을 생략한다
    private String monthlyLimit(SubscriptionResponse subscription) {
        if (subscriptionProperties.demoMode()) {
            return null;
        }
        Instant now = calendar.now();
        if (subscription.subscribedAt() != null && calendar.isSameMonth(subscription.subscribedAt(), now)) {
            return "가입한 달에는 요금제를 바꿀 수 없어요. 다음 달 1일부터 바꿀 수 있어요.";
        }
        if (historyRepository.existsChangeRequestedBetween(subscription.subscriptionId(),
                calendar.startOfMonth(now), calendar.startOfNextMonth(now))) {
            return "요금제는 한 달에 한 번만 바꿀 수 있어요. 이번 달에는 이미 바꾸셨어서 다음 달 1일부터 다시 바꿀 수 있어요.";
        }
        return null;
    }

    private String rules() {
        return subscriptionProperties.demoMode()
                ? "변경은 바로 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요."
                : "변경은 신청한 다음 날 0시부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요.";
    }

    static String ageNote(Plan plan) {
        return plan.getTargetGroup() == Plan.TargetGroup.GENERAL ? "" : " 나이 확인이 필요한 요금제라 화면에서 생년월일을 입력해요.";
    }
}
