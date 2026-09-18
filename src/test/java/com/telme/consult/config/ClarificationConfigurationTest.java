package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.service.ClarificationTextGenerator;
import com.telme.consult.service.DialogueService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClarificationConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ClarificationConfiguration.class, DialogueService.class);

    @Test
    void defaultTemplateWiresServiceWithoutModelOrDatabase() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(ClarificationTextGenerator.class);
                    assertThat(context).hasSingleBean(DialogueService.class);
                    var prompt =
                            new ClarificationTextGenerator.ClarificationPrompt("", "", "기본 질문");
                    assertThat(context.getBean(ClarificationTextGenerator.class).generate(prompt))
                            .isEqualTo("기본 질문");
                });
    }

    @Test
    void customAdapterReplacesTemplate() {
        runner.withBean(ClarificationTextGenerator.class, () -> prompt -> "모델 질문")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ClarificationTextGenerator.class);
                            assertThat(
                                            context.getBean(ClarificationTextGenerator.class)
                                                    .generate(
                                                            new ClarificationTextGenerator
                                                                    .ClarificationPrompt(
                                                                    "", "", "기본")))
                                    .isEqualTo("모델 질문");
                        });
    }

    @Test
    void fakeClientKeepsTemplateEvenWhenModelSettingIsEnabled() {
        runner.withPropertyValues("telme.consult.llm-enabled=true")
                .withBean(
                        com.telme.llm.service.LlmClient.class,
                        () -> new com.telme.llm.service.FakeLlmClient())
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ClarificationTextGenerator.class);
                            assertThat(
                                            context.getBean(ClarificationTextGenerator.class)
                                                    .generate(
                                                            new ClarificationTextGenerator
                                                                    .ClarificationPrompt(
                                                                    "", "", "어느 지역인가요?")))
                                    .isEqualTo("어느 지역인가요?");
                        });
    }

    @Test
    void enabledSettingRequiresClient() {
        runner.withPropertyValues("telme.consult.llm-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
