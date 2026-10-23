package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// FAQ 되묻기. 되묻기 경로(enabled)와 조건 뽑기 모델 선택(bedrock)은 기본으로 켜고, FAQ 근거에서 조건 뽑기(extractConditions)는
// 기본으로 끈다(TELME-218). enabled를 끄면 요금제 추천의 나이·사용량 되묻기도 함께 꺼진다
@ConfigurationProperties(prefix = "telme.consult.faq-clarification")
public record FaqClarificationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean bedrock,
        @DefaultValue("false") boolean extractConditions) {
}
