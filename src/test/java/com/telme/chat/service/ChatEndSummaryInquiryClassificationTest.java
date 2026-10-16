package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatEndSummaryInquiryClassificationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("번호이동의 서류와 처리 시간은 같은 단일 요청이면 한 문의로 묶는다")
    void 같은_번호이동_요청의_여러_문단을_묶는다() {
        String question = "번호이동 시 준비 서류와 처리 시간을 알려주세요.";
        String documents = "번호이동 시 신분증이 필요합니다.";
        String duration = "번호이동은 2영업일이 걸립니다.";
        var input = new ChatEndSummaryData.Input(1, 2, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", question, null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER",
                        documents + "\n\n" + duration, "GROUNDED", "COMPLETED")),
                List.of(new ChatEndSummaryData.Request(1, 1, question, "DONE", List.of(), 101L)),
                Set.of(), List.of(), OutputMaskContext.empty());
        var summary = mapper.createObjectNode();
        summary.putArray("topics");
        summary.putArray("pendingItems");
        var guidance = summary.putArray("guidance");
        for (String text : List.of(documents, duration)) {
            var item = guidance.addObject();
            item.put("text", text);
            item.putArray("sourceMessageIds").add(2);
        }

        var groups = ChatEndSummaryInquiryGrouper.group(input, summary);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sourceText()).isEqualTo(documents + " " + duration);
        assertThat(groups.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(groups.getFirst().sourceItems()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PORTING | 번호이동 문의", "NAME_CHANGE | 명의변경 문의", "SUBSCRIBE | 가입·개통 문의",
        "BILLING | 요금·청구 문의", "DEVICE | 단말·할부 문의", "SERVICE | 부가서비스·기타 문의",
        "PLAN | 요금제 문의", "USIM | 유심 절차", "ROAMING | 해외 로밍 문의", "TERMINATE | 해지 문의"
    })
    @DisplayName("질문에 업무 이름이 없어도 저장된 FAQ 카테고리로 여러 문단을 묶고 제목을 만든다")
    void 저장된_열_가지_분류를_그룹과_제목에_사용한다(String category, String title) {
        var input = createInput(List.of("진행 방법을 알려주세요."),
                Map.of(2L, new Classification("FAQ", List.of(category))));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 2));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().title()).isEqualTo(title);
        assertThat(groups.getFirst().sourceText()).isEqualTo("준비 서류 안내입니다. 처리 시간 안내입니다.");
    }

    @Test
    @DisplayName("원문에 유심이 언급돼도 실제 근거가 번호이동이면 번호이동 문의로 분류한다")
    void 저장된_분류를_단어_판별보다_우선한다() {
        var input = createInput(List.of("유심이 필요한가요?"),
                Map.of(2L, new Classification("FAQ", List.of("PORTING"))));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 2));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().title()).isEqualTo("번호이동 문의");
    }

    @Test
    @DisplayName("단일 STORE 요청 의도를 매장 분류에 사용한다")
    void 저장된_매장_의도를_사용한다() {
        var input = createInput(List.of("가까운 매장 위치를 알려주세요."),
                Map.of(2L, new Classification("STORE", List.of())));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 2));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().title()).isEqualTo("방문 매장 위치");
    }

    @Test
    @DisplayName("되묻기 응답 뒤에도 원래 요청의 매장 의도를 유지한다")
    void 조건_응답_뒤에도_원래_요청_분류를_유지한다() {
        var messages = List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "안내해주세요.", null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "CLARIFICATION",
                        "어느 지역인가요?", null, "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "CONDITION", "강남입니다.", null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "STORE_RESULT",
                        "방문 가능한 곳을 안내했습니다.", "GROUNDED", "COMPLETED"));
        var input = new ChatEndSummaryData.Input(1, 4, 1L, messages,
                List.of(new ChatEndSummaryData.Request(1, 1, "안내해주세요.", "DONE")), Set.of(),
                List.of(new ChatEndSummaryData.Condition(101, 1, "location", "강남", "FILLED", "ASKED", 3L, 3)),
                OutputMaskContext.empty(), Map.of(1L, new Classification("STORE", List.of())));
        var summary = createSummary(4, 4);

        var groups = ChatEndSummaryInquiryGrouper.group(input, summary);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().key()).isEqualTo("1:STORE");
        assertThat(groups.getFirst().title()).isEqualTo("매장 문의");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "본인 번호이동 문의 | 어머니 번호이동 문의",
        "첫 번째 회선 번호이동 문의 | 두 번째 회선 번호이동 문의",
        "번호이동 문의 | 별도 신청 건 번호이동 문의"
    })
    @DisplayName("같은 카테고리라도 사람과 회선 및 별도 요청은 합치지 않는다")
    void 같은_분류의_다른_대상을_구분한다(String first, String second) {
        var input = createInput(List.of(first, second), Map.of(
                2L, new Classification("FAQ", List.of("PORTING")),
                4L, new Classification("FAQ", List.of("PORTING"))));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 4));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(groups.getLast().sourceMessageIds()).containsExactly(4L);
    }

    @Test
    @DisplayName("한 질문의 번호이동과 명의변경 답변은 각 근거 카테고리로 구분한다")
    void 복합_질문의_다른_카테고리를_구분한다() {
        var first = createInput(List.of("번호이동과 명의변경을 알려주세요."), Map.of());
        var messages = new java.util.ArrayList<>(first.messages());
        messages.add(new ChatEndSummaryData.Message(3, 3, 1L, "ASSISTANT", "ANSWER",
                "처리 시간 안내입니다.", "GROUNDED", "COMPLETED"));
        var input = new ChatEndSummaryData.Input(1, 3, 1L, messages, List.of(
                new ChatEndSummaryData.Request(1, 1, "번호이동 문의", "DONE"),
                new ChatEndSummaryData.Request(1, 2, "명의변경 문의", "DONE")),
                Set.of(), List.of(), OutputMaskContext.empty(), Map.of(
                        2L, new Classification("FAQ", List.of("PORTING")),
                        3L, new Classification("FAQ", List.of("NAME_CHANGE"))));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 3));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().title()).isEqualTo("번호이동 문의");
        assertThat(groups.getLast().title()).isEqualTo("명의변경 문의");
    }

    @Test
    @DisplayName("FAQ 의도만 있고 분류가 없는 복수 요청은 임의로 합치지 않는다")
    void 분류_없는_복수_요청은_구분한다() {
        var original = createInput(List.of("두 가지 문의가 있습니다."), Map.of());
        var input = new ChatEndSummaryData.Input(1, 2, 1L, original.messages(), List.of(
                new ChatEndSummaryData.Request(1, 1, "첫 번째 문의", "DONE"),
                new ChatEndSummaryData.Request(1, 2, "두 번째 문의", "DONE")), Set.of(), List.of(),
                OutputMaskContext.empty(), Map.of(2L, new Classification("FAQ", List.of())));

        assertThat(ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 2))).hasSize(2);
    }

    @Test
    @DisplayName("한 답변에 여러 근거 카테고리가 섞이면 한 카테고리를 임의로 선택하지 않는다")
    void 복수_카테고리는_임의로_선택하지_않는다() {
        var input = createInput(List.of("진행 방법을 알려주세요."),
                Map.of(2L, new Classification("FAQ", List.of("PORTING", "NAME_CHANGE"))));

        var groups = ChatEndSummaryInquiryGrouper.group(input, createSummary(2, 2));

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(group -> assertThat(group.title()).isEqualTo("추가 문의"));
    }

    @Test
    @DisplayName("요금 청구 분류에서도 정정으로 철회된 요금제 안내를 다시 제공하지 않는다")
    void 요금_청구_분류의_철회된_안내를_제외한다() {
        var input = createInput(List.of("요금제 변경 문의", "가입월을 이번 달로 정정합니다."), Map.of(
                2L, new Classification("FAQ", List.of("BILLING")),
                4L, new Classification("FAQ", List.of("BILLING"))));
        var summary = createSummary(2, 4);
        ((ObjectNode) summary.path("guidance").get(0)).put("text", "지난달 가입 조건에서는 변경이 가능합니다.");
        ((ObjectNode) summary.path("guidance").get(1)).put("text",
                "이번 달 가입 조건에서는 변경이 불가합니다. 이전 안내는 적용되지 않습니다.");

        var groups = ChatEndSummaryInquiryGrouper.group(input, summary);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().title()).isEqualTo("요금제 변경 문의");
        assertThat(groups.getFirst().sourceText()).doesNotContain("변경이 가능합니다").contains("변경이 불가");
    }

    private ChatEndSummaryData.Input createInput(List<String> questions, Map<Long, Classification> categories) {
        var messages = new java.util.ArrayList<ChatEndSummaryData.Message>();
        var requests = new java.util.ArrayList<ChatEndSummaryData.Request>();
        for (int index = 0; index < questions.size(); index++) {
            long id = index * 2L + 1;
            messages.add(new ChatEndSummaryData.Message(id, (int) id, null, "USER", "QUESTION",
                    questions.get(index), null, "COMPLETED"));
            messages.add(new ChatEndSummaryData.Message(id + 1, (int) id + 1, id, "ASSISTANT", "ANSWER",
                    "준비 서류 안내입니다.\n\n처리 시간 안내입니다.", "GROUNDED", "COMPLETED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, questions.get(index), "DONE"));
        }
        return new ChatEndSummaryData.Input(1, messages.size(), 1L, messages, requests, Set.of(), List.of(),
                OutputMaskContext.empty(), categories);
    }

    private ObjectNode createSummary(long firstSource, long secondSource) {
        var root = mapper.createObjectNode();
        root.putArray("topics");
        root.putArray("pendingItems");
        var guidance = root.putArray("guidance");
        var first = guidance.addObject();
        first.put("text", "준비 서류 안내입니다.");
        first.putArray("sourceMessageIds").add(firstSource);
        var second = guidance.addObject();
        second.put("text", "처리 시간 안내입니다.");
        second.putArray("sourceMessageIds").add(secondSource);
        return root;
    }
}
