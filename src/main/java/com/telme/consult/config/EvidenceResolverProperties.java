package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 비교 근거 검증과 FAQ 후보 근거 검증. bedrock을 켜면 gpt-oss(Bedrock)를 먼저 쓰고 실패하면 엑사원으로 대체한다
@ConfigurationProperties(prefix = "telme.consult.evidence-resolver")
public record EvidenceResolverProperties(@DefaultValue("false") boolean bedrock) {
}
