package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.config.ChatQuestionResolutionProperties;
import com.telme.chat.config.ChatSessionTitleProperties;
import com.telme.chat.config.ChatSummaryProperties;
import com.telme.consult.config.EvidenceResolverProperties;
import com.telme.consult.config.FaqClarificationProperties;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.intent.config.RoutingLlmProperties;
import com.telme.rag.config.RagAnswerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

// 모델 전환 플래그의 기본값이 application.yml과 설정 클래스의 @DefaultValue에서 같은지, 정한 기본값이 맞는지 확인한다
class BedrockFlagDefaultsTest {

    @Configuration
    @EnableConfigurationProperties({RoutingLlmProperties.class, ChatQuestionResolutionProperties.class,
            PlanQuestionProperties.class, FaqClarificationProperties.class, RagAnswerProperties.class,
            EvidenceResolverProperties.class, ChatSessionTitleProperties.class, ChatSummaryProperties.class,
            ChatEndSummaryProperties.class})
    static class Properties {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Properties.class);

    @Test
    @DisplayName("application.yml의 기본값과 설정 클래스의 기본값이 같다")
    void yml과_설정_클래스의_기본값이_같다() {
        runner.run(context -> {
            assertThat(context.getBean(RoutingLlmProperties.class)).isEqualTo(classDefault("telme.routing", RoutingLlmProperties.class));
            assertThat(context.getBean(ChatQuestionResolutionProperties.class))
                    .isEqualTo(classDefault("chat.question-resolution", ChatQuestionResolutionProperties.class));
            assertThat(context.getBean(PlanQuestionProperties.class))
                    .isEqualTo(classDefault("telme.consult.plan-question", PlanQuestionProperties.class));
            // enabled는 시험 설정(src/test/resources/application.properties)이 끄므로 전환 플래그만 비교한다
            assertThat(context.getBean(FaqClarificationProperties.class).bedrock())
                    .isEqualTo(classDefault("telme.consult.faq-clarification", FaqClarificationProperties.class).bedrock());
            assertThat(context.getBean(RagAnswerProperties.class)).isEqualTo(classDefault("rag.answer", RagAnswerProperties.class));
            assertThat(context.getBean(ChatSessionTitleProperties.class))
                    .isEqualTo(classDefault("chat.session-title", ChatSessionTitleProperties.class));
            assertThat(context.getBean(ChatSummaryProperties.class))
                    .isEqualTo(classDefault("chat.summary", ChatSummaryProperties.class));
            assertThat(context.getBean(EvidenceResolverProperties.class))
                    .isEqualTo(classDefault("telme.consult.evidence-resolver", EvidenceResolverProperties.class));
            assertThat(context.getBean(ChatEndSummaryProperties.class).bedrock())
                    .isEqualTo(classDefault("chat.end-summary", ChatEndSummaryProperties.class).bedrock());
        });
    }

    @Test
    @DisplayName("기본은 호출 8개 모두 gpt-oss(Bedrock)이고 요청 개수 판정은 하지 않는다")
    void 기본은_모두_gpt_oss이고_요청_개수_판정은_하지_않는다() {
        runner.run(context -> {
            RoutingLlmProperties routing = context.getBean(RoutingLlmProperties.class);
            assertThat(routing.bedrock()).isTrue();
            assertThat(routing.requestInventoryEnabled()).isNull();
            assertThat(context.getBean(ChatQuestionResolutionProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(PlanQuestionProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(FaqClarificationProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(RagAnswerProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(ChatSessionTitleProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(ChatSummaryProperties.class).bedrock()).isTrue();
            assertThat(context.getBean(ChatEndSummaryProperties.class).bedrock()).isTrue();
            EvidenceResolverProperties evidence = context.getBean(EvidenceResolverProperties.class);
            assertThat(evidence.comparisonBedrock()).isTrue();
            assertThat(evidence.candidateBedrock()).isTrue();
        });
    }

    @Test
    @DisplayName("환경 변수 이름으로 호출마다 끌 수 있다")
    void 환경_변수_이름으로_호출마다_끌_수_있다() {
        runner.withPropertyValues(
                "ROUTING_BEDROCK=false", "CHAT_QUESTION_RESOLUTION_BEDROCK=false", "CONSULT_PLAN_QUESTION_BEDROCK=false",
                "CONSULT_FAQ_CLARIFICATION_BEDROCK=false", "RAG_ANSWER_BEDROCK=false", "CHAT_SESSION_TITLE_BEDROCK=false",
                "CHAT_SUMMARY_BEDROCK=false", "CHAT_END_SUMMARY_BEDROCK=false").run(context -> {
            // 환경 변수는 application.yml의 ${...} 자리표시자가 읽는다
            assertThat(context.getBean(RoutingLlmProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(ChatQuestionResolutionProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(PlanQuestionProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(FaqClarificationProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(RagAnswerProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(ChatSessionTitleProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(ChatSummaryProperties.class).bedrock()).isFalse();
            assertThat(context.getBean(ChatEndSummaryProperties.class).bedrock()).isFalse();
        });
    }

    private static <T> T classDefault(String prefix, Class<T> type) {
        return new Binder(new MapConfigurationPropertySource(Map.of())).bindOrCreate(prefix, type);
    }
}
