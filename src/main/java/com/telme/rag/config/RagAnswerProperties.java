package com.telme.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 답변 생성(RagAnswerGenerator). bedrock을 켜면 gpt-oss(Bedrock)로 답을 만들고, 첫 글자를 보내기 전에 실패하면 엑사원으로 대체한다
// (docs/chat-judge/results/20261018-answer-generation, TELME-180). 기본 켜짐
// amountRetry: 가드가 근거에 없는 금액으로 답을 막으면 gpt-oss 추론 강도를 medium으로 올려 한 번 다시 생성한다(TELME-205).
// bedrock이 꺼져 있으면 쓰지 않는다
@ConfigurationProperties(prefix = "rag.answer")
public record RagAnswerProperties(
        @DefaultValue("true") boolean bedrock,
        @DefaultValue("true") boolean amountRetry) {

    @ConstructorBinding
    public RagAnswerProperties {
    }

    public RagAnswerProperties(boolean bedrock) {
        this(bedrock, true);
    }
}
