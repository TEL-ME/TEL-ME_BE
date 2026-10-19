package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class ChatQuestionResolutionPropertiesTest {

    // 흔들림 보완은 기본으로 꺼져 있고, 문서에 적은 환경 변수로 켠다
    @Test
    void stabilityGuardsAreOffByDefaultAndTurnOnFromEnvironmentVariable() {
        assertThat(bind(Map.of()).stabilityGuards()).isFalse();
        var guarded = bind(Map.of("CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS", "true"));
        assertThat(guarded.stabilityGuards()).isTrue();
        assertThat(guarded.mode()).isEqualTo(ChatQuestionResolutionProperties.Mode.REGEX_GATED);
    }

    // Bedrock 판정은 기본으로 꺼져 있고, 켜면 흔들림 보완도 함께 켜진다
    @Test
    void bedrockIsOffByDefaultAndTurnsOnStabilityGuards() {
        var defaults = bind(Map.of());
        assertThat(defaults.bedrock()).isFalse();
        assertThat(defaults.guardsEnabled()).isFalse();
        var bedrock = bind(Map.of("CHAT_QUESTION_RESOLUTION_BEDROCK", "true"));
        assertThat(bedrock.bedrock()).isTrue();
        assertThat(bedrock.stabilityGuards()).isFalse();
        assertThat(bedrock.guardsEnabled()).isTrue();
    }

    private static ChatQuestionResolutionProperties bind(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test", variables));
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("chat.question-resolution", ChatQuestionResolutionProperties.class);
    }
}
