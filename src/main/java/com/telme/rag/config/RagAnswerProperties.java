package com.telme.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 답변 생성(RagAnswerGenerator). bedrock을 켜면 gpt-oss(Bedrock)로 답을 만들고, 첫 글자를 보내기 전에 실패하면 엑사원으로 대체한다
// (docs/chat-judge/results/20261018-answer-generation, TELME-180)
@ConfigurationProperties(prefix = "rag.answer")
public record RagAnswerProperties(@DefaultValue("false") boolean bedrock) {
}
