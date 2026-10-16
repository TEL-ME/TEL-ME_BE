package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtractionClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 조건을 뽑는다")
    void 꺼져_있으면_엑사원이다() {
        var properties = new FaqClarificationProperties(true, false);

        assertThat(ConsultRagAnswerConfiguration.extractionClient(properties, exaone, bedrock)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 뽑는다")
    void bedrock이_없으면_엑사원이다() {
        var properties = new FaqClarificationProperties(true, true);

        assertThat(ConsultRagAnswerConfiguration.extractionClient(properties, exaone, null)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 뽑고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이다() {
        var properties = new FaqClarificationProperties(true, true);

        assertThat(ConsultRagAnswerConfiguration.extractionClient(properties, exaone, bedrock))
                .isInstanceOf(FallbackLlmClient.class);
    }

    @Test
    @DisplayName("설정을 주지 않으면 조건 뽑기는 엑사원이다")
    void 기본값은_엑사원이다() {
        assertThat(new FaqClarificationProperties(true, false).bedrockExtraction()).isFalse();
    }
}
