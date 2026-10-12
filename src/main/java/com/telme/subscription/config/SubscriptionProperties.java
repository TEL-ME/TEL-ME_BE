package com.telme.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 시연 모드: 가입·변경 즉시 적용, 가입 당월·월 1회 변경 제한 생략. 끄면 정책대로 동작(가입은 다음 날 09:00 개통, 변경은 다음 날 0시 적용)
@ConfigurationProperties(prefix = "telme.subscription")
public record SubscriptionProperties(@DefaultValue("false") boolean demoMode) {
}
