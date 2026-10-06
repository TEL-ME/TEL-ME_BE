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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmFaqCandidateEvidenceResolver implements FaqCandidateEvidenceResolver {
    private static final Pattern FAQ_ID = Pattern.compile("(?:ID\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_WORD = Pattern.compile("[가-힣A-Za-z0-9]{2,}");
    private static final Set<String> QUESTION_FILLERS = Set.of(
            "무엇인가요", "무엇인가", "얼마인가요", "얼마인가", "알려줘", "알려주세요",
            "되나요", "주나요", "있나요", "하나요", "인가요", "어떤", "몇세인가요");
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

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public FaqSearchResponse resolve(Long executionId, String question, List<FaqSearchResponse> candidates) {
        return resolve(executionId, null, question, candidates);
    }

    @Override
    public FaqSearchResponse resolve(Long executionId, Long consultRequestId,
            String question, List<FaqSearchResponse> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder();
        for (var candidate : candidates) {
            context.append("ID ").append(candidate.faqId()).append(" Q: ")
                    .append(candidate.question()).append("\nA: ").append(candidate.answer()).append('\n');
        }
        var request = LlmRequest.builder()
                .executionId(executionId).taskType(TaskType.ROUTING)
                .consultRequestId(consultRequestId)
                .systemPrompt(PROMPT)
                .userPrompt("고객 질문: " + question + "\nFAQ 후보:\n" + context)
                .format(ResponseFormat.JSON).temperature(0.0).maxTokens(250)
                .contextCount(candidates.size()).promptVersion("faq-candidate-evidence-v2")
                .build();
        try {
            JsonNode response = objectMapper.readTree(llmClient.generate(request));
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
            if (!coversQuestionTerms(question, selected)) {
                return null;
            }
            if (hasExactQuote(response, selected)) {
                return selected;
            }
            // 모델이 ID는 맞게 찾고도 인용문을 의역할 수 있다. 원문 복사만 한 번 재시도한다.
            var retry = LlmRequest.builder()
                    .executionId(executionId).taskType(TaskType.ROUTING)
                    .consultRequestId(consultRequestId)
                    .systemPrompt("FAQ 답변(A)에서 고객 질문에 답하는 연속된 문구를 한 글자도 바꾸지 말고 "
                            + "복사하세요. 의역하거나 새 사실을 넣지 마세요. JSON만 출력하세요. "
                            + "{\"answerable\":false,\"faqId\":null,\"quote\":null}")
                    .userPrompt("고객 질문: " + question + "\nID " + selected.faqId()
                            + " Q: " + selected.question() + "\nA: " + selected.answer())
                    .format(ResponseFormat.JSON).temperature(0.0).maxTokens(250)
                    .contextCount(1).promptVersion("faq-candidate-quote-retry-v2")
                    .build();
            JsonNode repaired = objectMapper.readTree(llmClient.generate(retry));
            if (repaired == null || !repaired.isObject() || !repaired.path("answerable").isBoolean()) {
                return null;
            }
            Matcher retryId = FAQ_ID.matcher(repaired.path("faqId").asText(""));
            return repaired.path("answerable").asBoolean(false)
                    && retryId.matches() && Long.parseLong(retryId.group(1)) == id
                    && hasExactQuote(repaired, selected) ? selected : null;
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

    private boolean coversQuestionTerms(String question, FaqSearchResponse candidate) {
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
