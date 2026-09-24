package com.telme.consult.config;

import com.telme.consult.repository.PendingClarificationFinder;
import com.telme.consult.service.ChatCommandContextProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.FollowupContextService;
import com.telme.consult.service.FollowupSelectionValidator;
import com.telme.chat.service.ChatContextBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
public class FollowupConfiguration {
    @Bean
    PendingClarificationFinder pendingClarificationFinder(JdbcTemplate jdbc) {
        return new PendingClarificationFinder(jdbc);
    }

    @Bean
    FollowupContextService followupContextService(
            JdbcTemplate jdbc,
            PendingClarificationFinder finder,
            PlatformTransactionManager manager) {
        return new FollowupContextService(jdbc, finder, new TransactionTemplate(manager));
    }

    @Bean
    FollowupSelectionValidator followupSelectionValidator() {
        return new FollowupSelectionValidator();
    }

    @Bean
    ContextProvider consultContextProvider(
            JdbcTemplate jdbc,
            FollowupContextService contexts,
            PlatformTransactionManager manager,
            ChatContextBuilder chatContextBuilder) {
        return new ChatCommandContextProvider(
                jdbc, contexts, new TransactionTemplate(manager), chatContextBuilder);
    }
}
