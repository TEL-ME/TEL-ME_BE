package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 정상 답변 아래 추천 질문(followUps). 실제 기본값은 application.yml이 정한다(추천 질문·매장 버튼 켬,
// 답을 못 할 때 버튼 끔). 여기 기본값은 설정 파일 없이 만들 때만 쓰인다. 꺼져 있으면 빈 목록을 저장한다
// (docs/FOLLOWUP_RECOMMENDATION.md 7.2절, 12.1절)
// storeChipEnabled는 매장 찾기 버튼(10절)이다. 추천 질문이 켜져 있을 때만 동작한다
// noAnswerEnabled는 답을 못 할 때 "혹시 이런 내용을 찾으셨나요?" 버튼(11절)이다. 추천 질문이 켜져 있을 때만 동작한다
@ConfigurationProperties(prefix = "telme.consult.suggested-questions")
public record SuggestedQuestionProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean storeChipEnabled,
        @DefaultValue("false") boolean noAnswerEnabled
) {
}
