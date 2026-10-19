package com.telme.intent.config;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RoutingLlmClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 라우팅한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(false, true), exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 라우팅한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(true, true), exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock으로 라우팅하고 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock이고_실패하면_엑사원이다() {
        var client = RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(true, false), exaone, bedrock);

        assertThat(client).isInstanceOf(FallbackLlmClient.class);
        when(bedrock.generate(any())).thenThrow(new IllegalStateException("bedrock down"));
        when(exaone.generate(any())).thenReturn("엑사원 응답");
        var request = LlmRequest.builder().taskType(LlmGeneration.TaskType.ROUTING).userPrompt("질문").build();

        assertThat(client.generate(request)).isEqualTo("엑사원 응답");
    }

    @Test
    @DisplayName("설정을 주지 않으면 라우팅은 gpt-oss이고 요청 개수 판정은 하지 않는다")
    void 기본은_gpt_oss이고_요청_개수_판정은_하지_않는다() {
        assertThat(bind(Map.of()).bedrock()).isTrue();
        assertThat(bind(Map.of("telme.routing.bedrock", "false")).bedrock()).isFalse();
        assertThat(bind(Map.of()).requestInventoryEnabled()).isFalse();
        assertThat(bind(Map.of("telme.routing.request-inventory-enabled", "true")).requestInventoryEnabled())
                .isTrue();
    }

    @Test
    @DisplayName("Bedrock으로 라우팅하는데 요청 개수 판정이 켜져 있으면 경고를 남긴다")
    void bedrock인데_요청_개수_판정이_켜져_있으면_경고한다(CapturedOutput output) {
        RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(true, true), exaone, bedrock);

        assertThat(output).contains("ROUTING_REQUEST_INVENTORY_ENABLED");
    }

    @Test
    @DisplayName("요청 개수 판정을 껐거나 엑사원으로 라우팅하면 경고하지 않는다")
    void 어긋나지_않으면_경고하지_않는다(CapturedOutput output) {
        RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(true, false), exaone, bedrock);
        RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(true, true), exaone, null);
        RoutingLlmConfiguration.routingClient(new RoutingLlmProperties(false, true), exaone, bedrock);

        assertThat(output).doesNotContain("ROUTING_REQUEST_INVENTORY_ENABLED");
    }

    private static RoutingLlmProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("telme.routing", RoutingLlmProperties.class);
    }
}
