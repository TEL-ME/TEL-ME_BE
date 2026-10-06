package com.telme.consult.service;

import static com.telme.consult.dto.PlanChangeConditions.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.intent.dto.res.LlmFollowUpPayload.ResponseType;
import com.telme.intent.dto.res.LlmFollowUpPayload.Status;
import com.telme.intent.service.RuleBasedRoutingFallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class PlanChangeClarificationPolicyTest {
    static final String PERSONAL = "제가 지금 요금제를 바꿀 수 있나요?";
    static final FaqSearchResponse POLICY =
            new FaqSearchResponse(
                    357L,
                    "BILLING-0001",
                    "BILLING",
                    "요금제 바꾸는 거 한 달에 몇 번까지 되나요?",
                    "요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다."
                        + " 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.",
                    .7974,
                    1,
                    LocalDate.of(2026, 10, 1),
                    1,
                    "Q_A");
    private final DialogueService dialogue =
            new DialogueService(ClarificationTextGenerator.template());
    private final RuleBasedRoutingFallback fallback = new RuleBasedRoutingFallback();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "요금제 변경 기준이 뭐예요?",
                "요금제는 언제 변경할 수 있나요?",
                "요금제 변경 횟수를 알려주세요",
                "요금제 변경 조건을 경우별로 알려주세요",
                "제가 시니어 요금제로 바꿀 수 있나요?"
            })
    @DisplayName("일반 설명과 다른 자격 질문은 되묻지 않는다")
    void 일반_설명과_다른_자격_질문은_되묻지_않는다(String query) {
        assertThat(dialogue.assessFaq(1, query, Map.of(), List.of(POLICY))).isNull();
    }

    @Test
    @DisplayName("개인 질문은 한 조건씩 묻는다")
    void 개인_질문은_한_조건씩_묻는다() {
        var first = dialogue.assessFaq(1, PERSONAL, Map.of(), List.of(POLICY));
        assertThat(first.action()).isEqualTo(Action.ASK);
        assertThat(first.waitingField()).isEqualTo(JOINED);
        var second =
                dialogue.assessFaq(
                        1, PERSONAL, Map.of(JOINED, Condition.filled(NO)), List.of(POLICY));
        assertThat(second.waitingField()).isEqualTo(CHANGED);
        assertThat(second.conditions()).containsEntry(JOINED, Condition.filled(NO));
    }

    @ParameterizedTest
    @CsvSource({"예,아니요", "아니요,예", "아니요,아니요"})
    @DisplayName("제공한 조건으로 답변을 재개한다")
    void 제공한_조건으로_답변을_재개한다(String joined, String changed) {
        assertThat(
                        dialogue.assessFaq(
                                        1,
                                        PERSONAL,
                                        Map.of(
                                                JOINED,
                                                Condition.filled(joined),
                                                CHANGED,
                                                Condition.filled(changed)),
                                        List.of(POLICY))
                                .action())
                .isEqualTo(Action.PROCEED);
    }

    @Test
    @DisplayName("확인된 제한으로 다른 조건을 묻지 않는다")
    void 확인된_제한으로_다른_조건을_묻지_않는다() {
        assertThat(
                        dialogue.assessFaq(
                                        1, "이번 달에 가입했는데 요금제를 변경할 수 있나요?", Map.of(), List.of(POLICY))
                                .action())
                .isEqualTo(Action.PROCEED);
        assertThat(
                        dialogue.assessFaq(
                                        1,
                                        "이번 달에 이미 요금제를 변경했는데 제가 다시 바꿀 수 있나요?",
                                        Map.of(),
                                        List.of(POLICY))
                                .action())
                .isEqualTo(Action.PROCEED);
    }

    @Test
    @DisplayName("충분한 제한 근거에 다른 정책을 요구하지 않는다")
    void 충분한_제한_근거에_다른_정책을_요구하지_않는다() {
        var joinedOnly =
                new FaqSearchResponse(
                        399L,
                        "BILLING-0078",
                        "BILLING",
                        "가입한 달에 바로 요금제를 바꿀 수 있나요?",
                        "가입한 달에는 변경할 수 없습니다. 다음 달부터 변경하실 수 있습니다.",
                        .7371,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        var decision =
                dialogue.assessFaq(
                        1, "이번 달에 가입했는데 제가 요금제를 바꿀 수 있나요?", Map.of(), List.of(joinedOnly));
        assertThat(decision.action()).isEqualTo(Action.PROCEED);
        assertThat(decision.conditions()).doesNotContainKey(CHANGED);
    }

    @Test
    @DisplayName("부족하거나 질문에만 있는 정책은 사용하지 않는다")
    void 부족하거나_질문에만_있는_정책은_사용하지_않는다() {
        var questionOnly =
                new FaqSearchResponse(
                        1L,
                        null,
                        "BILLING",
                        POLICY.answer(),
                        "앱에서 확인하세요.",
                        .99,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        assertThat(dialogue.assessFaq(1, PERSONAL, Map.of(), List.of(questionOnly))).isNull();
        assertThat(dialogue.assessFaq(1, PERSONAL, Map.of(), List.of())).isNull();
    }

    @Test
    @DisplayName("월별 언급만으로 제한 정책을 확정하지 않는다")
    void 월별_언급만으로_제한_정책을_확정하지_않는다() {
        var source =
                new FaqSearchResponse(
                        1L,
                        null,
                        "BILLING",
                        "요금제 변경 기준은 무엇인가요?",
                        "가입한 달에는 변경할 수 없습니다. 월 1회 제한은 없습니다.",
                        .99,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        assertThat(dialogue.assessFaq(1, PERSONAL, Map.of(), List.of(source))).isNull();
    }

    @Test
    @DisplayName("거절은 값을 만들지 않고 질문을 종료한다")
    void 거절은_값을_만들지_않고_질문을_종료한다() {
        var result =
                dialogue.assessFaq(
                        1, PERSONAL, Map.of(JOINED, Condition.declined()), List.of(POLICY));
        assertThat(result.action()).isEqualTo(Action.PROCEED);
        assertThat(result.conditions()).doesNotContainKey(CHANGED);
    }

    @Test
    @DisplayName("최신 정정을 원문보다 우선한다")
    void 최신_정정을_원문보다_우선한다() {
        var result =
                dialogue.assessFaq(
                        1,
                        "이번 달에 가입했는데 제가 요금제를 바꿀 수 있나요?",
                        Map.of(JOINED, Condition.filled(NO)),
                        List.of(POLICY));
        assertThat(result.waitingField()).isEqualTo(CHANGED);
        assertThat(result.conditions().get(JOINED).value()).isEqualTo(NO);
    }

    @ParameterizedTest
    @CsvSource({
        "지난달에 가입했어요,joinedThisMonth,아니요",
        "가입은 지난달이에요,joinedThisMonth,아니요",
        "이번 달에 가입했어요,joinedThisMonth,예",
        "이번 달에는 아직 안 바꿨어요,changedThisMonth,아니요",
        "이번 달 요금제 변경은 안 했어요,changedThisMonth,아니요",
        "이번 달에 이미 요금제를 변경했어요,changedThisMonth,예",
        "네,joinedThisMonth,예",
        "아니요,changedThisMonth,아니요"
    })
    @DisplayName("명확한 후속 조건을 추출한다")
    void 명확한_후속_조건을_추출한다(String reply, String key, String value) {
        assertThat(extract(reply, Set.of(key))).containsEntry(key, value);
        assertThat(fallback.classifyFollowUp(reply, Set.of(key)).conditions())
                .anySatisfy(
                        condition -> {
                            assertThat(condition.key()).isEqualTo(key);
                            assertThat(condition.value()).isEqualTo(value);
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"잠깐만요", "나중에요", "음", "가정하면", "가입하면 가능한가요?"})
    @DisplayName("애매한 답변을 값으로 저장하지 않는다")
    void 애매한_답변을_값으로_저장하지_않는다(String reply) {
        assertThat(extract(reply, Set.of(JOINED))).isEmpty();
        assertThat(fallback.classifyFollowUp(reply, Set.of(JOINED)).conditions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"모르겠어요", "알려주기 싫어요"})
    @DisplayName("거절과 모름을 아니요로 변환하지 않는다")
    void 거절과_모름을_아니요로_변환하지_않는다(String reply) {
        var result = fallback.classifyFollowUp(reply, Set.of(JOINED));
        assertThat(result.conditions())
                .singleElement()
                .satisfies(
                        condition -> {
                            assertThat(condition.key()).isEqualTo(JOINED);
                            assertThat(condition.status()).isEqualTo(Status.DECLINED);
                        });
    }

    @Test
    @DisplayName("새 질문으로 옛 조건을 채우지 않는다")
    void 새_질문으로_옛_조건을_채우지_않는다() {
        var result = fallback.classifyFollowUp("유심 재발급 비용을 알려주세요", Set.of(JOINED));
        assertThat(result.responseType()).isEqualTo(ResponseType.NEW_QUESTION);
        assertThat(result.conditions()).isEmpty();
    }

    @Test
    @DisplayName("정정한 조건에 따라 질문을 유지하거나 종료한다")
    void 정정한_조건에_따라_질문을_유지하거나_종료한다() {
        var input =
                new DialogueInput(
                        1L,
                        DialogueInput.Purpose.GENERAL_FAQ,
                        Map.of(JOINED, Condition.filled(NO), CHANGED, Condition.pending()),
                        Map.of(JOINED, Condition.filled(NO)),
                        DialogueInput.LocationStatus.MISSING);
        assertThat(dialogue.assess(input).waitingField()).isEqualTo(CHANGED);
        var corrected =
                new DialogueInput(
                        1L,
                        DialogueInput.Purpose.GENERAL_FAQ,
                        input.previousConditions(),
                        Map.of(JOINED, Condition.filled(YES)),
                        DialogueInput.LocationStatus.MISSING);
        assertThat(dialogue.assess(corrected).action()).isEqualTo(Action.PROCEED);
    }

    @Test
    @DisplayName("정책 근거를 공유하고 검색 개수를 유지한다")
    void 정책_근거를_공유하고_검색_개수를_유지한다() {
        var requests = new ArrayList<FaqSearchRequest>();
        var generation = new AtomicInteger();
        var provider =
                new FaqSearchAnswerProvider(
                        request -> {
                            requests.add(request);
                            return List.of(POLICY);
                        },
                        (input, results) -> {
                            generation.incrementAndGet();
                            return GeneratedAnswer.withoutSources(
                                    new ChatAnswer(
                                            ChatMessage.MessageType.ANSWER,
                                            "안내",
                                            null,
                                            List.of(),
                                            null));
                        },
                        ExecutionTrace.noop(),
                        dialogue,
                        new ConfirmedConditionConverter());
        var prepared =
                provider.prepare(
                        new AnswerInput(
                                1,
                                1,
                                1,
                                DialogueInput.Purpose.GENERAL_FAQ,
                                PERSONAL,
                                PERSONAL,
                                Map.of()),
                        Map.of());
        assertThat(prepared.decision().action()).isEqualTo(Action.ASK);
        assertThat(generation).hasValue(0);
        assertThat(requests)
                .singleElement()
                .satisfies(request -> assertThat(request.topK()).isEqualTo(3));
    }

    @Test
    @DisplayName("보완 검색 오류를 근거 없음으로 바꾸지 않는다")
    void 보완_검색_오류를_근거_없음으로_바꾸지_않는다() {
        var calls = new AtomicInteger();
        var partial =
                new FaqSearchResponse(
                        1445L,
                        "BILLING-0085",
                        "BILLING",
                        "요금제를 두 번 바꿀 수 있나요?",
                        "월 1회만 변경할 수 있습니다.",
                        .9,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        var provider =
                new FaqSearchAnswerProvider(
                        request -> {
                            if (calls.incrementAndGet() == 1) return List.of(partial);
                            throw new IllegalStateException("검색 실패");
                        },
                        (input, results) -> {
                            throw new AssertionError();
                        },
                        ExecutionTrace.noop(),
                        dialogue,
                        new ConfirmedConditionConverter());
        assertThatThrownBy(
                        () ->
                                provider.prepare(
                                        new AnswerInput(
                                                1,
                                                1,
                                                1,
                                                DialogueInput.Purpose.GENERAL_FAQ,
                                                PERSONAL,
                                                PERSONAL,
                                                Map.of()),
                                        Map.of()))
                .isInstanceOf(FaqAnswerSearchException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "저는 언제 요금제를 바꿀 수 있나요?",
                "이번 달에 가입했는데 언제 요금제를 바꿀 수 있나요?",
                "제가 요금제를 변경할 조건이 되나요?"
            })
    @DisplayName("개인 가능 시점과 조건 질문을 처리한다")
    void 개인_가능_시점과_조건_질문을_처리한다(String query) {
        assertThat(isPersonalQuestion(query)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"이번 달인지 지난달인지 모르겠어요", "친구가 이번 달에 가입했어요", "이번 달에 가입했다면 변경할 수 있나요?", "네, 아니요"})
    @DisplayName("불확실한 가정과 타인 조건을 배제한다")
    void 불확실한_가정과_타인_조건을_배제한다(String reply) {
        assertThat(extract(reply, Set.of(JOINED))).isEmpty();
    }

    @Test
    @DisplayName("명확한 정정과 확인된 제한을 유지한다")
    void 명확한_정정과_확인된_제한을_유지한다() {
        assertThat(extract("이번 달 가입했어요. 아니, 지난달이에요.", Set.of(JOINED))).containsEntry(JOINED, NO);
        var result =
                dialogue.assessFaq(
                        1,
                        "이번 달 가입했어요. 이번 달 변경 여부는 모르겠어요. 지금 요금제를 바꿀 수 있나요?",
                        Map.of(),
                        List.of(POLICY));
        assertThat(result.action()).isEqualTo(Action.PROCEED);
        assertThat(result.conditions()).containsEntry(JOINED, Condition.filled(YES));
        assertThat(
                        PlanChangeClarificationPolicy.policyCompletion(
                                result.conditions(), List.of(POLICY)))
                .contains("이번 달에 가입하셨으므로");
    }

    @Test
    @DisplayName("정책 근거 없이 개인 결론을 만들지 않는다")
    void 정책_근거_없이_개인_결론을_만들지_않는다() {
        assertThat(
                        PlanChangeClarificationPolicy.policyCompletion(
                                Map.of(JOINED, Condition.filled(YES)), List.of()))
                .isNull();
    }

    @Test
    @DisplayName("미확인과 확정 조건의 안내를 구분한다")
    void 미확인과_확정_조건의_안내를_구분한다() {
        assertThat(
                        PlanChangeClarificationPolicy.policyCompletion(
                                Map.of(JOINED, Condition.declined()), List.of(POLICY)))
                .contains("개인별 변경 가능 여부는 확정할 수 없습니다");
        assertThat(
                        PlanChangeClarificationPolicy.policyCompletion(
                                Map.of(JOINED, Condition.filled(NO), CHANGED, Condition.filled(NO)),
                                List.of(POLICY)))
                .contains("두 기준에 한정한 안내");
    }

    @Test
    @DisplayName("변경 부정과 한글 횟수 근거를 보존한다")
    void 변경_부정과_한글_횟수_근거를_보존한다() {
        assertThat(extract("지난달에 가입했고 이번 달에는 요금제를 바꾼 적이 없어요. 지금 요금제를 바꿀 수 있나요?", Set.of()))
                .containsEntry(JOINED, NO)
                .containsEntry(CHANGED, NO);
        var korean =
                new FaqSearchResponse(
                        395L,
                        "BILLING-0057",
                        "BILLING",
                        "요금제는 어떻게 바꿔요",
                        "한 달에 한 번만 바꾸실 수 있습니다.",
                        .9,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        String answer =
                PlanChangeClarificationPolicy.policyCompletion(
                        Map.of(CHANGED, Condition.filled(YES)), List.of(korean));
        assertThat(answer).contains("한 달에 한 번").doesNotContain("1회");
        assertThat(
                        new com.telme.rag.service.AnswerGuard()
                                .applyEvidencePolicy(answer, korean.answer(), PERSONAL))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("다음 달 안내에는 명시된 근거가 필요하다")
    void 다음_달_안내에는_명시된_근거가_필요하다() {
        String answer =
                PlanChangeClarificationPolicy.policyCompletion(
                        Map.of(JOINED, Condition.filled(YES)), List.of(POLICY));
        assertThat(answer).contains("다음 달부터").doesNotContain("30일", "한 달이 지나", "다음 달 초");
        var noDate =
                new FaqSearchResponse(
                        1L,
                        null,
                        "BILLING",
                        "요금제 가입월 변경 기준",
                        "가입한 달에는 변경할 수 없습니다.",
                        .9,
                        1,
                        LocalDate.now(),
                        1,
                        null);
        assertThat(
                        PlanChangeClarificationPolicy.policyCompletion(
                                Map.of(JOINED, Condition.filled(YES)), List.of(noDate)))
                .doesNotContain("다음 달");
    }

    @Test
    @DisplayName("타인과 가정 질문으로 본인 상담을 열지 않는다")
    void 타인과_가정_질문으로_본인_상담을_열지_않는다() {
        assertThat(isPersonalQuestion("만약 이번 달에 가입했다면 지금 요금제를 바꿀 수 있나요?")).isFalse();
        assertThat(isPersonalQuestion("친구가 이번 달에 가입했는데 요금제를 바꿀 수 있나요?")).isFalse();
        assertThat(extract("네", Set.of(JOINED, CHANGED))).isEmpty();
    }
}
