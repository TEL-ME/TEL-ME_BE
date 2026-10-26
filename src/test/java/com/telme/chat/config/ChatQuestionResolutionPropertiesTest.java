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
    }

    // 기본은 앞 고객 발언이 있으면 모델로 판정하고, 환경 변수로 규칙 관문만 쓰게 되돌린다
    @Test
    void modeDefaultsToHistoryGatedAndSwitchesFromEnvironmentVariable() {
        assertThat(bind(Map.of()).mode()).isEqualTo(ChatQuestionResolutionProperties.Mode.HISTORY_GATED);
        assertThat(bind(Map.of("CHAT_QUESTION_RESOLUTION_MODE", "REGEX_GATED")).mode())
                .isEqualTo(ChatQuestionResolutionProperties.Mode.REGEX_GATED);
    }

    // Bedrock 판정은 기본으로 켜져 있고, 켜면 흔들림 보완도 함께 켜진다. 끄면 보완도 따라 꺼진다
    @Test
    void bedrockIsOnByDefaultAndTurnsOnStabilityGuards() {
        var defaults = bind(Map.of());
        assertThat(defaults.bedrock()).isTrue();
        assertThat(defaults.stabilityGuards()).isFalse();
        assertThat(defaults.guardsEnabled()).isTrue();
        var off = bind(Map.of("CHAT_QUESTION_RESOLUTION_BEDROCK", "false"));
        assertThat(off.bedrock()).isFalse();
        assertThat(off.guardsEnabled()).isFalse();
    }

    private static ChatQuestionResolutionProperties bind(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test", variables));
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("chat.question-resolution", ChatQuestionResolutionProperties.class);
    }
}
