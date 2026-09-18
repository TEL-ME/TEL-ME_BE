package com.telme.feedback.config;

import com.telme.chat.service.ChatActorProvider;
import com.telme.feedback.api.ChatFeedbackActorResolver;
import com.telme.feedback.api.VerifiedFeedbackActorResolver;
import com.telme.feedback.repository.JdbcFeedbackStore;
import com.telme.feedback.service.FeedbackService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telme.feedback.enabled", havingValue = "true")
public class FeedbackConfiguration {
    @Bean
    @ConditionalOnMissingBean(VerifiedFeedbackActorResolver.class)
    VerifiedFeedbackActorResolver feedbackActorResolver(ChatActorProvider provider) {
        return new ChatFeedbackActorResolver(provider);
    }

    @Bean
    FeedbackService feedbackService(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            VerifiedFeedbackActorResolver verifiedIdentityRequired) {
        // 인증 어댑터 없는 활성화를 막기 위해 사용하지 않더라도 주입을 유지한다.
        return new FeedbackService(new JdbcFeedbackStore(jdbc, new TransactionTemplate(manager)));
    }
}
