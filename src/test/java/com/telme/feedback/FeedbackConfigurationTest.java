package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.feedback.api.*;
import com.telme.feedback.config.FeedbackConfiguration;
import com.telme.feedback.controller.FeedbackController;
import com.telme.feedback.converter.FeedbackConverter;
import com.telme.feedback.service.FeedbackService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class FeedbackConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            FeedbackConfiguration.class,
                            FeedbackController.class,
                            FeedbackConverter.class)
                    .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                    .withBean(
                            PlatformTransactionManager.class,
                            () -> mock(PlatformTransactionManager.class));

    @Test
    void disabledByDefault() {
        runner.run(
                c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c).doesNotHaveBean(FeedbackController.class);
                });
    }

    @Test
    void enabledWithoutIdentityAdapterFailsStartup() {
        runner.withPropertyValues("telme.feedback.enabled=true")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test
    void enabledWithIdentityAdapterWiresController() {
        runner.withPropertyValues("telme.feedback.enabled=true")
                .withBean(VerifiedFeedbackActorResolver.class, () -> request -> null)
                .run(
                        c -> {
                            assertThat(c).hasNotFailed();
                            assertThat(c).hasSingleBean(FeedbackController.class);
                            assertThat(c).hasSingleBean(FeedbackService.class);
                        });
    }
}
