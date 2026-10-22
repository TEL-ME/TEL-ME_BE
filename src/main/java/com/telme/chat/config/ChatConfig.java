package com.telme.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        ChatExecutionProperties.class,
        ChatProcessingProperties.class,
        ChatContextProperties.class,
        ChatQuestionResolutionProperties.class,
        ChatSummaryProperties.class,
        ChatSessionTitleProperties.class,
        ChatGuestLimitProperties.class
})
public class ChatConfig {
}
