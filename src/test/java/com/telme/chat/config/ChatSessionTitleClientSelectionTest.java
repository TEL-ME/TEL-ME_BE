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

class ChatSessionTitleClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 제목을 만든다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(ChatSessionTitleLlmConfiguration.titleClient(
                new ChatSessionTitleProperties(30, 512, false), exaone, bedrock)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 만든다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(ChatSessionTitleLlmConfiguration.titleClient(
                new ChatSessionTitleProperties(30, 512, true), exaone, null)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 만들고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = ChatSessionTitleLlmConfiguration.titleClient(
                new ChatSessionTitleProperties(30, 512, true), exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("엑사원 제목");
        var request = LlmRequest.builder().taskType(TaskType.SESSION_TITLE).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 제목");
    }

    @Test
    @DisplayName("설정을 주지 않으면 gpt-oss이고 출력 한도는 512이며, 설정 이름으로 끈다")
    void 기본은_켜져_있고_출력_한도는_512다() {
        var defaults = bind(Map.of());
        assertThat(defaults.bedrock()).isTrue();
        assertThat(defaults.maxOutputTokens()).isEqualTo(512);
        assertThat(bind(Map.of("chat.session-title.bedrock", "false")).bedrock()).isFalse();
    }

    private static ChatSessionTitleProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("chat.session-title", ChatSessionTitleProperties.class);
    }
}
