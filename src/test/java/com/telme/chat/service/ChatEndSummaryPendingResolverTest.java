package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatEndSummaryPendingResolverTest {
    @Test
    @DisplayName("같은 질문의 지역과 방문자 조건을 서로 다른 항목으로 보존한다")
    void 지역과_방문자를_모두_보존한다() {
        var input = input("GROUNDED", "COMPLETED", List.of(
                request(11, "매장 위치"), request(12, "매장 방문자")), List.of(
                condition(11, "location", "PENDING"), condition(12, "agent_visit", "PENDING")));

        var result = ChatEndSummaryPendingResolver.resolve(input, Set.of(1L));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChatEndSummaryPendingResolver.Pending::key).doesNotHaveDuplicates();
        assertThat(result.getFirst().text()).contains("지역").doesNotContain("대리인");
        assertThat(result.getLast().text()).contains("본인", "대리인").doesNotContain("지역");
    }

    @Test
    @DisplayName("이미 확인된 지역을 남은 조건으로 다시 만들지 않는다")
    void 확인된_조건은_제외한다() {
        var input = input("GROUNDED", "COMPLETED", List.of(request(11, "매장 방문자")), List.of(
                condition(11, "location", "FILLED"), condition(11, "agent_visit", "PENDING")));

        var result = ChatEndSummaryPendingResolver.resolve(input, Set.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().text()).contains("대리인").doesNotContain("지역");
    }

    @Test
    @DisplayName("근거 없음은 신청 접수 상태가 아닌 미답변으로 기록한다")
    void 근거_없음을_미답변으로_기록한다() {
        var result = ChatEndSummaryPendingResolver.resolve(input("NO_EVIDENCE", "COMPLETED", List.of(), List.of()),
                Set.of(1L));

        assertThat(result.getFirst().kind()).isEqualTo(ChatEndSummaryPendingResolver.Kind.UNANSWERED);
        assertThat(result.getFirst().text()).doesNotContain("접수", "처리 대기", "예약");
    }

    @Test
    @DisplayName("실행 실패는 고객 조건 미확인과 구분한다")
    void 실행_실패를_중단으로_기록한다() {
        var result = ChatEndSummaryPendingResolver.resolve(input("", "FAILED", List.of(), List.of()), Set.of(1L));

        assertThat(result.getFirst().kind()).isEqualTo(ChatEndSummaryPendingResolver.Kind.INTERRUPTED);
    }

    @Test
    @DisplayName("신청을 안 했다는 안내만으로 새로운 남은 문의를 만들지 않는다")
    void 남은_상태가_없으면_항목을_만들지_않는다() {
        assertThat(ChatEndSummaryPendingResolver.resolve(input("GROUNDED", "COMPLETED", List.of(), List.of()),
                Set.of())).isEmpty();
    }

    @Test
    @DisplayName("한 문의의 근거 없음이 이후 정상 답변으로 대체되면 미답변으로 남기지 않는다")
    void 이후_정상_답변이_있으면_미답변을_제거한다() {
        var original = input("NO_EVIDENCE", "COMPLETED",
                List.of(new ChatEndSummaryData.Request(1, 1, "유심 비용", "DONE")), List.of());
        var messages = new java.util.ArrayList<>(original.messages());
        messages.add(new ChatEndSummaryData.Message(3, 3, 1L, "ASSISTANT", "ANSWER",
                "유심 비용은 7700원입니다.", "GROUNDED", "COMPLETED"));
        var updated = new ChatEndSummaryData.Input(1, 3, 1L, messages, original.requests(), Set.of(1L),
                List.of(), OutputMaskContext.empty());

        assertThat(ChatEndSummaryPendingResolver.resolve(updated, Set.of(1L))).isEmpty();
    }

    private ChatEndSummaryData.Request request(long id, String query) {
        return new ChatEndSummaryData.Request(1, (int) id, query, "WAITING_CONDITION", List.of(), id);
    }

    private ChatEndSummaryData.Condition condition(long request, String key, String status) {
        return new ChatEndSummaryData.Condition(request, 1, key, "", status, "ASKED", null, null);
    }

    private ChatEndSummaryData.Input input(String basis, String status, List<ChatEndSummaryData.Request> requests,
            List<ChatEndSummaryData.Condition> conditions) {
        return new ChatEndSummaryData.Input(1, 2, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "유심 교체 문의", null, status),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "신분증 안내", basis, "COMPLETED")),
                requests, Set.of(1L), conditions, OutputMaskContext.empty());
    }
}
