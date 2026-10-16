package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.safety.OutputMaskContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryResolutionTest {
    @ParameterizedTest
    @ValueSource(strings = {"ANSWER", "STORE_RESULT"})
    @DisplayName("같은 문의의 조건 응답에 정상 답변이 연결되고 완료되면 이전 되묻기를 제외한다")
    void 해결된_조건_응답은_남은_사항에서_제외한다(String type) {
        var input = input(messages(type, "GROUNDED"), List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L)), Set.of(1L));

        assertThat(resolve(input)).isEmpty();
    }

    @Test
    @DisplayName("같은 문의에서 여러 조건을 확인한 뒤 받은 최종 답변으로 앞선 되묻기도 해결한다")
    void 연속된_되묻기를_최종_답변과_연결한다() {
        var messages = new ArrayList<>(messages("CLARIFICATION", null));
        messages.set(3, message(4, 3L, "ASSISTANT", "CLARIFICATION", "이번 달 변경 이력이 있나요?", null));
        messages.add(message(5, null, "USER", "QUESTION", "아니요", null));
        messages.add(message(6, 5L, "ASSISTANT", "ANSWER", "요금제 변경이 가능합니다.", "GROUNDED"));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE")), List.of(
                condition(101, 1, "가입월", "FILLED", 2L, 3L),
                condition(101, 1, "변경이력", "FILLED", 4L, 5L)), Set.of(1L, 3L));

        assertThat(resolve(input)).isEmpty();
    }

    @Test
    @DisplayName("같은 조건을 다시 물어 저장된 질문 연결이 갱신되어도 최종 해결을 인식한다")
    void 같은_조건의_재질문_뒤_해결을_인식한다() {
        var messages = new ArrayList<>(messages("CLARIFICATION", null));
        messages.add(message(5, null, "USER", "QUESTION", "지난달이에요", null));
        messages.add(message(6, 5L, "ASSISTANT", "ANSWER", "이번 달 변경이 가능합니다.", "GROUNDED"));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 4L, 5L)), Set.of(1L, 3L));

        assertThat(resolve(input)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_EVIDENCE", "OUT_OF_SCOPE"})
    @DisplayName("조건을 대답했어도 최종 답변의 근거가 없거나 범위 밖이면 미해결 사유를 원래 문의에 남긴다")
    void 조건_응답_뒤_실패를_원래_문의에_남긴다(String basis) {
        var input = input(messages("ANSWER", basis), List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L)), Set.of(1L, 3L));

        var result = resolve(input);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().originMessageId()).isEqualTo(1);
        assertThat(result.getFirst().kind().name())
                .isEqualTo(basis.equals("NO_EVIDENCE") ? "UNANSWERED" : "OUT_OF_SCOPE");
        assertThat(result.getFirst().text()).contains("요금제").doesNotContain("‘아니요’");
    }

    @Test
    @DisplayName("다른 문의에 정상 답변이 있어도 대기 중인 원래 문의는 해결로 바꾸지 않는다")
    void 다른_문의의_정상_답변을_가져오지_않는다() {
        var messages = new ArrayList<>(messages("ANSWER", "GROUNDED"));
        messages.set(2, message(3, null, "USER", "QUESTION", "로밍 요금은 얼마인가요?", null));
        messages.set(3, message(4, 3L, "ASSISTANT", "ANSWER", "로밍은 하루 9,000원입니다.", "GROUNDED"));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "WAITING_CONDITION"),
                request(102, 3, "로밍 요금", "DONE")),
                List.of(condition(101, 1, "가입월", "PENDING", 2L, null)), Set.of(1L));

        assertThat(resolve(input)).hasSize(1);
        assertThat(resolve(input).getFirst().originMessageId()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 복합 문의의 해결된 조건과 별도 미확인 조건을 섞지 않는다")
    void 복합_문의의_미확인_조건을_남긴다() {
        var input = input(messages("ANSWER", "GROUNDED"), List.of(
                request(101, 1, "요금제 변경", "DONE"), request(102, 1, "매장 위치", "WAITING_CONDITION")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L),
                        condition(102, 1, "location", "PENDING", 2L, null)), Set.of(1L, 3L));

        var result = resolve(input);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().text()).contains("방문 지역").doesNotContain("가입월");
    }

    @Test
    @DisplayName("서로 다른 원래 문의가 섞여도 해결된 문의만 제외한다")
    void 서로_다른_문의의_해결_여부를_구분한다() {
        var messages = new ArrayList<>(messages("ANSWER", "GROUNDED"));
        messages.add(message(5, null, "USER", "QUESTION", "어머니 회선 매장을 찾아주세요", null));
        messages.add(message(6, 5L, "ASSISTANT", "CLARIFICATION", "어느 지역인가요?", null));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE"),
                request(102, 5, "어머니 회선 매장", "WAITING_CONDITION")), List.of(
                condition(101, 1, "가입월", "FILLED", 2L, 3L),
                condition(102, 5, "location", "PENDING", 6L, null)), Set.of(1L, 5L));

        var result = resolve(input);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().originMessageId()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "WAITING_CONDITION"})
    @DisplayName("정상 답변이 있어도 같은 요청의 미확인 조건은 지우지 않는다")
    void 추가_조건이_있으면_해결로_바꾸지_않는다(String status) {
        var input = input(messages("ANSWER", "GROUNDED"), List.of(request(101, 1, "요금제 변경", status)),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L),
                        condition(101, 1, "agent_visit", "PENDING", 2L, null)), Set.of(1L));

        assertThat(resolve(input)).hasSize(1);
        assertThat(resolve(input).getFirst().text()).contains("대리인");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CANCELLED"})
    @DisplayName("조건 응답 실행이 실패하거나 취소됐으면 다른 답변 문구만으로 해결 처리하지 않는다")
    void 실행_실패는_해결로_바꾸지_않는다(String status) {
        var messages = new ArrayList<>(messages("ANSWER", "GROUNDED"));
        messages.set(2, new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", "아니요", null, status));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L)), Set.of(1L, 3L));

        assertThat(resolve(input)).anyMatch(item -> item.kind() == ChatEndSummaryPendingResolver.Kind.INTERRUPTED);
    }

    @Test
    @DisplayName("완료 표시만 있고 정상 답변이 없으면 남은 사항에서 제외하지 않는다")
    void 완료_상태만으로_해결을_단정하지_않는다() {
        var input = input(messages("CLARIFICATION", null), List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L)), Set.of(1L, 3L));

        assertThat(resolve(input)).isNotEmpty();
    }

    @Test
    @DisplayName("새 문의를 함께 보낸 조건 응답은 원래 문의가 해결돼도 별도 미해결 요청을 유지한다")
    void 조건_응답에_포함된_새_문의는_남긴다() {
        var input = input(messages("ANSWER", "GROUNDED"), List.of(
                request(101, 1, "요금제 변경", "DONE"), request(102, 3, "로밍 문의", "WAITING_CONDITION")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L),
                        condition(102, 3, "country", "PENDING", 4L, null)), Set.of(1L, 3L));

        assertThat(resolve(input)).hasSize(1);
        assertThat(resolve(input).getFirst().originMessageId()).isEqualTo(3);
    }

    @Test
    @DisplayName("명시된 되묻기가 다른 문의에 속하면 연결 오류를 해결로 인정하지 않는다")
    void 다른_문의의_되묻기를_연결하지_않는다() {
        var messages = new ArrayList<>(messages("ANSWER", "GROUNDED"));
        messages.add(message(5, null, "USER", "QUESTION", "로밍 문의", null));
        messages.add(message(6, 5L, "ASSISTANT", "CLARIFICATION", "어느 국가인가요?", null));
        messages.add(message(7, null, "USER", "QUESTION", "일본", null));
        messages.add(message(8, 7L, "ASSISTANT", "ANSWER", "로밍 요금을 안내합니다.", "GROUNDED"));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE"),
                request(102, 5, "로밍 문의", "WAITING_CONDITION")),
                List.of(condition(101, 1, "가입월", "FILLED", 6L, 7L)), Set.of(1L));

        assertThat(resolve(input)).isNotEmpty();
    }

    @Test
    @DisplayName("정상 답변 뒤 원래 문의에 추가 되묻기가 오면 이전 완료 표시만으로 지우지 않는다")
    void 정상_답변_뒤_새_되묻기는_남긴다() {
        var messages = new ArrayList<>(messages("ANSWER", "GROUNDED"));
        messages.add(message(5, 1L, "ASSISTANT", "CLARIFICATION", "변경할 요금제도 확인해 주세요.", null));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE")),
                List.of(condition(101, 1, "가입월", "FILLED", 2L, 3L)), Set.of(1L));

        assertThat(resolve(input)).hasSize(1);
        assertThat(resolve(input).getFirst().text()).contains("변경할 요금제");
    }

    @Test
    @DisplayName("하위 요청 번호가 없는 과거 복합 기록은 조건의 소속이 모호하면 해결로 단정하지 않는다")
    void 과거_복합_기록의_모호한_조건은_유지한다() {
        var input = input(messages("ANSWER", "GROUNDED"), List.of(
                new ChatEndSummaryData.Request(1, 1, "요금제 변경", "DONE"),
                new ChatEndSummaryData.Request(1, 2, "매장 위치", "DONE")),
                List.of(condition(101, 1, "location", "PENDING", 2L, null)), Set.of(1L));

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(resolve(input)).isNotEmpty();
    }

    @Test
    @DisplayName("여러 하위 문의가 공유한 응답의 마지막 정상 답변으로 다른 문의까지 해결 처리하지 않는다")
    void 공유_응답의_하위_문의별_성공을_추정하지_않는다() {
        var messages = new ArrayList<>(messages("ANSWER", "NO_EVIDENCE"));
        messages.add(message(5, 3L, "ASSISTANT", "ANSWER", "유심 비용은 7,700원입니다.", "GROUNDED"));
        var input = input(messages, List.of(request(101, 1, "요금제 변경", "DONE"),
                request(102, 1, "유심 비용", "DONE")), List.of(
                condition(101, 1, "가입월", "FILLED", 2L, 3L),
                condition(102, 1, "방문자", "FILLED", 2L, 3L)), Set.of(1L, 3L));

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(resolve(input)).hasSize(1);
        assertThat(resolve(input).getFirst().kind()).isEqualTo(ChatEndSummaryPendingResolver.Kind.WAITING_RESPONSE);
    }

    private List<ChatEndSummaryData.Message> messages(String type, String basis) {
        return List.of(message(1, null, "USER", "QUESTION", "요금제 변경 가능 횟수와 적용 시점을 알려주세요.", null),
                message(2, 1L, "ASSISTANT", "CLARIFICATION", "이번 달 처음 가입하셨나요?", null),
                message(3, null, "USER", "QUESTION", "아니요", null),
                message(4, 3L, "ASSISTANT", type,
                        type.equals("CLARIFICATION") ? "이번 달 변경 이력이 있나요?" : "한 달에 한 번 변경 가능합니다.", basis));
    }

    private ChatEndSummaryData.Message message(long id, Long reply, String role, String type,
            String text, String basis) {
        return new ChatEndSummaryData.Message(id, (int) id, reply, role, type, text, basis, "COMPLETED");
    }

    private ChatEndSummaryData.Request request(long id, long origin, String query, String status) {
        return new ChatEndSummaryData.Request(origin, 1, query, status, List.of(), id);
    }

    private ChatEndSummaryData.Condition condition(long request, long origin, String key, String status,
            Long asked, Long answered) {
        return new ChatEndSummaryData.Condition(request, origin, key, "false", status, "ASKED", answered,
                answered == null ? null : answered.intValue(), asked);
    }

    private ChatEndSummaryData.Input input(List<ChatEndSummaryData.Message> messages,
            List<ChatEndSummaryData.Request> requests, List<ChatEndSummaryData.Condition> conditions,
            Set<Long> pending) {
        return new ChatEndSummaryData.Input(1, messages.size(), 1L, messages, requests, pending, conditions,
                OutputMaskContext.empty());
    }

    private List<ChatEndSummaryPendingResolver.Pending> resolve(ChatEndSummaryData.Input input) {
        return ChatEndSummaryPendingResolver.resolve(input, input.pendingIds());
    }
}
