package com.telme.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 켜면 가입·변경이 신청 즉시 적용된다(시연용). 끄면 정책대로 가입은 다음 날 09:00 개통, 변경은 다음 날 0시 적용
@ConfigurationProperties(prefix = "telme.subscription")
public record SubscriptionProperties(@DefaultValue("false") boolean instantApply) {
}
