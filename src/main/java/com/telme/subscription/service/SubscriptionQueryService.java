package com.telme.subscription.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.subscription.converter.SubscriptionConverter;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStateReader stateReader;
    private final SubscriptionCalendar calendar;
    private final SubscriptionConverter subscriptionConverter;
    private final ContractPenaltyCalculator penaltyCalculator;

    public SubscriptionMeResponse getMine(Long userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)
                .map(subscription -> SubscriptionMeResponse.of(
                        subscriptionConverter.toResponse(stateReader.read(subscription, calendar.now()))))
                .orElseGet(SubscriptionMeResponse::notSubscribed);
    }

    public ContractPenalty getTerminationPenalty(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)
                .orElseThrow(() -> new GeneralException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
        return penaltyCalculator.calculate(subscription, calendar.now());
    }
}
