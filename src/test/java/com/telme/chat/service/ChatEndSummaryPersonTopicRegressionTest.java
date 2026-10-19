package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class ChatEndSummaryPersonTopicRegressionTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 다른 가족 유심 재발급 비용은요?",
        "형 유심 재발급 비용은요? | 다른 형 유심 재발급 비용은요?",
        "직장 동료 유심 재발급 비용은요? | 다른 동료 유심 재발급 비용은요?",
        "직장 동료 유심 재발급 비용은요? | 다른 직장 동료 유심 재발급 비용은요?",
        "큰형 유심 재발급 비용은요? | 다른 큰형 유심 재발급 비용은요?",
        "큰형 유심 재발급 비용은요? | 작은형 유심 재발급 비용은요?",
        "친형 유심 재발급 비용은요? | 사촌형 유심 재발급 비용은요?"
    })
    @DisplayName("같은 호칭이라도 명시적으로 다른 사람을 물으면 안내를 합치지 않는다")
    void 명시적인_다른_대상의_경계를_검사한다(String first, String second) {
        var result = groups(first, second);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(result.getLast().sourceMessageIds()).containsExactly(4L);
        assertThat(result.getFirst().sourceText()).contains("7,700원").doesNotContain("5,500원");
        assertThat(result.getLast().sourceText()).contains("5,500원").doesNotContain("7,700원");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 내 유심 재발급 비용은요?",
        "형 유심 재발급 비용은요? | 저의 유심 재발급 비용은요?",
        "직장 동료 유심 재발급 비용은요? | 나의 유심 재발급 비용은요?",
        "형 1번 회선 유심 재발급 비용은요? | 본인 1번 회선 유심 재발급 비용은요?",
        "형 유심 재발급 비용은요? | 그럼내유심 재발급 비용은요?"
    })
    @DisplayName("다른 사람 상담 뒤 내 유심·저의 유심·나의 유심으로 돌아오면 본인 문의를 분리한다")
    void 일인칭_소유자_표현으로_돌아온_문의를_분리한다(String first, String second) {
        var result = groups(first, second);

        assertThat(result).hasSize(2);
        assertThat(result.getLast().title()).startsWith("본인 ");
        assertThat(result.getLast().sourceMessageIds()).containsExactly(4L);
    }

    @ParameterizedTest
    @CsvSource({"가족", "형", "직장 동료"})
    @DisplayName("같은 유심의 준비물에서 본인 신분증을 물어도 문의 소유자가 본인으로 바뀐 것으로 보지 않는다")
    void 준비물의_본인을_새_소유자로_오인하지_않는다(String person) {
        assertThat(groups(person + " 유심 재발급 비용은요?", "그 유심은 본인 신분증이 필요한가요?"))
                .singleElement().satisfies(inquiry -> {
                    assertThat(inquiry.title()).startsWith(person.replace("직장 ", "") + " ");
                    assertThat(inquiry.sourceMessageIds()).containsExactly(2L, 4L);
                    assertThat(inquiry.sourceText()).contains("7,700원", "명의자의 신분증");
                });
    }

    @ParameterizedTest
    @CsvSource({"큰형,작은형", "친형,사촌형", "큰 형,작은 형"})
    @DisplayName("형의 관계를 명시한 표현은 문단과 제목 모두에서 보존한다")
    void 구분한_형의_관계를_제목에도_보존한다(String first, String second) {
        var result = groups(first + " 유심 재발급 비용은요?", second + " 유심 재발급 비용은요?");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().title()).startsWith(first.replace(" ", "") + " ");
        assertThat(result.getLast().title()).startsWith(second.replace(" ", "") + " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"제", "내", "저의", "나의", "본인의", "본인은", "저는"})
    @DisplayName("명시적인 본인 소유 표현은 표기가 달라도 같은 본인 문의로 유지한다")
    void 본인의_소유_표현을_같은_대상으로_유지한다(String owner) {
        assertThat(groups("본인 유심 재발급 비용은요?", owner + " 유심 재발급 준비물은요?"))
                .singleElement().satisfies(inquiry -> {
                    assertThat(inquiry.title()).startsWith("본인 ");
                    assertThat(inquiry.sourceMessageIds()).containsExactly(2L, 4L);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"그 유심 본인 확인 준비물은요?",
        "그 유심은 본인이 방문할 때 준비물이 뭔가요?", "그 유심 명의자 본인의 신분증이 필요한가요?"})
    @DisplayName("본인 확인과 방문자 준비물은 기존 형의 유심 소유자를 바꾸지 않는다")
    void 방문자와_명의자_조건의_본인을_구분한다(String question) {
        assertThat(groups("형 유심 재발급 비용은요?", question)).singleElement()
                .satisfies(inquiry -> assertThat(inquiry.title()).startsWith("형 "));
    }

    @Test
    @DisplayName("다른 가족을 나눈 뒤 소유자가 모호한 지시어를 최근 가족에게 임의 연결하지 않는다")
    void 같은_호칭의_다른_사람_뒤에는_모호한_참조를_연결하지_않는다() {
        var messages = List.of(
                message(1, null, "USER", "QUESTION", "가족 유심 재발급 비용은요?", null),
                message(3, null, "USER", "QUESTION", "다른 가족 유심 재발급 비용은요?", null),
                message(5, null, "USER", "QUESTION", "그 유심 준비물은요?", null));
        var input = new ChatEndSummaryData.Input(1, 5, 1L, messages, List.of(), Set.of(), List.of(),
                OutputMaskContext.empty());

        assertThat(ChatEndSummaryInquiryLinker.resolveRoots(input)).containsEntry(1L, 1L)
                .containsEntry(3L, 3L).containsEntry(5L, 5L);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가입 문의드려요 | 신규 가입이요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "휴대폰이요 | 단말 문의요 | 기기변경 할부 조건 알려줘 | DEVICE",
        "청구 문의요 | 청구서이요 | 청구서 납부 방법 알려줘 | BILLING",
        "핸드폰이요 | 기기 문의요 | 휴대폰 변경 조건 알려줘 | DEVICE",
        "개통 문의요 | 신규 개통이요 | 신규 개통 서류 알려줘 | SUBSCRIBE",
        "eSIM이요 | 이심 문의요 | 이심 등록 방법 알려줘 | USIM"
    })
    @DisplayName("표현이 구체화되거나 동의어로 바뀐 같은 주제 확인도 최종 답변까지 연결한다")
    void 같은_주제의_다른_표현을_거친_확인을_검사한다(String first, String middle,
            String last, String category) {
        var input = chain(first, middle, last, category);

        assertThat(ChatEndSummaryResolution.isResolved(input, 3)).isTrue();
        assertThat(ChatEndSummaryResolution.isResolved(input, 1))
                .as("남은 사항: %s", ChatEndSummaryPendingResolver.resolve(input, Set.of(1L, 3L)))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 제 유심 재발급 비용은요? | 2",
        "본인 유심 재발급 비용은요? | 형 유심 재발급 비용은요? | 2",
        "형 유심 재발급 비용은요? | 그 유심 준비물은요? | 1",
        "형 유심 재발급 비용은요? | 형 유심 재발급 준비물은요? | 1"
    })
    @DisplayName("기존에 지원하던 본인 전환과 같은 대상의 정상 이어묻기는 대조군으로 검사한다")
    void 대상_구분의_정상_대조군을_검사한다(String first, String second, int expected) {
        assertThat(groups(first, second)).hasSize(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "납부 문의요 | 청구서이요 | 청구서 발급 방법 알려줘 | BILLING",
        "가입 문의드려요 | 해지요 | 해지 방법 알려줘 | TERMINATE",
        "단말이요 | 요금이요 | 요금제 종류 알려줘 | PLAN"
    })
    @DisplayName("같은 분류 안의 다른 업무와 중간에 바뀐 주제는 대조군에서도 해결로 처리하지 않는다")
    void 다른_주제와_세부_업무를_대조한다(String first, String middle, String last, String category) {
        assertThat(ChatEndSummaryResolution.isResolved(chain(first, middle, last, category), 1)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "신규 가입이요 | 가입이요 | 재가입 서류 알려줘 | SUBSCRIBE",
        "가입 문의드려요 | 신규 가입이요 | 재가입 서류 알려줘 | SUBSCRIBE",
        "할부 문의요 | 단말이요 | 기기변경 알려줘 | DEVICE",
        "부가서비스요 | 멤버십이요 | 멤버십 혜택 알려줘 | SERVICE",
        "실물 유심이요 | eSIM이요 | eSIM 등록 방법 알려줘 | USIM"
    })
    @DisplayName("별칭 연결을 늘려도 특정 조건이 지워지거나 같은 분류의 다른 업무로 해결되지 않는다")
    void 구체적인_조건과_다른_세부_업무를_보존한다(String first, String middle, String last, String category) {
        assertThat(ChatEndSummaryResolution.isResolved(chain(first, middle, last, category), 1)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가족", "형", "직장 동료"})
    @DisplayName("유심 외 가입 문의에서도 명시적인 다른 사람의 답변으로 앞선 주제 확인을 해결하지 않는다")
    void 다른_업무의_소유자_경계도_유지한다(String person) {
        var input = chain("가입 문의드려요", "신규 가입이요", person + " 신규 가입 서류 알려줘", "SUBSCRIBE");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryResolution.isResolved(input, 3)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, input.pendingIds())).hasSize(2);
    }

    @Test
    @DisplayName("가족 할인은 다른 가족 소유자 문의로 오인하지 않는 기존 정상 동작을 유지한다")
    void 가족_할인_상품_문의는_정상_연결한다() {
        assertThat(ChatEndSummaryResolution.isResolved(
                chain("요금이요", "요금이요", "5G 요금제의 가족 할인 알려줘", "PLAN"), 1)).isTrue();
    }

    private List<ChatEndSummaryInquiryGrouper.Inquiry> groups(String first, String second) {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        var body = new ObjectMapper().createObjectNode();
        body.putArray("topics");
        body.putArray("pendingItems");
        var guidance = body.putArray("guidance");
        var questions = List.of(first, second);
        for (int index = 0; index < questions.size(); index++) {
            long id = index * 2L + 1;
            String answer = "첫 안내 비용은 7,700원입니다.";
            if (index > 0) {
                answer = questions.get(index).contains("준비물") || questions.get(index).contains("신분증")
                        ? "해당 유심 명의자의 신분증을 지참합니다." : "두 번째 안내 비용은 5,500원입니다.";
            }
            messages.add(message(id, null, "USER", "QUESTION", questions.get(index), null));
            messages.add(message(id + 1, id, "ASSISTANT", "ANSWER", answer, "GROUNDED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, questions.get(index), "DONE"));
            var item = guidance.addObject();
            item.put("text", answer);
            item.putArray("sourceMessageIds").add(id + 1);
        }
        return ChatEndSummaryInquiryGrouper.group(new ChatEndSummaryData.Input(1, 4, 1L, messages, requests,
                Set.of(), List.of(), OutputMaskContext.empty()), body);
    }

    private ChatEndSummaryData.Input chain(String first, String middle, String last, String category) {
        var messages = List.of(
                message(1, null, "USER", "QUESTION", first, null),
                message(2, 1L, "ASSISTANT", "CLARIFICATION", ChatTopicClarification.MESSAGE, null),
                message(3, null, "USER", "QUESTION", middle, null),
                message(4, 3L, "ASSISTANT", "CLARIFICATION", ChatTopicClarification.MESSAGE, null),
                message(5, null, "USER", "QUESTION", last, null),
                message(6, 5L, "ASSISTANT", "ANSWER", "상담 안내 원문입니다.", "GROUNDED"));
        return new ChatEndSummaryData.Input(1, 6, 1L, messages,
                List.of(new ChatEndSummaryData.Request(5, 1, last, "DONE", List.of(), 103L)), Set.of(1L, 3L),
                List.of(), OutputMaskContext.empty(), Map.of(5L, new Classification("FAQ", List.of(category))));
    }

    private ChatEndSummaryData.Message message(long id, Long reply, String role, String type, String text,
            String basis) {
        return new ChatEndSummaryData.Message(id, (int) id, reply, role, type, text, basis, "COMPLETED");
    }
}
