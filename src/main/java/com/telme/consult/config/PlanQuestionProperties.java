package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// bedrock: 요금제 질문 분류를 gpt-oss(Bedrock)로 부르고 실패하면 엑사원으로 대체한다
@ConfigurationProperties(prefix = "telme.consult.plan-question")
public record PlanQuestionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean answerEnabled,
        @DefaultValue("false") boolean bedrock) {
}
