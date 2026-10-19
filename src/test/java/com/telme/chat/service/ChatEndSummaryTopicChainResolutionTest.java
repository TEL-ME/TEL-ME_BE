package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryTopicChainResolutionTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "요금이요 | 요금이요 | 청구서 납부 방법 알려줘 | BILLING",
        "요금이요 | 요금 알려줘 | 5G 요금제 알려줘 | PLAN",
        "유심이요 | 유심 알려줘 | 유심 재발급 비용 알려줘 | USIM",
        "번호이동 알려줘 | 번호이동이요 | 번호이동 서류 알려줘 | PORTING",
        "가입 문의드려요 | 가입이요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "청구서이요 | 청구서 알려줘 | 청구서 납부 방법 알려줘 | BILLING"
    })
    @DisplayName("같은 일반 주제를 반복 확인한 뒤 정상 답변이 오면 앞선 확인 항목도 모두 해소한다")
    void 같은_주제의_연속_확인을_완료까지_연결한다(String first, String second, String last, String category) {
        var input = chain(List.of(first, second, last), category, "DONE", "GROUNDED");

        assertThat(ChatEndSummaryResolution.isResolved(input, 3)).isTrue();
        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isTrue();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 20})
    @DisplayName("같은 요금 문의를 여러 번 확인해도 정상 답변까지 연결하고 미확인 항목을 중복으로 남기지 않는다")
    void 여러_번의_같은_되묻기를_연결한다(int repeats) {
        var questions = new ArrayList<>(java.util.Collections.nCopies(repeats, "요금이요"));
        questions.add("5G 요금제 알려줘");
        var input = chain(questions, "PLAN", "DONE", "GROUNDED");

        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가족", "형", "동료", "어머니", "다른 회선", "별도 문의로"})
    @DisplayName("연속 확인 뒤라도 다른 대상·회선·별도 문의의 답변으로 앞선 확인 항목을 해소하지 않는다")
    void 연속_확인_뒤_다른_대상을_구분한다(String subject) {
        var input = chain(List.of("요금이요", "요금이요", subject + " 5G 요금제 알려줘"),
                "PLAN", "DONE", "GROUNDED");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryResolution.isResolved(input, 3)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"유심이요", "다른 회선 요금이요", "별도 문의로 요금이요", "요금 말고 유심 알려줘"})
    @DisplayName("다른 주제나 대상이 중간에 끼면 나중에 같은 요금 답변이 와도 앞선 문의로 건너뛰지 않는다")
    void 중간의_다른_문의를_건너뛰지_않는다(String middle) {
        var input = chain(List.of("요금이요", middle, "5G 요금제 알려줘"), "PLAN", "DONE", "GROUNDED");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L))).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"DONE,NO_EVIDENCE", "DONE,OUT_OF_SCOPE", "PENDING,GROUNDED",
        "WAITING_CONDITION,GROUNDED", "CANCELLED,GROUNDED"})
    @DisplayName("마지막 요청이 미완료이거나 정상 근거 답변이 아니면 연속 확인도 해결로 바꾸지 않는다")
    void 최종_요청의_미완료와_실패를_보존한다(String status, String basis) {
        var input = chain(List.of("요금이요", "요금이요", "5G 요금제 알려줘"), "PLAN", status, basis);

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).isNotEmpty();
    }

    @Test
    @DisplayName("중간 확인 메시지에 실제 미해결 요청이 있으면 이후 정상 답변으로 그 조건을 건너뛰지 않는다")
    void 중간_요청의_미확인_조건을_보존한다() {
        var input = chain(List.of("요금이요", "요금이요", "5G 요금제 알려줘"), "PLAN", "DONE", "GROUNDED");
        var requests = new ArrayList<>(input.requests());
        requests.add(new ChatEndSummaryData.Request(3, 1, "요금이요", "WAITING_CONDITION", List.of(), 102L));
        var conditions = List.of(new ChatEndSummaryData.Condition(102, 3, "agent_visit", null, "PENDING",
                "ASKED", null, null, 4L));
        input = new ChatEndSummaryData.Input(1, 6, 1L, input.messages(), requests, input.pendingIds(),
                conditions, OutputMaskContext.empty(), input.inquiryClassifications());

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(3L)))
                .anyMatch(item -> item.text().contains("방문자"));
    }

    @Test
    @DisplayName("연속 주제 확인을 해결해도 별도로 남은 매장 지역 조건은 유지한다")
    void 별도_문의의_미확인_지역을_보존한다() {
        var input = chain(List.of("요금이요", "요금이요", "5G 요금제 알려줘"), "PLAN", "DONE", "GROUNDED");
        var messages = new ArrayList<>(input.messages());
        messages.add(message(7, null, "USER", "QUESTION", "가까운 매장", null, "COMPLETED"));
        messages.add(message(8, 7L, "ASSISTANT", "CLARIFICATION", "어느 지역인가요?", null, "COMPLETED"));
        var requests = new ArrayList<>(input.requests());
        requests.add(new ChatEndSummaryData.Request(7, 1, "가까운 매장", "WAITING_CONDITION", List.of(), 104L));
        var conditions = List.of(new ChatEndSummaryData.Condition(104, 7, "location", null, "PENDING", "ASKED",
                null, null, 8L));
        input = new ChatEndSummaryData.Input(1, 8, 1L, messages, requests, Set.of(1L, 3L, 7L), conditions,
                OutputMaskContext.empty(), input.inquiryClassifications());

        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds()))
                .singleElement().satisfies(item -> {
                    assertThat(item.originMessageId()).isEqualTo(7);
                    assertThat(item.text()).contains("방문 지역");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CANCELLED"})
    @DisplayName("중간 사용자 메시지의 실행이 실패하거나 취소됐으면 정상 완료까지 연결하지 않는다")
    void 중간_실행_실패를_보존한다(String execution) {
        var input = chain(List.of("요금이요", "요금이요", "5G 요금제 알려줘"), "PLAN", "DONE", "GROUNDED");
        var messages = new ArrayList<>(input.messages());
        messages.set(2, message(3, null, "USER", "QUESTION", "요금이요", null, execution));
        input = new ChatEndSummaryData.Input(1, 6, 1L, messages, input.requests(), input.pendingIds(),
                input.conditions(), OutputMaskContext.empty(), input.inquiryClassifications());

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    @Test
    @DisplayName("같은 질문을 반복했더라도 주제 외의 확인 질문은 최종 답변까지 건너뛰지 않는다")
    void 다른_종류의_확인_질문을_건너뛰지_않는다() {
        var input = chain(List.of("요금이요", "요금이요", "5G 요금제 알려줘"), "PLAN", "DONE", "GROUNDED");
        var messages = new ArrayList<>(input.messages());
        messages.set(3, message(4, 3L, "ASSISTANT", "CLARIFICATION", "방문자가 본인인가요?", null, "COMPLETED"));
        input = new ChatEndSummaryData.Input(1, 6, 1L, messages, input.requests(), input.pendingIds(),
                input.conditions(), OutputMaskContext.empty(), input.inquiryClassifications());

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).hasSize(2);
    }

    private ChatEndSummaryData.Input chain(List<String> questions, String category, String status, String basis) {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        for (int index = 0; index < questions.size(); index++) {
            long id = index * 2L + 1;
            boolean last = index == questions.size() - 1;
            messages.add(message(id, null, "USER", "QUESTION", questions.get(index), null, "COMPLETED"));
            messages.add(message(id + 1, id, "ASSISTANT", last ? "ANSWER" : "CLARIFICATION",
                    last ? "상담 안내 원문입니다." : ChatTopicClarification.MESSAGE,
                    last ? basis : null, "COMPLETED"));
        }
        long lastId = questions.size() * 2L - 1;
        var pending = messages.stream().filter(message -> message.role().equals("USER") && message.id() != lastId)
                .map(ChatEndSummaryData.Message::id).collect(Collectors.toSet());
        return new ChatEndSummaryData.Input(1, messages.size(), 1L, messages,
                List.of(new ChatEndSummaryData.Request(lastId, 1, questions.getLast(), status, List.of(), 103L)),
                pending, List.of(), OutputMaskContext.empty(),
                Map.of(lastId, new Classification("FAQ", List.of(category))));
    }

    private ChatEndSummaryData.Message message(long id, Long reply, String role, String type, String text,
            String basis, String execution) {
        return new ChatEndSummaryData.Message(id, (int) id, reply, role, type, text, basis, execution);
    }
}
