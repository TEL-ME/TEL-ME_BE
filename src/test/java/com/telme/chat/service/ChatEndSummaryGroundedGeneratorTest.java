package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatEndSummaryGroundedGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatEndSummaryGroundedGenerator generator = new ChatEndSummaryGroundedGenerator(mapper,
            new ChatInputInspector(mapper), new ChatOutputGuard(mapper));

    @Test
    @DisplayName("가능 여부와 대상별 금액을 재작성 없이 원문 그대로 보존한다")
    void 원문의_가능_여부와_대상별_금액을_보존한다() throws Exception {
        String answer = "유심 재발급은 가능합니다. 아버지는 7700원이고 자녀는 5500원입니다.";
        var input = createInput("본인 유심 재발급 문의", answer, "GROUNDED", Set.of());
        var result = generator.generate(input, createExtractiveResult(answer, "예약 접수되어 처리 대기 중입니다."));

        assertThat(result.summary().content().guidance()).containsExactly(answer);
        assertThat(result.summary().content().inquiries()).hasSize(1);
        assertThat(result.summary().content().inquiries().getFirst().text()).isEqualTo(answer);
        assertThat(result.summary().content().pendingItems()).isEmpty();
        assertThat(result.summary().json()).doesNotContain("온라인", "불가능", "예약 접수");
    }

    @Test
    @DisplayName("전 단계의 허위 접수 문장 대신 저장된 근거 없음 상태로 남은 사항을 조립한다")
    void 허위_접수_대신_미답변을_보존한다() throws Exception {
        var input = createInput("이번 달 요금제를 변경할 수 있나요?", "안내 정보가 없습니다.", "NO_EVIDENCE", Set.of(1L));
        var result = generator.generate(input, createExtractiveResult("", "예약이 접수되어 처리 대기 중입니다."));

        assertThat(result.summary().content().guidance()).isEmpty();
        assertThat(result.summary().content().remaining().getFirst().kind()).isEqualTo("UNANSWERED");
        assertThat(result.pending().getFirst().kind()).isEqualTo(ChatEndSummaryPendingResolver.Kind.UNANSWERED);
        assertThat(result.summary().content().pendingItems().getFirst()).contains("이번 달", "근거 있는 답변");
        assertThat(result.summary().json()).doesNotContain("예약이 접수", "처리 대기");
    }

    @Test
    @DisplayName("남은 사항 본문과 로컬 표시 메타데이터 모두 지원 민감번호를 가린다")
    void 남은_사항과_메타데이터도_마스킹한다() throws Exception {
        var input = createInput("번호 900101-1234567의 요금제를 확인해 주세요.", "안내 정보가 없습니다.",
                "NO_EVIDENCE", Set.of(1L));
        var result = generator.generate(input, createExtractiveResult("", ""));

        assertThat(result.summary().json()).doesNotContain("900101", "1234567");
        assertThat(result.pending().getFirst().text()).contains("***").doesNotContain("900101", "1234567");
    }

    @Test
    @DisplayName("미답변 준비물을 답변 제목에 합치지 않고 고정 확인 요청은 남은 사항으로 분리한다")
    void 미답변과_확인_요청을_답변_영역에서_분리한다() throws Exception {
        String answer = "유심 재발급 비용은 7700원이며 기기 불량 확인 시에만 무료입니다.";
        var input = new ChatEndSummaryData.Input(1, 6, 3L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "유심 재발급 비용과 무료 조건은 무엇인가요?", null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", answer, "GROUNDED", null),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION",
                        "그 유심 재발급 준비물은 무엇인가요?", null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER",
                        "관련 안내 정보가 없습니다.", "NO_EVIDENCE", null),
                new ChatEndSummaryData.Message(5, 5, null, "USER", "QUESTION",
                        "근처 매장을 알려주세요.", null, "COMPLETED"),
                new ChatEndSummaryData.Message(6, 6, 5L, "ASSISTANT", "CLARIFICATION",
                        "상품이나 상담 주제를 조금 더 구체적으로 알려주세요.", null, null)),
                List.of(new ChatEndSummaryData.Request(1, 1, "유심 재발급 비용", "DONE"),
                        new ChatEndSummaryData.Request(3, 1, "그 유심 재발급 준비물", "DONE")),
                Set.of(3L, 5L), List.of(), OutputMaskContext.empty());
        var result = generator.generate(input, createExtractiveResult(answer, ""));

        assertThat(result.summary().content().inquiries().getFirst().title()).doesNotContain("준비물");
        assertThat(result.summary().content().remaining()).hasSize(2);
        assertThat(result.summary().content().remaining().get(1).kind()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(result.summary().content().guidance()).containsExactly(answer);
    }

    private ChatEndSummaryData.Input createInput(String question, String answer, String basis, Set<Long> pending) {
        return new ChatEndSummaryData.Input(1, 2, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", question, null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", answer, basis, "COMPLETED")),
                List.of(new ChatEndSummaryData.Request(1, 1, question, "DONE")), pending, List.of(),
                OutputMaskContext.empty());
    }

    private ChatEndSummaryData.Result createExtractiveResult(String text, String pending) throws Exception {
        var root = mapper.createObjectNode();
        root.putArray("topics");
        var guidance = root.putArray("guidance");
        if (!text.isBlank()) {
            var item = guidance.addObject();
            item.put("text", "안내받은 내용: " + text);
            item.putArray("sourceMessageIds").add(2);
        }
        var remaining = root.putArray("pendingItems");
        if (!pending.isBlank()) {
            remaining.addObject().put("text", pending).putArray("sourceMessageIds").add(1);
        }
        var content = Content.builder()
                .topics(List.of())
                .guidance(List.of())
                .pendingItems(List.of())
                .inquiries(List.of())
                .remaining(List.of())
                .build();
        return new ChatEndSummaryData.Result(root.toString(), content,
                OutputMaskContext.empty());
    }
}
