package com.telme.subscription.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.plan.entity.Plan;
import com.telme.subscription.exception.SubscriptionErrorCode;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionEligibilityChecker {

    static final int YOUTH_MAX_AGE = 18;
    static final int SENIOR_MIN_AGE = 65;
    static final int MAX_PLAUSIBLE_AGE = 120;

    // 저장된 생년월일 우선, 자격이 확인된 경우에만 새로 저장
    public void verifyAndRemember(User user, Plan plan, LocalDate requestedBirthDate, LocalDate today) {
        if (plan.getTargetGroup() == Plan.TargetGroup.GENERAL) {
            return;
        }
        LocalDate birthDate = user.getBirthDate() != null ? user.getBirthDate() : requestedBirthDate;
        if (birthDate == null) {
            throw new GeneralException(SubscriptionErrorCode.BIRTH_DATE_REQUIRED);
        }
        if (birthDate.isAfter(today) || birthDate.isBefore(today.minusYears(MAX_PLAUSIBLE_AGE))) {
            throw new GeneralException(SubscriptionErrorCode.INVALID_BIRTH_DATE);
        }
        int age = Period.between(birthDate, today).getYears();
        boolean eligible = plan.getTargetGroup() == Plan.TargetGroup.YOUTH
                ? age <= YOUTH_MAX_AGE
                : age >= SENIOR_MIN_AGE;
        if (!eligible) {
            throw new GeneralException(SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        }
        user.fillBirthDateIfMissing(birthDate);
    }
}
