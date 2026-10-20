package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 채팅 턴을 처리하는 실행기(ChatProcessingDispatcher) 크기.
 * threads: 동시에 처리하는 턴 수. queueCapacity: 처리를 기다릴 수 있는 턴 수. 넘치면 그 턴은 바로 실패로 끝난다.
 * 기본값은 설정화 전 코드 고정값과 같다. 배포 값은 예상 동시 사용자 수와 GPU·Bedrock 처리량을 보고 정한다
 * (TELME-187 T1 실측: vLLM은 GPU보다 동시 4개 제한에 먼저 걸렸다)
 */
@ConfigurationProperties(prefix = "chat.processing")
public record ChatProcessingProperties(
        @DefaultValue("4") int threads,
        @DefaultValue("100") int queueCapacity
) {
    public ChatProcessingProperties {
        if (threads < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("채팅 처리 동시 턴 수는 1 이상, 대기열 크기는 0 이상이어야 합니다.");
        }
    }
}
