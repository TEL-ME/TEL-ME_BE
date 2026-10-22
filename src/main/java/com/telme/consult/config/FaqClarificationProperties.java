package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FAQ 되묻기. 기본으로 켜고, 문제가 생기면 환경 변수로 끈다.
// enabled를 끄면 요금제 추천의 나이·사용량 되묻기도 함께 꺼진다. FAQ 조건만 묻지 않으려면 extractConditions를 끈다
@ConfigurationProperties(prefix = "telme.consult.faq-clarification")
public record FaqClarificationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean bedrock,
        @DefaultValue("false") boolean extractConditions) {
}
