package com.telme.consult.service;

import static com.telme.consult.service.PlanTaskOutcome.reply;
import static com.telme.consult.service.PlanTaskSupport.MY_PLAN_QUESTION;

import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.PausePolicy;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Duration;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 일시 정지. 채팅에서 정지하지 않고, 정지할 수 없는 상태면 이유를, 할 수 있으면 기간 한도·요금 영향(기간을 말했으면 끝나는 날)을 알려주고 내 요금제 화면으로 안내한다
@Component
@RequiredArgsConstructor
public class PlanPauseAnswerer implements PlanTaskHandler {

    private final SubscriptionQueryService queryService;
    private final SubscriptionCalendar calendar;

    @Override
    public PlanTask task() {
        return PlanTask.PAUSE;
    }

    @Override
    public PlanTaskOutcome answer(Long userId, String original, String resolved) {
        if (userId == null) {
            return reply("일시 정지는 " + PlanTaskSupport.LOGIN_REQUIRED);
        }
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed()) {
            return reply("가입한 요금제가 없어서 일시 정지할 수 없어요.", PlanTaskSupport.CATALOG_QUESTION);
        }
        SubscriptionResponse subscription = mine.subscription();
        if (subscription.currentPlan() == null) {
            return reply("개통 전에는 일시 정지할 수 없어요. 개통된 뒤 다시 말씀해 주세요.", MY_PLAN_QUESTION);
        }
        if (subscription.pause() != null) {
            return reply("이미 일시 정지 중이에요. %s에 자동으로 다시 쓸 수 있고, 그 전에 풀려면 내 요금제 화면에서 해제할 수 있어요."
                    .formatted(PlanTaskSupport.hour(subscription.pause().endsAt())), MY_PLAN_QUESTION);
        }
        OptionalInt days = PlanTaskRules.pauseDays(original);
        if (days.isEmpty()) {
            days = PlanTaskRules.pauseDays(resolved);
        }
        return reply("일시 정지는 내 요금제 화면에서 신청할 수 있어요. %s한 번에 1일부터 %d일까지, 1년에 최대 %d일까지 정지할 수 있고,"
                        .formatted(period(days), PausePolicy.MAX_DAYS_PER_PAUSE, PausePolicy.MAX_DAYS_PER_YEAR)
                        + " 정지 기간에는 기본료의 %d%%만 내요. 기간이 끝나면 자동으로 다시 쓸 수 있어요."
                        .formatted(PausePolicy.REDUCED_FEE_PERCENT),
                PlanTaskLinks.PAUSE);
    }

    // 말한 기간으로 오늘 정지하면 끝나는 날. 한 번에 정지할 수 있는 기간을 넘으면 그 점을 먼저 알려준다
    private String period(OptionalInt days) {
        if (days.isEmpty()) {
            return "";
        }
        if (days.getAsInt() < 1 || days.getAsInt() > PausePolicy.MAX_DAYS_PER_PAUSE) {
            return "%d일은 한 번에 정지할 수 있는 기간을 넘어요. ".formatted(days.getAsInt());
        }
        return "오늘 %d일 정지하면 %s까지예요. ".formatted(days.getAsInt(),
                PlanTaskSupport.day(calendar.now().plus(Duration.ofDays(days.getAsInt()))));
    }
}
