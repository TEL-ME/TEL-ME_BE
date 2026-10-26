package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.question-resolution")
// stabilityGuards: gpt-oss 문맥 판정의 흔들림을 막는 보완(docs/01-pipeline/02-context-routing/context-resolution.md). EXAONE에서는 결과가 같아 기본은 끈다
// bedrock: 문맥 판정을 gpt-oss(Bedrock)로 부르고 실패하면 엑사원으로 대체한다. 켜면 흔들림 보완도 함께 켠다. 기본 켜짐
public record ChatQuestionResolutionProperties(@DefaultValue("HISTORY_GATED") Mode mode,
        @DefaultValue("false") boolean stabilityGuards,
        @DefaultValue("true") boolean bedrock) {
    public enum Mode {
        REGEX_GATED,
        // 앞 고객 발언이 있으면 규칙에 안 걸린 후속 질문도 모델로 판정, 없으면 규칙 후보만
        HISTORY_GATED,
        LLM_ALL
    }

    public boolean guardsEnabled() {
        return stabilityGuards || bedrock;
    }
}
