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

class ChatEndSummaryMixedScopeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("서로 다른 사람에게 동일한 안내를 했어도 각 문의와 출처를 보존한다")
    void 동일_안내의_사람별_출처를_분리한다() {
        verifySeparateSources("본인 유심 재발급 비용은요?", "어머니 유심 재발급 비용은요?");
    }

    @Test
    @DisplayName("서로 다른 회선에 동일한 안내를 했어도 각 문의와 출처를 보존한다")
    void 동일_안내의_회선별_출처를_분리한다() {
        verifySeparateSources("첫 번째 회선 유심 재발급 비용은요?", "두 번째 회선 유심 재발급 비용은요?");
    }

    @Test
    @DisplayName("가족의 가입 기간을 고객 본인의 가입 기간으로 바꾸지 않는다")
    void 가족_가입_기간의_주체를_바꾸지_않는다() {
        var input = input("아버지 유심 재발급 문의이고 가입 기간은 24개월입니다.",
                "본인 유심 재발급 비용은요?");
        var root = summary();
        var topic = root.withArray("topics").addObject();
        topic.put("text", "고객이 언급한 기간(원문): 아버지 유심 재발급 문의이고 가입 기간은 24개월입니다.");
        topic.putArray("sourceMessageIds").add(1);
        var generator = new ChatEndSummaryGroundedGenerator(mapper, new ChatInputInspector(mapper),
                new ChatOutputGuard(mapper));

        var result = generator.generate(input, new ChatEndSummaryData.Result(root.toString(),
                new Content(List.of(), List.of(), List.of()), OutputMaskContext.empty()));

        assertThat(result.summary().content().guidance().getFirst())
                .contains("아버지", "24개월")
                .doesNotContain("고객은 가입 기간이 24개월");
    }

    private void verifySeparateSources(String first, String second) {
        var groups = ChatEndSummaryInquiryGrouper.group(input(first, second), summary());

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).contains("7,700원");
        assertThat(groups.getLast().sourceText()).contains("7,700원");
        assertThat(groups.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(groups.getLast().sourceMessageIds()).containsExactly(4L);
    }

    private ChatEndSummaryData.Input input(String first, String second) {
        return new ChatEndSummaryData.Input(1, 4, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", first, null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", second, null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "안내", "GROUNDED", "COMPLETED")),
                List.of(), Set.of(), List.of(), OutputMaskContext.empty());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode summary() {
        var root = mapper.createObjectNode();
        root.putArray("topics");
        root.putArray("pendingItems");
        var quote = root.putArray("guidance").addObject();
        quote.put("text", "유심 재발급 비용은 7,700원입니다.");
        quote.putArray("sourceMessageIds").add(2).add(4);
        return root;
    }
}
