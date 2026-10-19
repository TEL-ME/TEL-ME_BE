package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatEndSummaryConditionScopeTest {
    private static final String SELF_QUESTION = "저는 가입한 지 12개월 됐고 유심을 분실했어요. "
            + "제가 직접 매장에 갈 때 재발급 비용과 준비물을 알려주세요.";
    private static final String MOTHER_QUESTION = "어머니는 가입한 지 18개월 됐고 유심을 분실했어요. "
            + "어머니가 직접 매장에 갈 때 재발급 비용과 준비물도 알려주세요.";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "제 유심 재발급 준비물은요? | 어머니 유심 재발급 준비물도 알려주세요.",
        "어머니 유심 재발급 준비물은요? | 제 유심 재발급 준비물도 알려주세요.",
        "제 A회선 유심 재발급 준비물은요? | 제 B회선 유심 재발급 준비물도 알려주세요.",
        "어머니와 아버지 유심 준비물은요? | 어머니 유심 준비물도 알려주세요."
    })
    @DisplayName("저장된 조건 응답이 다른 대상의 문의여도 묶음과 해결 여부를 덮어쓰지 않는다")
    void 다른_대상의_조건_연결을_신뢰하지_않는다(String original, String followup) {
        var input = input(original, followup);

        assertThat(ChatEndSummaryInquiryLinker.resolveRoots(input)).containsEntry(1L, 1L).containsEntry(3L, 3L);
        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds()))
                .singleElement().satisfies(pending -> assertThat(pending.originMessageId()).isEqualTo(1));
    }

    @Test
    @DisplayName("실제 실패 기록처럼 본인 조건에 어머니 발언이 저장돼도 기간과 안내를 어머니 항목에 섞지 않는다")
    void 본인과_어머니의_기간과_답변_출처를_분리한다() {
        var input = input(SELF_QUESTION, MOTHER_QUESTION);
        var summary = new ObjectMapper().createObjectNode();
        var topics = summary.putArray("topics");
        topics.addObject().put("text", "고객이 언급한 기간(원문): " + SELF_QUESTION)
                .putArray("sourceMessageIds").add(1);
        topics.addObject().put("text", "고객이 언급한 기간(원문): " + MOTHER_QUESTION)
                .putArray("sourceMessageIds").add(3);
        summary.putArray("guidance").addObject().put("text", input.messages().getLast().content())
                .putArray("sourceMessageIds").add(4);

        assertThat(ChatEndSummaryInquiryGrouper.group(input, summary)).singleElement().satisfies(group -> {
            assertThat(group.title()).startsWith("어머니");
            assertThat(group.questionMessageIds()).containsExactly(3L);
            assertThat(group.periodContext()).contains("18개월").doesNotContain("12개월");
        });
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).singleElement()
                .satisfies(pending -> {
                    assertThat(pending.title()).startsWith("본인");
                    assertThat(pending.text()).contains("12개월").doesNotContain("18개월");
                });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "제 유심 재발급 준비물은요? | 제가 직접 방문할게요.",
        "제 유심 재발급 준비물은요? | 본인 신분증을 지참합니다.",
        "제 유심 재발급 준비물은요? | 대리인이 직접 방문할게요.",
        "제 유심 재발급 준비물은요? | 어머니가 대신 매장에 방문합니다.",
        "어머니 유심 재발급 준비물은요? | 어머니가 직접 방문할게요.",
        "어머니 유심 재발급 준비물은요? | 본인 신분증을 지참합니다.",
        "어머니 유심 재발급 준비물은요? | 제가 대신 방문합니다."
    })
    @DisplayName("같은 대상의 정상 응답과 방문자·준비물 표현은 기존 문의를 해결한다")
    void 같은_대상의_정상_조건_응답은_유지한다(String original, String followup) {
        var input = input(original, followup);

        assertThat(ChatEndSummaryInquiryLinker.resolveRoots(input)).containsEntry(3L, 1L);
        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isTrue();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).isEmpty();
    }

    private ChatEndSummaryData.Input input(String original, String followup) {
        return new ChatEndSummaryData.Input(1, 4, 1L, List.of(
                message(1, null, "USER", "QUESTION", original, null),
                message(2, 1L, "ASSISTANT", "CLARIFICATION", "방문 시 필요한 준비물은 무엇인가요?", null),
                message(3, null, "USER", "QUESTION", followup, null),
                message(4, 3L, "ASSISTANT", "ANSWER",
                        "유심 재발급 비용은 7,700원입니다. 매장 방문 시 신분증을 지참해 주세요.", "GROUNDED")),
                List.of(new ChatEndSummaryData.Request(1, 1, original, "DONE", List.of(), 101L)), Set.of(1L),
                List.of(new ChatEndSummaryData.Condition(101, 1, "prepared_items", "신분증", "FILLED", "ASKED",
                        3L, 3, 2L)), OutputMaskContext.empty());
    }

    private ChatEndSummaryData.Message message(long id, Long reply, String role, String type, String text,
            String basis) {
        return new ChatEndSummaryData.Message(id, (int) id, reply, role, type, text, basis, "COMPLETED");
    }
}
