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
        // 엑사원 호출마다 AWS API를 부르지 않게 한다
        @DefaultValue("1m") Duration startInterval,
        // startWindow 안에 엑사원 호출이 이만큼 쌓여야 켠다
        // 조건 추출 파싱 실패나 일시 오류로 한 번 불린 걸로는 켜지 않게(한 번 켜면 30분 이상 과금)
        // Bedrock 장애면 한 턴에 라우팅, 조건 추출, 답변 등 3번 이상 불려 첫 턴에 바로 넘는다
        @DefaultValue("3") int startMinCalls,
        @DefaultValue("2m") Duration startWindow
) {

    public VllmGpuProperties {
        requirePositive("vllm.gpu.start-interval", startInterval);
        requirePositive("vllm.gpu.start-window", startWindow);
        if (startMinCalls < 1) {
            throw new IllegalArgumentException("vllm.gpu.start-min-calls는 1 이상이어야 합니다: " + startMinCalls);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }
}
