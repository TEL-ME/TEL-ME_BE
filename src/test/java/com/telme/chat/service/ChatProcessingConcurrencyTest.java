package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.config.ChatProcessingProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ChatProcessingConcurrencyTest {

    @Configuration
    @EnableConfigurationProperties(ChatProcessingProperties.class)
    static class Properties {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Properties.class);

    @Test
    @DisplayName("설정을 주지 않으면 설정화 전과 같이 동시 4개, 대기열 100이다")
    void 기본값은_예전과_같다() {
        runner.run(context -> {
            ChatProcessingProperties properties = context.getBean(ChatProcessingProperties.class);
            assertThat(properties).isEqualTo(new ChatProcessingProperties(4, 100));
            assertThat(properties).isEqualTo(new Binder(new MapConfigurationPropertySource(Map.of()))
                    .bindOrCreate("chat.processing", ChatProcessingProperties.class));
        });
    }

    @Test
    @DisplayName("환경 변수 이름으로 동시 처리 수와 대기열 크기를 바꾼다")
    void 환경_변수로_바꾼다() {
        runner.withPropertyValues("CHAT_PROCESSING_THREADS=16", "CHAT_PROCESSING_QUEUE=300").run(context ->
                assertThat(context.getBean(ChatProcessingProperties.class))
                        .isEqualTo(new ChatProcessingProperties(16, 300)));
    }

    @ParameterizedTest
    @CsvSource({"0,100", "-1,100", "4,-1"})
    @DisplayName("동시 처리 수가 1보다 작거나 대기열이 음수면 앱이 뜨지 않는다")
    void 잘못된_값은_시작할_때_막는다(String threads, String queue) {
        runner.withPropertyValues("chat.processing.threads=" + threads, "chat.processing.queue-capacity=" + queue)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("실행기는 설정한 수만큼 스레드를 고정하고 설정한 대기열을 쓴다")
    void 실행기는_설정값을_쓴다() {
        var executor = ChatProcessingDispatcher.createExecutor(new ChatProcessingProperties(12, 250));
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(12);
            assertThat(executor.getMaxPoolSize()).isEqualTo(12);
            assertThat(executor.getQueueCapacity()).isEqualTo(250);
            assertThat(executor.getThreadNamePrefix()).isEqualTo(ChatProcessingDispatcher.THREAD_NAME_PREFIX);
        } finally {
            executor.shutdown();
        }
    }
}
