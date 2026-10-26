package com.telme.consult.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 비교 근거 검증과 FAQ 후보 근거 검증(docs/01-pipeline/04-evidence-judgment/evidence-resolver-comparison.md).
// comparisonBedrock, candidateBedrock: 켜면 그 호출이 gpt-oss(Bedrock)를 먼저 쓰고 실패하면 엑사원으로 대체한다. 끄면 엑사원이다.
// 두 값의 기본은 CONSULT_EVIDENCE_RESOLVER_BEDROCK(application.yml)이고 호출마다 따로 엑사원으로 되돌릴 수 있다.
// candidateJudge: 후보 근거 검증에서 단어 검사 대신 고객 원문을 함께 주고, 고른 FAQ를 대상 낱말 규칙과 회색 구간 판정기로
// 다시 확인한다(TELME-184). 끄면 예전처럼 단어 검사로 막는다
@ConfigurationProperties(prefix = "telme.consult.evidence-resolver")
public record EvidenceResolverProperties(
        @DefaultValue("true") boolean comparisonBedrock,
        @DefaultValue("true") boolean candidateBedrock,
        @DefaultValue("true") boolean candidateJudge) {
}
