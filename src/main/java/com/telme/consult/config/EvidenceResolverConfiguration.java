package com.telme.consult.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.CandidateSubjectRule;
import com.telme.consult.service.LlmComparisonEvidenceResolver;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver.Options;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import com.telme.rag.service.FaqEvidenceJudge;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 두 근거 검증 호출이 쓸 모델을 고른다. 상담 연동 설정과 별개로 항상 만든다. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EvidenceResolverProperties.class)
public class EvidenceResolverConfiguration {

    static LlmClient evidenceClient(boolean useBedrock, LlmClient exaone, LlmClient bedrock) {
        return useBedrock && bedrock != null ? new FallbackLlmClient(bedrock, exaone) : exaone;
    }

    @Bean
    LlmComparisonEvidenceResolver comparisonEvidenceResolver(
            LlmClient llmClient, ObjectMapper objectMapper, EvidenceResolverProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock) {
        return new LlmComparisonEvidenceResolver(
                evidenceClient(properties.comparisonBedrock(), llmClient, bedrock.getIfAvailable()), objectMapper);
    }

    // 후보 근거 검증(TELME-184): candidateJudge를 켜면 단어 검사 대신 고객 원문을 함께 주고, 고른 FAQ가 질문과 같은 대상인지
    // 낱말로 먼저 확인한 뒤(CandidateSubjectRule) 회색 구간 판정기(v4)로 다시 판정한다. 판정기는 Bedrock이 켜졌을 때만 있다.
    // 꺼져 있거나 판정기가 없으면 예전처럼 단어 검사로 막는다. 판정기 호출이 실패하면(FAILED) 그 후보만 예전 단어 검사로 판단한다.
    // 회색 구간 판정이 실패하면 예전 기준으로 두는 것과 같다. 후보마다 막힌 단계를 실행 기록(faqCandidateVerifications)에 남긴다
    static Options candidateOptions(EvidenceResolverProperties properties, FaqEvidenceJudge judge, ExecutionTrace trace) {
        if (!properties.candidateJudge()) {
            return Options.CURRENT;
        }
        if (judge == null) {
            log.warn("후보 근거 검증 판정(candidate-judge)을 켰지만 Bedrock 판정기가 없어 단어 검사로 막는다(llm.bedrock.enabled 확인)");
            return Options.CURRENT;
        }
        return new Options(false, true, (executionId, consultRequestId, question, customerMessage, candidate) -> {
            boolean subjectPassed =
                    CandidateSubjectRule.passes(question, customerMessage, candidate.question(), candidate.answer());
            FaqEvidenceJudge.Verdict verdict = subjectPassed
                    ? judge.judge(executionId, consultRequestId, judgeQuestion(question, customerMessage),
                            candidate.question(), candidate.answer())
                    : null;
            boolean accepted = verdict == FaqEvidenceJudge.Verdict.ANSWERABLE
                    || verdict == FaqEvidenceJudge.Verdict.FAILED
                            && LlmFaqCandidateEvidenceResolver.coversQuestionTerms(question, candidate);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("consultRequestId", consultRequestId);
            record.put("faqId", candidate.faqId());
            record.put("subjectRule", subjectPassed ? "PASSED" : "REJECTED");
            record.put("verdict", verdict == null ? "NOT_CALLED" : verdict.name());
            record.put("accepted", accepted);
            trace.append(executionId, "faqCandidateVerifications", record);
            return accepted;
        });
    }

    // 판정기는 질문 하나를 받는다. 하위 질문이 조각이면 원문을 앞에 두고 판단할 부분을 밝힌다
    public static String judgeQuestion(String question, String customerMessage) {
        if (customerMessage == null || customerMessage.isBlank() || customerMessage.strip().equals(question.strip())) {
            return question;
        }
        return "고객 원문: " + customerMessage.strip() + "\n이 중 판단할 질문: " + question;
    }

    @Bean
    LlmFaqCandidateEvidenceResolver faqCandidateEvidenceResolver(
            LlmClient llmClient, ObjectMapper objectMapper, EvidenceResolverProperties properties,
            @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> bedrock,
            ObjectProvider<FaqEvidenceJudge> judge, ObjectProvider<ExecutionTrace> trace) {
        return new LlmFaqCandidateEvidenceResolver(
                evidenceClient(properties.candidateBedrock(), llmClient, bedrock.getIfAvailable()), objectMapper,
                candidateOptions(properties, judge.getIfAvailable(), trace.getIfAvailable(ExecutionTrace::noop)));
    }
}
