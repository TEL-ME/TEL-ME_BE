package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryStoreContinuationTest {
    @Test
    @DisplayName("지역 확인 중 취소된 요청 바로 뒤 같은 매장 검색이 완료되면 이전 확인 항목도 제외한다")
    void 취소_뒤_이어진_동일_매장_요청의_완료를_연결한다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isTrue();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"어머니 매장 알아보기", "친구 매장 알아보기", "다른 회선 매장 알아보기",
        "두 번째 회선 매장 알아보기", "별도 문의로 매장 알아보기", "그만 찾을게요"})
    @DisplayName("검색어가 같아도 재시작한 사용자 발화가 다른 사람·회선·문의나 중단이면 이전 문의를 남긴다")
    void 다른_대상이나_중단_표현은_연결하지_않는다(String nextMessage) {
        var input = createInput(nextMessage, "가까운 매장 알아보기", "DONE", true);

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"강남 유심 재발급 매장", "가까운 번호이동 매장", "가까운 요금제 가입 매장"})
    @DisplayName("바로 다음 매장 요청이어도 저장된 검색 문의가 달라지면 이전 요청의 해결로 쓰지 않는다")
    void 다른_매장_검색은_연결하지_않는다(String query) {
        assertThat(ChatEndSummaryResolution.isResolved(createInput("매장 다시 찾아줘", query, "DONE", true), 1))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "WAITING_CONDITION", "CANCELLED"})
    @DisplayName("재시작한 같은 매장 요청이 완료되지 않았으면 이전 확인 항목을 지우지 않는다")
    void 재시작한_요청의_미완료를_보존한다(String status) {
        assertThat(ChatEndSummaryResolution.isResolved(
                createInput("잘못 입력했어요", "가까운 매장 알아보기", status, true), 1)).isFalse();
    }

    @Test
    @DisplayName("재시작 요청의 완료 표시만 있고 실제 매장 목록이 없으면 이전 항목을 유지한다")
    void 빈_매장_목록으로_이전_요청을_해결하지_않는다() {
        assertThat(ChatEndSummaryResolution.isResolved(
                createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", false), 1)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"어머니 유심 재발급", "다른 회선 유심 재발급", "친구 유심 재발급"})
    @DisplayName("재시작 후 조건 응답에서 대상이 달라져도 이전 매장 문의를 해결 처리하지 않는다")
    void 후속_조건_응답의_다른_대상을_구분한다(String service) {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var messages = new ArrayList<>(input.messages());
        messages.set(6, message(7, null, "USER", "QUESTION", service, null, false));

        assertThat(ChatEndSummaryResolution.isResolved(withMessages(input, messages), 1)).isFalse();
    }

    @Test
    @DisplayName("이전 요청에 방문자 미확인 조건이 있으면 새 요청이 그 조건을 확인하지 않은 채 해결하지 않는다")
    void 이전_요청의_방문자_조건을_누락하지_않는다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var conditions = new ArrayList<>(input.conditions());
        conditions.add(new ChatEndSummaryData.Condition(101, 1, "agent_visit", null, "PENDING", "ASKED",
                null, null, 2L));
        input = new ChatEndSummaryData.Input(1, 8, 1L, input.messages(), input.requests(), Set.of(1L), conditions,
                OutputMaskContext.empty(), input.inquiryClassifications());

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    @Test
    @DisplayName("저장된 의도가 매장이 아니면 같은 검색어와 매장 답변만으로 취소 요청을 해소하지 않는다")
    void 다른_의도의_취소_요청은_해소하지_않는다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        input = new ChatEndSummaryData.Input(1, 8, 1L, input.messages(), input.requests(), Set.of(1L),
                input.conditions(),
                OutputMaskContext.empty(), Map.of(1L, new Classification("FAQ", List.of("USIM")),
                        3L, new Classification("STORE", List.of())));

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    @Test
    @DisplayName("취소 요청의 지역 확인 메시지 연결이 없으면 나중의 매장 목록으로 해결을 추정하지 않는다")
    void 이전_확인_질문의_연결이_없으면_보존한다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var conditions = new ArrayList<>(input.conditions());
        conditions.set(0, new ChatEndSummaryData.Condition(101, 1, "location", null, "PENDING", "ASKED",
                null, null, null));
        input = withConditions(input, conditions);

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    @Test
    @DisplayName("지역 외에 이미 확인한 업무가 달라진 재검색은 이전 요청을 해결하지 않는다")
    void 이미_확인한_업무가_달라지면_보존한다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var conditions = new ArrayList<>(input.conditions());
        conditions.add(new ChatEndSummaryData.Condition(101, 1, "serviceType", "PORT_IN", "FILLED", "QUERY",
                null, null, null));

        assertThat(ChatEndSummaryResolution.isResolved(withConditions(input, conditions), 1)).isFalse();
    }

    @Test
    @DisplayName("사용자가 거절한 방문자 조건이 있는 취소 요청은 다른 검색으로 해결하지 않는다")
    void 거절한_조건을_나중의_검색으로_지우지_않는다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var conditions = new ArrayList<>(input.conditions());
        conditions.add(new ChatEndSummaryData.Condition(101, 1, "agent_visit", null, "DECLINED", "ASKED",
                null, null, 2L));

        assertThat(ChatEndSummaryResolution.isResolved(withConditions(input, conditions), 1)).isFalse();
    }

    @Test
    @DisplayName("다른 문의가 사이에 있으면 그 뒤의 동일 검색 완료로 앞선 취소 요청을 해결하지 않는다")
    void 다른_중간_문의를_건너뛰지_않는다() {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        input.messages().forEach(message -> messages.add(new ChatEndSummaryData.Message(message.id(),
                message.sequence() < 3 ? message.sequence() : message.sequence() + 1, message.replyTo(),
                message.role(), message.type(), message.content(), message.basis(), message.executionStatus(),
                message.hasStoreResults())));
        messages.add(2, new ChatEndSummaryData.Message(9, 3, null, "USER", "QUESTION", "요금제 알려줘",
                null, "COMPLETED"));
        var conditions = input.conditions().stream().map(condition -> new ChatEndSummaryData.Condition(
                condition.requestId(), condition.originMessageId(), condition.key(), condition.value(),
                condition.status(), condition.source(), condition.answeredMessageId(),
                condition.answeredSequence() == null ? null : condition.answeredSequence() + 1,
                condition.askedMessageId())).toList();

        assertThat(ChatEndSummaryResolution.isResolved(withConditions(withMessages(input, messages), conditions), 1))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_EVIDENCE", "OUT_OF_SCOPE"})
    @DisplayName("매장 목록 유형이어도 실패 근거 상태가 있으면 이전 취소 요청을 해결하지 않는다")
    void 실패_근거_표시가_있는_목록을_해결로_쓰지_않는다(String basis) {
        var input = createInput("잘못 입력했어요", "가까운 매장 알아보기", "DONE", true);
        var messages = new ArrayList<>(input.messages());
        messages.set(7, message(8, 7L, "ASSISTANT", "STORE_RESULT", "검증 매장", basis, true));

        assertThat(ChatEndSummaryResolution.isResolved(withMessages(input, messages), 1)).isFalse();
    }

    private ChatEndSummaryData.Input withConditions(ChatEndSummaryData.Input input,
            List<ChatEndSummaryData.Condition> conditions) {
        return new ChatEndSummaryData.Input(1, 8, 1L, input.messages(), input.requests(), Set.of(1L), conditions,
                OutputMaskContext.empty(), input.inquiryClassifications());
    }

    private ChatEndSummaryData.Input createInput(String nextMessage, String query, String status, boolean stores) {
        var messages = List.of(message(1, null, "USER", "QUESTION", "가까운 매장 알아보기", null, false),
                message(2, 1L, "ASSISTANT", "CLARIFICATION", "어느 지역인가요?", null, false),
                message(3, null, "USER", "QUESTION", nextMessage, null, false),
                message(4, 3L, "ASSISTANT", "CLARIFICATION", "어느 지역인가요?", null, false),
                message(5, null, "USER", "QUESTION", "강남역", null, false),
                message(6, 5L, "ASSISTANT", "CLARIFICATION", "어떤 업무인가요?", null, false),
                message(7, null, "USER", "QUESTION", "유심 재발급", null, false),
                message(8, 7L, "ASSISTANT", "STORE_RESULT", "강남 검증 매장", null, stores));
        var conditions = List.of(
                new ChatEndSummaryData.Condition(101, 1, "location", null, "PENDING", "ASKED", null, null, 2L),
                new ChatEndSummaryData.Condition(102, 3, "location", "강남역", "FILLED", "ASKED", 5L, 5, 4L),
                new ChatEndSummaryData.Condition(102, 3, "serviceType", "USIM_REISSUE", "FILLED", "ASKED", 7L, 7, 6L));
        return new ChatEndSummaryData.Input(1, 8, 1L, messages, List.of(
                new ChatEndSummaryData.Request(1, 1, "가까운 매장 알아보기", "CANCELLED", List.of(), 101L),
                new ChatEndSummaryData.Request(3, 1, query, status, List.of(), 102L)), Set.of(1L), conditions,
                OutputMaskContext.empty(), Map.of(1L, new Classification("STORE", List.of()),
                        3L, new Classification("STORE", List.of())));
    }

    private ChatEndSummaryData.Message message(long id, Long replyTo, String role, String type, String text,
            String basis, boolean stores) {
        return new ChatEndSummaryData.Message(id, (int) id, replyTo, role, type, text, basis, "COMPLETED", stores);
    }

    private ChatEndSummaryData.Input withMessages(ChatEndSummaryData.Input input,
            List<ChatEndSummaryData.Message> messages) {
        return new ChatEndSummaryData.Input(1, 8, 1L, messages, input.requests(), Set.of(1L), input.conditions(),
                OutputMaskContext.empty(), input.inquiryClassifications());
    }
}
