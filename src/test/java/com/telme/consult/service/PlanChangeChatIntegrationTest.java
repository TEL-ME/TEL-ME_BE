package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.telme.consult.dto.PlanChangeConditions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.service.AnswerPromptTemplates;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 검색·외부 모델만 대체한다. 실제 라우팅·후속 분석·처리기·DB·Guard·SSE·조회 API를 통과한다. */
@SpringBootTest(properties = {"llm.provider=ollama", "llm.model=plan-change-test", "telme.consult.llm-enabled=false", "rag.evidence-check.enabled=false", "llm.retry.wait-duration=0ms"})
@AutoConfigureMockMvc
class PlanChangeChatIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired ChatProcessingPort processor;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatEmitterRegistry emitters;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;
    long userId;
    long sessionId;
    long executionId;
    int sequence;
    CaptureEmitter emitter;
    List<LlmRequest> generations = new ArrayList<>();
    Consumer<LlmStreamHandler> failure;
    String currentText;
    int explanationAnswers;

    @BeforeEach
    void setup() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'조건 확인 테스트') RETURNING user_id", Long.class, "plan-" + UUID.randomUUID() + "@example.com");
        sessionId = newSession();
        when(search.search(any())).thenReturn(List.of(PlanChangeClarificationPolicyTest.POLICY));
        // 첫 라우팅은 유효한 외부 모델 응답을 재생한다. 후속 입력은 실제 규칙·검증을 거친다.
        when(model.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            return request.systemPrompt().contains("직전 턴") ? "{}"
                    : mapper.writeValueAsString(new com.telme.intent.service.RuleBasedRoutingFallback().classify(currentText));
        });
        doAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            LlmStreamHandler stream = invocation.getArgument(1);
            generations.add(request);
            if (failure != null) { failure.accept(stream); return null; }
            String text;
            if (request.userPrompt().contains("이번 달 가입 여부: 예")) {
                text = "가입한 달에는 변경할 수 없습니다. 다음 달부터 변경하실 수 있습니다.";
            } else if (request.userPrompt().contains("이번 달 요금제 변경 이력: 예")) {
                text = "이번 달에 이미 변경했다면 다음 달에 가능합니다. 요금제 변경은 월 1회입니다.";
            } else if (request.userPrompt().contains("이번 달 가입 여부: 아니요") && request.userPrompt().contains("이번 달 요금제 변경 이력: 아니요")) {
                text = "가입월이 아니고 이번 달에 아직 변경하지 않으셨다면 월 1회 기준으로 변경할 수 있습니다.";
            } else {
                text = "가입한 달에는 변경할 수 없고, 다음 달부터 월 1회 변경할 수 있습니다.";
            }
            stream.onToken(text);
            assertThat(emitter.tokens).isEmpty();
            stream.onComplete();
            assertThat(emitter.tokens).isEmpty();
            return null;
        }).when(model).stream(any(), any());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN (SELECT consult_request_id FROM consult_requests WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id=?))", userId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void missingConditionsResumeSameConsultationAndStoreTransmitQuerySameFinalAnswer() throws Exception {
        assertThat(context.getBeansOfType(ChatProcessingPort.class)).hasSize(1);
        assertThat(processor).isInstanceOf(ConsultChatProcessingService.class);
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long consultation = requestId();
        assertClarification(JOINED);
        assertThat(generations).isEmpty();
        turn("지난달에 가입했어요");
        assertClarification(CHANGED);
        assertThat(requestId()).isEqualTo(consultation);
        turn("이번 달에는 아직 안 바꿨어요");
        assertThat(requestId()).isEqualTo(consultation);
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(generations).hasSize(1);
        assertThat(generations.getFirst().userPrompt()).contains(PlanChangeClarificationPolicyTest.PERSONAL, "이번 달 가입 여부: 아니요", "이번 달 요금제 변경 이력: 아니요");
        assertFinal();
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultation)).isEqualTo("DONE");
    }

    @Test
    void generalCriteriaAndCaseGuidanceNeverAsk() throws Exception {
        turn("요금제 변경 기준이 뭐예요?");
        assertThat(outputType()).isEqualTo("ANSWER");
        assertFinal();
        turn("요금제 변경 조건을 경우별로 알려주세요");
        assertThat(outputType()).isEqualTo("ANSWER");
        assertFinal();
        assertThat(clarificationCount()).isZero();
    }

    @Test
    void alreadyProvidedBlockingConditionAnswersWithoutAnotherQuestion() throws Exception {
        turn("이번 달에 가입했는데 제가 요금제를 바꿀 수 있나요?");
        assertThat(clarificationCount()).isZero();
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다");
        assertFinal();
    }

    @Test
    void alreadyProvidedBothConditionsDoNotAsk() throws Exception {
        turn("지난달에 가입했고 이번 달에는 아직 안 바꿨어요. 제가 지금 요금제를 바꿀 수 있나요?");
        assertThat(clarificationCount()).isZero();
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertFinal();
    }

    @Test
    void twoConditionsInOneFollowupAreRetainedEvenWhenModelOnlyReturnsWaitingCondition() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long consultation = requestId();
        assertClarification(JOINED);
        // 모델이 현재 질문한 가입월만 반환해도 사용자 발화의 다른 조건을 버리지 않는다.
        org.mockito.Mockito.doReturn("{\"conditions\":[{\"key\":\"" + JOINED
                + "\",\"status\":\"FILLED\",\"value\":\"아니요\"}]}").when(model).generate(any());
        turn("지난달에 가입했고 이번 달에는 아직 안 바꿨어요");
        assertThat(requestId()).isEqualTo(consultation);
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(generations).hasSize(1);
        assertThat(generations.getFirst().userPrompt()).contains("이번 달 가입 여부: 아니요", "이번 달 요금제 변경 이력: 아니요");
        assertThat(content()).contains("변경이 가능합니다", "두 기준에 한정한 안내");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultation)).isEqualTo("DONE");
        assertFinal();
    }

    @Test
    void originalConditionIsReusedAndLaterCorrectionIsNotOverwrittenByOriginalValue() throws Exception {
        turn("지난달에 가입했어요. 제가 지금 요금제를 바꿀 수 있나요?");
        long consultation = requestId();
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertClarification(CHANGED);
        turn("정정할게요. 가입은 이번 달이에요. 이번 달에는 아직 안 바꿨어요");
        assertThat(requestId()).isEqualTo(consultation);
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(generations).hasSize(1);
        assertThat(generations.getFirst().userPrompt()).contains("이번 달 가입 여부: 예", "이번 달 요금제 변경 이력: 아니요")
                .doesNotContain("이번 달 가입 여부: 아니요");
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultation)).isEqualTo("DONE");
        assertFinal();
    }

    @Test
    void correctionCanChangeAnotherConditionAndFinishBlockingAdvice() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        assertClarification(CHANGED);
        turn("정정할게요. 가입은 이번 달이에요");
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(clarificationCount()).isEqualTo(2);
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다");
        assertFinal();
    }

    @Test
    void correctionOfOtherConditionKeepsExistingQuestionWithoutDuplication() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        turn("정정할게요. 가입은 저번달이에요");
        assertThat(clarificationCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT output_message_id IS NULL FROM chat_executions WHERE execution_id=?", Boolean.class, executionId)).isTrue();
        turn("아니요");
        assertFinal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"모르겠어요", "잘 모르겠어요", "알려주기 싫어요", "알려주고 싶지 않아요"})
    void unknownOrRefusalEndsWithConditionalGuidanceInsteadOfAnotherQuestion(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn(reply);
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, requestId(), JOINED)).isEqualTo("DECLINED");
        assertThat(generations.getFirst().userPrompt()).contains("개인별 변경 가능과 변경 불가 모두 확정하지");
        assertFinal();
    }

    @Test
    void deferredReplyKeepsWaitingAndDoesNotSendOrSaveAnotherQuestion() {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("나중에요");
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(generations).isEmpty();
        assertThat(emitter.tokens).isEmpty();
        assertThat(emitter.names).containsExactly("complete");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, requestId())).isEqualTo("WAITING_CONDITION");
    }

    @Test
    void newQuestionCancelsOldPlanWaitingAndDoesNotApplyOldConditions() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long old = requestId();
        turn("유심 재발급 비용을 알려주세요");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, old)).isEqualTo("CANCELLED");
        assertThat(requestId()).isNotEqualTo(old);
        assertThat(generations.getFirst().userPrompt()).doesNotContain("이번 달 가입 여부:");
        assertFinal();
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        assertClarification(JOINED);
        assertThat(requestId()).isNotEqualTo(old);
    }

    @Test
    void storeLocationFlowStillResumesSameStoreConsultation() {
        turn("가까운 매장 찾아줘");
        assertClarification("location");
        long old = requestId();
        turn("강남역이요");
        assertThat(requestId()).isEqualTo(old);
        assertThat(outputType()).isEqualTo("ANSWER");
        assertThat(condition("location")).isEqualTo("강남역");
        assertThat(generations).isEmpty();
    }

    @Test
    void noSearchOrMissingPolicyReturnsNoEvidenceWithoutAsking() throws Exception {
        when(search.search(any())).thenReturn(List.of());
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        assertThat(clarificationCount()).isZero();
        assertThat(content()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(generations).isEmpty();
        assertFinal();
    }

    @Test
    void incompletePolicyIsSupplementedOnceAndSharedWithoutGenerationOnAsk() {
        var partial = new FaqSearchResponse(1451L, "BILLING-0092", "BILLING", "요금제 이번 달에 바꿀 수 있나요", "이번 달에 아직 안 바꾸셨으면 됩니다. 한 달에 한 번입니다.", .8951, 1, LocalDate.of(2026, 10, 1), 1, "QUESTION_ONLY");
        when(search.search(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, com.telme.faq.dto.req.FaqSearchRequest.class);
            assertThat(request.topK()).isEqualTo(3);
            return request.query().equals(PlanChangeClarificationPolicy.POLICY_QUERY) ? List.of(PlanChangeClarificationPolicyTest.POLICY) : List.of(partial);
        });
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        assertClarification(JOINED);
        org.mockito.Mockito.verify(search, org.mockito.Mockito.times(2)).search(any());
        assertThat(generations).isEmpty();
    }

    @Test
    void searchFailureIsFailureInsteadOfClarification() {
        when(search.search(any())).thenThrow(new IllegalStateException("테스트 검색 실패"));
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        assertThat(executionStatus()).isEqualTo("FAILED");
        assertThat(clarificationCount()).isZero();
        assertThat(emitter.names).containsExactly("error");
        assertThat(emitter.tokens).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIMEOUT", "CONNECTION_FAILED", "CANCELLED"})
    void generationFailureNeverTurnsIntoClarificationOrStoresPartialAnswer(String kind) {
        failure = stream -> { stream.onToken("검증되지 않은 문장"); stream.onError(kind.equals("CANCELLED") ? new LlmStreamCancelledException() : new GeneralException(LlmErrorCode.valueOf(kind))); };
        turn("지난달에 가입했고 이번 달에는 아직 안 바꿨어요. 제가 지금 요금제를 바꿀 수 있나요?");
        assertThat(executionStatus()).isEqualTo(kind.equals("CANCELLED") ? "CANCELLED" : "FAILED");
        assertThat(clarificationCount()).isZero();
        assertThat(emitter.tokens).isEmpty();
        assertThat(content()).isNull();
    }

    @Test
    void sessionsNeverShareConditions() {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long first = sessionId;
        turn("지난달에 가입했어요");
        sessionId = newSession();
        sequence = 0;
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        assertClarification(JOINED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions c JOIN consult_requests r USING(consult_request_id) WHERE r.session_id=? AND c.status='FILLED'", Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions c JOIN consult_requests r USING(consult_request_id) WHERE r.session_id=? AND c.condition_key=? AND c.condition_value=?", Integer.class, first, JOINED, NO)).isEqualTo(1);
    }

    @Test
    void personalWhenUsesConditionsAndProvidedJoinedMonthDoesNotAsk() throws Exception {
        turn("저는 언제 요금제를 바꿀 수 있나요?");
        assertClarification(JOINED);
        turn("이번 달에 가입했어요");
        assertFinal();
        assertThat(content()).contains("이번 달에 가입하셨으므로");
    }

    @Test
    void latestCorrectionWithinOneReplyWins() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("이번 달 가입했어요. 아니, 지난달이에요.");
        assertClarification(CHANGED);
        assertThat(condition(JOINED)).isEqualTo(NO);
        turn("아니요");
        assertFinal();
    }

    @Test
    void repeatedUnclearRepliesEndWithoutGuessOrDuplicateQuestion() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("이번 달인지 지난달인지 애매해요");
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(clarificationCount()).isEqualTo(1);
        turn("나중에요");
        turn("가입은 지난달이에요");
        assertClarification(CHANGED);
        turn("애매해요");
        turn("애매해서 답하기 어려워요");
        assertThat(clarificationCount()).isEqualTo(2);
        assertThat(condition(CHANGED)).isNull();
        assertThat(content()).contains("개인별 변경 가능 여부는 확정할 수 없습니다");
        assertFinal();
    }

    @Test
    void unknownResponseCannotExposeOrSaveModelPersonalAssertion() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        failure = stream -> { stream.onToken("현재 달에 요금제 변경은 불가능합니다. 다음 달 초에 신청하시면 됩니다."); stream.onComplete(); };
        turn("잘 모르겠어요");
        assertThat(content()).doesNotContain("현재 달", "다음 달 초").contains("개인별 변경 가능 여부는 확정할 수 없습니다");
        assertFinal();
    }

    @Test
    void knownChangeLimitRetainsAnswerEvenWhenModelRefuses() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        failure = stream -> { stream.onToken("안내드릴 수 있는 정보가 없습니다. 이번 달에는 변경이 어렵습니다."); stream.onComplete(); };
        turn("이번 달에 이미 변경했어요");
        assertThat(content()).contains("월 1회", "이번 달 추가 변경은 어렵습니다").doesNotContain("정보가 없습니다");
        assertFinal();
    }

    @Test
    void elapsedTimeInventedByModelIsNeverDeliveredForKnownSignupMonth() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        failure = stream -> { stream.onToken("가입한 지 한 달이 지나면 가능합니다. 30일 후에 변경하세요."); stream.onComplete(); };
        turn("이번 달에 가입했어요");
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다").doesNotContain("30일", "한 달이 지나면");
        assertFinal();
    }

    @Test
    void knownConditionCanBecomeUnknownWhileAnotherQuestionIsPending() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        turn("정정할게요. 가입한 달은 모르겠어요");
        assertThat(condition(JOINED)).isNull();
        assertThat(content()).contains("개인별 변경 가능 여부는 확정할 수 없습니다");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, requestId())).isEqualTo("DONE");
        assertFinal();
    }

    @Test
    void negativeChangeHistoryInFullQuestionDoesNotBecomeAlreadyChanged() throws Exception {
        turn("지난달에 가입했고 이번 달에는 요금제를 바꾼 적이 없어요. 지금 요금제를 바꿀 수 있나요?");
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(clarificationCount()).isZero();
        assertThat(content()).contains("변경이 가능합니다", "두 기준에 한정한 안내");
        assertFinal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"네", "네, 고마워요", "네 고마워요"})
    void telme104AcknowledgementAppliesOnlyToTheAskedCondition(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long existing = requestId();
        turn(reply);
        assertThat(requestId()).isEqualTo(existing);
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", Integer.class, existing, CHANGED)).isZero();
        assertThat(clarificationCount()).isEqualTo(1);
        assertFinal();
    }

    @Test
    void telme104BarePastMonthUsesAskedQuestionAndThenBareNoUsesChangeHistory() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long existing = requestId();
        turn("지난달이요");
        assertThat(requestId()).isEqualTo(existing);
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertClarification(CHANGED);
        turn("아니요");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertFinal();
    }

    @Test
    void telme104CommaSeparatedTwoConditionsDoNotDiscardUnaskedCondition() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했고, 이번 달에는 아직 요금제를 안 바꿨어요.");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(clarificationCount()).isEqualTo(1);
        assertFinal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"이번 달에 가입했어요. 지난달에 가입했어요", "이번 달에 가입했어요, 지난달에 가입했어요", "이번 달에 가입한 것 같아요"})
    void telme104UnclearContradictionNeverFillsEitherFlagAndAllowsClearReply(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn(reply);
        assertThat(condition(JOINED)).isNull();
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(emitter.tokens).isEmpty();
        turn("지난달이요");
        assertClarification(CHANGED);
        turn("아니요");
        assertFinal();
    }

    @Test
    void telme104ExplicitCorrectionOfOtherConditionCompletesWithoutRepeatedQuestion() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        assertClarification(CHANGED);
        turn("아니, 이번 달에 가입했어요");
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(clarificationCount()).isEqualTo(2);
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다");
        assertFinal();
    }

    @Test
    void telme104UncertainConflictDoesNotOverwriteKnownOtherCondition() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        long existing = requestId();
        turn("이번 달에 가입한 것 같아요");
        assertThat(requestId()).isEqualTo(existing);
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isNull();
        assertThat(clarificationCount()).isEqualTo(2);
        assertThat(emitter.names).containsExactly("complete");
        turn("아니요");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertFinal();
    }

    @Test
    void telme104UnknownCorrectionAndKnownOtherFlagAreBothSaved() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달에 가입했어요");
        turn("가입한 달은 모르겠어요. 이번 달에는 아직 안 바꿨어요");
        assertThat(condition(JOINED)).isNull();
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertThat(content()).contains("개인별 변경 가능 여부는 확정할 수 없습니다");
        assertFinal();
    }

    @Test
    void telme104MixedOwnAndFriendStatementKeepsOnlyOwnMonth() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("저는 지난달에 가입했고 친구는 이번 달이에요");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertClarification(CHANGED);
        turn("아니요");
        assertFinal();
    }

    @Test
    void telme104ExplanationRequestsKeepSameQuestionWithoutConsumingFailureLimit() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long existing = requestId();
        for (int n=0; n<2; n++) {
            turn("가입한 달이 무슨 뜻이에요?");
            assertExplanation("달력상의 달");
            assertThat(requestId()).isEqualTo(existing);
            assertThat(condition(JOINED)).isNull();
            assertThat(emitter.names).containsExactly("complete");
        }
        assertThat(clarificationCount()).isEqualTo(1);
        turn("지난달이요");
        assertClarification(CHANGED);
        turn("아니요");
        assertFinal();
    }

    @Test
    void telme104MixedNewRequestCancelsOldPlanWithoutConsumingAcknowledgement() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long old = requestId();
        turn("네, 유심 비용도 알려주세요");
        long replacement = requestId();
        assertThat(replacement).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, old)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT condition_value FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, old, JOINED)).isNull();
        assertFinal();
        turn("네");
        assertThat(requestId()).isEqualTo(replacement);
        assertThat(jdbc.queryForObject("SELECT condition_value FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, old, JOINED)).isNull();
        assertMetadataCompletionMatchesHistory();
    }

    @ParameterizedTest
    @ValueSource(strings = {"네", "아니요"})
    void telme104NoWaitingQuestionDoesNotResumeCompletedConsultation(String reply) throws Exception {
        turn("이번 달에 가입했는데 제가 요금제를 바꿀 수 있나요?");
        long old = requestId();
        turn(reply);
        assertThat(requestId()).isEqualTo(old);
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, old)).isEqualTo("DONE");
        assertMetadataCompletionMatchesHistory();
    }

    @Test
    void telme104AmbiguousPendingCandidatesFailWithoutAnyConditionUpdate() {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long old = requestId();
        long another = jdbc.queryForObject("INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status) SELECT session_id,origin_message_id,subquery_order+1,intent,query_text,status FROM consult_requests WHERE consult_request_id=? RETURNING consult_request_id", Long.class, old);
        jdbc.update("INSERT INTO consult_conditions(consult_request_id,condition_key,status,asked_message_id) SELECT ?,condition_key,status,asked_message_id FROM consult_conditions WHERE consult_request_id=?", another, old);
        turn("네");
        assertThat(executionStatus()).isEqualTo("FAILED");
        assertThat(emitter.tokens).isEmpty();
        assertThat(emitter.names).containsExactly("error");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions WHERE consult_request_id IN (?,?) AND condition_value IS NOT NULL", Integer.class, old, another)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE consult_request_id IN (?,?) AND status='WAITING_CONDITION'", Integer.class, old, another)).isEqualTo(2);
    }

    @Test
    void telme104StoreModelCannotInventLocationAndExistingLocationFlowResumes() {
        turn("가까운 매장 찾아줘");
        long old = requestId();
        org.mockito.Mockito.doReturn("{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}").when(model).generate(any());
        turn("네");
        assertThat(condition("location")).isNull();
        assertThat(emitter.tokens).isEmpty();
        turn("강남역이요");
        assertThat(requestId()).isEqualTo(old);
        assertThat(condition("location")).isEqualTo("강남역");
        assertThat(outputType()).isEqualTo("ANSWER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"친구는 가입한 달을 모르겠어요", "만약 가입한 달을 모르면 어떻게 돼요?"})
    void telme104OtherPersonsUnknownDoesNotEndOwnConsultation(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long existing = requestId();
        turn(reply);
        assertThat(requestId()).isEqualTo(existing);
        assertThat(condition(JOINED)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, existing, JOINED)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, existing)).isEqualTo("WAITING_CONDITION");
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(generations).isEmpty();
        assertThat(emitter.tokens).isEmpty();
        turn("지난달이요");
        assertClarification(CHANGED);
        turn("아니요");
        assertFinal();
    }

    @Test
    void telme104OwnMonthAndFriendsUnknownHistoryAskOnlyOwnMissingHistory() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("저는 지난달에 가입했고 친구는 이번 달 변경 이력을 모르겠어요");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, requestId(), CHANGED)).isEqualTo("PENDING");
        assertClarification(CHANGED);
        assertThat(generations).isEmpty();
        turn("아니요");
        assertThat(condition(CHANGED)).isEqualTo(NO);
        assertFinal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입한 달은 모르겠어요. 아니, 지난달에 가입했어요", "가입한 달은 모르겠어요. 아니, 지난달이에요"})
    void telme104LatestKnownCorrectionAfterUnknownResumesSameConsultation(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long existing = requestId();
        turn(reply);
        assertThat(requestId()).isEqualTo(existing);
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, existing, JOINED)).isEqualTo("FILLED");
        assertClarification(CHANGED);
        turn("아니요");
        assertFinal();
    }

    private void assertMetadataCompletionMatchesHistory() throws Exception {
        assertThat(emitter.tokens).isEmpty();
        assertThat(emitter.names).containsExactly("complete");
        long storedId = jdbc.queryForObject("SELECT output_message_id FROM chat_executions WHERE execution_id=?", Long.class, executionId);
        assertThat(emitter.completed.outputMessage().messageId()).isEqualTo(storedId);
        var identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        var response = mvc.perform(get("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity)).andExpect(status().isOk()).andReturn().getResponse();
        var messages = mapper.readTree(response.getContentAsByteArray()).path("result").path("messages");
        assertThat(messages.get(messages.size()-1).path("messageId").asLong()).isEqualTo(storedId);
        assertThat(messages.get(messages.size()-1).path("content").asText()).isEqualTo(content());
    }

    @ParameterizedTest
    @ValueSource(strings={"가입한 달이 무슨 뜻이에요?", "가입월이 뭔가요?", "무슨 뜻이에요?", "이 질문이 무슨 말인가요?", "가입한 달의 의미를 모르겠어요", "질문이 무슨 뜻이에요?"})
    void telme105MonthExplanationKeepsOriginalPendingConditionThenResumes(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long original = requestId();
        var before = jdbc.queryForList("SELECT condition_key,status,condition_value,asked_message_id,answered_message_id FROM consult_conditions WHERE consult_request_id=?", original);
        var version = jdbc.queryForObject("SELECT version FROM consult_requests WHERE consult_request_id=?", Integer.class, original);
        turn(reply);
        assertExplanation("달력상의 달");
        assertThat(jdbc.queryForList("SELECT condition_key,status,condition_value,asked_message_id,answered_message_id FROM consult_conditions WHERE consult_request_id=?", original)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT version FROM consult_requests WHERE consult_request_id=?", Integer.class, original)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, original)).isEqualTo("WAITING_CONDITION");
        assertThat(clarificationCount()).isEqualTo(1);
        assertThat(generations).isEmpty();
        turn("지난달이요");
        assertClarification(CHANGED);
        turn("아니요");
        assertThat(requestId()).isEqualTo(original);
        assertFinal();
    }

    @ParameterizedTest
    @ValueSource(strings={"그 질문 뜻이 뭐예요?", "변경 이력이 무슨 뜻이에요?", "무슨 뜻이에요?", "변경 여부 설명해 주세요"})
    void telme105HistoryExplanationPreservesKnownMonthAndConsumesNoCondition(String reply) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달이요");
        long original = requestId();
        turn(reply);
        assertExplanation("이번 달에 이미 요금제를 바꾼 적");
        assertThat(condition(JOINED)).isEqualTo(NO);
        assertThat(condition(CHANGED)).isNull();
        assertThat(clarificationCount()).isEqualTo(2);
        turn("아니요");
        assertThat(requestId()).isEqualTo(original);
        assertFinal();
    }

    @Test
    void telme105RepeatedExplanationsDoNotConsumeUnresolvedReplyLimit() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        for (int n=0; n<3; n++) {
            turn("무슨 뜻이에요?");
            assertExplanation("달력상의 달");
        }
        turn("가입한 것 같기도 해요");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, requestId())).isEqualTo("WAITING_CONDITION");
        assertThat(condition(JOINED)).isNull();
        assertThat(clarificationCount()).isEqualTo(1);
        turn("지난달이요");
        turn("아니요");
        assertFinal();
    }

    @Test
    void telme105CorrectionAfterExplanationUsesLatestMonth() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        turn("지난달이요");
        turn("변경 이력이 무슨 뜻이에요?");
        assertExplanation("요금제를 바꾼 적");
        turn("정정할게요. 이번 달에 가입했어요");
        assertThat(condition(JOINED)).isEqualTo(YES);
        assertThat(content()).contains("가입한 달에는 요금제를 변경할 수 없습니다");
        assertFinal();
    }

    @Test
    void telme105NewQuestionAfterExplanationCancelsOldConsultWithoutCopyingConditions() throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long original = requestId();
        turn("가입한 달이 무슨 뜻이에요?");
        assertExplanation("달력상의 달");
        turn("유심 재발급 비용 알려주세요");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, original)).isEqualTo("CANCELLED");
        assertThat(requestId()).isNotEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions WHERE consult_request_id=? AND condition_key IN ('joinedThisMonth','changedThisMonth')", Integer.class, requestId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void telme105StaleOrForeignPendingQuestionRollsBackExplanation(boolean foreign) throws Exception {
        turn(PlanChangeClarificationPolicyTest.PERSONAL);
        long questionId = jdbc.queryForObject("SELECT asked_message_id FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", Long.class, requestId(), JOINED);
        if (foreign) sessionId = newSession();
        else { turn("이번 달에 가입했어요"); assertFinal(); }
        int count = jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?", Integer.class, sessionId);
        int next = jdbc.queryForObject("SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE session_id=?", Integer.class, sessionId);
        long input = jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at) VALUES (?,?,'USER','QUESTION','설명해 주세요','COMPLETED',now()) RETURNING message_id", Long.class, sessionId,next);
        long execution = jdbc.queryForObject("INSERT INTO chat_executions(session_id,input_message_id,status) VALUES (?,?,'RUNNING') RETURNING execution_id", Long.class, sessionId,input);
        String oldSessionStatus = jdbc.queryForObject("SELECT status FROM chat_sessions WHERE session_id=?", String.class, sessionId);
        var answer = new com.telme.chat.service.ChatAnswer(com.telme.chat.entity.ChatMessage.MessageType.ANSWER,"설명",null,List.of(),null);
        assertThatThrownBy(() -> context.getBean(ConsultChatPersistenceService.class).persistWaiting(execution,sessionId,new ConsultService.PreparationResult(null,questionId),answer)).isInstanceOf(GeneralException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?", Integer.class, sessionId)).isEqualTo(count+1);
        assertThat(jdbc.queryForObject("SELECT status FROM chat_executions WHERE execution_id=?", String.class, execution)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT output_message_id FROM chat_executions WHERE execution_id=?", Long.class, execution)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM chat_sessions WHERE session_id=?", String.class, sessionId)).isEqualTo(oldSessionStatus);
    }

    private long newSession() {
        return jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'되묻기 검증') RETURNING session_id", Long.class, userId);
    }

    private void turn(String text) {
        currentText = text;
        sequence = jdbc.queryForObject("SELECT coalesce(max(sequence_no),0) FROM chat_messages WHERE session_id=?", Integer.class, sessionId) + 1;
        long input = jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at) VALUES (?,?,'USER','QUESTION',?,'COMPLETED',now()) RETURNING message_id", Long.class, sessionId, sequence, text);
        executionId = jdbc.queryForObject("INSERT INTO chat_executions(session_id,input_message_id,status) VALUES (?,?,'RUNNING') RETURNING execution_id", Long.class, sessionId, input);
        emitter = new CaptureEmitter();
        emitters.register(executionId, emitter);
        processor.request(new ChatProcessingCommand(executionId, sessionId, input, text));
    }

    private long requestId() { return jdbc.queryForObject("SELECT consult_request_id FROM consult_requests WHERE session_id=? ORDER BY consult_request_id DESC LIMIT 1", Long.class, sessionId); }
    private String condition(String key) { return jdbc.queryForObject("SELECT condition_value FROM consult_conditions WHERE consult_request_id=? AND condition_key=?", String.class, requestId(), key); }
    private String executionStatus() { return jdbc.queryForObject("SELECT status FROM chat_executions WHERE execution_id=?", String.class, executionId); }
    private String content() { return jdbc.queryForObject("SELECT content FROM chat_messages WHERE message_id=(SELECT output_message_id FROM chat_executions WHERE execution_id=?)", String.class, executionId); }
    private String outputType() { return jdbc.queryForObject("SELECT message_type FROM chat_messages WHERE message_id=(SELECT output_message_id FROM chat_executions WHERE execution_id=?)", String.class, executionId); }
    private int clarificationCount() { return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=? AND message_type='CLARIFICATION'", Integer.class, sessionId); }

    private void assertClarification(String key) {
        assertThat(outputType()).isEqualTo("CLARIFICATION");
        assertThat(content()).contains(key.equals("location") ? "어느 지역" : com.telme.consult.dto.PlanChangeConditions.question(key));
        assertThat(emitter.names).containsExactly("complete");
        assertThat(emitter.tokens).isEmpty();
    }

    private void assertFinal() throws Exception {
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(content()).isNotBlank();
        assertThat(emitter.names).containsExactly("start", "token", "complete");
        assertThat(emitter.tokens).containsExactly(content());
        var identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        var response = mvc.perform(get("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity)).andExpect(status().isOk()).andReturn().getResponse();
        var messages = mapper.readTree(response.getContentAsByteArray()).path("result").path("messages");
        assertThat(messages.get(messages.size()-1).path("content").asText()).isEqualTo(content());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=? AND message_type='ANSWER' AND status='COMPLETED'", Integer.class, sessionId)).isEqualTo((generations.isEmpty() ? 1 : generations.size()) + explanationAnswers);
    }

    private void assertExplanation(String expected) throws Exception {
        explanationAnswers++;
        assertThat(outputType()).isEqualTo("ANSWER");
        assertThat(content()).contains(expected);
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(emitter.names).containsExactly("complete");
        assertThat(emitter.tokens).isEmpty();
        assertThat(emitter.completed.outputMessage().messageId()).isEqualTo(jdbc.queryForObject("SELECT output_message_id FROM chat_executions WHERE execution_id=?", Long.class, executionId));
        assertThat(jdbc.queryForObject("SELECT status FROM chat_sessions WHERE session_id=?", String.class, sessionId)).isEqualTo("NEED_CLARIFICATION");
        var identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        var response = mvc.perform(get("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity)).andExpect(status().isOk()).andReturn().getResponse();
        var messages = mapper.readTree(response.getContentAsByteArray()).path("result").path("messages");
        assertThat(messages.get(messages.size()-1).path("content").asText()).isEqualTo(content());
    }

    private final class CaptureEmitter extends SseEmitter {
        List<String> names = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        com.telme.chat.service.ChatExecutionState completed;
        @Override public void send(SseEventBuilder builder) throws IOException {
            String name = null;
            Object payload = null;
            for (var item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String text && text.startsWith("event:")) name = text.substring(6, text.indexOf('\n'));
                else if (!(data instanceof String text && text.isBlank())) payload = data;
            }
            names.add(name);
            if ("complete".equals(name)) completed = (com.telme.chat.service.ChatExecutionState) payload;
            if ("token".equals(name)) { assertThat(executionStatus()).isEqualTo("COMPLETED"); assertThat(payload).isEqualTo(content()); tokens.add((String) payload); }
        }
    }
}
