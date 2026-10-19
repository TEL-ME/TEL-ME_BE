package com.telme.consult.service;

import static com.telme.consult.service.PlanTaskOutcome.reply;
import static com.telme.consult.service.PlanTaskSupport.CATALOG_QUESTION;
import static com.telme.consult.service.PlanTaskSupport.objectParticle;

import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.ContractPenalty;
import com.telme.subscription.service.SubscriptionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 해지. 되돌릴 수 없고 위약금이 생길 수 있어 채팅에서 해지하지 않는다.
// 지금 요금제·약정·위약금·바로 끊기는 영향과 함께 사라지는 정지·변경 신청을 먼저 보여주고 내 요금제 화면으로 안내한다
@Component
@RequiredArgsConstructor
public class PlanCancelAnswerer implements PlanTaskHandler {

    private final SubscriptionQueryService queryService;

    @Override
    public PlanTask task() {
        return PlanTask.CANCEL;
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved) {
        if (userId == null) {
            return reply("요금제 해지는 " + PlanTaskSupport.LOGIN_REQUIRED);
        }
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed()) {
            return reply("가입한 요금제가 없어서 해지할 요금제가 없어요.", CATALOG_QUESTION);
        }
        return reply(impact(mine.subscription(), queryService.getTerminationPenalty(userId))
                + " 해지는 내 요금제 화면에서 진행할 수 있어요.", PlanTaskLinks.CANCEL);
    }

    private static String impact(SubscriptionResponse subscription, ContractPenalty penalty) {
        StringBuilder text = new StringBuilder();
        // 개통 전이면 신청한 요금제가 해지 대상이다(기존 해지 규칙과 같다)
        if (subscription.currentPlan() != null) {
            String plan = subscription.currentPlan().name();
            text.append("%s%s 해지하면 바로 끊기고 되돌릴 수 없어요.".formatted(plan, objectParticle(plan)));
        } else {
            String plan = subscription.pendingActivation() == null ? "가입한 요금제" : subscription.pendingActivation().plan().name();
            text.append("개통 예정인 %s%s 해지하면 신청이 바로 취소되고 되돌릴 수 없어요.".formatted(plan, objectParticle(plan)));
        }
        if (penalty.contractMonths() != null) {
            text.append(" %d개월 약정 중 %d개월을 쓰셨어요.".formatted(penalty.contractMonths(), penalty.elapsedMonths()));
        }
        text.append(penalty.penalty() > 0
                ? " 지금 해지하면 위약금 %s이 청구돼요.".formatted(PlanFormat.money(penalty.penalty()))
                : " 위약금은 없어요.");
        if (subscription.pause() != null) {
            text.append(" 일시 정지도 함께 끝나요.");
        }
        if (subscription.pendingChange() != null) {
            text.append(" 적용 전인 요금제 변경 신청도 함께 사라져요.");
        }
        return text.toString();
    }
}
