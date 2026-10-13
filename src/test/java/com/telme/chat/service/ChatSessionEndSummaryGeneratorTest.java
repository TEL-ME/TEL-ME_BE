package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ChatSessionEndSummaryGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient client = mock(LlmClient.class);
    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");
    private ChatSessionEndSummaryGenerator generator;

    @BeforeEach
    void 생성기를_준비한다() {
        generator = generator(3072);
    }

    @Test
    @DisplayName("안내는 번호로 선택하고 서버가 원문과 출처를 조립한다")
    void 안내_번호만_받아_원문을_조립한다() throws Exception {
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(input());

        assertThat(result.content().guidance()).containsExactly("안내받은 내용: 홈페이지를 확인해 주세요.");
        assertThat(mapper.readTree(result.json()).path("guidance").get(0).path("sourceMessageIds").toString())
                .isEqualTo("[2]");
        var request = request();
        assertThat(request.executionId()).isEqualTo(10L);
        assertThat(request.promptVersion()).isEqualTo("consultation-end-summary-v9");
        var schema = mapper.valueToTree(request.responseSchema()).path("properties");
        assertThat(schema.has("guidance")).isFalse();
        assertThat(schema.path("selectedSentenceIds").path("items").path("enum").toString()).isEqualTo("[1]");
        assertThat(mapper.readTree(request.userPrompt()).path("guidanceCandidates").get(0).path("text").asText())
                .isEqualTo("홈페이지를 확인해 주세요.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[99]", "[0]", "[-1]", "[1,1]", "[1.5]", "[\"1\"]", "null"})
    @DisplayName("틀린 번호를 첫 안내로 대체하지 않고 제한된 재요청 후 거부한다")
    void 틀린_번호는_임의로_보정하지_않는다(String ids) throws Exception {
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.set("selectedSentenceIds", mapper.readTree(ids));
        when(client.generate(any())).thenReturn(response.toString());

        assertThatThrownBy(() -> generate(input())).hasMessage("INVALID_SELECTION");
        verify(client, times(2)).generate(any());
    }

    @Test
    @DisplayName("번호 선택 오류는 한 번 다시 요청하고 올바른 결과만 저장 후보로 사용한다")
    void 번호_재요청이_성공하면_정상_결과를_사용한다() {
        when(client.generate(any())).thenReturn(selection(1, 99), selection(1, 1));

        assertThat(generate(input()).content().guidance()).hasSize(1);
        verify(client, times(2)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "잘못된 JSON", "null",
            "{\"topics\":null,\"selectedSentenceIds\":[],\"pendingItems\":[]}",
            "{\"topics\":{\"1\":1},\"selectedSentenceIds\":[1],\"pendingItems\":[]}",
            "{\"topics\":{\"99\":\"문의\"},\"selectedSentenceIds\":[1],\"pendingItems\":[]}",
            "{\"topics\":{\"one\":\"문의\"},\"selectedSentenceIds\":[1],\"pendingItems\":[]}",
            "{\"topics\":{\"1\":\"문의\",\"1\":\"다른 문의\"},\"selectedSentenceIds\":[1],\"pendingItems\":[]}"})
    @DisplayName("형식이 틀리거나 문의 출처가 없는 결과는 거부한다")
    void 잘못된_형식과_출처를_거부한다(String response) {
        when(client.generate(any())).thenReturn(response);

        assertThatThrownBy(() -> generate(input())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("모델이 안내 문장을 직접 출력하거나 출처를 임의 추가하면 거부한다")
    void 모델의_안내_문장_출력은_받지_않는다() {
        when(client.generate(any())).thenReturn("""
                {"topics":[{"text":"로밍 문의","sourceMessageIds":[1]}],
                    "guidance":[{"text":"신청 완료","sourceMessageIds":[2]}],"pendingItems":[]}
                """);

        assertThatThrownBy(() -> generate(input())).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("같은 문장이어도 선택한 후보의 실제 원문 메시지만 출처로 조립한다")
    void 안내_출처를_서버에서_고정한다() throws Exception {
        var messages = new ArrayList<>(input().messages());
        messages.add(message(3, 3, 1L, "ASSISTANT", "ANSWER", "고객센터 안내", "GROUNDED"));
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(new ChatEndSummaryData.Input(1, 3, 10L, messages, List.of(), Set.of()));

        assertThat(mapper.readTree(result.json()).path("guidance").get(0).path("sourceMessageIds").toString())
                .isEqualTo("[2]");
    }

    @Test
    @DisplayName("조건과 예외 문장을 하나의 안내 후보로 유지한다")
    void 조건절을_따로_선택하지_않는다() throws Exception {
        String text = "가입할 수 있습니다. 단, 만 65세 이상인 경우에만 가능합니다.";
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(withGuidance(text));

        assertThat(result.content().guidance()).containsExactly("안내받은 내용: " + text);
        assertThat(mapper.readTree(request().userPrompt()).path("guidanceCandidates")).hasSize(1);
    }

    @Test
    @DisplayName("짧더라도 무료나 불가 같은 중요한 안내를 버리지 않는다")
    void 짧은_정책_안내도_유지한다() {
        when(client.generate(any())).thenReturn(selection(1, 1));

        assertThat(generate(withGuidance("무료입니다.")).content().guidance())
                .containsExactly("안내받은 내용: 무료입니다.");
    }

    @Test
    @DisplayName("실제 안내가 있는데 선택 번호가 비면 성공으로 표시하지 않는다")
    void 실제_안내의_누락은_거부한다() {
        when(client.generate(any())).thenReturn(selection(1));

        assertThatThrownBy(() -> generate(input())).hasMessage("INVALID_OUTPUT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_EVIDENCE", "OUT_OF_SCOPE"})
    @DisplayName("근거 없음·범위 밖 대체 문구는 안내 후보에서 제외하고 미답변 문의를 유지한다")
    void 답변_불가_문구를_정상_안내로_선택하지_않는다(String basis) throws Exception {
        var response = (ObjectNode) mapper.readTree(selection(1));
        var pending = response.withArray("pendingItems").addObject();
        pending.put("text", "답변하지 못한 로밍 문의");
        pending.putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());
        var input = new ChatEndSummaryData.Input(1, 2, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "로밍 신청 방법", null),
                message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내드릴 수 있는 정보가 없습니다.", basis)),
                List.of(), Set.of(1L));

        var result = generate(input);

        assertThat(result.content().guidance()).isEmpty();
        assertThat(result.content().pendingItems()).containsExactly("답변하지 못한 로밍 문의");
        var request = request();
        assertThat(mapper.readTree(request.userPrompt()).path("guidanceCandidates")).isEmpty();
        assertThat(mapper.valueToTree(request.responseSchema()).path("properties")
                .path("selectedSentenceIds").path("maxItems").asInt()).isZero();
    }

    @Test
    @DisplayName("되묻기 질문은 안내 후보에 넣지 않는다")
    void 되묻기_질문을_선택할_수_없다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 2, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "유심 서류", null),
                message(2, 2, 1L, "ASSISTANT", "CLARIFICATION", "대리인이 방문하시는 건가요?", null)),
                List.of(), Set.of());
        when(client.generate(any())).thenReturn(selection(1));

        assertThat(generate(input).content().guidance()).isEmpty();
        assertThat(mapper.readTree(request().userPrompt()).path("guidanceCandidates")).isEmpty();
    }

    @Test
    @DisplayName("복합 답변에서 안내는 보존하고 추가 조건 요청은 안내에서 제외한다")
    void 부분_답변과_확인_요청을_구분한다() {
        var input = new ChatEndSummaryData.Input(1, 2, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "로밍과 매장 문의", null),
                message(2, 2, 1L, "ASSISTANT", "CLARIFICATION",
                        "로밍은 홈페이지에서 신청합니다. 방문할 지역을 알려주세요.", null)), List.of(), Set.of());
        when(client.generate(any())).thenReturn(selection(1, 1));

        assertThat(generate(input).content().guidance())
                .containsExactly("안내받은 내용: 로밍은 홈페이지에서 신청합니다.");
    }

    @Test
    @DisplayName("지원 개인정보는 후보와 최종 결과에서 마스킹한다")
    void 후보와_결과에_민감번호를_남기지_않는다() {
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(withGuidance("번호 900101-1000000을 전달했습니다."));

        assertThat(result.json()).contains("[주민등록번호]").doesNotContain("900101-1000000");
        assertThat(request().userPrompt()).doesNotContain("900101-1000000");
    }

    @Test
    @DisplayName("선택한 원문에도 기존 출력 욕설 검사를 적용한다")
    void 원문_조립_후_출력_검사를_유지한다() {
        when(client.generate(any())).thenReturn(selection(1, 1));

        assertThatThrownBy(() -> generate(withGuidance("씨발")))
                .isInstanceOf(ChatOutputBlockedException.class);
    }

    @Test
    @DisplayName("지난 작업 기한에서는 모델을 호출하지 않는다")
    void 기한이_지나면_모델을_부르지_않는다() {
        assertThatThrownBy(() -> generator.generate(input(), now)).hasMessage("WORK_LIMIT");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("한 대화가 예산을 넘으면 원문을 임의로 잘라 성공시키지 않는다")
    void 한_대화_초과는_명확히_거부한다() {
        var input = new ChatEndSummaryData.Input(1, 1, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "문의".repeat(4000), null)), List.of(), Set.of());

        assertThatThrownBy(() -> generate(input)).hasMessage("INPUT_LIMIT");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("v8 저장 결과는 새 버전에서도 재생성 없이 조회할 수 있다")
    void 기존_저장_결과의_조회_계약을_유지한다() {
        String stored = """
                {"topics":[{"text":"로밍 문의","sourceMessageIds":[1]}],
                    "guidance":[{"text":"안내받은 내용: 홈페이지 확인","sourceMessageIds":[2]}],"pendingItems":[]}
                """;

        assertThat(generator.readContent(stored).guidance()).containsExactly("안내받은 내용: 홈페이지 확인");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("손상된 저장 결과를 빈 정상 요약으로 바꾸지 않는다")
    void 손상된_저장_결과는_거부한다() {
        assertThatThrownBy(() -> generator.readContent("{}")).hasMessage("INVALID_STORED_RESULT");
    }

    @Test
    @DisplayName("원래 문의가 있으면 단답형 조건 응답만 문의 출처로 쓰지 않는다")
    void 문의_출처는_원래_질문을_유지한다() {
        var messages = new ArrayList<>(input().messages());
        messages.add(message(3, 3, null, "USER", "QUESTION", "예", null));
        var input = new ChatEndSummaryData.Input(1, 3, 10L, messages,
                List.of(new ChatEndSummaryData.Request(1, 1, "로밍", "DONE")), Set.of());
        when(client.generate(any())).thenReturn(selection(3, 1));

        assertThatThrownBy(() -> generate(input)).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("다른 문의를 빠뜨리거나 원본 번호를 한 항목에 합치면 거부한다")
    void 여러_문의의_원본을_보존한다() {
        var messages = new ArrayList<>(input().messages());
        messages.add(message(3, 3, null, "USER", "QUESTION", "유심 서류", null));
        var input = new ChatEndSummaryData.Input(1, 3, 10L, messages, List.of(
                new ChatEndSummaryData.Request(1, 1, "로밍", "DONE"),
                new ChatEndSummaryData.Request(3, 1, "유심 서류", "DONE")), Set.of());
        when(client.generate(any())).thenReturn(selection(3, 1));

        assertThatThrownBy(() -> generate(input)).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("미확인 조건은 모델이 일부만 적어도 저장 상태에서 모두 보존한다")
    void 미확인_조건을_모두_남긴다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 2, 10L, input().messages(), List.of(
                new ChatEndSummaryData.Request(1, 1, "로밍", "WAITING_CONDITION",
                        List.of("방문할 지역을 알려주세요.", "대리인이 방문하시는 건가요?"))), Set.of(1L));
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.withArray("pendingItems").addObject().put("text", "지역 확인 필요")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        assertThat(generate(input).content().pendingItems()).containsExactly(
                "미확인 조건: 방문할 지역을 알려주세요.", "미확인 조건: 대리인이 방문하시는 건가요?");
    }

    @Test
    @DisplayName("최신 조건값과 정정 원문은 모든 부분 생성과 병합에 동일하게 전달한다")
    void 긴_상담에서도_조건_스냅샷과_번호를_유지한다() throws Exception {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        for (int index = 0; index < 3; index++) {
            long q = index * 2L + 1;
            messages.add(message(q, index * 2 + 1, null, "USER", "QUESTION", "요금제 문의 " + index, null));
            String paragraph = ("안내 항목 " + index + "입니다. ").repeat(30);
            messages.add(message(q + 1, index * 2 + 2, q, "ASSISTANT", "ANSWER",
                    paragraph + "\n\n" + paragraph + "\n\n" + paragraph, "GROUNDED"));
            requests.add(new ChatEndSummaryData.Request(q, 1, "요금제 문의 " + index, "DONE"));
        }
        messages.add(message(7, 7, null, "USER", "QUESTION", "지난달이 아니라 이번 달이에요", null));
        var conditions = List.of(new ChatEndSummaryData.Condition(11, 1, "joined_this_month", "true",
                "FILLED", "ASKED", 7L, 7));
        var input = new ChatEndSummaryData.Input(1, 7, 10L, messages, requests, Set.of(), conditions);
        var calls = new ArrayList<JsonNode>();
        when(client.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            var body = mapper.readTree(request.userPrompt());
            calls.add(body);
            var result = mapper.createObjectNode();
            var topics = result.putObject("topics");
            if (body.has("partialSummaries")) {
                for (var partial : body.path("partialSummaries")) {
                    partial.path("topics").forEach(topic -> topics.put(
                            topic.path("sourceMessageIds").get(0).asText(), topic.path("text").asText()));
                }
            } else {
                for (var origin : body.path("requests")) {
                    topics.put(origin.path("originMessageId").asText(), origin.path("query").asText() + " 이번 달 기준");
                }
                if (topics.isEmpty()) {
                    topics.put("7", "가입월 정정");
                }
            }
            var selected = result.putArray("selectedSentenceIds");
            if (!body.path("guidanceCandidates").isEmpty()) {
                selected.add(body.path("guidanceCandidates").get(0).path("sentenceId").intValue());
            }
            result.putArray("pendingItems");
            return result.toString();
        });

        var result = generator(2300).generate(input, now.plusSeconds(300));

        assertThat(calls).hasSizeGreaterThan(1);
        assertThat(calls).allSatisfy(body -> {
            assertThat(body.path("currentConditions").get(0).path("value").asText()).isEqualTo("true");
            assertThat(body.path("conditionSources").get(0).path("content").asText()).contains("이번 달");
        });
        assertThat(result.content().topics()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(calls.stream().flatMap(body -> java.util.stream.StreamSupport.stream(
                        body.path("guidanceCandidates").spliterator(), false))
                .map(candidate -> candidate.path("sentenceId").intValue()).distinct().toList())
                .containsExactly(1, 2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CANCELLED"})
    @DisplayName("끝내지 못한 답변은 모델의 새 질문 대신 미완료 문의로 표시한다")
    void 실패한_문의에_새_질문을_만들지_않는다(String status) throws Exception {
        var input = new ChatEndSummaryData.Input(1, 1, 10L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "유심 재발급 비용이 얼마예요?", null, status)), List.of(), Set.of(1L));
        var response = (ObjectNode) mapper.readTree(selection(1));
        response.withObject("topics").put("1", "유심 재발급 비용");
        response.withArray("pendingItems").addObject().put("text", "유심 비용을 알려주세요.")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        var result = generate(input);

        assertThat(result.content().guidance()).isEmpty();
        assertThat(result.content().pendingItems()).containsExactly("답변을 마치지 못한 문의: 유심 재발급 비용");
        assertThat(mapper.readTree(result.json()).path("pendingItems").get(0)
                .path("sourceMessageIds").toString()).isEqualTo("[1]");
    }

    @Test
    @DisplayName("모델이 누락해도 실행 실패로 남은 문의를 보존한다")
    void 모델이_누락한_실패_문의도_보존한다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 1, 10L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "유심 재발급 비용이 얼마예요?", null, "FAILED")), List.of(), Set.of(1L));
        var response = (ObjectNode) mapper.readTree(selection(1));
        response.withObject("topics").put("1", "유심 재발급 비용");
        when(client.generate(any())).thenReturn(response.toString());

        assertThat(generate(input).content().pendingItems())
                .containsExactly("답변을 마치지 못한 문의: 유심 재발급 비용");
    }

    @Test
    @DisplayName("미답변 상태가 없는 상담에 새로운 남은 문의를 만들지 않는다")
    void 완료된_상담에_남은_문의를_추가하지_않는다() throws Exception {
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.withArray("pendingItems").addObject().put("text", "새 문의가 필요합니다")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        assertThatThrownBy(() -> generate(input())).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("모델이 과거 안내만 골라도 최신 조건 응답의 실제 안내를 보존한다")
    void 최신_조건에_답한_안내를_누락하지_않는다() {
        var messages = List.of(
                message(1, 1, null, "USER", "QUESTION", "지난달 가입 요금제 변경", null),
                message(2, 2, 1L, "ASSISTANT", "ANSWER", "지난달 가입 조건에서는 변경 가능합니다.", "GROUNDED"),
                message(3, 3, null, "USER", "QUESTION", "이번 달 가입으로 정정합니다.", null),
                message(4, 4, 3L, "ASSISTANT", "ANSWER", "이번 달 가입 조건에서는 변경 불가합니다.", "GROUNDED"));
        var input = new ChatEndSummaryData.Input(1, 4, 10L, messages,
                List.of(new ChatEndSummaryData.Request(1, 1, "요금제 변경", "DONE")), Set.of(),
                List.of(new ChatEndSummaryData.Condition(11, 1, "joined_this_month", "true",
                        "FILLED", "ASKED", 3L, 3)));
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(input);

        assertThat(result.content().guidance()).containsExactly(
                "조건 확인 전 안내: 지난달 가입 조건에서는 변경 가능합니다.",
                "조건 확인 후 안내: 이번 달 가입 조건에서는 변경 불가합니다.");
    }

    static String selection(long question, int... ids) {
        var mapper = new ObjectMapper();
        var result = mapper.createObjectNode();
        result.putObject("topics").put(Long.toString(question), "로밍 신청 방법 문의");
        var selected = result.putArray("selectedSentenceIds");
        for (int id : ids) {
            selected.add(id);
        }
        result.putArray("pendingItems");
        return result.toString();
    }

    private ChatSessionEndSummaryGenerator generator(int budget) {
        var properties = new ChatEndSummaryProperties(true, false, false, budget, 768, 400, 8, 2,
                Duration.ofMinutes(5), Duration.ofMinutes(15));
        return new ChatSessionEndSummaryGenerator(mapper, client, new ChatTokenEstimator(),
                new ChatInputInspector(mapper), new ChatOutputGuard(mapper), properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private ChatEndSummaryData.Result generate(ChatEndSummaryData.Input input) {
        return generator.generate(input, now.plusSeconds(300));
    }

    private LlmRequest request() {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).generate(captor.capture());
        return captor.getValue();
    }

    private ChatEndSummaryData.Input input() {
        return withGuidance("홈페이지를 확인해 주세요.");
    }

    private ChatEndSummaryData.Input withGuidance(String text) {
        return new ChatEndSummaryData.Input(1, 2, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "로밍 신청 방법", null),
                message(2, 2, 1L, "ASSISTANT", "ANSWER", text, "GROUNDED")), List.of(), Set.of());
    }

    private ChatEndSummaryData.Message message(long id, int sequence, Long reply, String role,
            String type, String text, String basis) {
        return new ChatEndSummaryData.Message(id, sequence, reply, role, type, text, basis, "COMPLETED");
    }
}
