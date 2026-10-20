package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 평소 꺼 두는 vLLM GPU EC2
@ConfigurationProperties(prefix = "vllm.gpu")
public record VllmGpuProperties(
        String instanceId,
        @DefaultValue("ap-northeast-2") String region,
        // 이 간격 안에서는 상태 확인, 기동 요청을 다시 보내지 X
        // 대체 호출마다 AWS API를 부르지 않게 한다
        @DefaultValue("1m") Duration startInterval,
        // startWindow 안에 Bedrock 실패 대체가 이만큼 쌓여야 켠다
        // 8초 시간 초과, 429 같은 일시 오류 한 번으로는 켜지 않게(한 번 켜면 30분 이상 과금)
        // 세션 첫 질문은 라우팅, 제목 생성 등으로 대체가 2번 넘어 첫 턴에 켜지고
        // 대화 중간의 이어 묻는 질문, 매장 질문은 턴당 1번이라 두 번째 실패한 턴에 켜진다
        @DefaultValue("2") int startMinFallbacks,
        @DefaultValue("5m") Duration startWindow
) {

    public VllmGpuProperties {
        requirePositive("vllm.gpu.start-interval", startInterval);
        requirePositive("vllm.gpu.start-window", startWindow);
        if (startMinFallbacks < 1) {
            throw new IllegalArgumentException("vllm.gpu.start-min-fallbacks는 1 이상이어야 합니다: " + startMinFallbacks);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }
}
