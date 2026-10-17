package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 비교 근거 검증과 FAQ 후보 근거 검증. bedrock을 켜면 gpt-oss(Bedrock)를 먼저 쓰고 실패하면 엑사원으로 대체한다
// candidateJudge: 후보 근거 검증에서 단어 검사 대신 고객 원문을 함께 주고 고른 FAQ를 회색 구간 판정기로 다시 판정한다(TELME-184).
// 답하는 질문이 크게 늘지만 대상이 다른 FAQ로 틀린 사실을 답하는 경우가 남아 기본은 끈다. 켤 때는 bedrock도 켠다
@ConfigurationProperties(prefix = "telme.consult.evidence-resolver")
public record EvidenceResolverProperties(
        @DefaultValue("false") boolean bedrock,
        @DefaultValue("false") boolean candidateJudge) {
}
