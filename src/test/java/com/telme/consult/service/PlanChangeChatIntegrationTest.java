package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=? AND message_type='ANSWER' AND status='COMPLETED'", Integer.class, sessionId)).isEqualTo(generations.isEmpty() ? 1 : generations.size());
    }

    private final class CaptureEmitter extends SseEmitter {
        List<String> names = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        @Override public void send(SseEventBuilder builder) throws IOException {
            String name = null;
            Object payload = null;
            for (var item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String text && text.startsWith("event:")) name = text.substring(6, text.indexOf('\n'));
                else if (!(data instanceof String text && text.isBlank())) payload = data;
            }
            names.add(name);
            if ("token".equals(name)) { assertThat(executionStatus()).isEqualTo("COMPLETED"); assertThat(payload).isEqualTo(content()); tokens.add((String) payload); }
        }
    }
}
