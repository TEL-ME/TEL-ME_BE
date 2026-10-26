package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ExtractionClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    private static final List<FaqSearchResponse> SOURCES = List.of(new FaqSearchResponse(1L, null, "번호이동",
            "번호이동 하려면 뭐 가져가요", "신분증만 있으면 됩니다. 밀린 요금이 있으면 먼저 내셔야 합니다.", 0.9, 1, null, 1, null));

    @Test
    @DisplayName("조건 뽑기를 켜면 조건 뽑기 모델을 불러 되물을 조건을 정한다")
    void 조건_뽑기를_켜면_모델로_조건을_뽑는다() {
        when(exaone.generate(any())).thenReturn("""
                {"conditions":[{"key":"unpaid_bill","question":"현재 밀린 요금이 있으신가요?",
                 "options":["예","아니요"],"evidence":"밀린 요금이 있으면 먼저 내셔야 합니다"}]}""");
        var planner = ConsultRagAnswerConfiguration.clarificationPlanner(
                new FaqClarificationProperties(true, false, true), exaone, () -> bedrock);

        assertThat(planner.plan(1L, "번호이동 하고 싶어요", SOURCES).first().key()).isEqualTo("unpaid_bill");
        verify(exaone).generate(any());
    }

    @Test
    @DisplayName("조건 뽑기를 끄면 Bedrock 클라이언트를 꺼내지 않고 모델도 부르지 않는다")
    void 조건_뽑기를_끄면_모델을_부르지_않는다() {
        Supplier<LlmClient> bedrockSupplier = () -> {
            throw new AssertionError("조건을 뽑지 않으면 조건 뽑기 클라이언트를 만들지 않아야 합니다.");
        };
        var planner = ConsultRagAnswerConfiguration.clarificationPlanner(
                new FaqClarificationProperties(true, true, false), exaone, bedrockSupplier);

        assertThat(planner.plan(1L, "번호이동 하고 싶어요", SOURCES).needsClarification()).isFalse();
        verifyNoInteractions(exaone, bedrock);
    }

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 조건을 뽑는다")
    void 꺼져_있으면_엑사원이다() {
        var properties = new FaqClarificationProperties(true, false, true);

        assertThat(ConsultRagAnswerConfiguration.extractionClient(properties, exaone, bedrock)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 뽑는다")
    void bedrock이_없으면_엑사원이다() {
        var properties = new FaqClarificationProperties(true, true, true);

        assertThat(ConsultRagAnswerConfiguration.extractionClient(properties, exaone, null)).isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 뽑고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이다() {
        var properties = new FaqClarificationProperties(true, true, true);

        var client = ConsultRagAnswerConfiguration.extractionClient(properties, exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("엑사원 응답");

        assertThat(client.generate(LlmRequest.builder().systemPrompt("s").userPrompt("u").build())).isEqualTo("엑사원 응답");
    }

    @Test
    @DisplayName("설정을 주지 않으면 조건 뽑기는 gpt-oss이고, 끄면 엑사원이다")
    void 기본값은_bedrock이다() {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of()));
        var defaults = binder.bindOrCreate("telme.consult.faq-clarification", FaqClarificationProperties.class);

        assertThat(defaults.bedrock()).isTrue();
        // FAQ 조건은 기본으로 뽑지 않는다(TELME-218). 되묻기 경로 자체(enabled)는 켜 둔다
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.extractConditions()).isFalse();
        assertThat(ConsultRagAnswerConfiguration.extractionClient(defaults, exaone, bedrock))
                .isInstanceOf(FallbackLlmClient.class);
        var off = new Binder(new MapConfigurationPropertySource(Map.of("telme.consult.faq-clarification.bedrock", "false")))
                .bindOrCreate("telme.consult.faq-clarification", FaqClarificationProperties.class);
        assertThat(off.bedrock()).isFalse();
        assertThat(ConsultRagAnswerConfiguration.extractionClient(off, exaone, bedrock)).isSameAs(exaone);
    }
}
