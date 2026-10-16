package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.question-resolution")
// stabilityGuards: gpt-oss 문맥 판정의 흔들림을 막는 보완(docs/CONTEXT_RESOLUTION.md). EXAONE에서는 결과가 같아 기본은 끄고,
// 문맥 판정을 gpt-oss(Bedrock)로 바꿀 때 켠다
public record ChatQuestionResolutionProperties(@DefaultValue("REGEX_GATED") Mode mode,
        @DefaultValue("false") boolean stabilityGuards) {
    public enum Mode {
        REGEX_GATED,
        LLM_ALL
    }
}
