package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ChatSummaryClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 요약한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(ChatSummaryLlmConfiguration.summaryClient(properties(false), exaone, bedrock)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 요약한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(ChatSummaryLlmConfiguration.summaryClient(properties(true), exaone, null)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 요약하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = ChatSummaryLlmConfiguration.summaryClient(properties(true), exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("엑사원 요약");
        var request = LlmRequest.builder().taskType(TaskType.SUMMARY).userPrompt("대화").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 요약");
    }

    @Test
    @DisplayName("설정을 주지 않으면 gpt-oss이고, 설정 이름으로 끈다")
    void 기본은_켜져_있다() {
        assertThat(bind(Map.of()).bedrock()).isTrue();
        assertThat(bind(Map.of("chat.summary.bedrock", "false")).bedrock()).isFalse();
    }

    private static ChatSummaryProperties properties(boolean bedrock) {
        return new ChatSummaryProperties(16, 2_048, 8, 1_024, 16, 3_072, 512, true, bedrock);
    }

    private static ChatSummaryProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("chat.summary", ChatSummaryProperties.class);
    }
}
