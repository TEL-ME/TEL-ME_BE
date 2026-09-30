package com.telme.faq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.service.FaqEmbeddingTextVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class FaqEmbeddingTextPropertiesTest {

    // 기본 러너는 OS 환경변수를 읽어 FAQ_EMBEDDING_TEXT_VARIANT가 export돼 있으면 기본값 검사가 깨진다
    // MockEnvironment는 OS 환경변수·시스템 프로퍼티를 읽지 않아 withPropertyValues로 준 값만 본다
    private final ApplicationContextRunner runner = new ApplicationContextRunner(() -> {
                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                context.setEnvironment(new MockEnvironment());
                return context;
            })
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(FaqEmbeddingTextProperties.class)
    static class Config {
    }

    @Test
    @DisplayName("설정이 없으면 TELME-58 실험으로 고른 Q_A")
    void 기본값은_최적안이다() {
        runner.run(context -> assertThat(context.getBean(FaqEmbeddingTextProperties.class).variant())
                .isEqualTo(FaqEmbeddingTextVariant.Q_A));
    }

    @Test
    @DisplayName("설정한 안으로 바인딩된다")
    void 설정값이_바인딩된다() {
        runner.withPropertyValues("faq.embedding-text.variant=Q_A_HEAD200")
                .run(context -> assertThat(context.getBean(FaqEmbeddingTextProperties.class).variant())
                        .isEqualTo(FaqEmbeddingTextVariant.Q_A_HEAD200));
    }

    @Test
    @DisplayName("없는 안을 주면 기동이 실패한다 - 오타가 조용히 기본값으로 떨어지지 않게")
    void 잘못된_값이면_기동에_실패한다() {
        runner.withPropertyValues("faq.embedding-text.variant=QUESTION_AND_ANSWER")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("variant가 null이면 생성자가 막는다")
    void null_variant는_거부한다() {
        assertThatThrownBy(() -> new FaqEmbeddingTextProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
