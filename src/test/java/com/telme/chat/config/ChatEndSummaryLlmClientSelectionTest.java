package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ChatEndSummaryLlmClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 요약한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(ChatEndSummaryConfiguration.summaryClient(properties(false), exaone, bedrock, mapper))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 요약한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(ChatEndSummaryConfiguration.summaryClient(properties(true), exaone, null, mapper))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("켜져 있으면 Bedrock에는 스키마를 지시문으로 붙여 보내고, 실패하면 엑사원에 원래 요청을 보낸다")
    void 켜져_있으면_bedrock_먼저다() {
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
        when(exaone.generate(any())).thenReturn("{}");
        var client = ChatEndSummaryConfiguration.summaryClient(properties(true), exaone, bedrock, mapper);
        var request = LlmRequest.builder().userPrompt("상담 자료")
                .responseSchema(Map.of("type", "object")).build();

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        assertThat(client.generate(request)).isEqualTo("{}");

        var sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(bedrock).generate(sent.capture());
        assertThat(sent.getValue().userPrompt())
                .isEqualTo("상담 자료\n\n다음 JSON Schema를 따르는 JSON 객체 하나만 출력하세요.\n{\"type\":\"object\"}");
        verify(exaone).generate(request);
    }

    @Test
    @DisplayName("설정을 주지 않으면 gpt-oss(Bedrock)이고, false를 주면 엑사원이다")
    void 기본은_gpt_oss이고_설정으로_끈다() {
        assertThat(bind(Map.of()).bedrock()).isTrue();
        assertThat(bind(Map.of("chat.end-summary.bedrock", "false")).bedrock()).isFalse();
    }

    private static ChatEndSummaryProperties properties(boolean bedrock) {
        return new ChatEndSummaryProperties(true, true, true, 3072, 768, 400, 8, 2,
                Duration.ofMinutes(5), Duration.ofMinutes(15), bedrock);
    }

    private static ChatEndSummaryProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("chat.end-summary", ChatEndSummaryProperties.class);
    }
}
