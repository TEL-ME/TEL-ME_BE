package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatEndSummaryInquiryGrouperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("명시적 가입월 정정은 같은 문의로 묶고 철회된 이전 안내를 다시 제시하지 않는다")
    void 정정_전후를_한_문의로_묶는다() throws Exception {
        var input = createInput("이번 달 가입으로 정정합니다.", "이번 달 요금제 변경 문의");
        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(
                "지난달 가입은 요금제 변경이 가능합니다.",
                "이번 달 가입은 요금제 변경이 불가합니다. 이전 안내는 적용되지 않습니다."));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sourceItems()).isEqualTo(2);
        assertThat(groups.getFirst().sourceText()).doesNotContain("변경이 가능합니다");
        assertThat(groups.getFirst().sourceText()).contains("변경이 불가");
    }

    @Test
    @DisplayName("다른 문의의 안내는 한 문단으로 합치지 않는다")
    void 유심과_로밍은_분리한다() throws Exception {
        var input = createInput("해외 로밍 기간과 금액", "해외 로밍 기간과 금액");
        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(
                "유심 재발급 비용은 7700원입니다.", "해외 로밍은 3일에 9000원입니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().title()).isEqualTo("본인 유심 문의");
        assertThat(groups.getLast().title()).isEqualTo("해외 로밍 기간·요금");
    }

    @Test
    @DisplayName("같은 종류의 문의라도 연결 근거가 없는 별도 질문은 임의로 합치지 않는다")
    void 관계가_불명확한_질문은_분리한다() throws Exception {
        var input = createInput("다른 요금제도 변경할 수 있나요?", "다른 요금제 변경 문의");
        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(
                "기존 요금제 변경은 가능합니다.", "다른 요금제 변경은 불가합니다."));

        assertThat(groups).hasSize(2);
    }

    @Test
    @DisplayName("가족의 정정은 본인 문의에 합치지 않는다")
    void 본인과_가족의_정정을_섞지_않는다() throws Exception {
        var input = createInput("어머니의 가입월을 이번 달로 정정합니다.", "어머니 요금제 변경 문의");
        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(
                "본인 요금제 변경은 가능합니다.", "어머니 요금제 변경은 불가합니다."));

        assertThat(groups).hasSize(2);
    }

    @Test
    @DisplayName("같은 유심 답변의 동일 문장을 제거하되 새 제한 조건은 남긴다")
    void 원문_문장_중복만_제거하고_새_조건을_남긴다() {
        var messages = List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "유심 재발급 문의", null, null),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION",
                        "같은 유심 재발급 절차를 다시 알려주세요.", null, null),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null));
        var input = new ChatEndSummaryData.Input(1, 4, 1L, messages, List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());
        String common = "유심 재발급 비용은 7,700원입니다. 신분증을 가지고 매장을 방문해야 합니다.";

        var groups = ChatEndSummaryInquiryGrouper.group(input,
                createSummary(common, common + " 대리인은 위임장도 필요합니다."));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sourceText()).isEqualTo(common + " 대리인은 위임장도 필요합니다.");
        assertThat(groups.getFirst().sourceMessageIds()).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("동일 문장이어도 서로 다른 앞 조건을 참조하는 안내는 삭제하지 않는다")
    void 앞_조건을_참조하는_문장은_각각_남긴다() {
        String first = "분실 재발급은 유료입니다. 이 경우 신분증이 필요합니다.";
        String second = "불량 확인 시 무료입니다. 이 경우 신분증이 필요합니다.";

        assertThat(ChatEndSummarySourceText.deduplicate(List.of(first, second)))
                .isEqualTo(first + " " + second);
    }

    @Test
    @DisplayName("같은 안내 원문이 합쳐져도 이어 묻기에서 밝힌 가입 기간을 해당 문의에 보존한다")
    void 안내가_합쳐져도_이어_묻기의_가입_기간을_보존한다() {
        var messages = List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "유심 재발급 문의", null, null),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION",
                        "가입 기간은 18개월입니다. 같은 유심 절차를 다시 알려주세요.", null, null),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null));
        var input = new ChatEndSummaryData.Input(1, 4, 1L, messages, List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());
        var root = createSummary("유심 재발급 비용은 7,700원입니다.", "유심 재발급 비용은 7,700원입니다.");
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("guidance")).remove(1);
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("guidance").get(0).path("sourceMessageIds"))
                .add(4);
        var period = ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("topics")).addObject();
        period.put("text", "고객이 언급한 기간(원문): 가입 기간은 18개월입니다.");
        period.putArray("sourceMessageIds").add(3);

        var groups = ChatEndSummaryInquiryGrouper.group(input, root);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().periodContext()).isEqualTo("가입 기간은 18개월입니다.");
        assertThat(groups.getFirst().sourceMessageIds()).containsExactly(2L, 4L, 3L);
    }

    private ChatEndSummaryData.Input createInput(String latestQuestion, String latestQuery) {
        return new ChatEndSummaryData.Input(1, 4, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "본인 요금제 변경 문의", null, null),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", latestQuestion, null, null),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", null)),
                List.of(new ChatEndSummaryData.Request(1, 1, "요금제 변경 문의", "DONE"),
                        new ChatEndSummaryData.Request(3, 1, latestQuery, "DONE")),
                Set.of(), List.of(), OutputMaskContext.empty());
    }

    private com.fasterxml.jackson.databind.JsonNode createSummary(String first, String second) {
        var root = mapper.createObjectNode();
        root.putArray("topics");
        var guidance = root.putArray("guidance");
        var old = guidance.addObject();
        old.put("text", first);
        old.putArray("sourceMessageIds").add(2);
        var latest = guidance.addObject();
        latest.put("text", second);
        latest.putArray("sourceMessageIds").add(4);
        root.putArray("pendingItems");
        return root;
    }
}
