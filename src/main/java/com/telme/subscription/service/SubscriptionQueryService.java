package com.telme.subscription.service;

import com.telme.subscription.converter.SubscriptionConverter;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.entity.Subscription;
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

    public SubscriptionMeResponse getMine(Long userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)
                .map(subscription -> SubscriptionMeResponse.of(
                        subscriptionConverter.toResponse(stateReader.read(subscription, calendar.now()))))
                .orElseGet(SubscriptionMeResponse::notSubscribed);
    }
}
