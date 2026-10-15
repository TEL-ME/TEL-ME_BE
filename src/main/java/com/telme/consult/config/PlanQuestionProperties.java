package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "telme.consult.plan-question")
public record PlanQuestionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean answerEnabled) {
}
