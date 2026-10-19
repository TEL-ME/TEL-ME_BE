package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import com.telme.chat.safety.OutputMaskContext;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("고정 확인 요청의 설명 문장도 안내 후보에 포함하지 않는다")
    void 확인_요청의_설명도_안내_후보에서_제외한다() {
        when(client.generate(any())).thenReturn(selection(1, 1));
        var input = new ChatEndSummaryData.Input(1, 4, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "유심 재발급 비용", null),
                message(2, 2, 1L, "ASSISTANT", "ANSWER", "유심 재발급 비용은 7700원입니다.", "GROUNDED"),
                message(3, 3, null, "USER", "QUESTION", "근처 매장을 알려주세요.", null),
                message(4, 4, 3L, "ASSISTANT", "CLARIFICATION",
                        "어떤 내용에 대한 질문인지 확인이 필요합니다. "
                                + "상품이나 상담 주제를 조금 더 구체적으로 알려주세요.", null)),
                List.of(new ChatEndSummaryData.Request(1, 1, "유심 재발급 비용", "DONE")),
                Set.of(3L), List.of(), OutputMaskContext.empty());
        var result = generate(input);

        assertThat(result.content().guidance()).hasSize(1);
        assertThat(result.content().guidance().getFirst()).doesNotContain("확인", "구체적");
        assertThat(result.content().pendingItems().getFirst()).contains("확인 요청");
    }

    @Test
    @DisplayName("저장된 문의 제목과 남은 사유를 재조회 응답에도 보존한다")
    void 문의_구조를_재조회에도_보존한다() {
        String stored = """
                {"topics":[],"guidance":[{"title":"유심 재발급","text":"재발급 비용은 7700원입니다."}],
                 "pendingItems":[{"title":"방문자 구분","text":"방문자가 미확인입니다.",
                                  "kind":"NEEDS_CONFIRMATION"}]}
                """;
        var content = generator.readContent(stored);

        assertThat(content.inquiries().getFirst().title()).isEqualTo("유심 재발급");
        assertThat(content.remaining().getFirst().kind()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(content.pendingItems()).containsExactly("방문자가 미확인입니다.");
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
        assertThat(request.promptVersion()).isEqualTo("consultation-end-summary-v14");
        var schema = mapper.valueToTree(request.responseSchema()).path("properties");
        assertThat(schema.has("guidance")).isFalse();
        assertThat(schema.path("selectedSentenceIds").path("items").path("enum").toString()).isEqualTo("[1]");
        assertThat(mapper.readTree(request.userPrompt()).path("guidanceCandidates").get(0).path("text").asText())
                .isEqualTo("홈페이지를 확인해 주세요.");
    }

    @Test
    @DisplayName("존재하는 안내 번호가 반복돼도 원문과 출처는 한 번만 조립한다")
    void 반복된_유효_번호를_중복_조립하지_않는다() throws Exception {
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.set("selectedSentenceIds", mapper.readTree("[1,1]"));
        when(client.generate(any())).thenReturn(response.toString());

        assertThat(generate(input()).content().guidance()).hasSize(1);
        verify(client, times(1)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[99]", "[0]", "[-1]", "[1.5]", "[\"1\"]", "null"})
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

        var result = generate(new ChatEndSummaryData.Input(1, 3, 10L, messages, List.of(), Set.of(), List.of(),
                OutputMaskContext.empty()));

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
                List.of(), Set.of(1L), List.of(), OutputMaskContext.empty());

        var result = generate(input);

        assertThat(result.content().guidance()).isEmpty();
        assertThat(result.content().pendingItems()).hasSize(1);
        assertThat(result.content().pendingItems().getFirst()).contains("로밍 신청 방법");
        assertThat(result.content().pendingItems().getFirst()).doesNotContain("답변하지 못한 로밍 문의");
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
                List.of(), Set.of(), List.of(), OutputMaskContext.empty());
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
                        "로밍은 홈페이지에서 신청합니다. 방문할 지역을 알려주세요.", null)), List.of(), Set.of(), List.of(),
                        OutputMaskContext.empty());
        when(client.generate(any())).thenReturn(selection(1, 1));

        assertThat(generate(input).content().guidance())
                .containsExactly("안내받은 내용: 로밍은 홈페이지에서 신청합니다.");
    }

    @Test
    @DisplayName("지원 개인정보는 후보와 최종 결과에서 마스킹한다")
    void 후보와_결과에_민감번호를_남기지_않는다() {
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(withGuidance("번호 900101-1000000을 전달했습니다."));

        assertThat(result.json()).contains("***").doesNotContain("900101-1000000");
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
                message(1, 1, null, "USER", "QUESTION", "문의".repeat(4000), null)), List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());

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
                List.of(new ChatEndSummaryData.Request(1, 1, "로밍", "DONE")), Set.of(), List.of(),
                OutputMaskContext.empty());
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
                new ChatEndSummaryData.Request(3, 1, "유심 서류", "DONE")), Set.of(), List.of(), OutputMaskContext.empty());
        when(client.generate(any())).thenReturn(selection(3, 1));

        assertThatThrownBy(() -> generate(input)).hasMessage("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("미확인 조건은 모델이 일부만 적어도 저장 상태에서 모두 보존한다")
    void 미확인_조건을_모두_남긴다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 2, 10L, input().messages(), List.of(
                new ChatEndSummaryData.Request(1, 1, "로밍", "WAITING_CONDITION",
                        List.of("방문할 지역을 알려주세요.", "대리인이 방문하시는 건가요?"))), Set.of(1L), List.of(),
                        OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.withArray("pendingItems").addObject().put("text", "지역 확인 필요")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        assertThat(generate(input).content().pendingItems()).containsExactly(
                "‘로밍’ 문의에서 아직 확인되지 않은 내용: ‘방문할 지역을 알려주세요.’",
                "‘로밍’ 문의에서 아직 확인되지 않은 내용: ‘대리인이 방문하시는 건가요?’");
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
        var input = new ChatEndSummaryData.Input(1, 7, 10L, messages, requests, Set.of(), conditions,
                OutputMaskContext.empty());
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
                        "유심 재발급 비용이 얼마예요?", null, status)), List.of(), Set.of(1L), List.of(),
                        OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1));
        response.withObject("topics").put("1", "유심 재발급 비용");
        response.withArray("pendingItems").addObject().put("text", "유심 비용을 알려주세요.")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        var result = generate(input);

        assertThat(result.content().guidance()).isEmpty();
        assertThat(result.content().pendingItems()).containsExactly("답변을 마치지 못한 문의: ‘유심 재발급 비용이 얼마예요?’");
        assertThat(mapper.readTree(result.json()).path("pendingItems").get(0)
                .path("sourceMessageIds").toString()).isEqualTo("[1]");
    }

    @Test
    @DisplayName("모델이 누락해도 실행 실패로 남은 문의를 보존한다")
    void 모델이_누락한_실패_문의도_보존한다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 1, 10L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "유심 재발급 비용이 얼마예요?", null, "FAILED")), List.of(), Set.of(1L), List.of(),
                        OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1));
        response.withObject("topics").put("1", "유심 재발급 비용");
        when(client.generate(any())).thenReturn(response.toString());

        assertThat(generate(input).content().pendingItems())
                .containsExactly("답변을 마치지 못한 문의: ‘유심 재발급 비용이 얼마예요?’");
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
                        "FILLED", "ASKED", 3L, 3)), OutputMaskContext.empty());
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(input);

        assertThat(result.content().guidance()).containsExactly(
                "조건 확인 전 안내: 지난달 가입 조건에서는 변경 가능합니다.",
                "조건 확인 후 안내: 이번 달 가입 조건에서는 변경 불가합니다.");
    }

    @Test
    @DisplayName("모델이 8개를 골라도 최신 안내의 자리를 먼저 확보해 정상 요약한다")
    void 여덟_선택과_최신_안내_보정이_충돌하지_않는다() {
        selectOfferedGuidance();

        var result = generate(latestInput(1));

        assertThat(result.content().guidance()).hasSize(9);
        assertThat(result.content().guidance()).contains("조건 확인 후 안내: 최신 조건 안내 1입니다.");
        for (int index = 1; index <= 8; index++) {
            assertThat(result.content().guidance()).contains("조건 확인 전 안내: 이전 조건 안내 " + index + "입니다.");
        }
        verify(client, times(2)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    @DisplayName("최신 필수 안내가 8개와 9개여도 호출을 나눠 모두 보존한다")
    void 최신_필수_안내_자체의_한도를_지킨다(int count) {
        selectOfferedGuidance();
        var target = generator(20000);
        var guidance = target.generate(latestInput(count), now.plusSeconds(300)).content().guidance();

        assertThat(guidance).hasSize(8 + count);
        for (int index = 1; index <= count; index++) {
            assertThat(guidance).contains("조건 확인 후 안내: 최신 조건 안내 " + index + "입니다.");
        }
        for (int index = 1; index <= 8; index++) {
            assertThat(guidance).contains("조건 확인 전 안내: 이전 조건 안내 " + index + "입니다.");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 12})
    @DisplayName("저장된 필수 미확인 조건 수에 맞춰 스키마와 보정 결과의 한도를 함께 늘린다")
    void 여덟_개를_넘는_필수_미확인_조건도_보존한다(int count) throws Exception {
        var questions = IntStream.rangeClosed(1, count).mapToObj(i -> "확인 항목 " + i + " 알려주세요.").toList();
        var input = new ChatEndSummaryData.Input(1, 2, 10L, input().messages(), List.of(
                new ChatEndSummaryData.Request(1, 1, "로밍", "WAITING_CONDITION", questions, 11L)), Set.of(1L), List.of(),
                OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.withArray("pendingItems").addObject().put("text", "지역 확인 필요")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        var result = generate(input);

        assertThat(result.content().pendingItems()).containsExactlyElementsOf(
                questions.stream().map(question -> "‘로밍’ 문의에서 아직 확인되지 않은 내용: ‘" + question + "’").toList());
        assertThat(mapper.valueToTree(request().responseSchema()).path("properties").path("pendingItems")
                .path("maxItems").asInt()).isZero();
    }

    @Test
    @DisplayName("저장된 근거 없이 모델이 남은 항목 수를 임의로 늘리지 못한다")
    void 모델의_임의_남은_항목_확장을_거부한다() throws Exception {
        var input = new ChatEndSummaryData.Input(1, 2, 10L, input().messages(), List.of(), Set.of(1L), List.of(),
                OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        for (int index = 0; index < 9; index++) {
            response.withArray("pendingItems").addObject().put("text", "남은 항목 " + index)
                    .putArray("sourceMessageIds").add(1);
        }
        when(client.generate(any())).thenReturn(response.toString());

        assertThatThrownBy(() -> generate(input)).hasMessage("INVALID_OUTPUT");
        assertThat(mapper.valueToTree(request().responseSchema()).path("properties").path("pendingItems")
                .path("maxItems").asInt()).isZero();
    }

    @Test
    @DisplayName("필수 확인 항목 9개와 답변을 마치지 못한 다른 문의를 함께 보존한다")
    void 필수_조건과_실패_문의의_한도를_함께_계산한다() throws Exception {
        var questions = IntStream.rangeClosed(1, 9).mapToObj(i -> "확인 항목 " + i + " 알려주세요.").toList();
        var messages = new ArrayList<>(input().messages());
        messages.add(new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", "유심 비용 문의",
                null, "FAILED"));
        var input = new ChatEndSummaryData.Input(1, 3, 10L, messages, List.of(
                new ChatEndSummaryData.Request(1, 1, "로밍", "WAITING_CONDITION", questions, 11L),
                new ChatEndSummaryData.Request(3, 1, "유심 비용", "PENDING")), Set.of(1L, 3L), List.of(),
                OutputMaskContext.empty());
        var response = (ObjectNode) mapper.readTree(selection(1, 1));
        response.withObject("topics").put("3", "유심 비용 문의");
        response.withArray("pendingItems").addObject().put("text", "지역 확인 필요")
                .putArray("sourceMessageIds").add(1);
        when(client.generate(any())).thenReturn(response.toString());

        var result = generate(input);

        assertThat(result.content().pendingItems()).hasSize(10)
                .contains("답변을 마치지 못한 문의: ‘유심 비용 문의’");
        assertThat(mapper.valueToTree(request().responseSchema()).path("properties").path("pendingItems")
                .path("maxItems").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 16})
    @DisplayName("분할·모델 호출·최종 검증에서 전체 안내 원문의 후보 검사를 작업당 한 번만 수행한다")
    void 전체_안내_원문은_후보_생성에서_한_번만_검사한다(int exchanges) throws Exception {
        var inspector = spy(new ChatInputInspector(mapper));
        var target = new ChatSessionEndSummaryGenerator(mapper,
                (modelRequest, modelDeadline) -> client.generate(modelRequest), new ChatTokenEstimator(), inspector,
                new ChatOutputGuard(mapper), new ChatEndSummaryProperties(true, false, false, 20000, 768,
                        400, 8, 2, Duration.ofMinutes(5), Duration.ofMinutes(15)), Clock.fixed(now, ZoneOffset.UTC));
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        Set<String> originals = new java.util.HashSet<>();
        for (int index = 0; index < exchanges; index++) {
            long q = index * 2L + 1;
            String text = "안내 " + index + "의 조건입니다.\n\n안내 " + index + "의 예외입니다.";
            originals.add(text);
            messages.add(message(q, index * 2 + 1, null, "USER", "QUESTION", "서비스 문의 " + index, null));
            messages.add(message(q + 1, index * 2 + 2, q, "ASSISTANT", "ANSWER", text, "GROUNDED"));
            requests.add(new ChatEndSummaryData.Request(q, 1, "서비스 문의 " + index, "DONE"));
        }
        when(client.generate(any())).thenAnswer(invocation -> {
            var prompt = mapper.readTree(((LlmRequest) invocation.getArgument(0)).userPrompt());
            var output = mapper.createObjectNode();
            var topics = output.putObject("topics");
            for (var request : prompt.path("requests")) {
                topics.put(request.path("originMessageId").asText(), request.path("query").asText());
            }
            output.putArray("selectedSentenceIds").add(prompt.path("guidanceCandidates").get(0)
                    .path("sentenceId").asInt());
            output.putArray("pendingItems");
            return output.toString();
        });

        var input = new ChatEndSummaryData.Input(1, exchanges * 2, 10L,
                messages, requests, Set.of(), List.of(), OutputMaskContext.empty());
        var result = target.generate(input, now.plusSeconds(300));
        assertThat(result.content().topics()).hasSize(exchanges);
        assertThat(result.content().guidance()).hasSize(exchanges * 2);
        long inspections = mockingDetails(inspector).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("inspect"))
                .filter(call -> originals.contains((String) call.getArgument(0))).count();
        assertThat(inspections).isEqualTo(exchanges);
    }

    @Test
    @DisplayName("같은 생성기에서도 다음 상담의 후보에 이전 상담이 섞이지 않는다")
    void 같은_생성기의_상담별_후보를_분리한다() {
        when(client.generate(any())).thenReturn(selection(1, 1), selection(11, 1));
        var first = generate(withGuidance("첫 번째 상담 안내입니다."));
        var second = generate(new ChatEndSummaryData.Input(2, 2, 20L, List.of(
                message(11, 1, null, "USER", "QUESTION", "두 번째 문의", null),
                message(12, 2, 11L, "ASSISTANT", "ANSWER", "두 번째 상담 안내입니다.", "GROUNDED")),
                List.of(), Set.of(), List.of(), OutputMaskContext.empty()));

        assertThat(first.content().guidance()).containsExactly("안내받은 내용: 첫 번째 상담 안내입니다.");
        assertThat(second.content().guidance()).containsExactly("안내받은 내용: 두 번째 상담 안내입니다.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("모델 인용과 서버가 조립한 미확인 조건 모두 실제 마스크 경계로 검사한다")
    void 마스크_인용과_서버_남은_사항을_차단하지_않는다(String original, String visible) throws Exception {
        var inspected = new ChatInputInspector(mapper).inspect(original);
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspected)));
        var messages = List.of(message(1, 1, null, "USER", "QUESTION", visible, null));
        var requests = List.of(new ChatEndSummaryData.Request(1, 1, visible, "WAITING_CONDITION",
                List.of("가입 시점 확인 필요"), 11L));
        var input = new ChatEndSummaryData.Input(1, 1, 10L, messages, requests, Set.of(1L), List.of(), masks);
        var response = mapper.createObjectNode();
        response.putObject("topics").put("1", visible);
        response.putArray("selectedSentenceIds");
        response.putArray("pendingItems");
        when(client.generate(any())).thenReturn(response.toString());

        var result = generate(input);

        assertThat(result.content().topics()).containsExactly(visible);
        assertThat(result.content().pendingItems()).anyMatch(item -> item.contains(visible));
        assertThat(result.json()).doesNotContain("4111", "990101", "1234567", "\uFFFC");
        assertThat(request().userPrompt()).doesNotContain("4111", "990101", "1234567", "maskSpans");
    }

    @Test
    @DisplayName("출처 없는 같은 별표 요약과 새 욕설은 기존 출력 정책으로 차단한다")
    void 마스크_출처가_없는_별표_요약을_허용하지_않는다() {
        var input = new ChatEndSummaryData.Input(1, 1, 10L,
                List.of(message(1, 1, null, "USER", "QUESTION", "요금 문의", null)), List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());
        when(client.generate(any())).thenReturn(
                "{\"topics\":{\"1\":\"씨 *** 발 요금 문의\"},\"selectedSentenceIds\":[],\"pendingItems\":[]}");
        assertThatThrownBy(() -> generate(input)).isInstanceOf(ChatOutputBlockedException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "씨 990101-1234567 발 요금 안내입니다. | 씨 *** 발 요금 안내입니다.",
        "개 4111111111111111 새끼 유심 안내입니다. | 개 *** 새끼 유심 안내입니다."
    })
    @DisplayName("안내 원문을 처음 가린 위치를 최종 요약 검사까지 보존한다")
    void 안내_후보의_첫_검사_마스크_위치를_유지한다(String original, String expected) {
        when(client.generate(any())).thenReturn(selection(1, 1));

        var result = generate(withGuidance(original));

        assertThat(result.content().guidance()).containsExactly("안내받은 내용: " + expected);
        assertThat(result.maskContext().sources()).anySatisfy(source -> {
            assertThat(source.content()).isEqualTo(expected);
            assertThat(source.spans()).hasSize(1);
        });
        assertThat(request().userPrompt()).doesNotContain("990101", "1234567", "4111");
    }

    private void selectOfferedGuidance() {
        when(client.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            var body = mapper.readTree(request.userPrompt());
            var output = mapper.createObjectNode();
            var topics = output.putObject("topics");
            mapper.valueToTree(request.responseSchema()).path("properties").path("topics").path("required")
                    .forEach(id -> topics.put(id.asText(), "요금제 문의"));
            var ids = output.putArray("selectedSentenceIds");
            body.path("guidanceCandidates").forEach(candidate -> ids.add(candidate.path("sentenceId").asInt()));
            output.putArray("pendingItems");
            return output.toString();
        });
    }

    private ChatEndSummaryData.Input latestInput(int count) {
        String earlier = IntStream.rangeClosed(1, 8).mapToObj(i -> "이전 조건 안내 " + i + "입니다.")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        String current = IntStream.rangeClosed(1, count).mapToObj(i -> "최신 조건 안내 " + i + "입니다.")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new ChatEndSummaryData.Input(1, 4, 10L, List.of(
                message(1, 1, null, "USER", "QUESTION", "지난달 가입 요금제 변경", null),
                message(2, 2, 1L, "ASSISTANT", "ANSWER", earlier, "GROUNDED"),
                message(3, 3, null, "USER", "QUESTION", "이번 달 가입으로 정정합니다.", null),
                message(4, 4, 3L, "ASSISTANT", "ANSWER", current, "GROUNDED")),
                List.of(new ChatEndSummaryData.Request(1, 1, "요금제 변경", "DONE")), Set.of(),
                List.of(new ChatEndSummaryData.Condition(11, 1, "joined_this_month", "true",
                        "FILLED", "ASKED", 3L, 3)), OutputMaskContext.empty());
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
        return new ChatSessionEndSummaryGenerator(mapper,
                (modelRequest, modelDeadline) -> client.generate(modelRequest), new ChatTokenEstimator(),
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
                message(2, 2, 1L, "ASSISTANT", "ANSWER", text, "GROUNDED")), List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());
    }

    private ChatEndSummaryData.Message message(long id, int sequence, Long reply, String role,
            String type, String text, String basis) {
        return new ChatEndSummaryData.Message(id, sequence, reply, role, type, text, basis, "COMPLETED");
    }
}
