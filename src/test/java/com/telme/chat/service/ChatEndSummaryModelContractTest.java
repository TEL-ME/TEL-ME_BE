package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryModelContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient client = mock(LlmClient.class);
    private final List<LlmRequest> calls = new ArrayList<>();

    @Test
    @DisplayName("연속하지 않는 원래 문의 번호를 프롬프트와 출력 스키마에 똑같이 명시한다")
    void 원래_문의_번호를_그대로_요구한다() throws Exception {
        respond();

        generate(input(true));

        assertThat(calls).singleElement().satisfies(request -> {
            JsonNode prompt = parse(request.userPrompt());
            assertThat(prompt.path("requiredTopicMessageIds").toString()).isEqualTo("[11,29]");
            assertThat(mapper.valueToTree(request.responseSchema()).path("properties")
                    .path("topics").path("required").toString()).isEqualTo("[\"11\",\"29\"]");
            assertThat(request.systemPrompt()).contains("새 번호로 바꾸지 않습니다", "JSON 객체 하나만");
        });
    }

    @Test
    @DisplayName("하위 문의 기록이 없는 조건 응답 구간도 실제 사용자 메시지 번호를 요구한다")
    void 요청_기록이_없는_구간의_출처를_유지한다() {
        respond();

        generate(input(false));

        assertThat(parse(calls.getFirst().userPrompt()).path("requiredTopicMessageIds").toString())
                .isEqualTo("[11,29]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"1\":\"유심\",\"2\":\"매장\"}", "{\"11\":\"유심\"}",
        "{\"11\":\"유심\",\"29\":\"매장\",\"99\":\"추가 문의\"}"})
    @DisplayName("프롬프트를 보강해도 문의 번호 변경·누락·추가 응답은 계속 거부한다")
    void 문의_번호_검증을_완화하지_않는다(String topics) {
        when(client.generate(any())).thenReturn("{\"topics\":" + topics
                + ",\"selectedSentenceIds\":[1,2],\"pendingItems\":[]}");

        assertThatThrownBy(() -> generate(input(true))).hasMessage("INVALID_OUTPUT");
    }

    private void respond() {
        when(client.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            calls.add(request);
            var prompt = mapper.readTree(request.userPrompt());
            var response = mapper.createObjectNode();
            var topics = response.putObject("topics");
            prompt.path("requiredTopicMessageIds").forEach(id -> topics.put(id.asText(), "문의"));
            var selected = response.putArray("selectedSentenceIds");
            prompt.path("guidanceCandidates").forEach(item -> selected.add(item.path("sentenceId").asInt()));
            response.putArray("pendingItems");
            return response.toString();
        });
    }

    private ChatEndSummaryData.Input input(boolean withRequests) {
        var messages = List.of(
                message(11, 1, null, "USER", "유심 재발급 비용은요?"),
                message(12, 2, 11L, "ASSISTANT", "유심 재발급 비용은 7,700원입니다."),
                message(29, 3, null, "USER", "매장 방문 준비물은요?"),
                message(30, 4, 29L, "ASSISTANT", "명의자 본인의 신분증을 지참해 매장을 방문해야 합니다."));
        var requests = withRequests ? List.of(
                new ChatEndSummaryData.Request(11, 1, "유심 재발급 비용", "DONE"),
                new ChatEndSummaryData.Request(29, 1, "매장 방문 준비물", "DONE"))
                : List.<ChatEndSummaryData.Request>of();
        return new ChatEndSummaryData.Input(1, 4, 1L, messages, requests, Set.of(), List.of(),
                OutputMaskContext.empty());
    }

    private ChatEndSummaryData.Message message(long id, int sequence, Long replyTo, String role, String text) {
        boolean answer = role.equals("ASSISTANT");
        return new ChatEndSummaryData.Message(id, sequence, replyTo, role, answer ? "ANSWER" : "QUESTION",
                text, answer ? "GROUNDED" : null, "COMPLETED");
    }

    private void generate(ChatEndSummaryData.Input input) {
        var target = new ChatSessionEndSummaryGenerator(mapper,
                (modelRequest, modelDeadline) -> client.generate(modelRequest), new ChatTokenEstimator(),
                new ChatInputInspector(mapper), new ChatOutputGuard(mapper),
                new ChatEndSummaryProperties(true, false, false, 3072, 768, 400, 8, 2,
                        Duration.ofMinutes(5), Duration.ofMinutes(15)), Clock.systemUTC());
        target.generate(input, Instant.now().plusSeconds(300));
    }

    private JsonNode parse(String text) {
        try {
            return mapper.readTree(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new AssertionError(failure);
        }
    }
}
