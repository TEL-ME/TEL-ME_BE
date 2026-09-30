package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerQualityBaselineProbeTest {

    private final AnswerQualityBaselineProbe probe = new AnswerQualityBaselineProbe();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void Bedrock유료호출은_명시적_허용_없이는_차단한다() {
        assertThatThrownBy(() -> AnswerQualityBaselineProbe.validatePaidCallOptIn("bedrock", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("유료 호출");
        AnswerQualityBaselineProbe.validatePaidCallOptIn("ollama", false);
    }

    @Test
    void Bedrock유료측정은_단가출처와_기준일이_없으면_차단한다() {
        assertThatThrownBy(() -> AnswerQualityBaselineProbe.validatePaidPricingMetadata("bedrock", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비용 재현 메타데이터");

        AnswerQualityBaselineProbe.validatePaidPricingMetadata("bedrock", Map.of(
                "AWS_REGION", "us-east-1",
                "TELME_PROBE_BEDROCK_TIER", "standard",
                "TELME_PROBE_INPUT_USD_PER_MTOK", "0.15",
                "TELME_PROBE_OUTPUT_USD_PER_MTOK", "0.60",
                "TELME_PROBE_PRICE_SOURCE", "AWS Bedrock pricing",
                "TELME_PROBE_PRICE_AS_OF", "2026-09-30"));
        AnswerQualityBaselineProbe.validatePaidPricingMetadata("ollama", Map.of());
    }

    @Test
    void 비교식별자와_반복번호가_없으면_측정을_시작하지_않는다() {
        assertThatThrownBy(() -> AnswerQualityBaselineProbe.validateComparisonMetadata(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPARISON_ID");
        assertThatThrownBy(() -> AnswerQualityBaselineProbe.validateComparisonMetadata(Map.of(
                "TELME_PROBE_COMPARISON_ID", "model-compare-v1",
                "TELME_PROBE_CODE_REVISION", "abc123",
                "TELME_PROBE_REPEAT_INDEX", "0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 이상");

        AnswerQualityBaselineProbe.validateComparisonMetadata(Map.of(
                "TELME_PROBE_COMPARISON_ID", "model-compare-v1",
                "TELME_PROBE_CODE_REVISION", "abc123",
                "TELME_PROBE_REPEAT_INDEX", "3"));
    }

    @Test
    void 토큰사용량과_실행당_예상비용을_함께_기록한다() {
        Map<String, Object> metadata = AnswerQualityBaselineProbe.bedrockUsageMetadata(
                "bedrock", "openai.gpt-oss-120b-1:0", 42_250, 4_019, 150,
                null, null, "us-east-1", "standard",
                new BigDecimal("0.15"), new BigDecimal("0.60"),
                "AWS Bedrock pricing", "2026-09-30");

        assertThat(metadata)
                .containsEntry("region", "us-east-1")
                .containsEntry("inference_tier", "standard")
                .containsEntry("estimated_cost_usd", new BigDecimal("0.00874890"))
                .containsEntry("price_source", "AWS Bedrock pricing")
                .containsEntry("price_as_of", "2026-09-30");
    }

    @Test
    void 결과에_기록하는_모델명은_실제_호출_설정에서_가져온다() {
        assertThat(AnswerQualityBaselineProbe.resolveGeneratorModel("bedrock",
                Map.of("TELME_PROBE_MODEL", "anthropic.claude-test")))
                .isEqualTo("anthropic.claude-test");
        assertThat(AnswerQualityBaselineProbe.resolveGeneratorModel("ollama",
                Map.of("LLM_MODEL", "exaone-test", "TELME_PROBE_MODEL", "unused-label")))
                .isEqualTo("exaone-test");
        assertThatThrownBy(() -> AnswerQualityBaselineProbe.resolveGeneratorModel("unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");
    }

    @Test
    void 비교에서는_RAG_답변_생성만_Bedrock으로_전환한다() {
        assertThat(AnswerQualityBaselineProbe.isBedrockTargetTask(
                LlmRequest.builder().taskType(TaskType.RAG_ANSWER).build())).isTrue();
        assertThat(AnswerQualityBaselineProbe.isBedrockTargetTask(
                LlmRequest.builder().taskType(TaskType.ROUTING).build())).isFalse();
    }

    @Test
    void Bedrock요청은_system과_user_역할을_분리하고_비교_생성설정을_고정한다() {
        LlmRequest request = LlmRequest.builder()
                .systemPrompt("근거 안에서 답하세요")
                .userPrompt("질문과 근거")
                .build();

        JsonNode body = mapper.valueToTree(AnswerQualityBaselineProbe.bedrockRequestBody(request));

        assertThat(body.path("system").get(0).path("text").asText()).isEqualTo("근거 안에서 답하세요");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").get(0).path("text").asText())
                .isEqualTo("질문과 근거");
        assertThat(body.path("inferenceConfig").path("temperature").asDouble()).isZero();
        assertThat(body.path("inferenceConfig").path("maxTokens").asInt()).isEqualTo(1024);
    }

    @Test
    void 유료_측정이_중단돼도_완료건수와_누적_토큰을_메타데이터에_남긴다() {
        Map<String, Object> metadata = AnswerQualityBaselineProbe.bedrockUsageMetadata(
                "bedrock", "openai.gpt-oss-120b-1:0", 1200, 340, 4,
                "EVAL-005", "Bedrock 호출 실패", "us-east-1", "standard",
                new BigDecimal("0.15"), new BigDecimal("0.60"),
                "AWS Bedrock pricing", "2026-09-30");

        assertThat(metadata)
                .containsEntry("input_tokens", 1200L)
                .containsEntry("output_tokens", 340L)
                .containsEntry("recorded_cases", 4)
                .containsEntry("status", "aborted")
                .containsEntry("failed_eval_id", "EVAL-005")
                .containsEntry("stop_reason", "Bedrock 호출 실패");
    }

    @Test
    void 중간_실패_시_부분결과와_사용량을_실제_파일로_함께_저장한다() throws IOException {
        Path directory = Files.createTempDirectory("answer-quality-partial");
        List<Map<String, Object>> partialResults = List.of(
                Map.of("eval_id", "EVAL-001", "answer", "첫 결과"),
                Map.of("eval_id", "EVAL-002", "answer", "두 번째 결과"));
        Map<String, Object> usage = AnswerQualityBaselineProbe.bedrockUsageMetadata(
                "bedrock", "test-model", 1200, 340, partialResults.size(),
                "EVAL-003", "모델 응답 실패", "us-east-1", "standard",
                new BigDecimal("0.15"), new BigDecimal("0.60"),
                "AWS Bedrock pricing", "2026-09-30");

        AnswerQualityBaselineProbe.ProbeArtifacts artifacts = AnswerQualityBaselineProbe.persistArtifacts(
                directory, "test-run", "평가셋 150건, 저장된 결과 2건\n중단 상태\n",
                partialResults, usage, mapper);

        try {
            assertThat(Files.readString(artifacts.report())).contains("저장된 결과 2건", "중단 상태");
            JsonNode results = mapper.readTree(artifacts.results().toFile());
            assertThat(results).hasSize(2);
            assertThat(results.get(1).path("eval_id").asText()).isEqualTo("EVAL-002");
            JsonNode savedUsage = mapper.readTree(artifacts.usage().toFile());
            assertThat(savedUsage.path("status").asText()).isEqualTo("aborted");
            assertThat(savedUsage.path("failed_eval_id").asText()).isEqualTo("EVAL-003");
            assertThat(savedUsage.path("input_tokens").asLong()).isEqualTo(1200);
            assertThat(savedUsage.path("output_tokens").asLong()).isEqualTo(340);
        } finally {
            Files.deleteIfExists(artifacts.report());
            Files.deleteIfExists(artifacts.results());
            Files.deleteIfExists(artifacts.usage());
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void replay입력에서_evalId에_맞는_검색근거를_복원한다() throws IOException {
        Path fixture = fixture("""
                [{"eval_id":"EVAL-001","question":"질문","sources":[{"faqId":7,"slotId":"PLAN-1",
                "category":"PLAN","question":"질문","answer":"근거 답변","score":0.91,
                "version":2,"updatedAt":"2026-09-29","searchRank":1,"matchedVariant":"QUESTION_ONLY"}]}]
                """);
        try {
            Map<String, List<FaqSearchResponse>> replay = probe.loadReplaySources(
                    fixture, List.of(new AnswerQualityBaselineProbe.EvalCase("EVAL-001", "ANSWER", "질문", null)));

            FaqSearchResponse source = replay.get("EVAL-001").getFirst();
            assertThat(source.faqId()).isEqualTo(7L);
            assertThat(source.slotId()).isEqualTo("PLAN-1");
            assertThat(source.question()).isEqualTo("질문");
            assertThat(source.answer()).isEqualTo("근거 답변");
            assertThat(source.matchedVariant()).isEqualTo("QUESTION_ONLY");
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    @Test
    void 평가셋과_evalId_question이_다르면_재생하지_않는다() throws IOException {
        Path fixture = fixture("""
                [{"eval_id":"EVAL-001","question":"다른 질문","sources":[]}]
                """);
        try {
            assertThatThrownBy(() -> probe.loadReplaySources(
                    fixture, List.of(new AnswerQualityBaselineProbe.EvalCase("EVAL-001", "ANSWER", "질문", null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eval_id/question");
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    @Test
    void replay입력의_evalId_누락을_감지한다() throws IOException {
        Path fixture = fixture("[]");
        try {
            assertThatThrownBy(() -> probe.loadReplaySources(
                    fixture, List.of(new AnswerQualityBaselineProbe.EvalCase("EVAL-001", "ANSWER", "질문", null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("구성");
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "{}",
            "[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\"}]",
            "[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\",\"sources\":null}]",
            "[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\",\"sources\":{}}]",
            "[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\",\"sources\":[\"손상된 근거\"]}]",
            "[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\",\"sources\":[{\"question\":\"FAQ\"}]}]"
    })
    void 손상된_재생입력을_검색결과없음으로_처리하지_않는다(String content) throws IOException {
        Path fixture = fixture(content);
        try {
            assertThatThrownBy(() -> probe.loadReplaySources(fixture,
                    List.of(new AnswerQualityBaselineProbe.EvalCase("EVAL-001", "ANSWER", "질문", null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Replay 입력");
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    @Test
    void 명시적인_빈_검색결과는_유효한_재생입력이다() throws IOException {
        Path fixture = fixture("[{\"eval_id\":\"EVAL-001\",\"question\":\"질문\",\"sources\":[]}]");
        try {
            assertThat(probe.loadReplaySources(fixture,
                    List.of(new AnswerQualityBaselineProbe.EvalCase("EVAL-001", "ANSWER", "질문", null))))
                    .containsEntry("EVAL-001", List.of());
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    @Test
    void 재생입력해시로_같은검색근거를_비교했는지_식별한다() throws IOException {
        Path fixture = fixture("same replay input");
        try {
            assertThat(AnswerQualityBaselineProbe.sha256(fixture))
                    .isEqualTo("3b53da29c900065b482074de3af6ac92e15cd40fe495fa2430f0ca7505daf3e8");
        } finally {
            Files.deleteIfExists(fixture);
        }
    }

    private Path fixture(String content) throws IOException {
        Path path = Files.createTempFile("answer-quality-replay", ".json");
        Files.writeString(path, content);
        return path;
    }
}
