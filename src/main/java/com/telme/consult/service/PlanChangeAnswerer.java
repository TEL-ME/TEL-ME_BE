package com.telme.consult.service;

import com.telme.consult.dto.MissingCondition;
import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.plan.entity.Plan;
import com.telme.plan.exception.PlanErrorCode;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.subscription.service.SubscriptionSnapshot;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 채팅의 "5G 라이트로 바꿔줘"를 한 번 확인받은 뒤 기존 요금제 변경(changePlan)으로 처리한다. 신규 가입은 요금제 화면에 맡긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanChangeAnswerer {

    public static final String CONFIRM_KEY = "plan_change_confirm";
    static final String YES = "예";
    static final String NO = "아니요";
    private static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    private static final String MY_PLAN_QUESTION = "내 요금제 알려줘";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EFFECTIVE = DateTimeFormatter.ofPattern("M월 d일 H시", Locale.KOREAN);

    // content와 asks 중 하나만 있다. asks가 있으면 확인 질문을 되물어야 한다
    public record Outcome(String content, List<String> followUps, List<MissingCondition> asks) {
    }

    private final PlanRepository planRepository;
    private final SubscriptionQueryService queryService;
    private final SubscriptionCommandService commandService;
    private final SubscriptionProperties subscriptionProperties;

    // 회원이 아니면 userId는 null. 요금제 이름은 사용자가 쓴 원문과 문맥으로 풀어쓴 문장에서 함께 찾는다
    public Outcome answer(Long userId, String original, String resolved, Map<String, String> confirmed, boolean mayAsk) {
        if (userId == null) {
            return reply("요금제 변경은 로그인한 회원만 할 수 있어요. 로그인한 뒤 다시 말씀해 주세요.");
        }
        List<Plan> selling = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
        // 문맥으로 풀어쓴 문장에는 앞 대화의 다른 요금제가 섞일 수 있어 사용자가 쓴 원문에 이름이 없을 때만 본다
        List<Plan> named = mentionedPlans(selling, original);
        if (named.isEmpty()) {
            named = mentionedPlans(selling, resolved);
        }
        if (named.isEmpty()) {
            return reply("어떤 요금제로 바꿀지 요금제 이름과 함께 말씀해 주세요. 예: \"5G 라이트로 바꿔줘\"", CATALOG_QUESTION);
        }

        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed()) {
            return reply("아직 가입한 요금제가 없어요. 새로 가입하시려면 요금제 화면에서 신청해 주세요.", CATALOG_QUESTION);
        }
        SubscriptionResponse subscription = mine.subscription();
        if (subscription.currentPlan() == null) {
            return reply("개통 전에는 요금제를 바꿀 수 없어요. 개통된 뒤 다시 말씀해 주세요.");
        }
        Long currentPlanId = subscription.currentPlan().planId();
        // "LTE 맥스에서 5G 라이트로 바꿔줘"처럼 지금 요금제를 함께 말하면 바꿀 요금제만 남긴다
        if (named.size() > 1) {
            named = named.stream().filter(plan -> !plan.getPlanId().equals(currentPlanId)).toList();
        }
        if (named.size() > 1) {
            return reply("한 번에 한 요금제로만 바꿀 수 있어요. 바꿀 요금제 하나만 말씀해 주세요.", CATALOG_QUESTION);
        }
        Plan target = named.getFirst();
        if (currentPlanId.equals(target.getPlanId())) {
            return reply("%s%s 지금 쓰고 계신 요금제예요.".formatted(target.getName(), topicParticle(target.getName())),
                    MY_PLAN_QUESTION);
        }
        // 확인을 받고 나서 거절하지 않도록 지금 상태로 알 수 있는 제한은 먼저 알려준다
        if (subscription.pause() != null) {
            return reply("일시 정지 중에는 요금제를 바꿀 수 없어요. 정지가 끝난 뒤 다시 말씀해 주세요.");
        }
        if (subscription.pendingChange() != null) {
            return reply("이미 %s%s 변경을 신청해 두셨어요. %s부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요."
                    .formatted(subscription.pendingChange().plan().name(),
                            directionParticle(subscription.pendingChange().plan().name()),
                            EFFECTIVE.format(subscription.pendingChange().effectiveFrom().atZone(KOREA))),
                    MY_PLAN_QUESTION);
        }

        String decision = confirmed == null ? null : confirmed.get(CONFIRM_KEY);
        if (decision == null) {
            // 실제 계약을 바꾸는 일이라 확인 질문을 할 수 없는 흐름에서는 바꾸지 않는다
            if (!mayAsk) {
                return reply("요금제 변경은 확인을 받은 뒤에 진행해요. 바꿀 요금제 이름과 함께 다시 말씀해 주세요.");
            }
            return new Outcome(null, List.of(), List.of(new MissingCondition(CONFIRM_KEY,
                    confirmation(target, subscription), List.of(YES, NO), null)));
        }
        if (!YES.equals(decision)) {
            return reply("요금제를 바꾸지 않았어요. 필요하시면 언제든 다시 말씀해 주세요.");
        }
        try {
            SubscriptionSnapshot changed = commandService.changePlan(userId, target.getPlanId(), null);
            log.info("[채팅 요금제 변경] 변경 신청 userId={}, planId={}", userId, target.getPlanId());
            return reply(done(target, changed), MY_PLAN_QUESTION);
        } catch (GeneralException exception) {
            log.info("[채팅 요금제 변경] 변경하지 못함 userId={}, planId={}, code={}",
                    userId, target.getPlanId(), exception.getErrorCode().getCode());
            return reply(refusal(exception.getErrorCode(), target));
        } catch (RuntimeException exception) {
            // 그대로 던지면 요금제 답변 경로가 FAQ 답변으로 넘겨 변경 요청에 엉뚱한 답이 나간다
            log.error("[채팅 요금제 변경] 변경 중 오류 userId={}, planId={}", userId, target.getPlanId(), exception);
            return reply("지금은 요금제를 바꾸지 못했어요. 잠시 후 다시 시도하거나 요금제 화면에서 바꿔 주세요.");
        }
    }

    // 추천 답변 아래 버튼 문구. 누르면 그대로 변경 요청으로 분류된다
    public static String changeRequest(String planName) {
        return "%s%s 바꿔줘".formatted(planName, directionParticle(planName));
    }

    // 추천 답변에 지금 요금제 말고 다른 요금제가 하나만 나오면 그 요금제로 바꾸는 버튼 문구를 준다
    public Optional<String> changeButton(Long userId, String recommendation) {
        if (userId == null || recommendation == null) {
            return Optional.empty();
        }
        SubscriptionMeResponse mine = queryService.getMine(userId);
        if (!mine.subscribed() || mine.subscription().currentPlan() == null) {
            return Optional.empty();
        }
        Long currentPlanId = mine.subscription().currentPlan().planId();
        List<Plan> others = mentionedPlans(planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE),
                recommendation).stream().filter(plan -> !plan.getPlanId().equals(currentPlanId)).toList();
        return others.size() == 1 ? Optional.of(changeRequest(others.getFirst().getName())) : Optional.empty();
    }

    private String confirmation(Plan target, SubscriptionResponse subscription) {
        SubscriptionResponse.ContractResponse contract = subscription.contract();
        String fee = PlanFormat.money(target.getMonthlyFee())
                + (contract != null && contract.active() ? "(약정 할인 %d%% 적용 전)".formatted(contract.discountPercent()) : "");
        String timing = subscriptionProperties.demoMode() ? "바로 적용되고" : "신청한 다음 날 0시부터 적용되고";
        return "지금 쓰는 %s에서 %s(월 %s, 데이터 %s)%s 바꿀까요? 변경은 %s, 요금제는 한 달에 한 번만 바꿀 수 있어요."
                .formatted(subscription.currentPlan().name(), target.getName(), fee,
                        PlanFormat.data(target.isUnlimited(), target.getDataGb()),
                        directionParticle(target.getName()), timing);
    }

    private static String done(Plan target, SubscriptionSnapshot changed) {
        if (changed.pendingChange() == null) {
            return "%s%s 바꿨어요. 지금부터 적용돼요.".formatted(target.getName(), directionParticle(target.getName()));
        }
        return "%s%s 변경을 신청했어요. %s부터 적용돼요.".formatted(target.getName(), directionParticle(target.getName()),
                EFFECTIVE.format(changed.pendingChange().effectiveFrom().atZone(KOREA)));
    }

    private static String refusal(BaseErrorCode code, Plan target) {
        if (code == SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED) {
            return "요금제는 한 달에 한 번만 바꿀 수 있어요. 이번 달에는 이미 변경 신청이 있어서 다음 달에 다시 말씀해 주세요.";
        }
        if (code == SubscriptionErrorCode.CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH) {
            return "가입한 달에는 요금제를 바꿀 수 없어요. 다음 달부터 바꿀 수 있어요.";
        }
        if (code == SubscriptionErrorCode.CHANGE_NOT_ALLOWED_WHILE_PAUSED) {
            return "일시 정지 중에는 요금제를 바꿀 수 없어요. 정지가 끝난 뒤 다시 말씀해 주세요.";
        }
        if (code == SubscriptionErrorCode.CHANGE_NOT_ALLOWED_BEFORE_ACTIVATION) {
            return "개통 전에는 요금제를 바꿀 수 없어요. 개통된 뒤 다시 말씀해 주세요.";
        }
        if (code == SubscriptionErrorCode.SAME_PLAN) {
            return "%s%s 지금 쓰고 계신 요금제예요.".formatted(target.getName(), topicParticle(target.getName()));
        }
        if (code == SubscriptionErrorCode.BIRTH_DATE_REQUIRED || code == SubscriptionErrorCode.INVALID_BIRTH_DATE) {
            return "%s%s 나이 확인이 필요한 요금제예요. 요금제 화면에서 생년월일을 입력하고 바꿔 주세요."
                    .formatted(target.getName(), topicParticle(target.getName()));
        }
        if (code == SubscriptionErrorCode.AGE_NOT_ELIGIBLE) {
            return "%s%s 가입 대상 나이가 아니어서 바꿀 수 없어요.".formatted(target.getName(), topicParticle(target.getName()));
        }
        if (code == SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND) {
            return "가입한 요금제가 없어요. 새로 가입하시려면 요금제 화면에서 신청해 주세요.";
        }
        if (code == PlanErrorCode.PLAN_NOT_FOUND) {
            return "%s%s 지금 판매하지 않는 요금제예요.".formatted(target.getName(), topicParticle(target.getName()));
        }
        return "지금은 요금제를 바꾸지 못했어요. 잠시 후 다시 시도하거나 요금제 화면에서 바꿔 주세요.";
    }

    // 띄어쓰기와 대소문자를 무시하고, 음성 인식이 소리 나는 대로 적은 "오지"·"엘티이"도 5G·LTE로 읽는다
    static List<Plan> mentionedPlans(List<Plan> selling, String... texts) {
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (text != null) {
                joined.append(normalize(text)).append('|');
            }
        }
        String haystack = joined.toString();
        List<Plan> found = new ArrayList<>();
        for (Plan plan : selling) {
            if (haystack.contains(normalize(plan.getName()))) {
                found.add(plan);
            }
        }
        // "5G 라이트"를 말했을 때 이름이 그 안에 들어가는 다른 요금제까지 함께 잡히지 않도록 가장 긴 이름만 남긴다
        Set<Plan> distinct = new LinkedHashSet<>(found);
        distinct.removeIf(plan -> found.stream().anyMatch(other -> other != plan
                && normalize(other.getName()).contains(normalize(plan.getName()))));
        return List.copyOf(distinct);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).replace("오지", "5g").replace("엘티이", "lte");
    }

    // 받침이 없거나 ㄹ 받침이면 "로", 그 밖의 받침이면 "으로". 한글이 아닌 글자로 끝나면 "로"
    static String directionParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return "로";
        }
        int finalConsonant = (last - '가') % 28;
        return finalConsonant == 0 || finalConsonant == 8 ? "로" : "으로";
    }

    // 받침이 있으면 "은", 없으면 "는"("5G 프리미엄은", "LTE 맥스는"). 한글이 아닌 글자로 끝나면 "는"
    static String topicParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return "는";
        }
        return (last - '가') % 28 == 0 ? "는" : "은";
    }

    private static Outcome reply(String content, String... followUps) {
        return new Outcome(content, List.of(followUps), null);
    }
}
