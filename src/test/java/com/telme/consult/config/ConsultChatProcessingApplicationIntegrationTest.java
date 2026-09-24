package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.service.ChatProcessingPort;
import com.telme.consult.service.ConsultChatProcessingService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 전체 애플리케이션에서 상담 처리기만 Chat 비동기 실행 진입점으로 등록되는지 확인한다. */
@SpringBootTest(
        properties = {
            "telme.consult.persistence-enabled=true",
            "telme.consult.chat-integration-enabled=true",
            "telme.consult.rag-integration-enabled=true",
            "telme.chat.pipeline.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=2"
        })
class ConsultChatProcessingApplicationIntegrationTest {

    @Autowired ApplicationContext context;

    @Test
    void registersConsultPipelineAsOnlyChatProcessingPort() {
        var ports = context.getBeansOfType(ChatProcessingPort.class);

        assertThat(ports).hasSize(1);
        assertThat(ports.values().iterator().next())
                .isInstanceOf(ConsultChatProcessingService.class);
    }
}
