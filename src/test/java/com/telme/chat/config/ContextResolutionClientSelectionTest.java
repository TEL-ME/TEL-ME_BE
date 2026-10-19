package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatQuestionResolutionProperties.Mode;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextResolutionClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 문맥을 판정한다")
    void 꺼져_있으면_엑사원이다() {
        var properties = new ChatQuestionResolutionProperties(Mode.REGEX_GATED, false, false);

        assertThat(ChatQuestionResolutionLlmConfiguration.resolutionClient(properties, exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 판정한다")
    void bedrock이_없으면_엑사원이다() {
        var properties = new ChatQuestionResolutionProperties(Mode.REGEX_GATED, false, true);

        assertThat(ChatQuestionResolutionLlmConfiguration.resolutionClient(properties, exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 판정하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var properties = new ChatQuestionResolutionProperties(Mode.REGEX_GATED, false, true);
        var client = ChatQuestionResolutionLlmConfiguration.resolutionClient(properties, exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("엑사원 응답");
        var request = LlmRequest.builder()
                .taskType(LlmGeneration.TaskType.CONTEXT_RESOLUTION).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
    }
}
