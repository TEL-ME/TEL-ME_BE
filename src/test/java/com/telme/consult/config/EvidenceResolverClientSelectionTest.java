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
        assertThat(EvidenceResolverConfiguration.evidenceClient(false, exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 검증한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(EvidenceResolverConfiguration.evidenceClient(true, exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 검증하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = EvidenceResolverConfiguration.evidenceClient(true, exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        org.mockito.Mockito.when(bedrock.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("bedrock down"));
        org.mockito.Mockito.when(exaone.generate(org.mockito.ArgumentMatchers.any())).thenReturn("엑사원 응답");

        var request = LlmRequest.builder().taskType(LlmGeneration.TaskType.EVIDENCE_CHECK).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
    }

    @Test
    @DisplayName("설정을 주지 않으면 두 호출 모두 gpt-oss이고, 호출마다 따로 끈다")
    void 기본은_gpt_oss이고_호출마다_끈다() {
        assertThat(bind(Map.of()).comparisonBedrock()).isTrue();
        assertThat(bind(Map.of()).candidateBedrock()).isTrue();
        var exaoneCandidate = bind(Map.of("telme.consult.evidence-resolver.candidate-bedrock", "false"));
        assertThat(exaoneCandidate.comparisonBedrock()).isTrue();
        assertThat(exaoneCandidate.candidateBedrock()).isFalse();
    }

    // application.yml: 호출별 환경 변수 → 공통 CONSULT_EVIDENCE_RESOLVER_BEDROCK → true 순서로 정한다
    @Test
    @DisplayName("공통 환경 변수로 둘 다 끄고, 호출별 환경 변수가 공통 값보다 앞선다")
    void 공통_환경_변수와_호출별_환경_변수() throws Exception {
        assertThat(bindYaml(Map.of()).comparisonBedrock()).isTrue();
        var allExaone = bindYaml(Map.of("CONSULT_EVIDENCE_RESOLVER_BEDROCK", "false"));
        assertThat(allExaone.comparisonBedrock()).isFalse();
        assertThat(allExaone.candidateBedrock()).isFalse();
        var onlyComparison = bindYaml(Map.of("CONSULT_EVIDENCE_RESOLVER_BEDROCK", "false",
                "CONSULT_COMPARISON_EVIDENCE_BEDROCK", "true"));
        assertThat(onlyComparison.comparisonBedrock()).isTrue();
        assertThat(onlyComparison.candidateBedrock()).isFalse();
        assertThat(bindYaml(Map.of("CONSULT_CANDIDATE_EVIDENCE_JUDGE", "false")).candidateJudge()).isFalse();
    }

    private static EvidenceResolverProperties bindYaml(Map<String, Object> environmentVariables) throws Exception {
        var environment = new org.springframework.core.env.StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.addFirst(new org.springframework.core.env.MapPropertySource("env", environmentVariables));
        for (var yaml : new org.springframework.boot.env.YamlPropertySourceLoader()
                .load("application", new org.springframework.core.io.ClassPathResource("application.yml"))) {
            sources.addLast(yaml);
        }
        return Binder.get(environment)
                .bindOrCreate("telme.consult.evidence-resolver", EvidenceResolverProperties.class);
    }

    private static EvidenceResolverProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("telme.consult.evidence-resolver", EvidenceResolverProperties.class);
    }
}
