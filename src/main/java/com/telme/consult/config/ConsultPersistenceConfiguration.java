package com.telme.consult.config;

import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.service.ConsultService;
import com.telme.consult.service.DialogueService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
// 상담 저장을 사용할 때만 등록한다.
@ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
public class ConsultPersistenceConfiguration {
    @Bean
    JdbcConsultStateStore consultStateStore(
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcConsultStateStore(jdbc, new TransactionTemplate(transactionManager));
    }

    @Bean
    ConsultService consultService(
            JdbcConsultStateStore stateStore, DialogueService dialogueService) {
        return new ConsultService(stateStore, dialogueService);
    }

}
