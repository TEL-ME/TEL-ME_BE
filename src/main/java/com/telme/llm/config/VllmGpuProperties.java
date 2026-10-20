package com.telme.llm.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 평소 꺼 두는 vLLM GPU EC2
@ConfigurationProperties(prefix = "vllm.gpu")
public record VllmGpuProperties(
        String instanceId,
        @DefaultValue("ap-northeast-2") String region,
        // 이 간격 안에서는 상태 확인,ㅊ기동 요청을 다시 보내지 X
        // 엑사원 호출마다 AWS API를 부르지 않게 한다
        @DefaultValue("1m") Duration startInterval
) {

    public VllmGpuProperties {
        if (startInterval == null || startInterval.isNegative() || startInterval.isZero()) {
            throw new IllegalArgumentException("vllm.gpu.start-interval은 0보다 커야 합니다: " + startInterval);
        }
    }
}
