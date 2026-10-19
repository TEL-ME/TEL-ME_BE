package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.safety.OutputMaskContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryInquiryContinuationTest {
    @Test
    @DisplayName("유심 비용과 무료 조건과 준비물의 이어묻기를 한 문의로 묶는다")
    void 같은_유심의_이어묻기를_묶는다() {
        var groups = groups(List.of("유심 재발급 비용은 얼마인가요?", "그 유심이 무료인 조건은요?", "그 재발급 준비물은요?"),
                List.of("유심 재발급 비용은 7700원입니다.", "기기 불량 확인 시에만 무료입니다.", "신분증을 지참해 매장을 방문합니다."));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sourceText()).contains("7700원", "무료", "신분증", "매장");
        assertThat(groups.getFirst().title()).isEqualTo("유심 재발급 비용·무료 조건·준비물");
    }

    @Test
    @DisplayName("다른 주제가 끼어도 명시적으로 유심을 다시 지칭하면 기존 문의에 연결한다")
    void 로밍_뒤의_유심_참조를_연결한다() {
        var groups = groups(List.of("유심 재발급 비용은요?", "로밍 요금은요?", "아까 그 유심 준비물은요?"),
                List.of("유심 재발급 비용은 7700원입니다.", "로밍은 3일에 9000원입니다.", "신분증을 지참해 매장을 방문합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).contains("7700원", "신분증");
        assertThat(groups.getLast().sourceText()).contains("9000원");
    }

    @Test
    @DisplayName("본인과 어머니의 유심 문의는 나누고 본인에 대한 명시적 참조만 되돌려 연결한다")
    void 사람별_문의가_섞이지_않는다() {
        var groups = groups(List.of("본인 유심 재발급 비용은요?", "어머니 유심 재발급 비용은요?", "아까 제 유심 준비물은요?"),
                List.of("본인 유심 비용은 7700원입니다.", "어머니 유심 비용은 5500원입니다.", "본인은 신분증을 지참합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).doesNotContain("5500원").contains("신분증");
        assertThat(groups.getLast().sourceText()).contains("어머니", "5500원");
    }

    @Test
    @DisplayName("실물 유심과 eSIM의 다른 절차를 같은 문단에 합치지 않는다")
    void 실물_유심과_이심을_분리한다() {
        var groups = groups(List.of("실물 유심 재발급 비용은요?", "eSIM 최초 등록 방법은요?"),
                List.of("실물 유심 비용은 7700원입니다.", "eSIM 최초 등록은 기기 설정에서 진행합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getLast().title()).isEqualTo("eSIM 최초 등록 방법");
    }

    @Test
    @DisplayName("다른 회선과 별도 문의는 같은 유심이라는 이유로 합치지 않는다")
    void 새_회선과_신청_건을_분리한다() {
        var groups = groups(List.of("첫 번째 회선 유심 재발급 비용은요?", "두 번째 회선 유심 재발급 비용은요?",
                "별도 신청 건으로 유심 재발급 비용을 문의합니다."),
                List.of("유심 비용은 7700원입니다.", "유심 비용은 5500원입니다.", "유심 비용은 8800원입니다."));

        assertThat(groups).hasSize(3);
    }

    @Test
    @DisplayName("최초 등록 상담에 재발급과 교체 제목을 붙이지 않는다")
    void 실제_문의에_맞는_제목을_사용한다() {
        var groups = groups(List.of("유심 최초 등록 방법을 알려주세요."), List.of("유심 최초 등록 절차를 안내했습니다."));

        assertThat(groups.getFirst().title()).isEqualTo("유심 최초 등록 방법");
    }

    @Test
    @DisplayName("회선 이름의 앞뒤 표현이 바뀌어도 명시한 회선에만 이어 묻기를 연결한다")
    void 회선_호칭이_바뀌어도_다른_회선에_붙이지_않는다() {
        var groups = groups(List.of("본인 A회선 유심 재발급 비용은요?", "본인 B회선 유심 재발급 비용은요?",
                "그 회선 A의 유심 준비물은요?"),
                List.of("A회선 비용은 7700원입니다.", "B회선 비용은 5500원입니다.", "A회선은 신분증이 필요합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).contains("신분증").doesNotContain("5500원");
        assertThat(groups.getLast().sourceText()).doesNotContain("신분증").contains("5500원");
    }

    @Test
    @DisplayName("첫 번째 회선을 숫자로 다시 지칭해도 두 번째 회선과 섞지 않는다")
    void 회선_순번과_숫자_표현을_같은_대상으로_확인한다() {
        var groups = groups(List.of("첫 번째 회선 유심 재발급 비용은요?", "두 번째 회선 유심 재발급 비용은요?",
                "그 회선 1의 유심 준비물은요?"),
                List.of("1번 회선 비용은 7700원입니다.", "2번 회선 비용은 5500원입니다.", "1번 회선은 신분증이 필요합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).contains("신분증").doesNotContain("5500원");
        assertThat(groups.getLast().sourceText()).doesNotContain("신분증").contains("5500원");
    }

    @Test
    @DisplayName("대상이 여럿이면 모호한 유심 지시어를 임의의 사람에게 연결하지 않는다")
    void 모호한_지시어를_합치지_않는다() {
        var groups = groups(List.of("본인 유심 재발급 비용은요?", "어머니 유심 재발급 비용은요?", "로밍 요금은요?",
                "아까 그 유심 준비물은요?"),
                List.of("본인 유심 비용은 7700원입니다.", "어머니 유심 비용은 5500원입니다.",
                        "로밍은 3일에 9000원입니다.", "유심 준비물은 추가 확인이 필요합니다."));

        assertThat(groups).hasSize(4);
    }

    @Test
    @DisplayName("한 질문에 유심과 로밍이 함께 있어도 이후 유심 참조를 유심 안내에 연결한다")
    void 한_질문의_서로_다른_문의도_구분한다() {
        var groups = groups(List.of("유심 재발급 비용과 로밍 요금은요?", "그 유심 준비물은요?"),
                List.of("유심 비용은 7700원입니다.\n\n로밍 요금은 9000원입니다.", "신분증을 지참합니다."));

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().sourceText()).contains("7700원", "신분증").doesNotContain("9000원");
        assertThat(groups.getLast().sourceText()).contains("9000원").doesNotContain("신분증");
    }

    @Test
    @DisplayName("여덟 번 이어 물은 유심 안내의 사실을 한 문의에 모두 보존한다")
    void 여덟_번의_이어묻기도_사실을_보존한다() {
        var groups = groups(List.of("유심 재발급 비용은요?", "그 유심 무료 조건은요?", "그 유심 준비물은요?",
                "그 유심 방문 절차는요?", "그 유심 신청 상태는요?", "그 유심 확인 방법은요?",
                "그 유심 비용을 다시 확인해 주세요.", "그 유심 신분증도 다시 알려주세요."),
                List.of("유심 재발급 비용은 7700원입니다.", "기기 불량 확인 시에만 무료입니다.",
                        "본인 신분증을 지참합니다.", "매장을 방문하는 절차입니다.", "실제 신청을 진행하지 않았습니다.",
                        "매장에서 기기 불량을 확인합니다.", "유심 재발급 비용은 7700원입니다.", "본인 신분증을 지참합니다."));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sourceItems()).isEqualTo(8);
        assertThat(groups.getFirst().sourceText()).contains("7700원", "불량", "무료", "신분증", "매장", "신청");
        assertThat(groups.getFirst().sourceText().split("7700원", -1)).hasSize(2);
    }

    @Test
    @DisplayName("최근 답변 바로 뒤라도 본인과 가족 중 모호한 유심 참조는 임의로 연결하지 않는다")
    void 최근_문의라도_대상이_모호하면_합치지_않는다() {
        var groups = groups(List.of("본인 유심 재발급 비용은요?", "어머니 유심 재발급 비용은요?", "아까 그 유심 준비물은요?"),
                List.of("본인 유심 비용은 7700원입니다.", "어머니 유심 비용은 5500원입니다.",
                        "유심 소유자의 추가 확인이 필요합니다."));

        assertThat(groups).hasSize(3);
        assertThat(groups.getLast().sourceText()).doesNotContain("5500원", "7700원");
    }

    @Test
    @DisplayName("엄마라는 호칭도 본인과 다른 대상으로 구분한다")
    void 엄마와_본인_문의를_구분한다() {
        var result = groups(List.of("본인 유심 재발급 비용은요?", "엄마 유심 재발급 비용은요?"),
                List.of("본인 유심 비용은 7700원입니다.", "엄마 유심 비용은 5500원입니다."));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("그건 어떻게 진행하나요도 대상이 한 개인 유심 문의에 연결한다")
    void 생략한_절차_이어묻기를_연결한다() {
        var result = groups(List.of("유심 재발급 비용은요?", "그건 어떻게 진행하나요?"),
                List.of("유심 비용은 7700원입니다.", "신분증을 지참해 매장을 방문합니다."));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourceText()).contains("7700원", "신분증", "매장");
    }

    @ParameterizedTest
    @CsvSource({"가족,가족", "형,형", "직장 동료,동료", "그럼 형,형", "그리고 형,형"})
    @DisplayName("가족·형·동료의 안내와 제목을 본인 유심 문의에 합치지 않는다")
    void 추가_대상의_원문과_제목을_구분한다(String person, String title) {
        var result = groups(List.of("본인 유심 재발급 비용은요?", person + " 유심 재발급 비용은요?"),
                List.of("본인 유심 비용은 7700원입니다.", person + " 유심 비용은 5500원입니다."));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().sourceText()).contains("7700원").doesNotContain("5500원");
        assertThat(result.getLast().sourceText()).contains("5500원").doesNotContain("7700원");
        assertThat(result.getLast().title()).startsWith(title + " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가족", "형", "직장 동료"})
    @DisplayName("본인과 추가 대상이 함께 있으면 모호한 유심 지시어를 최근 대상에 임의 연결하지 않는다")
    void 추가_대상이_있는_모호한_참조를_구분한다(String person) {
        var result = groups(List.of("본인 유심 재발급 비용은요?", person + " 유심 재발급 비용은요?",
                "아까 그 유심 준비물은요?"), List.of("본인 비용은 7700원입니다.", person + " 비용은 5500원입니다.",
                "소유자 확인이 필요합니다."));

        assertThat(result).hasSize(3);
        assertThat(result.getLast().sourceText()).doesNotContain("7700원", "5500원");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가족", "형", "직장 동료"})
    @DisplayName("대상이 한 명으로 유지되는 가족·형·동료 문의의 이어묻기는 같은 문단으로 묶는다")
    void 같은_추가_대상의_이어묻기를_유지한다(String person) {
        var result = groups(List.of(person + " 유심 재발급 비용은요?", "그 유심 준비물은요?"),
                List.of(person + " 유심 비용은 5500원입니다.", "신분증을 지참합니다."));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourceText()).contains("5500원", "신분증");
    }

    @ParameterizedTest
    @ValueSource(strings = {"그 유심 유형별 준비물은요?", "그 유심 가족 결합 무료 조건은요?",
        "그 유심 선택형 요금은요?", "그 유심 신형 기기 준비물은요?"})
    @DisplayName("유형과 가족 결합 같은 상품 표현을 형이나 다른 가족 소유자로 오인하지 않는다")
    void 상품_표현을_사람으로_오인하지_않는다(String question) {
        var result = groups(List.of("유심 재발급 비용은요?", question),
                List.of("재발급 비용은 7700원입니다.", "문의한 조건을 안내받았습니다."));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourceText()).contains("7700원", "문의한 조건");
    }

    private List<ChatEndSummaryInquiryGrouper.Inquiry> groups(List<String> questions, List<String> answers) {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        var root = new ObjectMapper().createObjectNode();
        root.putArray("topics");
        root.putArray("pendingItems");
        var guidance = root.putArray("guidance");
        for (int index = 0; index < questions.size(); index++) {
            int id = index * 2 + 1;
            messages.add(new ChatEndSummaryData.Message(id, id, null, "USER", "QUESTION", questions.get(index),
                    null, "COMPLETED"));
            messages.add(new ChatEndSummaryData.Message(id + 1, id + 1, (long) id, "ASSISTANT", "ANSWER",
                    answers.get(index), "GROUNDED", "COMPLETED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, questions.get(index), "DONE"));
            for (String paragraph : answers.get(index).split("\\n\\n")) {
                var item = guidance.addObject();
                item.put("text", paragraph);
                item.putArray("sourceMessageIds").add(id + 1);
            }
        }
        return ChatEndSummaryInquiryGrouper.group(new ChatEndSummaryData.Input(1, messages.size(), 1L,
                messages, requests, Set.of(), List.of(), OutputMaskContext.empty()), root);
    }
}
