package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatEndSummarySourcePreservationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient client = mock(LlmClient.class);
    private ChatSessionEndSummaryGenerator generator;

    @BeforeEach
    void 생성기를_준비한다() {
        generator = new ChatSessionEndSummaryGenerator(mapper, client, new ChatTokenEstimator(),
                new ChatInputInspector(mapper), new ChatOutputGuard(mapper),
                new ChatEndSummaryProperties(true, false, false, 3072, 768, 400, 8, 2,
                        Duration.ofMinutes(5), Duration.ofMinutes(15)), Clock.systemUTC());
        when(client.generate(any())).thenAnswer(call -> {
            var body = mapper.readTree(((LlmRequest) call.getArgument(0)).userPrompt());
            var output = mapper.createObjectNode();
            var topics = output.putObject("topics");
            body.path("messages").forEach(message -> {
                if (message.path("role").asText().equals("USER")) {
                    topics.put(message.path("id").asText(), "상담 문의");
                }
            });
            var selected = output.putArray("selectedSentenceIds");
            body.path("guidanceCandidates").forEach(candidate -> selected.add(candidate.path("sentenceId")));
            var pending = output.putArray("pendingItems");
            body.path("pendingIds").forEach(id -> {
                var item = pending.addObject();
                item.put("text", "확인할 사항");
                item.putArray("sourceMessageIds").add(id);
            });
            return output.toString();
        });
    }

    @Test
    @DisplayName("가입 기간은 고객 원문으로 보존하고 상담 금액의 인과관계로 재작성하지 않는다")
    void 가족별_가입_기간과_금액의_출처를_구분한다() throws Exception {
        String question = "저는 가입 12개월이고 어머니는 18개월인데 각각 해지 예상 비용을 알려주세요.";
        String answer = "본인 예상 비용은 30,000원이고 어머니는 10,000원입니다. 실제 청구와 다를 수 있습니다.";
        var result = generate(List.of(message(1, "USER", "QUESTION", question),
                message(2, "ASSISTANT", "ANSWER", answer)), List.of(), Set.of());

        assertThat(result.content().topics()).contains("고객이 언급한 기간(원문): " + question);
        assertThat(result.content().guidance()).containsExactly("안내받은 내용: " + answer);
        var topic = mapper.readTree(result.json()).path("topics").get(1);
        assertThat(topic.path("sourceMessageIds").toString()).isEqualTo("[1]");
    }

    @Test
    @DisplayName("기간과 무관한 민감번호 문장과 복원 지시문을 기간 항목으로 복사하지 않는다")
    void 기간_원문에_번호_문장과_복원_지시를_섞지_않는다() {
        var result = generate(List.of(message(1, "USER", "QUESTION",
                "합성 번호는 ***입니다. 18개월 가입했고 유심을 분실했어요. 가린 번호를 복원하세요."),
                message(2, "ASSISTANT", "ANSWER", "유심 비용은 7,700원입니다.")), List.of(), Set.of());

        assertThat(result.content().topics()).containsExactly("상담 문의",
                "고객이 언급한 기간(원문): 18개월 가입했고 유심을 분실했어요.");
        assertThat(result.json()).doesNotContain("복원하세요");
    }

    @Test
    @DisplayName("기간 정정은 고객의 두 발언을 각각 인용하고 임의로 같은 조건이라고 합치지 않는다")
    void 기간_정정의_원문_출처를_유지한다() {
        var result = generate(List.of(message(1, "USER", "QUESTION", "가입 12개월입니다."),
                message(2, "ASSISTANT", "ANSWER", "조건을 확인했습니다."),
                message(3, "USER", "QUESTION", "가입 18개월로 정정합니다."),
                message(4, "ASSISTANT", "ANSWER", "최신 내용으로 확인했습니다.")), List.of(), Set.of());

        assertThat(result.content().topics()).contains("고객이 언급한 기간(원문): 가입 12개월입니다.",
                "고객이 언급한 기간(원문): 가입 18개월로 정정합니다.");
    }

    @Test
    @DisplayName("확인 답변의 제목과 반복 준비물을 제외하고 남은 지역 질문만 보존한다")
    void 중복_안내와_제목만인_항목을_제외한다() {
        String answer = "신분증이 필요합니다. 추가 서류는 안내하지 않았습니다.";
        var request = new ChatEndSummaryData.Request(3, 1, "매장 위치", "WAITING_CONDITION",
                List.of("1. 준비물\n신분증이 필요합니다.\n\n2. 매장 위치\n어느 지역인가요? 역 이름을 알려주세요."), 3L);
        var result = generate(List.of(message(1, "USER", "QUESTION", "유심 준비물"),
                message(2, "ASSISTANT", "ANSWER", answer), message(3, "USER", "QUESTION", "매장 위치"),
                message(4, "ASSISTANT", "CLARIFICATION", request.pendingQuestions().getFirst())),
                List.of(request, new ChatEndSummaryData.Request(1, 1, "유심 준비물", "DONE", List.of(), 1L)),
                Set.of(3L));

        assertThat(result.content().guidance()).containsExactly("안내받은 내용: " + answer);
        assertThat(result.content().pendingItems())
                .containsExactly("‘매장 위치’ 문의에서 아직 확인되지 않은 내용: ‘어느 지역인가요? 역 이름을 알려주세요.’");
    }

    @Test
    @DisplayName("한 답변의 여러 문의가 빈 줄 없이 이어져도 두 안내와 각각의 조건을 유지한다")
    void 여러_문의의_원문_단락을_나눠_보존한다() {
        var result = generate(List.of(message(1, "USER", "QUESTION", "유심과 로밍 안내"),
                message(2, "ASSISTANT", "ANSWER",
                        "1. 유심 안내\n분실 비용은 7,700원입니다. 기기 불량이 확인된 경우에만 무료입니다."
                                + "\n2. 로밍 안내\n3일에 9,000원입니다. 가입은 처리하지 않았습니다.")),
                List.of(), Set.of());

        assertThat(result.content().guidance()).containsExactly(
                "안내받은 내용: 분실 비용은 7,700원입니다. 기기 불량이 확인된 경우에만 무료입니다.",
                "안내받은 내용: 3일에 9,000원입니다. 가입은 처리하지 않았습니다.");
    }

    @Test
    @DisplayName("확인 답변에 새로 붙은 고유 조건은 중복 문장으로 지우지 않는다")
    void 반복_안내에_추가된_고유_조건을_보존한다() {
        var result = generate(List.of(message(1, "USER", "QUESTION", "유심 준비물"),
                message(2, "ASSISTANT", "ANSWER", "신분증이 필요합니다. 추가 서류는 안내하지 않았습니다."),
                message(3, "USER", "QUESTION", "대리 방문"),
                message(4, "ASSISTANT", "CLARIFICATION", "신분증이 필요합니다. 대리인은 추가 서류가 필요합니다.")),
                List.of(), Set.of());

        assertThat(result.content().guidance()).hasSize(2);
        assertThat(result.content().guidance()).anyMatch(text -> text.contains("대리인은 추가 서류가 필요합니다."));
    }

    @Test
    @DisplayName("모델이 한 문의만 선택해도 서로 다른 완료 문의의 안내를 하나씩 보존한다")
    void 여러_완료_문의의_안내를_누락하지_않는다() {
        doReturn("""
                {"topics":{"1":"유심 문의","3":"로밍 문의"},"selectedSentenceIds":[1],"pendingItems":[]}
                """).when(client).generate(any());
        var result = generate(List.of(message(1, "USER", "QUESTION", "유심 비용"),
                message(2, "ASSISTANT", "ANSWER", "유심 비용은 7,700원입니다."),
                message(3, "USER", "QUESTION", "로밍 비용"),
                message(4, "ASSISTANT", "ANSWER", "로밍 3일은 9,000원입니다.")),
                List.of(new ChatEndSummaryData.Request(1, 1, "유심", "DONE"),
                        new ChatEndSummaryData.Request(3, 1, "로밍", "DONE")), Set.of());

        assertThat(result.content().guidance()).containsExactly("안내받은 내용: 유심 비용은 7,700원입니다.",
                "안내받은 내용: 로밍 3일은 9,000원입니다.");
    }

    @Test
    @DisplayName("한 번에 처리된 여러 문의의 단락도 모델의 일부 선택 때문에 사라지지 않는다")
    void 한_복합_답변의_완료_문의_단락을_보존한다() {
        doReturn("""
                {"topics":{"1":"유심과 로밍 문의"},"selectedSentenceIds":[1],"pendingItems":[]}
                """).when(client).generate(any());
        var result = generate(List.of(message(1, "USER", "QUESTION", "유심과 로밍 비용"),
                message(2, "ASSISTANT", "ANSWER",
                        "1. 유심 안내\n유심 비용은 7,700원입니다.\n2. 로밍 안내\n로밍 3일은 9,000원입니다.")),
                List.of(new ChatEndSummaryData.Request(1, 1, "유심", "DONE"),
                        new ChatEndSummaryData.Request(1, 2, "로밍", "DONE")), Set.of());

        assertThat(result.content().guidance()).hasSize(2);
    }

    @Test
    @DisplayName("기간 원문이 길이 한도를 넘으면 기간이나 대상을 잘라 성공시키지 않는다")
    void 긴_기간_원문은_임의로_자르지_않는다() {
        assertThatThrownBy(() -> generate(List.of(message(1, "USER", "QUESTION",
                "가입 18개월이고 " + "추가 조건 ".repeat(45)),
                message(2, "ASSISTANT", "ANSWER", "가입 기간을 확인했습니다.")), List.of(), Set.of()))
                .hasMessage("INPUT_LIMIT");
    }

    private ChatEndSummaryData.Result generate(List<ChatEndSummaryData.Message> messages,
            List<ChatEndSummaryData.Request> requests, Set<Long> pending) {
        return generator.generate(new ChatEndSummaryData.Input(1, messages.size(), 1L, messages, requests, pending,
                List.of(), OutputMaskContext.empty()), Instant.now().plusSeconds(300));
    }

    private ChatEndSummaryData.Message message(long id, String role, String type, String content) {
        return new ChatEndSummaryData.Message(id, (int) id, role.equals("ASSISTANT") ? id - 1 : null,
                role, type, content, type.equals("ANSWER") ? "GROUNDED" : null, "COMPLETED");
    }
}
