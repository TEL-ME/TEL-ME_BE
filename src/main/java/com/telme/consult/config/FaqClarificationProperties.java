package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FAQ 되묻기. 켜는 결정 전까지 기본으로 끈다. 꺼져 있으면 지금처럼 바로 답한다
@ConfigurationProperties(prefix = "telme.consult.faq-clarification")
public record FaqClarificationProperties(@DefaultValue("false") boolean enabled) {
}
