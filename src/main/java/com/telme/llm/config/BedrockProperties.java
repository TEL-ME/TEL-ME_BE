package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 검색 근거 판정용 Bedrock 설정. 답변 생성(Ollama)과 따로 둔다
// 자격 증명은 코드·설정에 두지 않고 AWS SDK가 환경에서 찾는다.
// AWS_BEARER_TOKEN_BEDROCK(Bedrock API 키)이 있으면 그것을, 없으면 AWS_ACCESS_KEY_ID 등 기본 자격 증명(EC2 역할 포함)을 쓴다
@ConfigurationProperties(prefix = "llm.bedrock")
public record BedrockProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("openai.gpt-oss-120b-1:0") String model,
        // 호출 1회 전체 제한. 로컬 측정 p95 3.8초(docs/GRAY_ZONE_UPPER_BAND.md 4.4절)
        @DefaultValue("5s") Duration timeout,
        // gpt-oss의 생각 길이. 기본값은 길게 생각해 느리고 비싸다. low로도 판정 시험 합격(TELME-122)
        // gpt-oss가 아닌 모델로 바꾸면 비워 둔다
        @DefaultValue("low") String reasoningEffort,
        // 요청에 maxTokens가 없을 때 쓴다. gpt-oss는 생각 토큰도 출력에 들어가 짧으면 답이 잘린다
        @DefaultValue("1000") int maxTokens,
        // 앱 전체에서 동시에 Bedrock으로 나가는 호출 수 상한(HTTP 연결 수). 기본은 SDK 기본값과 같은 50이다.
        // 호출 1회 약 1.5초라 분당 최대 약 max-connections × 40회다. Bedrock 분당 한도를 넘지 않게 조정한다
        @DefaultValue("50") int maxConnections,
        // 연결이 모두 쓰이는 중이면 이만큼만 기다리고 판정 실패로 넘긴다. 기다리는 동안 고객 응답도 늦어진다
        @DefaultValue("1s") Duration connectionAcquireTimeout
) {

    public BedrockProperties {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("llm.bedrock.max-tokens는 1 이상이어야 합니다: " + maxTokens);
        }
        if (maxConnections < 1) {
            throw new IllegalArgumentException("llm.bedrock.max-connections는 1 이상이어야 합니다: " + maxConnections);
        }
    }
}
