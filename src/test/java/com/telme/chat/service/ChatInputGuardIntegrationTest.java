package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.guard.InputGuardProperties;
import com.telme.chat.guard.InputGuardRetention;
import com.telme.chat.guard.JdbcInputGuardStore;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 분석·생성만 대체하며 활성 처리기, 접수 API, 상태 저장 및 기록 조회는 실제 구현을 사용한다. */
@SpringBootTest(
        properties = {
            "llm.provider=fake",
            "chat.summary.trigger-messages=1000",
            "chat.title.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=8"
        })
@AutoConfigureMockMvc
class ChatInputGuardIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-07T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChatSessionService sessions;
    @Autowired private ChatProcessingPort processor;
    @Autowired private ChatContextBuilder contexts;
    @Autowired private ContextProvider consultContexts;
    @Autowired private ConsultTurnPreparationService turns;
    @Autowired private JdbcConsultStateStore consultStates;
    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private ChatMessageRepository messageRepository;
    @Autowired private ChatExecutionRepository executionRepository;
    @Autowired private ChatTokenEstimator tokenEstimator;
    @Autowired private InputGuardProperties properties;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Clock clock;
    @MockitoBean private TurnAnalyzer analyzer;
    @MockitoBean private AnswerProvider answers;
    @MockitoSpyBean private JdbcInputGuardStore guardStore;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private final List<Long> users = new ArrayList<>();
    private final List<UUID> guests = new ArrayList<>();
    private final AtomicReference<String> analyzedContent = new AtomicReference<>();
    private long userId;
    private MockHttpSession identity;

    @BeforeEach
    void 준비한다() {
        now.set(START);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        userId = member();
        identity = identity(userId);
        when(analyzer.analyze(any()))
                .thenAnswer(
                        invocation -> {
                            ChatProcessingCommand command = invocation.getArgument(0);
                            analyzedContent.set(command.content());
                            return AnalyzedTurn.direct(
                                    new ChatAnswer(
                                            ChatMessage.MessageType.ANSWER,
                                            "통신 서비스 문의를 확인했습니다.",
                                            null,
                                            List.of(),
                                            null));
                        });
        when(answers.generate(any()))
                .thenReturn(
                        GeneratedAnswer.withoutSources(
                                new ChatAnswer(
                                        ChatMessage.MessageType.ANSWER,
                                        "조건에 맞는 매장을 안내합니다.",
                                        null,
                                        List.of(),
                                        null)));
    }

    @AfterEach
    void 합성_입력만_정리한다() {
        for (Long owner : users) {
            jdbc.update(
                    "DELETE FROM consult_conditions WHERE consult_request_id IN (SELECT"
                        + " consult_request_id FROM consult_requests WHERE session_id IN (SELECT"
                        + " session_id FROM chat_sessions WHERE user_id=?))",
                    owner);
            jdbc.update(
                    "DELETE FROM consult_requests WHERE session_id IN"
                            + " (SELECT session_id FROM chat_sessions WHERE user_id=?)",
                    owner);
            jdbc.update("DELETE FROM chat_sessions WHERE user_id=?", owner);
        }
        for (UUID guest : guests) {
            jdbc.update("DELETE FROM chat_sessions WHERE guest_id=?", guest);
            jdbc.update("DELETE FROM guests WHERE guest_id=?", guest);
        }
        for (Long owner : users) {
            jdbc.update("DELETE FROM users WHERE user_id=?", owner);
        }
    }

    @Test
    @DisplayName("같은 사용자는 새 대화와 정상 입력 사이에서도 최근 감지 횟수를 누적한다")
    void 새_대화에서도_누적하고_세_번째에_제한한다() throws Exception {
        assertThat(processor).isInstanceOf(ConsultChatProcessingService.class);
        long first = session(identity);
        long second = session(identity);
        assertThat(send(first, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        waitCompleted(send(first, "요금제 기준 알려주세요").at("/result/executionId").asLong());
        assertThat(send(second, "ㅂㅅ").at("/result/inputGuard/violationCount").asInt()).isEqualTo(2);
        var result = request(second, identity, "개새끼", UUID.randomUUID(), 200);
        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("60");
        assertThat(body(result).at("/result/inputGuard/action").asText()).isEqualTo("RESTRICTED");
        assertThat(body(result).at("/result").has("executionId")).isFalse();
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id IN"
                                        + " (SELECT session_id FROM chat_sessions WHERE user_id=?)"
                                        + " AND role='USER' AND message_type='QUESTION'",
                                Integer.class,
                                userId))
                .isEqualTo(1);
        assertThat(history(second).at("/result/messages").get(0).path("messageType").asText())
                .isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("제한 중 입력은 대화·이력·횟수·종료 시각을 늘리지 않고 만료 후 새 집계를 시작한다")
    void 제한_중에는_저장하지_않고_만료_후_초기화한다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        send(sid, "ㅅㅂ");
        send(sid, "지랄");
        var until = restrictionUntil();
        now.set(START.plusSeconds(20));
        var restricted = send(sid, "요금제 알려주세요");
        assertThat(restricted.at("/result/inputGuard/retryAfterSeconds").asLong()).isEqualTo(40);
        assertThat(restricted.at("/result").has("messageId")).isFalse();
        assertThat(count("chat_messages")).isEqualTo(3);
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
        assertThat(restrictionUntil()).isEqualTo(until);
        verifyNoInteractions(analyzer, answers);
        now.set(START.plusSeconds(60));
        waitCompleted(send(sid, "요금제 기준").at("/result/executionId").asLong());
        assertThat(send(sid, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(restrictionUntil()).isNull();
        assertThat(count("chat_input_guard_events")).isEqualTo(4);
    }

    @Test
    @DisplayName("최근 10분의 경계 밖 기록은 제재 횟수에서 제외한다")
    void 정확히_십_분_지난_기록은_누적하지_않는다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        now.set(START.plusSeconds(600));
        assertThat(send(sid, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 감지 요청의 재전송은 원래 기록을 반환하고 횟수나 메시지를 추가하지 않는다")
    void 감지_요청_식별자가_같으면_중복_저장하지_않는다() throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, "씨발", key, 200));
        var repeated = body(request(sid, identity, "씨발", key, 200));
        assertThat(repeated).isEqualTo(first);
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        request(sid, identity, "ㅂㅅ", key, 409);
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(send(sid, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 입력의 여러 욕설 규칙은 기록하되 제재 횟수는 한 번만 증가한다")
    void 여러_욕설이_있어도_한_번_집계한다() throws Exception {
        long sid = session(identity);
        var result = send(sid, "씨발 개새끼 ㅂㅅ");
        assertThat(result.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(result.at("/result/inputGuard/detections").size()).isEqualTo(3);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        verifyNoInteractions(analyzer, answers);
    }

    @Test
    @DisplayName("마스킹한 요청을 재전송해도 원래 실행과 메시지를 반환한다")
    void 마스킹한_질문을_중복_생성하지_않는다() throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, "카드 4111111111111111 납부 문의", key, 201));
        long eid = first.at("/result/executionId").asLong();
        waitCompleted(eid);
        var repeated = body(request(sid, identity, "카드 4111111111111111 납부 문의", key, 201));
        assertThat(repeated.at("/result/messageId")).isEqualTo(first.at("/result/messageId"));
        assertThat(repeated.at("/result/executionId").asLong()).isEqualTo(eid);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("제한을 발생시킨 요청의 재전송에는 현재 남은 시간을 반환한다")
    void 재전송이_제한_남은_시간을_되돌리지_않는다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        send(sid, "씨발");
        UUID key = UUID.randomUUID();
        request(sid, identity, "씨발", key, 200);
        now.set(START.plusSeconds(25));
        var retry = body(request(sid, identity, "씨발", key, 200));
        assertThat(retry.at("/result/inputGuard/retryAfterSeconds").asLong()).isEqualTo(35);
        now.set(START.plusSeconds(60));
        assertThat(
                        body(request(sid, identity, "씨발", key, 200))
                                .at("/result/inputGuard/retryAfterSeconds")
                                .asLong())
                .isZero();
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
    }

    @Test
    @DisplayName("여러 세션에서 동시에 보낸 욕설도 사용자 잠금으로 한 번씩 누적한다")
    void 동시_입력의_횟수가_누락되지_않는다() throws Exception {
        List<Long> ids = List.of(session(identity), session(identity), session(identity));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var futures =
                    ids.stream()
                            .map(
                                    sid ->
                                            executor.submit(
                                                    () -> {
                                                        assertThat(start.await(5, TimeUnit.SECONDS))
                                                                .isTrue();
                                                        return sessions.sendMessage(
                                                                new ChatActor(userId, null),
                                                                sid,
                                                                new ChatMessageSendRequest(
                                                                        "씨발",
                                                                        null,
                                                                        null,
                                                                        UUID.randomUUID()));
                                                    }))
                            .toList();
            start.countDown();
            List<Integer> counts = new ArrayList<>();
            for (var future : futures) {
                counts.add(future.get(10, TimeUnit.SECONDS).inputGuard().violationCount());
            }
            assertThat(counts).containsExactlyInAnyOrder(1, 2, 3);
        }
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
        assertThat(count("chat_executions")).isZero();
    }

    @Test
    @DisplayName("다른 사용자·게스트의 횟수는 섞이지 않으며 게스트 새 대화는 같은 횟수를 사용한다")
    void 사용자와_게스트의_식별자_경계를_유지한다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        var other = identity(member());
        var otherSid = session(other);
        assertThat(
                        body(request(otherSid, other, "씨발", UUID.randomUUID(), 200))
                                .at("/result/inputGuard/violationCount")
                                .asInt())
                .isEqualTo(1);
        UUID guestId = guest();
        var guestIdentity = guestIdentity(guestId);
        request(session(guestIdentity), guestIdentity, "씨발", UUID.randomUUID(), 200);
        assertThat(
                        body(request(
                                        session(guestIdentity),
                                        guestIdentity,
                                        "씨발",
                                        UUID.randomUUID(),
                                        200))
                                .at("/result/inputGuard/violationCount")
                                .asInt())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("게스트 승계 관계가 저장되면 회원에게 기존 제한을 적용한다")
    void 승계된_게스트의_제한을_회원에게_유지한다() throws Exception {
        UUID guestId = guest();
        var guestIdentity = guestIdentity(guestId);
        long sid = session(guestIdentity);
        for (int index = 0; index < 3; index++) {
            request(sid, guestIdentity, "씨발", UUID.randomUUID(), 200);
        }
        // 인증 모듈의 승계 결과만 준비한다. 로그인 구현은 이 테스트의 검증 범위가 아니다.
        jdbc.update(
                "UPDATE guests SET merged_user_id=?,merged_at=now() WHERE guest_id=?",
                userId,
                guestId);
        now.set(START.plusSeconds(10));
        var result = send(session(identity), "정상 문의");
        assertThat(result.at("/result/inputGuard/retryAfterSeconds").asLong()).isEqualTo(50);
        assertThat(result.at("/result/inputGuard/violationCount").asInt()).isEqualTo(3);
        now.set(START.plusSeconds(60));
        assertThat(send(session(identity), "씨발").at("/result/inputGuard/violationCount").asInt())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("소유권·종료 상태가 잘못된 요청은 감지 기록이나 제한 상태를 변경하지 않는다")
    void 권한과_종료_상태를_입력_검사보다_먼저_확인한다() throws Exception {
        long sid = session(identity);
        request(sid, identity(member()), "씨발", UUID.randomUUID(), 404);
        mvc.perform(patch("/api/v1/chat/sessions/{id}/close", sid).session(identity))
                .andExpect(status().isOk());
        request(sid, identity, "씨발", UUID.randomUUID(), 409);
        assertThat(count("chat_input_guard_events")).isZero();
        assertThat(count("chat_input_guard_states")).isZero();
    }

    @Test
    @DisplayName("개인정보는 접수·분석·추적·이력에서 마스킹하며 남은 정상 질문을 처리한다")
    void 마스킹된_질문만_활성_처리기로_전달한다() throws Exception {
        long sid = session(identity);
        String sensitive = "카드 4111-1111-1111-1111 123원 요금 납부 방법 알려주세요";
        var accepted = send(sid, sensitive);
        assertThat(accepted.at("/result/inputGuard/action").asText()).isEqualTo("MASKED");
        long eid = accepted.at("/result/executionId").asLong();
        waitCompleted(eid);
        assertThat(analyzedContent.get()).isEqualTo("카드 [카드번호] 123원 요금 납부 방법 알려주세요");
        assertThat(history(sid).toString()).doesNotContain("4111");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sanitized_content FROM chat_input_guard_events WHERE"
                                        + " session_id=?",
                                String.class,
                                sid))
                .doesNotContain("4111");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT response_snapshot::text FROM chat_input_guard_events WHERE"
                                        + " session_id=?",
                                String.class,
                                sid))
                .doesNotContain("4111");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT pipeline_trace::text FROM chat_executions WHERE"
                                        + " execution_id=?",
                                String.class,
                                eid))
                .doesNotContain("4111");
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(send(sid, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        var subscription =
                mvc.perform(
                                get(
                                                "/api/v1/chat/sessions/{sid}/executions/{eid}/subscribe",
                                                sid,
                                                eid)
                                        .session(identity))
                        .andReturn();
        var stream =
                mvc.perform(asyncDispatch(subscription)).andExpect(status().isOk()).andReturn();
        assertThat(stream.getResponse().getContentAsString())
                .contains("event:complete", "COMPLETED")
                .doesNotContain("4111");
        String data = stream.getResponse().getContentAsString().split("data:", 2)[1].trim();
        long completedMessage = mapper.readTree(data).at("/outputMessage/messageId").asLong();
        var storedAnswer = history(sid).at("/result/messages").get(1);
        assertThat(completedMessage).isEqualTo(storedAnswer.path("messageId").asLong());
        assertThat(storedAnswer.path("content").asText()).isEqualTo("통신 서비스 문의를 확인했습니다.");

    }

    @Test
    @DisplayName("마스킹 후 내용이 없으면 재입력 안내를 주고 욕설이 함께 있으면 마스킹한 차단 입력만 남긴다")
    void 개인정보_감지_자체는_제재_횟수에_넣지_않는다() throws Exception {
        long sid = session(identity);
        var result = send(sid, "4111111111111111");
        assertThat(result.at("/result/inputGuard/action").asText()).isEqualTo("REWRITE_REQUIRED");
        assertThat(result.at("/result/inputGuard/violationCount").asInt()).isZero();
        var mixed = send(sid, "씨발 카드 4111111111111111");
        assertThat(mixed.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(history(sid).toString()).doesNotContain("4111");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT bool_and(sanitized_content NOT LIKE '%4111%')"
                                        + " FROM chat_input_guard_events WHERE session_id=?",
                                Boolean.class, sid))
                .isTrue();
        assertThat(count("chat_executions")).isZero();
        verifyNoInteractions(analyzer, answers);
    }

    @Test
    @DisplayName("기존 대기 상담과 질문은 차단·제한 중 그대로 유지하며 제한 후 같은 상담을 재개한다")
    void 제한_해제_후_기존_매장_되묻기를_이어간다() throws Exception {
        doAnswer(invocation -> storeTurn(invocation.getArgument(0))).when(analyzer).analyze(any());
        long sid = session(identity);
        waitCompleted(send(sid, "매장 알려주세요").at("/result/executionId").asLong());
        long consultId =
                jdbc.queryForObject(
                        "SELECT consult_request_id FROM consult_requests WHERE session_id=?",
                        Long.class,
                        sid);
        var waiting = consultStates.load(sid, consultId);
        var pending = consultStates.findPendingClarificationMessageId(sid, consultId, "location");
        send(sid, "씨발");
        send(sid, "씨발");
        send(sid, "씨발");
        send(sid, "강남역이요");
        assertThat(consultStates.load(sid, consultId)).isEqualTo(waiting);
        assertThat(consultStates.findPendingClarificationMessageId(sid, consultId, "location"))
                .isEqualTo(pending);
        now.set(START.plusSeconds(60));
        waitCompleted(send(sid, "강남역이요").at("/result/executionId").asLong());
        assertThat(consultStates.load(sid, consultId).conditions().get("location"))
                .isEqualTo(Condition.filled("강남역"));
        assertThat(consultStates.load(sid, consultId).status()).isEqualTo("DONE");
        assertThat(history(sid).at("/result/messages").get(6).path("content").asText())
                .isEqualTo("조건에 맞는 매장을 안내합니다.");
    }

    @Test
    @DisplayName("감지 이력 저장 실패는 메시지·제재 상태까지 롤백하고 생성 작업을 시작하지 않는다")
    void 감지_저장이_실패하면_불완전한_접수를_남기지_않는다() throws Exception {
        long sid = session(identity);
        doThrow(new IllegalStateException("검증용 기록 실패"))
                .when(guardStore)
                .record(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
        assertThatThrownBy(
                        () ->
                                sessions.sendMessage(
                                        new ChatActor(userId, null),
                                        sid,
                                        new ChatMessageSendRequest("카드 4111111111111111 납부 문의")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("검증용 기록 실패");
        assertThat(count("chat_messages")).isZero();
        assertThat(count("chat_executions")).isZero();
        assertThat(count("chat_input_guard_states")).isZero();
        verifyNoInteractions(analyzer, answers);
    }

    @Test
    @DisplayName("30일 정리는 감지 이력만 삭제하고 차단 메시지·제재 상태는 보존한다")
    void 보존_기간_정리와_오탐_표시가_제재를_바꾸지_않는다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        send(sid, "씨발");
        send(sid, "씨발");
        jdbc.update(
                "UPDATE chat_input_guard_events SET"
                        + " review_status='FALSE_POSITIVE',reviewed_at=now() WHERE session_id=?",
                sid);
        assertThat(send(sid, "정상 문의").at("/result/inputGuard/violationCount").asInt()).isEqualTo(3);
        now.set(START.plus(Duration.ofDays(31)));
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status ->
                                new InputGuardRetention(guardStore, properties, clock)
                                        .purgeExpiredEvents());
        assertThat(count("chat_input_guard_events")).isZero();
        assertThat(count("chat_messages")).isEqualTo(3);
        assertThat(count("chat_input_guard_states")).isEqualTo(1);
    }

    @Test
    @DisplayName("차단·오류 메시지는 조회에 남지만 문맥·요약의 조회 제한과 입력에서 제외된다")
    void 문맥과_요약에서_차단과_오류를_함께_제외한다() throws Exception {
        long sid = session(identity);
        List<Long> questions = new ArrayList<>();
        int sequence = 1;
        for (int index = 0; index < 4; index++) {
            long question = message(sid, sequence++, "USER", "QUESTION", "요금제 질문 " + index, null);
            questions.add(question);
            message(sid, sequence++, "ASSISTANT", "ANSWER", "정상 안내 " + index, question);
            for (int blocked = 0; blocked < 5; blocked++) {
                message(sid, sequence++, "USER", "BLOCKED", "차단 입력", null);
                message(sid, sequence++, "ASSISTANT", "ERROR", "오류 입력", question);
            }
        }
        long current = message(sid, sequence++, "USER", "QUESTION", "새 질문", null);
        long eid =
                jdbc.queryForObject(
                        "INSERT INTO chat_executions(session_id,input_message_id,status)"
                                + " VALUES (?,?,'RUNNING') RETURNING execution_id",
                        Long.class,
                        sid,
                        current);
        var context = contexts.build(new ChatProcessingCommand(eid, sid, current, "새 질문"), 2000);
        assertThat(context.history()).hasSize(8);
        assertThat(context.history())
                .allMatch(
                        value ->
                                value.content().startsWith("요금제")
                                        || value.content().startsWith("정상"));
        jdbc.update(
                "UPDATE chat_executions SET status='COMPLETED',ended_at=now() WHERE execution_id=?",
                eid);
        var summary =
                new ChatSummaryStore(
                        sessionRepository,
                        messageRepository,
                        executionRepository,
                        new ChatSummaryProperties(4, 2048, 2, 1024, 16, 3072, 512),
                        tokenEstimator);
        int through = sequence - 1;
        var snapshot =
                new TransactionTemplate(transactions)
                        .execute(
                                status ->
                                        summary.prepare(new ChatSummaryRequested(eid, sid, through))
                                                .orElseThrow());
        assertThat(snapshot.messages()).isNotEmpty();
        assertThat(snapshot.messages())
                .allMatch(
                        value ->
                                value.messageType() != ChatMessage.MessageType.BLOCKED
                                        && value.messageType() != ChatMessage.MessageType.ERROR);
        assertThat(snapshot.messages())
                .noneMatch(
                        value -> value.content().contains("차단") || value.content().contains("오류"));
    }

    private AnalyzedTurn storeTurn(ChatProcessingCommand command) {
        var context = consultContexts.loadVerified(command);
        if (context.candidates().isEmpty()) {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO consult_requests"
                                + " (session_id,origin_message_id,subquery_order,intent,query_text)"
                                + " VALUES (?,?,1,'STORE','매장') RETURNING consult_request_id",
                            Long.class,
                            command.sessionId(),
                            command.inputMessageId());
            return new AnalyzedTurn(
                    turns.prepareAnalysis(
                            command.sessionId(),
                            new IntentSubQueryResponse(
                                    id, (short) 1, ConsultRequest.Intent.STORE, "매장", Map.of()),
                            LocationStatus.MISSING),
                    null,
                    Purpose.NEARBY_STORE,
                    command.content(),
                    "매장");
        }
        var candidate = context.candidates().getFirst();
        var followup =
                turns.prepareFollowup(
                        context,
                        new Selection(
                                candidate.consultRequestId(),
                                candidate.questionMessageId(),
                                "location",
                                Map.of("location", Condition.filled("강남역"))),
                        LocationStatus.AVAILABLE);
        return new AnalyzedTurn(
                followup.preparation(),
                followup.followup().answeredField(),
                followup.purpose(),
                followup.originalUserQuery(),
                followup.searchQuery());
    }

    private long member() {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO users(email,name) VALUES (?,'입력 검사 검증') RETURNING user_id",
                        Long.class,
                        "input-guard-" + UUID.randomUUID() + "@example.com");
        users.add(id);
        return id;
    }

    private UUID guest() {
        UUID id = UUID.randomUUID();
        guests.add(id);
        jdbc.update(
                "INSERT INTO guests(guest_id,expires_at) VALUES (?,?)",
                id,
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
        return id;
    }

    private MockHttpSession identity(long id) {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, id);
        return session;
    }

    private MockHttpSession guestIdentity(UUID id) {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, id);
        return session;
    }

    private long session(MockHttpSession actor) throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/chat/sessions")
                                        .session(actor)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return body(result).at("/result/sessionId").asLong();
    }

    private JsonNode send(long sid, String content) throws Exception {
        var result = request(sid, identity, content, UUID.randomUUID(), 0);
        int status = result.getResponse().getStatus();
        assertThat(status).isIn(200, 201);
        return body(result);
    }

    private MvcResult request(
            long sid, MockHttpSession actor, String content, UUID requestId, int expected)
            throws Exception {
        var builder =
                mvc.perform(
                        post("/api/v1/chat/sessions/{id}/messages", sid)
                                .session(actor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "content",
                                                        content,
                                                        "requestId",
                                                        requestId))));
        if (expected > 0) {
            builder.andExpect(status().is(expected));
        }
        return builder.andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode history(long sid) throws Exception {
        return body(
                mvc.perform(
                                get("/api/v1/chat/sessions/{id}/messages", sid)
                                        .session(identity)
                                        .param("size", "50"))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM "
                        + table
                        + " WHERE "
                        + (table.equals("chat_input_guard_states")
                                        || table.equals("chat_input_guard_events")
                                ? "user_id=?"
                                : "session_id IN (SELECT session_id FROM chat_sessions WHERE"
                                        + " user_id=?)"),
                Integer.class,
                userId);
    }

    private Instant restrictionUntil() {
        var value =
                jdbc.queryForObject(
                        "SELECT restriction_until FROM chat_input_guard_states WHERE user_id=?",
                        java.sql.Timestamp.class,
                        userId);
        return value == null ? null : value.toInstant();
    }

    private void waitCompleted(long eid) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT status FROM chat_executions WHERE"
                                                                + " execution_id=?",
                                                        String.class,
                                                        eid))
                                        .isEqualTo("COMPLETED"));
    }

    private long message(
            long sid, int sequence, String role, String type, String content, Long replyTo) {
        return jdbc.queryForObject(
                "INSERT INTO chat_messages"
                    + " (session_id,sequence_no,role,message_type,content,status,completed_at,reply_to_id)"
                    + " VALUES (?,?,?,?,?,'COMPLETED',now(),?) RETURNING message_id",
                Long.class,
                sid,
                sequence,
                role,
                type,
                content,
                replyTo);
    }
}
