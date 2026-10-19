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

class ChatEndSummaryOwnerBoundaryTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 내 실물 유심 재발급 비용은요?",
        "형 유심 재발급 비용은요? | 저의 분실 유심 재발급 비용은요?",
        "직장 동료 유심 재발급 비용은요? | 나의 물리 유심 재발급 비용은요?",
        "가족 유심 재발급 비용은요? | 내 새로 발급받은 실물 유심 재발급 비용은요?",
        "가족 유심 재발급 비용은요? | 제 분실한 유심 재발급 비용은요?",
        "가족 유심 재발급 비용은요? | 저의 사용 중인 유심 재발급 비용은요?",
        "가족 유심 재발급 비용은요? | 나의 재발급 받은 유심 재발급 비용은요?",
        "가족 유심 재발급 비용은요? | 본인의 새 실물 유심 재발급 비용은요?"
    })
    @DisplayName("명시한 본인 소유자와 유심 사이에 수식어가 있어도 이전 다른 사람의 안내와 섞지 않는다")
    void 수식어가_있는_본인_소유자를_구분한다(String first, String second) {
        var result = groups(first, second);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(result.getLast().title()).startsWith("본인 ");
        assertThat(result.getLast().sourceMessageIds()).containsExactly(4L);
        assertThat(result.getFirst().sourceText()).contains("7,700원").doesNotContain("5,500원");
        assertThat(result.getLast().sourceText()).contains("5,500원").doesNotContain("7,700원");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가족", "형", "직장 동료"})
    @DisplayName("방문 장소를 함께 말한 본인 방문자 조건도 기존 유심 소유자를 바꾸지 않는다")
    void 매장_방문자_조건을_본인_소유자로_오인하지_않는다(String owner) {
        var result = groups(owner + " 유심 재발급 비용은요?",
                "그 유심은 본인이 매장에 방문할 때 준비물이 뭔가요?");

        assertThat(result).singleElement().satisfies(inquiry -> {
            assertThat(inquiry.title()).startsWith(owner.replace("직장 ", "") + " ");
            assertThat(inquiry.sourceMessageIds()).containsExactly(2L, 4L);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"그 유심은 본인이 가까운 매장으로 방문할 때 준비물이 뭔가요?",
        "그 유심은 본인이 직접 매장에서 재발급받을 때 준비물이 뭔가요?",
        "그 유심은 본인이 주말에 매장에 방문할 때 준비물이 뭔가요?",
        "그 유심 본인 매장 방문 준비물은요?", "그 유심은 본인의 신분증이 필요한가요?",
        "그 유심은 내 신분증을 지참해야 하나요?"})
    @DisplayName("장소·시점·소유격이 포함된 준비물과 방문자 조건도 기존 유심 문의에서 유지한다")
    void 방문_조건의_표현_변형을_유지한다(String followup) {
        assertThat(groups("형 유심 재발급 비용은요?", followup)).singleElement().satisfies(inquiry -> {
            assertThat(inquiry.title()).startsWith("형 ");
            assertThat(inquiry.sourceMessageIds()).containsExactly(2L, 4L);
            assertThat(inquiry.sourceText()).contains("7,700원", "신분증");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"큰형과 작은형", "친형과 사촌형", "가족과 동료", "가족과 본인"})
    @DisplayName("접속 조사로 나열한 여러 소유자를 마지막 한 사람으로 축소하지 않는다")
    void 접속_조사로_연결한_여러_소유자를_인식한다(String owners) {
        assertThat(ChatEndSummaryInquiryLinker.extractPerson(owners + " 유심 재발급 비용은요?"))
                .isEqualTo("MULTIPLE");
    }

    @Test
    @DisplayName("큰형과 작은형을 함께 문의한 원문에 작은형만의 제목을 붙이지 않는다")
    void 여러_소유자의_제목을_한_사람으로_축소하지_않는다() {
        assertThat(groups("큰형과 작은형 유심 재발급 비용은요?")).singleElement()
                .satisfies(inquiry -> assertThat(inquiry.title()).startsWith("큰형·작은형 "));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "친형과사촌형 | 친형·사촌형", "가족과 본인 | 가족·본인", "본인과가족 | 본인·가족",
        "큰형,작은형 | 큰형·작은형", "큰형·작은형 | 큰형·작은형"
    })
    @DisplayName("접속 조사·쉼표·가운뎃점으로 나열한 소유자를 언급한 순서대로 제목에 남긴다")
    void 복수_대상을_제목에_모두_보존한다(String owners, String title) {
        assertThat(groups(owners + " 유심 재발급 비용은요?")).singleElement()
                .satisfies(inquiry -> assertThat(inquiry.title()).startsWith(title + " "));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 | 내 가족 실물 유심 준비물은요?", "형 | 제 형 유심 준비물은요?",
        "직장 동료 | 저의 직장 동료 유심 준비물은요?"
    })
    @DisplayName("내 가족·제 형·저의 동료는 본인 소유자로 바꾸지 않고 명시한 다른 사람으로 유지한다")
    void 소유격_뒤의_가족_관계를_유지한다(String owner, String followup) {
        assertThat(groups(owner + " 유심 재발급 비용은요?", followup)).singleElement()
                .satisfies(inquiry -> assertThat(inquiry.title()).startsWith(owner.replace("직장 ", "") + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"그 유심 준비물은요?", "작은형 유심 준비물은요?", "본인 유심 준비물은요?"})
    @DisplayName("여러 사람의 공통 안내를 모호한 지시어나 특정 한 사람의 안내로 임의 연결하지 않는다")
    void 복수_대상_이후의_부분_참조를_구분한다(String followup) {
        var result = groups("큰형과 작은형 유심 재발급 비용은요?", followup);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().title()).startsWith("큰형·작은형 ");
        assertThat(result.getFirst().sourceMessageIds()).containsExactly(2L);
        assertThat(result.getLast().sourceMessageIds()).containsExactly(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"큰형과 작은형 유심 준비물은요?", "작은형과 큰형 유심 준비물은요?"})
    @DisplayName("같은 여러 대상을 다시 명시하면 순서가 바뀌어도 공통 문의로 묶는다")
    void 같은_복수_대상의_명시적인_이어묻기를_묶는다(String followup) {
        assertThat(groups("큰형과 작은형 유심 재발급 비용은요?", followup)).singleElement()
                .satisfies(inquiry -> {
                    assertThat(inquiry.title()).startsWith("큰형·작은형 ");
                    assertThat(inquiry.sourceMessageIds()).containsExactly(2L, 4L);
                });
    }

    @Test
    @DisplayName("여러 대상 중 일부만 같아도 다른 대상 집합이면 안내를 합치지 않는다")
    void 서로_다른_복수_대상을_분리한다() {
        var result = groups("가족과 동료 유심 재발급 비용은요?", "형과 동료 유심 준비물은요?");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().title()).startsWith("가족·동료 ");
        assertThat(result.getLast().title()).startsWith("형·동료 ");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "휴대폰이요 | 핸드폰 문의요 | 기기변경 조건 알려줘 | DEVICE",
        "가입 문의드려요 | 신규 가입이요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "청구서이요 | 청구 문의요 | 청구서 납부 방법 알려줘 | BILLING"
    })
    @DisplayName("지원하는 같은 주제의 별칭과 구체화는 새 경계 검사에서도 정상 해결한다")
    void 주제_별칭의_정상_대조군을_유지한다(String first, String middle, String last, String category) {
        var input = chain(first, middle, last, category);

        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L, 3L))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "청구서이요 | 청구 문의요 | 납부 방법 알려줘 | BILLING",
        "가입 문의드려요 | 신규 가입이요 | 재가입 서류 알려줘 | SUBSCRIBE",
        "할부 문의요 | 기기 문의요 | 기기변경 알려줘 | DEVICE"
    })
    @DisplayName("같은 분류 안의 다른 업무나 특정한 신규 조건을 별칭이라는 이유로 해결하지 않는다")
    void 주제_별칭의_다른_업무_대조군을_유지한다(String first, String middle, String last, String category) {
        assertThat(ChatEndSummaryResolution.isResolved(chain(first, middle, last, category), 1)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"아들", "딸", "부모님", "친구"})
    @DisplayName("대상 판정 공통화 후에도 다른 사람의 답변으로 원래 주제 확인을 해결하지 않는다")
    void 기존의_다른_사람_보호를_유지한다(String owner) {
        var input = chain("요금이요", "요금이요", owner + " 5G 요금제 알려줘", "PLAN");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryResolution.isResolved(input, 3)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L, 3L))).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 내 유심 재발급 준비물은요? | 2",
        "형 유심 재발급 비용은요? | 그 유심은 본인 신분증이 필요한가요? | 1",
        "큰형 유심 재발급 비용은요? | 작은형 유심 재발급 비용은요? | 2"
    })
    @DisplayName("이미 보완한 명시적 소유자와 준비물의 최소 정상 대조군은 유지한다")
    void 대상_경계의_정상_대조군을_유지한다(String first, String second, int expected) {
        assertThat(groups(first, second)).hasSize(expected);
    }

    @Test
    @DisplayName("기존 이름 목록으로 이미 인식하던 여러 소유자는 정상 대조군에서도 유지한다")
    void 기존_복수_소유자_대조군을_유지한다() {
        assertThat(ChatEndSummaryInquiryLinker.extractPerson("어머니와 아버지 유심 재발급 비용은요?"))
                .isEqualTo("MULTIPLE");
    }

    private List<ChatEndSummaryInquiryGrouper.Inquiry> groups(String... questions) {
        var messages = new ArrayList<ChatEndSummaryData.Message>();
        var requests = new ArrayList<ChatEndSummaryData.Request>();
        var body = new ObjectMapper().createObjectNode();
        body.putArray("topics");
        body.putArray("pendingItems");
        var guidance = body.putArray("guidance");
        for (int index = 0; index < questions.length; index++) {
            long id = index * 2L + 1;
            String answer = "유심 재발급 비용은 7,700원입니다.";
            if (index > 0) {
                answer = questions[index].contains("준비물") || questions[index].contains("신분증")
                        ? "유심 명의자의 신분증을 지참해 매장에 방문합니다." : "유심 재발급 비용은 5,500원입니다.";
            }
            messages.add(message(id, null, "USER", "QUESTION", questions[index], null));
            messages.add(message(id + 1, id, "ASSISTANT", "ANSWER", answer, "GROUNDED"));
            requests.add(new ChatEndSummaryData.Request(id, 1, questions[index], "DONE"));
            var item = guidance.addObject();
            item.put("text", answer);
            item.putArray("sourceMessageIds").add(id + 1);
        }
        return ChatEndSummaryInquiryGrouper.group(new ChatEndSummaryData.Input(1, messages.size(), 1L,
                messages, requests, Set.of(), List.of(), OutputMaskContext.empty()), body);
    }

    private ChatEndSummaryData.Input chain(String first, String middle, String last, String category) {
        var messages = List.of(
                message(1, null, "USER", "QUESTION", first, null),
                message(2, 1L, "ASSISTANT", "CLARIFICATION", ChatTopicClarification.MESSAGE, null),
                message(3, null, "USER", "QUESTION", middle, null),
                message(4, 3L, "ASSISTANT", "CLARIFICATION", ChatTopicClarification.MESSAGE, null),
                message(5, null, "USER", "QUESTION", last, null),
                message(6, 5L, "ASSISTANT", "ANSWER", "문의하신 서류와 절차를 안내합니다.", "GROUNDED"));
        return new ChatEndSummaryData.Input(1, 6, 1L, messages,
                List.of(new ChatEndSummaryData.Request(5, 1, last, "DONE", List.of(), 103L)), Set.of(1L, 3L),
                List.of(), OutputMaskContext.empty(), Map.of(5L, new Classification("FAQ", List.of(category))));
    }

    private ChatEndSummaryData.Message message(long id, Long reply, String role, String type, String text,
            String basis) {
        return new ChatEndSummaryData.Message(id, (int) id, reply, role, type, text, basis, "COMPLETED");
    }
}
