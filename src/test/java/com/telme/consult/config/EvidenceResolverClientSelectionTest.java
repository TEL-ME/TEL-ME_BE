package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class EvidenceResolverClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 근거를 검증한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(EvidenceResolverConfiguration.evidenceClient(new EvidenceResolverProperties(false), exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 검증한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(EvidenceResolverConfiguration.evidenceClient(new EvidenceResolverProperties(true), exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 검증하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = EvidenceResolverConfiguration.evidenceClient(new EvidenceResolverProperties(true), exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        org.mockito.Mockito.when(bedrock.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("bedrock down"));
        org.mockito.Mockito.when(exaone.generate(org.mockito.ArgumentMatchers.any())).thenReturn("엑사원 응답");

        var request = LlmRequest.builder().taskType(LlmGeneration.TaskType.EVIDENCE_CHECK).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
    }

    @Test
    @DisplayName("설정을 주지 않으면 꺼져 있고, 설정 이름으로 켠다")
    void 기본은_꺼져_있고_설정으로_켠다() {
        assertThat(bind(Map.of()).bedrock()).isFalse();
        assertThat(bind(Map.of("telme.consult.evidence-resolver.bedrock", "true")).bedrock()).isTrue();
    }

    private static EvidenceResolverProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("telme.consult.evidence-resolver", EvidenceResolverProperties.class);
    }
}
