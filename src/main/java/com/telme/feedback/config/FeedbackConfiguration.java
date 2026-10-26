package com.telme.feedback.config;

import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatFeedbackReader;
import com.telme.feedback.api.ChatFeedbackActorResolver;
import com.telme.feedback.api.ChatFeedbackReaderAdapter;
import com.telme.feedback.api.VerifiedFeedbackActorResolver;
import com.telme.feedback.repository.FeedbackStore;
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
    FeedbackStore feedbackStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        // 다른 모듈(회원 승계 등)이 FeedbackStore를 빈으로 찾아 선택 주입할 수 있도록 별도로 노출
        return new JdbcFeedbackStore(jdbc, new TransactionTemplate(manager));
    }

    @Bean
    FeedbackService feedbackService(
            FeedbackStore feedbackStore,
            VerifiedFeedbackActorResolver verifiedIdentityRequired) {
        // 인증 어댑터 없는 활성화를 막기 위해 사용하지 않더라도 주입을 유지한다.
        return new FeedbackService(feedbackStore);
    }

    @Bean
    ChatFeedbackReader chatFeedbackReader(FeedbackService feedbackService) {
        return new ChatFeedbackReaderAdapter(feedbackService);
    }
}
