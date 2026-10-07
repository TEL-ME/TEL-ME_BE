package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 정상 답변 아래 추천 질문(followUps). 실제 채팅 경로 표본 확인 전까지 기본으로 끈다
// (docs/FOLLOWUP_RECOMMENDATION.md 8절). 꺼져 있으면 지금처럼 빈 목록을 저장한다
@ConfigurationProperties(prefix = "telme.consult.suggested-questions")
public record SuggestedQuestionProperties(
        @DefaultValue("false") boolean enabled
) {
}
