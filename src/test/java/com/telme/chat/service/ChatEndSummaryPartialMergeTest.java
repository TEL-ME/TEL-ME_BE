package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryPartialMergeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient client = mock(LlmClient.class);
    private final List<JsonNode> prompts = new ArrayList<>();

    @BeforeEach
    void 부분_요약의_선택_결과를_준비한다() {
        when(client.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            var body = mapper.readTree(request.userPrompt());
            prompts.add(body);
            var output = mapper.createObjectNode();
            var topics = output.putObject("topics");
            for (var query : body.path("requests")) {
                topics.put(query.path("originMessageId").asText(), "유심 재발급 문의");
            }
            var ids = output.putArray("selectedSentenceIds");
            body.path("guidanceCandidates").forEach(value -> ids.add(value.path("sentenceId").asInt()));
            output.putArray("pendingItems");
            return output.toString();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    @DisplayName("부분 요약의 병합 입력이 초과해도 8개와 9개 안내를 모두 보존한다")
    void 병합_입력_초과에서_원문과_출처를_보존하고_기존_한도를_지킨다(int unique) throws Exception {
        var input = input(unique, false);
        var result = generate(input);
        assertThat(result.content().guidance()).hasSize(unique);
        var root = mapper.readTree(result.json());
        var sources = new java.util.HashSet<Long>();
        root.path("guidance").forEach(item -> item.path("sourceMessageIds")
                .forEach(id -> sources.add(id.asLong())));
        assertThat(sources).containsExactlyInAnyOrder(2L, 4L, 6L, 8L, 10L, 12L, 14L, 16L, 18L);
        for (var message : input.messages()) {
            if (message.role().equals("ASSISTANT")) {
                assertThat(result.content().guidance()).contains("안내받은 내용: " + message.content());
            }
        }
        assertThat(prompts).hasSize(2).allSatisfy(prompt -> assertThat(prompt.has("partialSummaries")).isFalse());
    }

    @Test
    @DisplayName("서버 병합에도 부분 요약 단계의 민감번호 마스킹과 최종 출력 검사를 유지한다")
    void 병합_대체_경로에서_민감번호를_노출하지_않는다() {
        var result = generate(input(8, true));

        assertThat(result.json()).contains("***").doesNotContain("900101-1234567", "1234567");
        assertThat(prompts).allSatisfy(prompt -> assertThat(prompt.toString())
                .doesNotContain("900101-1234567", "1234567"));
        assertThat(result.content().guidance()).hasSize(8);
    }

    @Test
    @DisplayName("병합 입력 초과 뒤에도 마지막 문의의 미확인 지역을 남은 사항에 보존한다")
    void 병합_대체_경로에서_남은_사항을_보존한다() {
        var original = input(8, false);
        var messages = new ArrayList<>(original.messages());
        messages.add(new ChatEndSummaryData.Message(19, 19, null, "USER", "QUESTION",
                "방문할 매장을 알려주세요.", null, "COMPLETED"));
        messages.add(new ChatEndSummaryData.Message(20, 20, 19L, "ASSISTANT", "CLARIFICATION",
                "방문할 지역을 알려주세요.", null, "COMPLETED"));
        var requests = new ArrayList<>(original.requests());
        requests.add(new ChatEndSummaryData.Request(19, 1, "방문 매장 문의", "WAITING_CONDITION",
                List.of("방문할 지역을 알려주세요."), 99L));
        var input = new ChatEndSummaryData.Input(1, 20, 1L, messages, requests, Set.of(19L), List.of(),
                OutputMaskContext.empty());

        var result = generate(input);

        assertThat(result.content().pendingItems()).anySatisfy(text -> assertThat(text).contains("지역"));
        assertThat(result.content().guidance()).hasSize(8);
        assertThat(prompts).allSatisfy(prompt -> assertThat(prompt.has("partialSummaries")).isFalse());
    }

    @Test
    @DisplayName("부분 결과의 잘못된 출처는 서버 병합으로 우회하지 않는다")
    void 검증되지_않은_부분_결과를_병합하지_않는다() {
        doReturn("""
                {"topics":{"999":"유심 문의"},"selectedSentenceIds":[1],"pendingItems":[]}
                """).when(client).generate(any());

        assertThatThrownBy(() -> generate(input(8, false))).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("병합 대체 경로에서도 본인과 어머니 및 다른 회선의 원문 출처를 구분한다")
    void 같은_원문이어도_다른_대상과_회선의_문의로_잘못_합치지_않는다() throws Exception {
        var original = input(8, false);
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        for (int index = 0; index < 9; index++) {
            long id = index * 2L + 1;
            String question = switch (index) {
                case 0 -> "본인 첫 번째 회선 유심 재발급 절차를 알려주세요.";
                case 1 -> "어머니 회선 유심 재발급 절차를 알려주세요.";
                case 8 -> "본인의 다른 회선 유심 재발급 절차를 알려주세요.";
                default -> "본인 첫 번째 회선 유심 재발급 조건 " + index + "을 알려주세요.";
            };
            String prefix = index == 1
                    ? "어머니 본인이 신분증을 지참하고 매장을 방문해야 하며 비용은 5,500원입니다. "
                    : "명의자 본인이 신분증을 지참하고 매장을 방문해야 하며 비용은 7,700원입니다. ";
            String answer = prefix + original.messages().get(index * 2 + 1).content();
            messages.add(new ChatEndSummaryData.Message(id, (int) id, null, "USER", "QUESTION",
                    question, null, "COMPLETED"));
            messages.add(new ChatEndSummaryData.Message(id + 1, (int) id + 1, id, "ASSISTANT", "ANSWER",
                    answer, "GROUNDED", "COMPLETED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, question, "DONE"));
        }
        var input = new ChatEndSummaryData.Input(1, 18, 1L, messages, requests, Set.of(), List.of(),
                OutputMaskContext.empty());
        var extracted = generate(input);
        var result = new ChatEndSummaryGroundedGenerator(mapper, new ChatInputInspector(mapper),
                new ChatOutputGuard(mapper)).generate(input, extracted);
        var inquiries = mapper.readTree(result.summary().json()).path("guidance");

        assertThat(prompts).hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(prompt -> assertThat(prompt.has("partialSummaries")).isFalse());
        var matchedSources = new java.util.HashSet<Long>();
        for (var inquiry : inquiries) {
            var ids = new java.util.HashSet<Long>();
            inquiry.path("sourceMessageIds").forEach(value -> ids.add(value.asLong()));
            if (ids.contains(4L)) {
                matchedSources.add(4L);
                assertThat(ids).doesNotContain(2L, 18L);
                assertThat(inquiry.path("text").asText()).contains("어머니 본인", "신분증", "5,500원");
            }
            if (ids.contains(2L) || ids.contains(18L)) {
                assertThat(ids).doesNotContain(4L);
                assertThat(ids.contains(2L) && ids.contains(18L)).isFalse();
                matchedSources.addAll(ids);
                assertThat(inquiry.path("text").asText()).contains("명의자 본인", "신분증", "7,700원");
            }
        }
        assertThat(matchedSources).contains(2L, 4L, 18L);
        assertThat(result.summary().json()).doesNotContain("처리 대기", "접수되었습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "[2,1]", "[2]"})
    @DisplayName("선택 순서와 과거 안내 복원 여부가 달라도 최신 정정만 남긴다")
    void 누락_보완으로_과거_안내가_추가돼도_정정을_되돌리지_않는다(String selections) {
        String oldAnswer = "지난달 가입한 요금제는 변경이 가능합니다.";
        String corrected = "이번 달 가입한 요금제는 변경이 불가능합니다. 이전 안내는 현재 조건에 적용되지 않습니다.";
        var input = new ChatEndSummaryData.Input(1, 4, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "지난달 가입한 요금제를 변경할 수 있나요?", null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER",
                        oldAnswer, "GROUNDED", "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION",
                        "지난달이 아니라 이번 달 가입입니다. 정정합니다.", null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER",
                        corrected, "GROUNDED", "COMPLETED")), List.of(
                new ChatEndSummaryData.Request(1, 1, "요금제 변경 문의", "DONE"),
                new ChatEndSummaryData.Request(3, 1, "이번 달 가입 요금제 변경 문의", "DONE")),
                Set.of(), List.of(), OutputMaskContext.empty());
        doReturn("{\"topics\":{\"1\":\"요금제 변경\",\"3\":\"요금제 변경\"},"
                + "\"selectedSentenceIds\":" + selections + ",\"pendingItems\":[]}")
                .when(client).generate(any());

        var result = new ChatEndSummaryGroundedGenerator(mapper, new ChatInputInspector(mapper),
                new ChatOutputGuard(mapper)).generate(input, generate(input));

        assertThat(result.summary().content().guidance()).containsExactly(corrected);
        assertThat(result.summary().json()).doesNotContain(oldAnswer);
    }

    private ChatEndSummaryData.Result generate(ChatEndSummaryData.Input input) {
        var target = new ChatSessionEndSummaryGenerator(mapper, client, new ChatTokenEstimator(),
                new ChatInputInspector(mapper), new ChatOutputGuard(mapper),
                new ChatEndSummaryProperties(true, false, false, 3072, 768, 400, 8, 2,
                        Duration.ofMinutes(5), Duration.ofMinutes(15)), Clock.systemUTC());
        return target.generate(input, Instant.now().plusSeconds(300));
    }

    private ChatEndSummaryData.Input input(int unique, boolean privacy) {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        for (int index = 0; index < 9; index++) {
            long id = index * 2L + 1;
            String question = "유심 재발급 절차와 준비물을 확인하는 문의입니다 ".substring(0, 12) + " " + index;
            String prefix = privacy && index % unique == 0 ? "합성 주민번호 900101-1234567입니다. " : "";
            String answer = prefix + "유심 재발급의 안내 항목 " + index % unique + "입니다. "
                    + "방문 전 준비물과 이용 절차 및 필요한 조건을 확인하는 안내입니다 ".repeat(20)
                    .substring(0, 125) + " 안내를 확인했습니다.";
            messages.add(new ChatEndSummaryData.Message(id, (int) id, null, "USER", "QUESTION",
                    question, null, "COMPLETED"));
            messages.add(new ChatEndSummaryData.Message(id + 1, (int) id + 1, id, "ASSISTANT", "ANSWER",
                    answer, "GROUNDED", "COMPLETED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, question, "DONE"));
        }
        return new ChatEndSummaryData.Input(1, 18, 1L, messages, requests, Set.of(), List.of(),
                OutputMaskContext.empty());
    }
}
