package com.telme.consult.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LlmFaqCandidateEvidenceResolver implements FaqCandidateEvidenceResolver {
    // gpt-oss(Bedrock)는 답 앞의 내부 추론도 출력 한도를 써서 한도가 250이면 JSON이 끊긴 사례가 있다(docs/01-pipeline/04-evidence-judgment/evidence-resolver-comparison.md)
    private static final int MAX_OUTPUT_TOKENS = 512;
    private static final Pattern FAQ_ID = Pattern.compile("(?:ID\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_WORD = Pattern.compile("[가-힣A-Za-z0-9]{2,}");
    private static final Set<String> QUESTION_FILLERS = Set.of(
            "무엇인가요", "무엇인가", "얼마인가요", "얼마인가", "알려줘", "알려주세요",
            "되나요", "주나요", "있나요", "하나요", "인가요", "어떤", "몇세인가요",
            "뭐고", "뭐야", "뭔가요", "뭔지");
    private static final List<String> PARTICLES = List.of(
            "에서는", "으로", "에게", "부터", "까지", "한", "은", "는", "이", "가", "을", "를", "에", "의");
    private static final String PROMPT = """
            당신은 통신 FAQ 검색 후보를 검증합니다. 고객 질문 전체에 답할 수 있는 FAQ를 하나만 고르세요.
            FAQ 질문(Q)은 대상을 찾기 위한 제목입니다. 실제 답변의 사실 근거는 FAQ 답변(A)뿐입니다.
            주제만 비슷하거나 질문의 핵심 조건, 상품, 속성이 다르면 선택하지 마세요.
            답변할 수 있으면 FAQ 답변(A)에서 질문에 답하는 문구를 정확히 복사하세요.
            애매하면 선택하지 마세요. JSON 객체만 출력하세요.
            {"answerable":false,"faqId":null,"quote":null}
            """;
    // 복합 질문의 하위 질문은 라우팅이 나눈 조각이라 대상·조건이 빠질 수 있다. 고객 원문을 함께 주고 답할 부분을 지정한다
    private static final String CONTEXT_RULE = """
            고객 원문에는 여러 질문이 섞여 있을 수 있습니다. '이번에 답할 부분'에 답하는 FAQ만 고르세요.
            이번에 답할 부분에 빠진 대상이나 조건은 고객 원문에서 찾아 판단하세요. 원문의 다른 질문에만 답하는 FAQ는 고르지 마세요.
            """;

    /**
     * 판정 방식(TELME-184, docs/01-pipeline/04-evidence-judgment/evidence-resolver-comparison.md). questionTermCheck: 질문 낱말이 FAQ 문답에 모두 있어야
     * 통과(TELME-87). customerMessageContext: 고객 원문을 함께 넘긴다. verifier: 모델이 고른 FAQ를 한 번 더 확인한다(없으면 생략).
     */
    public record Options(boolean questionTermCheck, boolean customerMessageContext, CandidateVerifier verifier) {
        public static final Options CURRENT = new Options(true, false, null);
    }

    // 모델이 고른 FAQ가 답할 부분에 실제로 답하는지 확인한다
    @FunctionalInterface
    public interface CandidateVerifier {
        boolean answers(Long executionId, Long consultRequestId, String question, String customerMessage,
                FaqSearchResponse candidate);
    }

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Options options;

    public LlmFaqCandidateEvidenceResolver(LlmClient llmClient, ObjectMapper objectMapper) {
        this(llmClient, objectMapper, Options.CURRENT);
    }

    public LlmFaqCandidateEvidenceResolver(LlmClient llmClient, ObjectMapper objectMapper, Options options) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.options = options;
    }

    @Override
    public FaqSearchResponse resolve(Long executionId, String question, List<FaqSearchResponse> candidates) {
        return resolve(executionId, null, question, candidates);
    }

    @Override
    public FaqSearchResponse resolve(Long executionId, Long consultRequestId,
            String question, List<FaqSearchResponse> candidates) {
        return resolve(executionId, consultRequestId, question, candidates, false);
    }

    @Override
    public FaqSearchResponse resolve(Long executionId, Long consultRequestId, String question,
            List<FaqSearchResponse> candidates, boolean allowQuoteRepairFailure) {
        return resolve(executionId, consultRequestId, question, null, candidates, allowQuoteRepairFailure);
    }

    @Override
    public FaqSearchResponse resolve(Long executionId, Long consultRequestId, String question, String customerMessage,
            List<FaqSearchResponse> candidates, boolean allowQuoteRepairFailure) {
        FaqSearchResponse selected = select(executionId, consultRequestId, question, customerMessage, candidates,
                allowQuoteRepairFailure);
        if (selected == null || options.verifier() == null) {
            return selected;
        }
        return options.verifier().answers(executionId, consultRequestId, question, customerMessage, selected)
                ? selected : null;
    }

    private FaqSearchResponse select(Long executionId, Long consultRequestId, String question, String customerMessage,
            List<FaqSearchResponse> candidates, boolean allowQuoteRepairFailure) {
        if (candidates.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder();
        for (var candidate : candidates) {
            context.append("ID ").append(candidate.faqId()).append(" Q: ")
                    .append(candidate.question()).append("\nA: ").append(candidate.answer()).append('\n');
        }
        boolean withMessage = options.customerMessageContext() && customerMessage != null
                && !customerMessage.isBlank() && !customerMessage.strip().equals(question.strip());
        String asked = withMessage
                ? "고객 원문: " + customerMessage.strip() + "\n이번에 답할 부분: " + question
                : "고객 질문: " + question;
        var request = LlmRequest.builder()
                .executionId(executionId).taskType(TaskType.EVIDENCE_CHECK)
                .consultRequestId(consultRequestId)
                .systemPrompt(withMessage ? PROMPT + CONTEXT_RULE : PROMPT)
                .userPrompt(asked + "\nFAQ 후보:\n" + context)
                .format(ResponseFormat.JSON).temperature(0.0).maxTokens(MAX_OUTPUT_TOKENS)
                .contextCount(candidates.size())
                .promptVersion(withMessage ? "faq-candidate-evidence-v3" : "faq-candidate-evidence-v2")
                .build();
        try {
            String output = llmClient.generate(request);
            if (output == null || output.isBlank()) {
                return null;
            }
            JsonNode response = objectMapper.readTree(output);
            if (response == null || !response.isObject()
                    || !response.path("answerable").isBoolean()
                    || !response.path("answerable").booleanValue()) {
                return null;
            }
            Matcher idMatcher = FAQ_ID.matcher(response.path("faqId").asText(""));
            if (!idMatcher.matches()) {
                return null;
            }
            long id = Long.parseLong(idMatcher.group(1));
            FaqSearchResponse selected = candidates.stream()
                    .filter(candidate -> candidate.faqId() == id && candidate.answer() != null)
                    .findFirst().orElse(null);
            if (selected == null) {
                return null;
            }
            if (options.questionTermCheck() && !coversQuestionTerms(question, selected)) {
                return null;
            }
            if (hasExactQuote(response, selected)) {
                return selected;
            }
            // 모델이 ID는 맞게 찾고도 인용문을 의역할 수 있다. 원문 복사만 한 번 재시도한다.
            var retry = LlmRequest.builder()
                    .executionId(executionId).taskType(TaskType.EVIDENCE_CHECK)
                    .consultRequestId(consultRequestId)
                    .systemPrompt("FAQ 답변(A)에서 고객 질문에 답하는 연속된 문구를 한 글자도 바꾸지 말고 "
                            + "복사하세요. 의역하거나 새 사실을 넣지 마세요. JSON만 출력하세요. "
                            + "{\"answerable\":false,\"faqId\":null,\"quote\":null}")
                    .userPrompt(asked + "\nID " + selected.faqId()
                            + " Q: " + selected.question() + "\nA: " + selected.answer())
                    .format(ResponseFormat.JSON).temperature(0.0).maxTokens(MAX_OUTPUT_TOKENS)
                    .contextCount(1).promptVersion("faq-candidate-quote-retry-v2")
                    .build();
            String retryOutput = llmClient.generate(retry);
            if (retryOutput == null || retryOutput.isBlank()) {
                return null;
            }
            JsonNode repaired = objectMapper.readTree(retryOutput);
            if (repaired == null || !repaired.isObject() || !repaired.path("answerable").isBoolean()) {
                return null;
            }
            Matcher retryId = FAQ_ID.matcher(repaired.path("faqId").asText(""));
            boolean sameCandidateAnswerable = repaired.path("answerable").asBoolean(false)
                    && retryId.matches() && Long.parseLong(retryId.group(1)) == id;
            // 재시도가 같은 후보를 골랐다면 인용문만 틀려도 원문 후보는 살린다
            return sameCandidateAnswerable && (allowQuoteRepairFailure || hasExactQuote(repaired, selected))
                    ? selected : null;
        } catch (JsonProcessingException | NumberFormatException failure) {
            log.warn("FAQ 후보 근거 판정 형식 오류: executionId={}, errorType={}",
                    executionId, failure.getClass().getSimpleName());
            return null;
        }
    }

    private boolean hasExactQuote(JsonNode response, FaqSearchResponse candidate) {
        String quote = response.path("quote").asText("").strip();
        return quote.length() >= 8 && candidate.answer().contains(quote);
    }

    // 질문 낱말이 FAQ 문답에 모두 있어야 통과(TELME-87). 후보 판정기가 실패했을 때도 이 검사로 돌아간다
    public static boolean coversQuestionTerms(String question, FaqSearchResponse candidate) {
        String source = (candidate.question() + candidate.answer())
                .replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        Matcher words = QUESTION_WORD.matcher(question);
        int checked = 0;
        while (words.find()) {
            String token = words.group().toLowerCase(java.util.Locale.ROOT);
            if (QUESTION_FILLERS.contains(token) || token.startsWith("무엇")) {
                continue;
            }
            for (String particle : PARTICLES) {
                if (token.endsWith(particle) && token.length() - particle.length() >= 2) {
                    token = token.substring(0, token.length() - particle.length());
                    break;
                }
            }
            if (QUESTION_FILLERS.contains(token)) {
                continue;
            }
            checked++;
            if (!source.contains(token)) {
                return false;
            }
        }
        return checked >= 2;
    }
}
