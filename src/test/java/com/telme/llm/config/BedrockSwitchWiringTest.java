package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatQuestionResolutionLlmConfiguration;
import com.telme.chat.config.ChatQuestionResolutionProperties;
import com.telme.chat.config.ChatSessionTitleLlmConfiguration;
import com.telme.chat.config.ChatSessionTitleProperties;
import com.telme.chat.config.ChatSummaryLlmConfiguration;
import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.consult.config.PlanQuestionConfiguration;
import com.telme.consult.service.PlanQuestionClassifier;
import com.telme.intent.config.RoutingLlmConfiguration;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.RagAnswerLlmConfiguration;
import com.telme.rag.service.RagAnswerGenerator;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// 호출별 전환 설정의 이름이 실제로 모델 선택에 이어지는지 스프링 설정으로 확인한다
class BedrockSwitchWiringTest {

    private static final Map<String, String> BEAN_BY_FLAG = Map.of(
            "telme.routing.bedrock", RoutingLlmConfiguration.ROUTING_LLM_CLIENT,
            "chat.question-resolution.bedrock", ChatQuestionResolutionLlmConfiguration.CONTEXT_RESOLUTION_LLM_CLIENT,
            "telme.consult.plan-question.bedrock", PlanQuestionConfiguration.PLAN_QUESTION_LLM_CLIENT,
            "rag.answer.bedrock", RagAnswerLlmConfiguration.RAG_ANSWER_LLM_CLIENT,
            "chat.session-title.bedrock", ChatSessionTitleLlmConfiguration.SESSION_TITLE_LLM_CLIENT,
            "chat.summary.bedrock", ChatSummaryLlmConfiguration.CHAT_SUMMARY_LLM_CLIENT);

    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withUserConfiguration(RoutingLlmConfiguration.class, ChatQuestionResolutionLlmConfiguration.class,
                    PlanQuestionConfiguration.class, RagAnswerLlmConfiguration.class, ChatSessionTitleLlmConfiguration.class,
                    ChatSummaryLlmConfiguration.class, Exaone.class);

    @Configuration
    @EnableConfigurationProperties({ChatQuestionResolutionProperties.class, ChatSessionTitleProperties.class, ChatSummaryProperties.class})
    static class Exaone {
        @Bean
        @Primary
        LlmClient exaone() {
            return mock(LlmClient.class);
        }
    }

    @Configuration
    static class Bedrock {
        @Bean(name = BedrockConfig.JUDGE_LLM_CLIENT, defaultCandidate = false)
        LlmClient bedrock() {
            return mock(LlmClient.class);
        }
    }

    @Test
    @DisplayName("플래그를 켜면 호출마다 Bedrock이 먼저이고 실패하면 엑사원 결과가 나온다")
    void 켜면_호출마다_bedrock_먼저이고_실패하면_엑사원이다() {
        base.withUserConfiguration(Bedrock.class)
                .withPropertyValues(BEAN_BY_FLAG.keySet().stream().map(flag -> flag + "=true").toArray(String[]::new))
                .run(context -> {
                    LlmClient exaone = context.getBean("exaone", LlmClient.class);
                    LlmClient bedrock = context.getBean(BedrockConfig.JUDGE_LLM_CLIENT, LlmClient.class);
                    when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
                    when(exaone.generate(any())).thenReturn("엑사원 응답");
                    LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

                    for (String bean : BEAN_BY_FLAG.values()) {
                        assertThat(context.getBean(bean, LlmClient.class).generate(request))
                                .as(bean).isEqualTo("엑사원 응답");
                    }
                });
    }

    @Test
    @DisplayName("설정을 주지 않으면 호출마다 gpt-oss이고, 실패하면 엑사원이다")
    void 기본은_호출마다_bedrock이다() {
        base.withUserConfiguration(Bedrock.class).run(context -> {
            LlmClient exaone = context.getBean("exaone", LlmClient.class);
            LlmClient bedrock = context.getBean(BedrockConfig.JUDGE_LLM_CLIENT, LlmClient.class);
            when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
            when(exaone.generate(any())).thenReturn("엑사원 응답");
            LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

            for (String bean : BEAN_BY_FLAG.values()) {
                assertThat(context.getBean(bean, LlmClient.class)).as(bean).isNotSameAs(exaone);
                assertThat(context.getBean(bean, LlmClient.class).generate(request)).as(bean).isEqualTo("엑사원 응답");
            }
        });
    }

    @Test
    @DisplayName("플래그를 켜면 Bedrock이 성공할 때 엑사원을 쓰지 않는다")
    void 켜면_bedrock이_성공할_때_엑사원을_쓰지_않는다() {
        base.withUserConfiguration(Bedrock.class)
                .withPropertyValues(BEAN_BY_FLAG.keySet().stream().map(flag -> flag + "=true").toArray(String[]::new))
                .run(context -> {
                    LlmClient bedrock = context.getBean(BedrockConfig.JUDGE_LLM_CLIENT, LlmClient.class);
                    when(bedrock.generate(any())).thenReturn("Bedrock 응답");
                    LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

                    for (String bean : BEAN_BY_FLAG.values()) {
                        assertThat(context.getBean(bean, LlmClient.class).generate(request))
                                .as(bean).isEqualTo("Bedrock 응답");
                    }
                });
    }

    @Test
    @DisplayName("플래그를 끄거나 Bedrock 빈이 없으면 호출마다 엑사원이다")
    void 끄거나_bedrock이_없으면_엑사원이다() {
        base.withUserConfiguration(Bedrock.class)
                .withPropertyValues(BEAN_BY_FLAG.keySet().stream().map(flag -> flag + "=false").toArray(String[]::new))
                .run(context -> {
                    LlmClient exaone = context.getBean("exaone", LlmClient.class);
                    for (String bean : BEAN_BY_FLAG.values()) {
                        assertThat(context.getBean(bean, LlmClient.class)).as(bean).isSameAs(exaone);
                    }
                });
        base.withPropertyValues(BEAN_BY_FLAG.keySet().stream().map(flag -> flag + "=true").toArray(String[]::new))
                .run(context -> {
                    LlmClient exaone = context.getBean("exaone", LlmClient.class);
                    for (String bean : BEAN_BY_FLAG.values()) {
                        assertThat(context.getBean(bean, LlmClient.class)).as(bean).isSameAs(exaone);
                    }
                });
    }

    @Test
    @DisplayName("호출하는 쪽이 이름 붙은 클라이언트를 받아야 플래그가 실제로 적용된다")
    void 호출하는_쪽이_이름_붙은_클라이언트를_받는다() throws Exception {
        assertThat(qualifierOfLlmClient(QueryRoutingService.class)).isEqualTo(RoutingLlmConfiguration.ROUTING_LLM_CLIENT);
        assertThat(qualifierOfLlmClient(ChatQuestionResolver.class))
                .isEqualTo(ChatQuestionResolutionLlmConfiguration.CONTEXT_RESOLUTION_LLM_CLIENT);
        assertThat(qualifierOfLlmClient(PlanQuestionClassifier.class)).isEqualTo(PlanQuestionConfiguration.PLAN_QUESTION_LLM_CLIENT);
        assertThat(qualifierOfLlmClient(RagAnswerGenerator.class)).isEqualTo(RagAnswerLlmConfiguration.RAG_ANSWER_LLM_CLIENT);
        assertThat(qualifierOfLlmClient(Class.forName("com.telme.chat.service.ChatSessionTitleService")))
                .isEqualTo(ChatSessionTitleLlmConfiguration.SESSION_TITLE_LLM_CLIENT);
        assertThat(qualifierOfLlmClient(com.telme.chat.service.ChatSummaryService.class))
                .isEqualTo(ChatSummaryLlmConfiguration.CHAT_SUMMARY_LLM_CLIENT);
    }

    // 스프링이 쓰는 생성자(@Autowired가 붙었거나 하나뿐인 생성자)에서 LlmClient 매개변수의 @Qualifier 이름
    private static String qualifierOfLlmClient(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Constructor<?> constructor = Arrays.stream(constructors)
                .filter(candidate -> candidate.isAnnotationPresent(Autowired.class)).findFirst()
                .orElse(constructors.length == 1 ? constructors[0] : null);
        assertThat(constructor).as(type.getSimpleName()).isNotNull();
        return Arrays.stream(constructor.getParameters())
                .filter(parameter -> parameter.getType() == LlmClient.class)
                .map(parameter -> parameter.getAnnotation(Qualifier.class))
                .map(Qualifier::value).findFirst().orElse(null);
    }
}
