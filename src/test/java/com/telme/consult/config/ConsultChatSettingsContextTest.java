package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.TelmeApplication;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.consult.service.ConsultChatProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

// 상담 설정 네 개는 독립적으로 끌 수 있다. 채팅 처리에 필요한 셋(chat·persistence·rag)이 모두 켜졌을 때만
// 채팅 처리 빈을 등록하고, 하나라도 꺼지면 기동은 되고 채팅은 처리 구현 없음(AI_NOT_CONNECTED)으로 실패한다
class ConsultChatSettingsContextTest {

    @ParameterizedTest(name = "persistence={0}, llm={1}, chat={2}, rag={3} → 채팅 처리 {4}")
    @CsvSource({
            "true,  true,  true,  true,  true",
            "true,  false, true,  true,  true",
            "true,  true,  true,  false, false",
            "false, true,  true,  true,  false",
            "true,  true,  false, true,  false",
            "false, true,  false, true,  false",
            "true,  true,  false, false, false",
            "false, false, false, false, false"
    })
    @DisplayName("상담 설정 조합과 상관없이 기동하고, 필요한 설정이 모두 켜졌을 때만 채팅 처리 빈을 등록한다")
    void startsWithAnyCombination(boolean persistence, boolean llm, boolean chat, boolean rag,
                                  boolean chatProcessing) {
        // properties()는 application.yml보다 우선순위가 낮아 기본값(true)에 덮이므로 실행 인자로 넘긴다
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TelmeApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--telme.consult.persistence-enabled=" + persistence,
                        "--telme.consult.llm-enabled=" + llm,
                        "--telme.consult.chat-integration-enabled=" + chat,
                        "--telme.consult.rag-integration-enabled=" + rag,
                        "--chat.execution.timeout-scheduler-enabled=false")) {
            assertThat(context.getBeansOfType(ChatProcessingPort.class)).hasSize(chatProcessing ? 1 : 0);
            assertThat(context.getBeansOfType(ConsultChatProcessingService.class)).hasSize(chatProcessing ? 1 : 0);
        }
    }
}
