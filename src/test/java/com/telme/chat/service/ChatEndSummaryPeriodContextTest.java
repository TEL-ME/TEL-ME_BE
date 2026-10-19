package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryPeriodContextTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatInputInspector inspector = new ChatInputInspector(mapper);

    @ParameterizedTest
    @ValueSource(strings = {
            "같은 문의의 가입 기간을 정정할게요. 18개월이 아니라 24개월입니다.",
            "같은 문의의 가입 기간을 정정할게요.\n18개월이 아니라 24개월입니다.",
            "가입 18개월이라고 잘못 말했어요. 24개월입니다.",
            "가입 기간은 18개월이 아니라 24개월입니다."
    })
    @DisplayName("가입 기간 정정은 같은 문의에 최신 원문만 남기고 금액의 원인으로 만들지 않는다")
    void 최신_기간_정정과_안내를_별도로_보존한다(String correction) throws Exception {
        var input = input(correction);
        var result = generate(input);

        assertThat(result.inquiries()).hasSize(1);
        assertThat(result.inquiries().getFirst().periodContext()).contains("24개월")
                .doesNotContain("제 회선 가입 기간은 18개월입니다.");
        assertThat(result.summary().content().guidance().getFirst()).contains("24개월", "7,700원", "미접수")
                .doesNotContain("24개월이므로", "18개월이므로");
        assertThat(result.summary().content().topics()).noneMatch(text -> text.contains("18개월입니다"));
    }

    @Test
    @DisplayName("기간 질문이나 다른 사람·다른 종류의 기간은 이전 사실을 대체하지 않는다")
    void 미확정_정정과_다른_대상의_기간을_보존한다() {
        String original = "제 가입 기간은 18개월입니다.";
        assertThat(ChatEndSummaryPeriodContext.latest(List.of(original, "가입 기간은 24개월로 정정하는 게 맞나요?")))
                .containsExactly(original, "가입 기간은 24개월로 정정하는 게 맞나요?");
        assertThat(ChatEndSummaryPeriodContext.latest(List.of(original, "어머니 가입 기간은 24개월로 정정합니다.")))
                .containsExactly(original, "어머니 가입 기간은 24개월로 정정합니다.");
        assertThat(ChatEndSummaryPeriodContext.latest(List.of(original, "제 약정 기간은 24개월로 정정합니다.")))
                .containsExactly(original, "제 약정 기간은 24개월로 정정합니다.");
    }

    @Test
    @DisplayName("연속 정정은 마지막 원문을 유지하고 다른 회선은 대체하지 않는다")
    void 연속_정정과_회선_범위를_구분한다() {
        assertThat(ChatEndSummaryPeriodContext.latest(List.of("제 A회선 가입 기간은 18개월입니다.",
                "제 A회선 가입 기간은 24개월로 정정합니다.", "제 A회선 가입 기간은 30개월로 정정합니다.")))
                .containsExactly("제 A회선 가입 기간은 30개월로 정정합니다.");
        assertThat(ChatEndSummaryPeriodContext.latest(List.of("제 A회선 가입 기간은 18개월입니다.",
                "제 B회선 가입 기간은 24개월로 정정합니다."))).hasSize(2);
        assertThat(ChatEndSummaryPeriodContext.latest(List.of("제 A회선 가입 기간은 18개월입니다.",
                "가입 기간은 24개월로 정정합니다."))).containsExactly("가입 기간은 24개월로 정정합니다.");
        assertThat(ChatEndSummaryPeriodContext.latest(List.of("형 B회선 가입 기간은 12개월입니다.",
                "아까 형 B회선의 가입 기간을 정정합니다. 12개월이 아니라 24개월입니다.")))
                .containsExactly("아까 형 B회선의 가입 기간을 정정합니다. 12개월이 아니라 24개월입니다.");
        assertThat(ChatEndSummaryPeriodContext.latest(List.of("형 B회선 가입 기간은 12개월입니다.",
                "다른 형 B회선 가입 기간은 24개월로 정정합니다."))).hasSize(2);
    }

    @Test
    @DisplayName("두 번째 문장의 기간도 원래 발언의 대상과 회선을 물려받고 다른 기간은 보존한다")
    void 생략한_대상의_기간_종류별_정정을_반영한다() {
        String before = "제 B회선 가입 기간은 6개월입니다. 약정 기간은 1년입니다.";
        String correction = "앞서 제 B회선의 약정 기간을 정정합니다. 1년이 아니라 2년입니다.";
        var periods = List.of(
                new ChatEndSummaryPeriodContext.Quote("제 B회선 가입 기간은 6개월입니다.", before),
                new ChatEndSummaryPeriodContext.Quote("약정 기간은 1년입니다.", before),
                new ChatEndSummaryPeriodContext.Quote(correction, correction));

        assertThat(ChatEndSummaryPeriodContext.latestScoped(periods))
                .containsExactly("제 B회선 가입 기간은 6개월입니다.", correction);
    }

    @Test
    @DisplayName("기간에 명시한 본인을 뒤 문장의 명의 양도 상대방으로 바꾸지 않는다")
    void 명시한_기간_대상은_문장_밖의_다른_당사자보다_우선한다() {
        String old = "제 회선 가입 기간은 18개월입니다.";
        String correction = "같은 문의의 가입 기간을 정정합니다. 18개월이 아니라 24개월입니다.";

        assertThat(ChatEndSummaryPeriodContext.latestScoped(List.of(
                new ChatEndSummaryPeriodContext.Quote(old, old + " 명의를 아버지한테서 저한테 바꾸려면요?"),
                new ChatEndSummaryPeriodContext.Quote(correction, correction))))
                .containsExactly(correction);
    }

    private ChatEndSummaryData.Input input(String correction) {
        var messages = List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION",
                        "제 회선 가입 기간은 18개월입니다. 유심 재발급 비용은요?", null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER",
                        "유심 재발급 비용은 7,700원입니다.", "GROUNDED", "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", correction, null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER",
                        "신분증을 지참해 매장을 방문합니다. 신청은 미접수 상태입니다.", "GROUNDED", "COMPLETED"));
        var category = new Classification("FAQ", List.of("USIM"));
        return new ChatEndSummaryData.Input(1, 4, 1L, messages, List.of(), Set.of(), List.of(
                new ChatEndSummaryData.Condition(1, 1, "tenure_months", "24", "FILLED", "ASKED", 3L, 3)),
                OutputMaskContext.empty(), Map.of(1L, category, 2L, category, 3L, category, 4L, category));
    }

    private ChatEndSummaryGroundedGenerator.Result generate(ChatEndSummaryData.Input input) {
        var root = mapper.createObjectNode();
        var topics = root.putArray("topics");
        var guidance = root.putArray("guidance");
        root.putArray("pendingItems");
        for (var message : input.messages()) {
            if (message.role().equals("USER")) {
                for (String quote : ChatEndSummarySourceText.extractPeriodQuotes(message.content())) {
                    topics.addObject().put("text", "고객이 언급한 기간(원문): " + quote)
                            .putArray("sourceMessageIds").add(message.id());
                }
            } else {
                guidance.addObject().put("text", message.content()).putArray("sourceMessageIds").add(message.id());
            }
        }
        return new ChatEndSummaryGroundedGenerator(mapper, inspector, new ChatOutputGuard(mapper))
                .generate(input, new ChatEndSummaryData.Result(root.toString(), null, OutputMaskContext.empty()));
    }

}
