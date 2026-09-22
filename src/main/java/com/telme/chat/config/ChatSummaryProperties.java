package com.telme.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chat.summary")
public record ChatSummaryProperties(
        @DefaultValue("16") int triggerMessages,
        @DefaultValue("2048") int triggerTokens,
        @DefaultValue("8") int retainedMessages,
        @DefaultValue("1024") int retainedTokens,
        @DefaultValue("16") int maxBatchMessages,
        @DefaultValue("3072") int maxInputTokens,
        @DefaultValue("512") int maxOutputTokens
) {

    public ChatSummaryProperties {
        if (triggerMessages < 2) {
            throw new IllegalArgumentException("요약 시작 메시지 개수는 2 이상이어야 합니다.");
        }
        if (triggerTokens < 2) {
            throw new IllegalArgumentException("요약 시작 토큰 수는 2 이상이어야 합니다.");
        }
        if (retainedMessages < 1) {
            throw new IllegalArgumentException("요약 후 유지할 최근 메시지 개수는 1 이상이어야 합니다.");
        }
        if (retainedMessages >= triggerMessages) {
            throw new IllegalArgumentException("요약 후 유지할 메시지 개수는 요약 시작 기준보다 작아야 합니다.");
        }
        if (retainedTokens < 1) {
            throw new IllegalArgumentException("요약 후 유지할 최근 대화 토큰 수는 1 이상이어야 합니다.");
        }
        if (retainedTokens >= triggerTokens) {
            throw new IllegalArgumentException("요약 후 유지할 토큰 수는 요약 시작 기준보다 작아야 합니다.");
        }
        if (maxBatchMessages < 2) {
            throw new IllegalArgumentException("질문과 답변을 함께 요약하려면 메시지 개수는 2 이상이어야 합니다.");
        }
        if (maxInputTokens < 1) {
            throw new IllegalArgumentException("요약 입력 토큰 수는 1 이상이어야 합니다.");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("요약 출력 토큰 수는 1 이상이어야 합니다.");
        }
    }
}
