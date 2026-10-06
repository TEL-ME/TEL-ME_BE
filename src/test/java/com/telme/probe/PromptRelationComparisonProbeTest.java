package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.AnswerPromptTemplates;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PromptRelationComparisonProbeTest {

    @Test
    @DisplayName("생성 원문과 Guard가 수정한 최종 답변을 별도로 기록한다")
    void 생성_원문과_Guard_최종문을_분리한다() throws Exception {
        var result = PromptRelationComparisonProbe.runCase(
                row(), emitting("7,700원입니다. 홈페이지에서 신청하세요."), "prompt", "v", false);

        assertThat(result)
                .containsEntry("raw_answer", "7,700원입니다. 홈페이지에서 신청하세요.")
                .containsEntry("answer", "7,700원입니다.")
                .containsEntry("observed_stage", "ANSWERED");
    }

    @Test
    @DisplayName("생성 전 검사·모델 거절·Guard 교체를 추정하지 않고 구분한다")
    void 거절_발생_단계를_구분한다() throws Exception {
        var pre = emitting("unused");
        when(pre.generate(any())).thenReturn("{\"answerable\":false}");

        assertThat(PromptRelationComparisonProbe.runCase(row(), pre, "prompt", "v", true))
                .containsEntry("observed_stage", "PRECHECK_REJECTED");
        verify(pre, never()).stream(any(), any());
        assertThat(PromptRelationComparisonProbe.runCase(
                row(), emitting(AnswerPromptTemplates.NO_EVIDENCE_ANSWER), "prompt", "v", false))
                .containsEntry("observed_stage", "MODEL_REFUSAL");
        assertThat(PromptRelationComparisonProbe.runCase(
                row(), emitting("홈페이지에서 신청하세요."), "prompt", "v", false))
                .containsEntry("observed_stage", "GUARD_REPLACED");
    }

    @Test
    @DisplayName("생성 실패와 검색 근거 없음도 결과에서 누락하지 않는다")
    void 생성_실패와_검색_없음을_보존한다() throws Exception {
        LlmClient error = mock(LlmClient.class);
        doThrow(new IllegalStateException("offline")).when(error).stream(any(), any());
        JsonNode empty = PromptRelationComparisonProbe.MAPPER.readTree(
                "{\"eval_id\":\"E\",\"type\":\"ANSWER\",\"question\":\"Q\",\"sources\":[]}");

        assertThat(PromptRelationComparisonProbe.runCase(row(), error, "p", "v", false))
                .containsEntry("status", "GENERATION_ERROR")
                .containsEntry("raw_answer", null);
        assertThat(PromptRelationComparisonProbe.runCase(empty, error, "p", "v", false))
                .containsEntry("status", "NO_SEARCH")
                .containsEntry("observed_stage", "NO_SEARCH");
    }

    @Test
    @DisplayName("v3과 새 후보 비교에서 입력·검색 근거·실제 모델 옵션을 동일하게 유지한다")
    void 전후_입력과_모델_옵션을_유지한다() throws Exception {
        var client = emitting("7,700원입니다.");
        String baseline = Files.readString(PromptRelationComparisonProbe.RESOURCES.resolve("rag-answer-v3.txt"));
        var converter = new OllamaRequestConverter(new LlmProperties(
                "test-model", Duration.ofSeconds(5), Duration.ofSeconds(60), 8192));

        PromptRelationComparisonProbe.runCase(row(), client, baseline, "rag-answer-v3", false);
        PromptRelationComparisonProbe.runCase(row(), client, AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT,
                AnswerPromptTemplates.PROMPT_VERSION, false);

        var capture = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client, times(2)).stream(capture.capture(), any());
        var requests = capture.getAllValues();
        LlmRequest before = requests.get(0);
        LlmRequest after = requests.get(1);
        assertThat(before.userPrompt()).isEqualTo(after.userPrompt());
        assertThat(before.contextCount()).isEqualTo(after.contextCount());
        assertThat(before.taskType()).isEqualTo(after.taskType());
        assertThat(before.format()).isEqualTo(after.format());
        assertThat(before.temperature()).isEqualTo(after.temperature());
        assertThat(before.maxTokens()).isEqualTo(after.maxTokens());
        assertThat(before.systemPrompt()).isEqualTo(baseline);
        assertThat(before.promptVersion()).isEqualTo("rag-answer-v3");
        assertThat(after.systemPrompt()).isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
        assertThat(after.promptVersion()).isEqualTo("rag-answer-v4.2");
        assertThat(converter.toChatRequest(before, true).options())
                .isEqualTo(converter.toChatRequest(after, true).options());
        assertThat(converter.toChatRequest(before, true).model())
                .isEqualTo(converter.toChatRequest(after, true).model());
    }

    @Test
    @DisplayName("기존 열 문항을 보존하고 미선택 문항도 결과에 남긴다")
    void 기존_열_문항과_미선택_상태를_보존한다() throws Exception {
        var cases = PromptRelationComparisonProbe.loadCases(
                PromptRelationComparisonProbe.RESOURCES.resolve("replay-cases.json"));

        assertThat(cases).hasSize(10);
        assertThat(PromptRelationComparisonProbe.selectedIds(cases, "EVAL-001,HALLU-010"))
                .containsExactly("EVAL-001", "HALLU-010");
        assertThat(PromptRelationComparisonProbe.pending(cases.get(0), false))
                .containsEntry("status", "NOT_SELECTED");
        assertThatThrownBy(() -> PromptRelationComparisonProbe.selectedIds(cases, "FAKE-ID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("개인 경로 없는 출처 설명을 읽고 고정 재생 문항과 연결한다")
    void 출처_설명과_고정_재생_입력을_읽는다() throws Exception {
        JsonNode provenance = PromptRelationComparisonProbe.MAPPER.readTree(
                PromptRelationComparisonProbe.RESOURCES.resolve("provenance.json").toFile());
        var cases = PromptRelationComparisonProbe.loadCases(
                PromptRelationComparisonProbe.RESOURCES.resolve("replay-cases.json"));
        List<String> selected = new ArrayList<>();
        provenance.path("selected_eval_ids").forEach(id -> selected.add(id.asText()));

        assertThat(provenance.has("origin_path")).isFalse();
        assertThat(provenance.path("origin_description").asText()).isNotBlank();
        assertThat(provenance.path("origin_sha256").asText()).matches("[0-9a-f]{64}");
        assertThat(selected)
                .containsExactlyElementsOf(cases.stream().map(row -> row.path("eval_id").asText()).toList());
        assertThat(provenance.path("new_gold_labels").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("손상된 재생 입력을 생성 전에 거부한다")
    void 손상된_재생_입력을_생성_전에_거부한다() throws Exception {
        var file = Files.createTempFile("prompt-replay", ".json");
        try {
            Files.writeString(file,
                    "[{\"eval_id\":\"E\",\"question\":\"Q\",\"sources\":["
                            + "{\"question\":\"FAQ\",\"answer\":null,\"score\":\"1\"}]}]");

            assertThatThrownBy(() -> PromptRelationComparisonProbe.loadCases(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("E sources[0]");
        } finally {
            Files.delete(file);
        }
    }

    @Test
    @DisplayName("마침표 없는 거절과 거절이 섞인 답변의 원래 상태를 유지한다")
    void 거절_문구와_혼합_답변을_구분한다() throws Exception {
        assertThat(PromptRelationComparisonProbe.runCase(
                row(), emitting("안내드릴 수 있는 정보가 없습니다"), "p", "v", false))
                .containsEntry("observed_stage", "MODEL_REFUSAL")
                .containsEntry("status", "GROUNDED");
        // 프로브는 관찰만 수행하며 원래 Guard·Judge 판정을 변경하지 않는다.
        assertThat(PromptRelationComparisonProbe.runCase(
                row(), emitting("7,700원입니다. 안내드릴 수 있는 정보가 없습니다."), "p", "v", false))
                .containsEntry("observed_stage", "UNCONFIRMED_MIXED_REFUSAL");
    }

    @Test
    @DisplayName("재시도는 이전 원문을 폐기하고 타임아웃은 불완전 원문으로 구분한다")
    void 재시도_원문_폐기와_타임아웃_상태를_보존한다() throws Exception {
        LlmClient retry = mock(LlmClient.class);
        doAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken("discarded");
            stream.onRetry(2, new IllegalStateException("retry"));
            stream.onToken("7,700원입니다.");
            stream.onComplete();
            return null;
        }).when(retry).stream(any(), any());

        var success = PromptRelationComparisonProbe.runCase(row(), retry, "p", "v", false);

        assertThat(success)
                .containsEntry("raw_answer", "7,700원입니다.")
                .containsEntry("answer", "7,700원입니다.");
        assertThat((List<?>) success.get("retry_events")).hasSize(1);
        LlmClient timeout = mock(LlmClient.class);
        doAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken("unfinished");
            stream.onError(new GeneralException(LlmErrorCode.TIMEOUT));
            return null;
        }).when(timeout).stream(any(), any());

        assertThat(PromptRelationComparisonProbe.runCase(row(), timeout, "p", "v", false))
                .containsEntry("status", "TIMEOUT")
                .containsEntry("raw_answer", null)
                .containsEntry("partial_raw", "unfinished")
                .containsEntry("answer", "")
                .containsEntry("raw_state", "INCOMPLETE");
    }

    private JsonNode row() throws Exception {
        return PromptRelationComparisonProbe.MAPPER.readTree(
                "{\"eval_id\":\"EXISTING\",\"type\":\"ANSWER\",\"question\":\"cost?\",\"sources\":["
                        + "{\"question\":\"FAQ?\",\"answer\":\"7,700원입니다.\",\"score\":0.9}]}");
    }

    private LlmClient emitting(String raw) {
        LlmClient client = mock(LlmClient.class);
        doAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken(raw);
            stream.onComplete();
            return null;
        }).when(client).stream(any(), any());
        return client;
    }
}
