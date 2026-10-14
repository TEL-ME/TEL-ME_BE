package com.telme.consult.service;

import com.telme.global.common.TimeZones;
import com.telme.global.common.exception.GeneralException;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.ContractPenalty;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.usage.service.ExpectedFee;
import com.telme.usage.service.MonthUsage;
import com.telme.usage.service.ResourceUsage;
import com.telme.usage.service.SubscriptionUsageService;
import com.telme.usage.service.UsageOverview;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPlanAnswerer {

    public record MemberAnswer(String content, List<String> followUps) {
    }

    private static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    private static final String RECOMMEND_QUESTION = "나한테 맞는 요금제 추천해줘";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
            .withZone(TimeZones.KST);
    private static final DateTimeFormatter ACTIVATION = DateTimeFormatter.ofPattern("M월 d일 a h시", Locale.KOREAN)
            .withZone(TimeZones.KST);

    private final SubscriptionQueryService queryService;
    private final SubscriptionUsageService usageService;

    // 회원이 아니면 userId는 null. 내 요금제·위약금·사용량 질문이 아니면 null
    public MemberAnswer answer(PlanQuestionType type, Long userId) {
        if (type == PlanQuestionType.MY_USAGE) {
            return usage(userId);
        }
        if (type != PlanQuestionType.MY_SUBSCRIPTION && type != PlanQuestionType.MY_PENALTY) {
            return null;
        }
        boolean penalty = type == PlanQuestionType.MY_PENALTY;
        if (userId == null) {
            return nudge(penalty
                    ? "로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요."
                    : "로그인하시면 가입하신 요금제를 확인해 드릴 수 있어요.");
        }
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed()) {
            return notSubscribed(penalty);
        }
        if (!penalty) {
            return new MemberAnswer(subscription(mine.subscription()), List.of());
        }
        try {
            return new MemberAnswer(penalty(mine.subscription(), queryService.getTerminationPenalty(userId)), List.of());
        } catch (GeneralException subscriptionEnded) {
            return notSubscribed(true);
        }
    }

    private MemberAnswer usage(Long userId) {
        if (userId == null) {
            return nudge("로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요.");
        }
        UsageOverview overview = usageService.getOverview(userId);
        return switch (overview.status()) {
            case NOT_SUBSCRIBED -> nudge("가입한 요금제가 없어서 확인할 사용량이 없어요.");
            case PENDING_ACTIVATION -> nudge("아직 개통 전이라 사용량이 없어요. %s에 개통될 예정이에요."
                    .formatted(ACTIVATION.format(overview.activatesAt())));
            case ACTIVE -> new MemberAnswer(thisMonth(overview.usage()), List.of(RECOMMEND_QUESTION));
        };
    }

    private String thisMonth(MonthUsage usage) {
        StringBuilder text = new StringBuilder("%d월 사용량이에요. ".formatted(usage.month().getMonthValue()));
        text.append(usage.daysUntilBilling() == 0 ? "오늘이 정산일이에요." : "정산일까지 %d일 남았어요.".formatted(usage.daysUntilBilling()));
        text.append("\n- 데이터 ").append(resource(usage.data(), PlanFormat::megabytes))
                .append("\n- 통화 ").append(resource(usage.voice(), minutes -> minutes + "분"))
                .append("\n- 문자 ").append(resource(usage.sms(), count -> count + "건"));
        ExpectedFee fee = usage.expectedFee();
        text.append("\n- 예상 요금 ").append(PlanFormat.money(fee.amount()));
        if (fee.difference() != null && fee.difference() != 0) {
            text.append(" (지난달보다 %s %s)".formatted(PlanFormat.money(Math.abs(fee.difference())),
                    fee.difference() > 0 ? "많아요" : "적어요"));
        }
        if (fee.prorated()) {
            text.append("\n- 이번 달은 날짜별로 나눠 계산했어요 (요금제 변경·약정 만료·개통일 반영)");
        }
        if (Boolean.TRUE.equals(usage.paused())) {
            text.append("\n- 지금은 일시 정지 중이에요");
        }
        return text.toString();
    }

    private static String resource(ResourceUsage usage, LongFunction<String> format) {
        if (usage.unlimited()) {
            return "%s 사용 (무제한)".formatted(format.apply(usage.used()));
        }
        String text = "%s / %s (남은 양 %s)".formatted(format.apply(usage.used()), format.apply(usage.limit()),
                format.apply(usage.remaining()));
        return usage.exceeded() ? text + " - 한도를 넘었어요" : text;
    }

    private MemberAnswer notSubscribed(boolean penalty) {
        return nudge(penalty
                ? "가입한 요금제가 없어서 확인할 약정이 없어요."
                : "아직 가입한 요금제가 없어요.");
    }

    private MemberAnswer nudge(String head) {
        return new MemberAnswer(head + " 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.", List.of(CATALOG_QUESTION));
    }

    private String subscription(SubscriptionResponse subscription) {
        StringBuilder text = new StringBuilder();
        var scheduled = subscription.currentPlan() == null ? subscription.pendingActivation() : null;
        var plan = scheduled != null ? scheduled.plan() : subscription.currentPlan();
        if (scheduled != null) {
            text.append("%s 요금제에 가입하셨어요. %s에 개통될 예정이에요.".formatted(plan.name(), ACTIVATION.format(scheduled.effectiveFrom())));
        } else {
            text.append("지금 가입한 요금제는 %s예요.".formatted(plan.name()));
        }
        text.append("\n- 월정액 ").append(PlanFormat.money(plan.monthlyFee()))
                .append("\n- 데이터 ").append(PlanFormat.data(plan.unlimited(), plan.dataGb()))
                .append("\n- 통화 ").append(PlanFormat.voice(plan.voiceUnlimited(), plan.voiceMinutes()))
                .append("\n- 문자 ").append(PlanFormat.sms(plan.smsUnlimited(), plan.smsCount()));
        var contract = subscription.contract();
        if (contract != null) {
            text.append(contract.active()
                    ? "\n- 약정 %d개월 (월 %s 할인, %s까지)".formatted(
                            contract.months(), PlanFormat.money(contract.monthlyDiscount()), date(contract.endsAt()))
                    : "\n- 약정은 %s에 끝났어요".formatted(date(contract.endsAt())));
        }
        var pause = subscription.pause();
        if (pause != null) {
            text.append("\n- 일시 정지 중이에요 (%s까지, 정지 중 기본료 %s)".formatted(
                    date(pause.endsAt()), PlanFormat.money(pause.reducedMonthlyFee())));
        }
        var change = subscription.pendingChange();
        if (change != null) {
            text.append("\n- %s부터 %s 요금제로 바뀔 예정이에요".formatted(date(change.effectiveFrom()), change.plan().name()));
        }
        return text.toString();
    }

    private String penalty(SubscriptionResponse subscription, ContractPenalty penalty) {
        if (penalty.contractMonths() == null) {
            return "약정 없이 가입하셔서 지금 해지하셔도 위약금은 없어요(0원).";
        }
        var contract = subscription.contract();
        if (subscription.currentPlan() == null) {
            return "아직 개통 전이라 받은 할인이 없어서 지금 해지하셔도 위약금은 없어요(0원).";
        }
        if (!contract.active()) {
            return "약정이 %s에 끝나서 지금 해지하셔도 위약금은 없어요(0원).".formatted(date(contract.endsAt()));
        }
        if (penalty.penalty() == 0) {
            return "약정 중이지만 아직 받은 할인이 없어서 지금 해지하셔도 위약금은 없어요(0원). 약정은 %s에 끝나요."
                    .formatted(date(contract.endsAt()));
        }
        return "약정 %d개월 중 %d개월이 지났어요. 약정은 %s에 끝나요.\n지금 해지하면 위약금은 %s이에요. (그동안 받은 할인 %s의 %d%%)"
                .formatted(penalty.contractMonths(), penalty.elapsedMonths(), date(contract.endsAt()),
                        PlanFormat.money(penalty.penalty()), PlanFormat.money(penalty.receivedDiscount()),
                        penalty.refundPercent());
    }

    private static String date(Instant at) {
        return DATE.format(at);
    }
}
