package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PlanQuestionClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 분류한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(PlanQuestionConfiguration.classifierClient(new PlanQuestionProperties(true, true, false), exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 분류한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(PlanQuestionConfiguration.classifierClient(new PlanQuestionProperties(true, true, true), exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 분류하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = PlanQuestionConfiguration.classifierClient(new PlanQuestionProperties(true, true, true), exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("{\"type\":\"NONE\"}");
        var request = LlmRequest.builder().taskType(LlmGeneration.TaskType.ROUTING).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("{\"type\":\"NONE\"}");
    }

    @Test
    @DisplayName("설정을 주지 않으면 꺼져 있고, 설정 이름으로 켠다")
    void 기본은_꺼져_있고_설정으로_켠다() {
        assertThat(bind(Map.of()).bedrock()).isFalse();
        assertThat(bind(Map.of("telme.consult.plan-question.bedrock", "true")).bedrock()).isTrue();
    }

    private static PlanQuestionProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("telme.consult.plan-question", PlanQuestionProperties.class);
    }
}
