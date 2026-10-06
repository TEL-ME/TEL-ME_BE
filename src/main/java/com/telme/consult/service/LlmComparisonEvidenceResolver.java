package com.telme.consult.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.intent.service.ComparisonQuestionPolicy;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 두 비교 대상의 근거를 확인한 뒤 FAQ 답변 원문으로 비교 내용을 구성한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmComparisonEvidenceResolver implements ComparisonEvidenceResolver {
    private static final Pattern FAQ_ID = Pattern.compile("(?:ID\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CRITERION_FILLERS = Set.of("및", "와", "과", "하고", "랑", "수");
    private static final Set<String> COMPARISON_WORDS = Set.of("비교", "차이", "차이점");
    private static final String PROMPT = """
            당신은 통신 FAQ 근거 검증기입니다. 고객의 비교 질문에 답하려면 두 대상 각각에 대해
            질문한 속성의 사실이 FAQ 답변(A)에 명시되어야 합니다. FAQ 질문(Q)은 검색 제목이고
            사실 근거가 아닙니다. 한 대상에만 조건이 있거나, 다른 대상을 단순 언급하거나,
            질문과 다른 속성만 설명하면 그 대상의 근거는 없습니다.
            두 대상과 비교 기준은 고객 질문의 표현을 그대로 사용하세요. 일반 지식으로 빈칸을 채우지 마세요.
            각 대상의 근거가 있으면 해당 FAQ 답변(A)에서 대상과 사실을 함께 담은 문구를 정확히 복사하고
            FAQ ID를 적으세요. 같은 FAQ가 양쪽 사실을 담으면 같은 ID를 써도 됩니다.
            JSON 객체만 출력하세요. 형식:
            {"leftTarget":"", "rightTarget":"", "criterion":"", "leftFaqId":null,
             "leftQuote":null, "rightFaqId":null, "rightQuote":null, "answerable":false}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean applies(String question) {
        return ComparisonQuestionPolicy.isStandaloneComparison(question, null);
    }

    @Override
    public List<FaqSearchResponse> resolve(
            Long executionId, String question, List<FaqSearchResponse> originalResults,
            ComparisonSearcher searcher) {
        return resolveDetailed(executionId, question, originalResults, searcher).sources();
    }

    @Override
    public Resolution resolveDetailed(
            Long executionId, String question, List<FaqSearchResponse> originalResults,
            ComparisonSearcher searcher) {
        return resolveDetailed(executionId, null, question, originalResults, searcher);
    }

    @Override
    public Resolution resolveDetailed(
            Long executionId, Long consultRequestId, String question,
            List<FaqSearchResponse> originalResults, ComparisonSearcher searcher) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(originalResults, "originalResults");
        Objects.requireNonNull(searcher, "searcher");

        Assessment first = assess(executionId, consultRequestId, question, originalResults);
        if (first.supported()) {
            return first.resolution();
        }
        if (!first.hasValidTargets(question)) {
            return new Resolution(List.of(), null);
        }

        String leftQuery = first.leftTarget() + " " + first.criterion();
        String rightQuery = first.rightTarget() + " " + first.criterion();
        if (leftQuery.length() > 500 || rightQuery.length() > 500) {
            return new Resolution(List.of(), null);
        }
        Map<Long, FaqSearchResponse> candidates = byId(originalResults);
        searchSide(searcher, leftQuery, first.leftTarget(), "COMPARISON_LEFT")
                .forEach(source -> candidates.putIfAbsent(source.faqId(), source));
        searchSide(searcher, rightQuery, first.rightTarget(), "COMPARISON_RIGHT")
                .forEach(source -> candidates.putIfAbsent(source.faqId(), source));
        return assess(executionId, consultRequestId, question, List.copyOf(candidates.values())).resolution();
    }

    private List<FaqSearchResponse> searchSide(
            ComparisonSearcher searcher, String query, String target, String kind) {
        List<FaqSearchResponse> results = searcher.search(query, kind);
        if (!results.isEmpty()) {
            return results;
        }
        results = searcher.search(target, kind + "_TARGET");
        if (!results.isEmpty()) {
            return results;
        }
        String compactTarget = compact(target);
        return compactTarget.equals(target)
                ? List.of() : searcher.search(compactTarget, kind + "_COMPACT_TARGET");
    }

    private Assessment assess(Long executionId, Long consultRequestId,
            String question, List<FaqSearchResponse> sources) {
        StringBuilder context = new StringBuilder();
        for (FaqSearchResponse source : sources) {
            context.append("ID ").append(source.faqId()).append(" Q: ")
                    .append(source.question()).append("\nA: ").append(source.answer()).append('\n');
        }
        LlmRequest request = LlmRequest.builder()
                .executionId(executionId)
                .consultRequestId(consultRequestId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(PROMPT)
                .userPrompt("고객 질문: " + question + "\nFAQ 후보:\n" + context)
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .maxTokens(400)
                .contextCount(sources.size())
                .promptVersion("comparison-evidence-v3")
                .build();
        try {
            String output = llmClient.generate(request);
            if (output == null || output.isBlank()) {
                return Assessment.invalid();
            }
            JsonNode response = objectMapper.readTree(output);
            if (response == null || !response.isObject() || !response.path("answerable").isBoolean()) {
                return Assessment.invalid();
            }
            Assessment assessment = new Assessment(
                    response.path("leftTarget").asText(""),
                    response.path("rightTarget").asText(""),
                    response.path("criterion").asText(""),
                    verifiedSource(response.path("leftFaqId"), response.path("leftQuote"),
                            response.path("leftTarget").asText(""), sources),
                    verifiedSource(response.path("rightFaqId"), response.path("rightQuote"),
                            response.path("rightTarget").asText(""), sources),
                    response.path("answerable").asBoolean(false));
            if (!assessment.hasValidTargets(question)) {
                return Assessment.invalid();
            }
            return assessment;
        } catch (JsonProcessingException | NumberFormatException failure) {
            // 호출 장애와 취소는 상위 처리기로 전달하고, 읽을 수 없는 판정만 근거로 사용하지 않는다.
            log.warn("비교 근거 판정 형식 오류: executionId={}, errorType={}",
                    executionId, failure.getClass().getSimpleName());
            return Assessment.invalid();
        }
    }

    private VerifiedQuote verifiedSource(
            JsonNode faqId, JsonNode quote, String target, List<FaqSearchResponse> sources) {
        Matcher idMatcher = FAQ_ID.matcher(faqId.asText(""));
        String excerpt = quote.asText("").strip();
        if (!idMatcher.matches() || excerpt.length() < 8 || target.isBlank()) {
            return null;
        }
        long id = Long.parseLong(idMatcher.group(1));
        return sources.stream()
                .filter(source -> source.faqId() == id && source.answer() != null
                        && source.answer().contains(excerpt)
                        // FAQ 답변은 질문의 주어를 생략하는 경우가 많다.
                        && hasTargetAnchor(source.question() + " " + excerpt, target))
                .findFirst()
                .map(source -> new VerifiedQuote(source, enclosingSentence(source.answer(), excerpt)))
                .orElse(null);
    }

    private static String enclosingSentence(String answer, String excerpt) {
        int offset = answer.indexOf(excerpt);
        int start = offset;
        while (start > 0 && !isSentenceEnd(answer, start - 1)) {
            start--;
        }
        int end = offset + excerpt.length();
        while (end < answer.length() && !isSentenceEnd(answer, end - 1)) {
            end++;
        }
        return answer.substring(start, end).strip();
    }

    private static boolean isSentenceEnd(String text, int index) {
        char c = text.charAt(index);
        return (c == '.' || c == '!' || c == '?')
                && (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)));
    }

    private static Map<Long, FaqSearchResponse> byId(List<FaqSearchResponse> sources) {
        Map<Long, FaqSearchResponse> result = new LinkedHashMap<>();
        sources.forEach(source -> result.putIfAbsent(source.faqId(), source));
        return result;
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean hasTargetAnchor(String value, String target) {
        String normalized = compact(value);
        List<String> anchors = List.of(target.split("\\s+")).stream()
                .map(LlmComparisonEvidenceResolver::compact)
                .filter(token -> token.length() >= 2 && !token.equals("요금제"))
                .toList();
        return !anchors.isEmpty() && anchors.stream().allMatch(normalized::contains);
    }

    private static boolean hasCriterionAnchor(String question, String criterion) {
        String normalized = compact(question);
        // 연결어, '종류 수'의 보조 표현, 비교 표현은 허용하되 질문에 없는 속성은 허용하지 않는다.
        List<String> words = List.of(criterion.strip().split("\\s+")).stream()
                .map(LlmComparisonEvidenceResolver::compact)
                .filter(word -> !CRITERION_FILLERS.contains(word))
                .toList();
        return !words.isEmpty() && words.stream().allMatch(word -> normalized.contains(word)
                || (COMPARISON_WORDS.contains(word)
                    && (normalized.contains("비교") || normalized.contains("차이"))));
    }

    private record Assessment(
            String leftTarget, String rightTarget, String criterion,
            VerifiedQuote left, VerifiedQuote right, boolean answerable) {
        static Assessment invalid() {
            return new Assessment("", "", "", null, null, false);
        }

        boolean hasValidTargets(String question) {
            return leftTarget.length() >= 2 && rightTarget.length() >= 2
                    && !compact(leftTarget).equals(compact(rightTarget))
                    && hasTargetAnchor(question, leftTarget)
                    && hasTargetAnchor(question, rightTarget)
                    && !criterion.isBlank() && criterion.length() <= 80
                    && hasCriterionAnchor(question, criterion);
        }

        boolean supported() {
            return answerable && left != null && right != null;
        }

        Resolution resolution() {
            if (!supported()) {
                return new Resolution(List.of(), null);
            }
            List<FaqSearchResponse> result = new ArrayList<>();
            result.add(left.source());
            if (right.source().faqId() != left.source().faqId()) {
                result.add(right.source());
            }
            String answer;
            if (left.quote().contains(right.quote())) {
                answer = left.quote();
            } else if (right.quote().contains(left.quote())) {
                answer = right.quote();
            } else {
                answer = "%s: %s\n%s: %s".formatted(
                        leftTarget, left.quote(), rightTarget, right.quote());
            }
            return new Resolution(result, answer);
        }
    }

    private record VerifiedQuote(FaqSearchResponse source, String quote) {}
}
