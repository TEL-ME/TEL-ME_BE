package com.telme.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.ComparisonCheckProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableConfigurationProperties(ComparisonCheckProperties.class)
public class ComparisonEvidenceJudge {

    private final LlmClient client;
    private final ObjectMapper mapper;
    private final ComparisonCheckProperties properties;
    private final ExecutionTrace trace;

    @Autowired
    public ComparisonEvidenceJudge(
            @Qualifier(BedrockConfig.EVIDENCE_JUDGE_LLM_CLIENT) ObjectProvider<LlmClient> clients,
            ObjectMapper mapper, ComparisonCheckProperties properties, ExecutionTrace trace) {
        this(clients.getIfAvailable(), mapper, properties, trace);
    }

    public ComparisonEvidenceJudge(LlmClient client, ObjectMapper mapper,
            ComparisonCheckProperties properties, ExecutionTrace trace) {
        this.client = client;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        this.properties = properties;
        this.trace = trace;
    }

    // 생성 재시도에도 같은 세션을 넘겨 한 답변 요청에서 추가 판정 호출을 최대 한 번으로 제한한다.
    Function<List<String>, Set<String>> newReview(Long executionId, Long consultRequestId,
            String question, String evidence) {
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Set<String>> cached = new AtomicReference<>(Set.of());
        return sentences -> {
            if (!properties.enabled() || client == null) {
                record(executionId, "NOT_RUN", sentences.size(), 0);
                return Set.of();
            }
            if (!called.compareAndSet(false, true)) {
                Set<String> approved = new HashSet<>(sentences);
                approved.retainAll(cached.get());
                record(executionId, "CACHED", sentences.size(), approved.size());
                return Set.copyOf(approved);
            }
            if (sentences.size() > properties.maxSentences() || evidence.isBlank()
                    || (long) question.length() + evidence.length()
                    + sentences.stream().mapToLong(String::length).sum() > properties.maxInputChars()) {
                record(executionId, "LIMIT", sentences.size(), 0);
                return Set.of();
            }
            try {
                String response = client.generate(
                        request(executionId, consultRequestId, question, evidence, sentences));
                Set<String> approved = parse(response, evidence, sentences);
                cached.set(approved);
                record(executionId, "COMPLETED", sentences.size(), approved.size());
                return approved;
            } catch (LlmStreamCancelledException cancelled) {
                throw cancelled;
            } catch (Exception failure) {
                log.warn("[ComparisonEvidenceJudge] 판정 실패 executionId={} type={}",
                        executionId, failure.getClass().getSimpleName());
                record(executionId, "FAILED", sentences.size(), 0);
                return Set.of();
            }
        };
    }

    private LlmRequest request(Long executionId, Long consultRequestId, String question, String evidence,
            List<String> sentences) throws Exception {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int id = 0; id < sentences.size(); id++) {
            candidates.add(Map.of("id", id, "text", sentences.get(id)));
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question", question);
        input.put("evidence", evidence);
        input.put("sentences", candidates);
        return LlmRequest.builder().executionId(executionId).consultRequestId(consultRequestId)
                .taskType(TaskType.EVIDENCE_CHECK).systemPrompt(ComparisonEvidencePromptTemplates.SYSTEM_PROMPT)
                .userPrompt(mapper.writeValueAsString(input)).format(ResponseFormat.JSON)
                .temperature(0.0).maxTokens(properties.maxTokens())
                .promptVersion(ComparisonEvidencePromptTemplates.PROMPT_VERSION).build();
    }

    private Set<String> parse(String response, String evidence, List<String> sentences) throws Exception {
        if (response == null || response.length() > 32000) {
            return Set.of();
        }
        JsonNode root = mapper.readTree(response);
        if (root == null || !root.isObject() || root.size() != 1) {
            return Set.of();
        }
        JsonNode decisions = root.path("decisions");
        if (!decisions.isArray() || decisions.size() != sentences.size()) {
            return Set.of();
        }
        Set<Integer> seen = new HashSet<>();
        Set<String> approved = new HashSet<>();
        for (JsonNode decision : decisions) {
            JsonNode id = decision.path("id");
            JsonNode supported = decision.path("supported");
            JsonNode quote = decision.path("evidence");
            if (!decision.isObject() || decision.size() != 3 || !id.isIntegralNumber() || !id.canConvertToInt()
                    || id.intValue() < 0 || id.intValue() >= sentences.size() || !seen.add(id.intValue())
                    || !supported.isBoolean() || !quote.isTextual()) {
                return Set.of();
            }
            if (supported.booleanValue()) {
                if (quote.textValue().isBlank() || !evidence.contains(quote.textValue())) {
                    return Set.of();
                }
                approved.add(sentences.get(id.intValue()));
            }
        }
        return Set.copyOf(approved);
    }

    private void record(Long executionId, String outcome, int candidates, int approved) {
        trace.append(executionId, "comparisonChecks", Map.of("outcome", outcome,
                "candidateCount", candidates, "approvedCount", approved,
                "promptVersion", ComparisonEvidencePromptTemplates.PROMPT_VERSION));
    }
}
