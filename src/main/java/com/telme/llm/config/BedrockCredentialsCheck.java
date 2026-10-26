package com.telme.llm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// 키가 없으면 모든 Bedrock 호출이 실패해 조용히 엑사원으로 돌므로 시작할 때 한 번 경고한다. 값은 로그에 남기지 않는다.
// 키가 맞는지는 호출해 봐야 알 수 있어 확인하지 않는다(유료 호출이 시작마다 생긴다)
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.bedrock.enabled", havingValue = "true")
class BedrockCredentialsCheck {

    static final String TOKEN_ENV = "AWS_BEARER_TOKEN_BEDROCK";
    static final String TOKEN_PROPERTY = "aws.bearerTokenBedrock";

    private final Environment environment;

    BedrockCredentialsCheck(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfTokenMissing() {
        if (hasText(environment.getProperty(TOKEN_ENV)) || hasText(System.getProperty(TOKEN_PROPERTY))) {
            return;
        }
        log.warn("Bedrock API 키({})가 없다. 키 없이 IAM 등 다른 자격 증명을 쓰는 환경이 아니면 모든 Bedrock 호출이 실패해 엑사원으로 동작한다",
                TOKEN_ENV);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
