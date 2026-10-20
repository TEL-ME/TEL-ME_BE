package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 검색 근거 판정용 Bedrock 설정. 답변 생성(Ollama)과 따로 둔다
// 자격 증명은 코드·설정에 두지 않고 AWS SDK가 환경에서 찾는다.
// AWS_BEARER_TOKEN_BEDROCK(Bedrock API 키)이 있으면 그것을, 없으면 AWS_ACCESS_KEY_ID 등 기본 자격 증명(EC2 역할 포함)을 쓴다
@ConfigurationProperties(prefix = "llm.bedrock")
public record BedrockProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("openai.gpt-oss-120b-1:0") String model,
        // 호출 1회 전체 제한. 넘으면 판정 실패로 처리한다. 끝까지 기다린 호출 119회 중 5초 초과 5.9%, 8초 초과 0.8%,
        // 최대 8.6초라 대부분 조금 느린 호출이다(docs/GRAY_ZONE_UPPER_BAND.md 5.5절)
        @DefaultValue("8s") Duration timeout,
        // gpt-oss의 생각 길이. 기본값은 길게 생각해 느리고 비싸다. low로도 판정 시험 합격(TELME-122)
        // gpt-oss가 아닌 모델로 바꾸면 비워 둔다
        @DefaultValue("low") String reasoningEffort,
        // 요청에 maxTokens가 없을 때 쓴다. gpt-oss는 생각 토큰도 출력에 들어가 짧으면 답이 잘린다
        @DefaultValue("1000") int maxTokens,
        // 앱 전체에서 동시에 진행되는 Bedrock 호출 수 상한(HTTP 연결 수). 기본은 SDK 기본값과 같은 50이다.
        // 분당 호출 수 한도는 아니다. 끝난 연결은 바로 다시 쓰여, 응답이 빠르거나 오류가 바로 돌아오면 분당 호출이 늘어난다.
        // 평균 1.5초면 분당 약 max-connections × 40회 정도다. 분당 한도를 강제하려면 별도 제한이 필요하다
        @DefaultValue("50") int maxConnections,
        // 연결이 모두 쓰이는 중이면 이만큼만 기다리고 판정 실패로 넘긴다. 기다리는 동안 고객 응답도 늦어진다
        @DefaultValue("1s") Duration connectionAcquireTimeout,
        // 비우면 지역의 기본 주소를 쓴다. 서빙 비교의 "Bedrock 응답 없음" 장애를 만들 때만 응답하지 않는 서버 주소를 준다
        // (scripts/serving-comparison/fault-hang.yml)
        String endpoint
) {

    public BedrockProperties(boolean enabled, String region, String model, Duration timeout, String reasoningEffort,
            int maxTokens, int maxConnections, Duration connectionAcquireTimeout) {
        this(enabled, region, model, timeout, reasoningEffort, maxTokens, maxConnections, connectionAcquireTimeout,
                null);
    }

    @ConstructorBinding
    public BedrockProperties {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("llm.bedrock.max-tokens는 1 이상이어야 합니다: " + maxTokens);
        }
        if (maxConnections < 1) {
            throw new IllegalArgumentException("llm.bedrock.max-connections는 1 이상이어야 합니다: " + maxConnections);
        }
    }
}
