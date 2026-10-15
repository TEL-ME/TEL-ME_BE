package com.telme.consult.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlanQuestionProperties.class)
public class PlanQuestionConfiguration {
}
