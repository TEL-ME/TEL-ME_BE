package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FAQ 되묻기. 기본으로 켜고, 문제가 생기면 환경 변수로 끈다.
@ConfigurationProperties(prefix = "telme.consult.faq-clarification")
public record FaqClarificationProperties(@DefaultValue("true") boolean enabled) {
}
