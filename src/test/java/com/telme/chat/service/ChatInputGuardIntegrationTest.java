package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.repository.ChatInputMaskRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.guard.InputGuardProperties;
import com.telme.chat.guard.InputGuardRetention;
import com.telme.chat.guard.JdbcInputGuardStore;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
    @Autowired private ChatInputMaskRepository inputMasks;
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
    @Autowired private ChatSummaryConverter summaryConverter;
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
    @DisplayName("기존 경고가 있어도 정상 단어의 오탐 후보는 접수·완료되며 제재 횟수와 감지 이력을 늘리지 않는다")
    void 정상_단어가_섞인_질문으로_제한하지_않는다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        send(sid, "ㅅㅂ");
        List<String> inputs =
                List.of(
                        "유심 다시 발급받고 싶어요",
                        "택배는 몇 시 발송돼요?",
                        "오후 3시 발송인가요",
                        "새 요금제 출시 발표 언제예요?",
                        "개통 시 발신 제한이 있나요?",
                        "병 신청은 어디서 해요",
                        "아저씨 발 사이즈",
                        "유심 다시발급받고 싶어요",
                        "새 요금제 출시발표 언제예요?",
                        "개통시발신 제한이 있나요?");

        for (String input : inputs) {
            JsonNode accepted = body(request(sid, identity, input, UUID.randomUUID(), 201));
            assertThat(accepted.at("/result").has("inputGuard")).isFalse();
            long executionId = accepted.at("/result/executionId").asLong();
            assertThat(executionId).isPositive();
            waitCompleted(executionId);
            assertThat(analyzedContent.get()).isEqualTo(input);
            long messageId = accepted.at("/result/messageId").asLong();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT message_type FROM chat_messages WHERE message_id=?",
                                    String.class,
                                    messageId))
                    .isEqualTo("QUESTION");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT content FROM chat_messages WHERE message_id=?",
                                    String.class,
                                    messageId))
                    .isEqualTo(input);
            assertThat(count("chat_input_guard_events")).isEqualTo(2);
            assertThat(restrictionUntil()).isNull();
        }

        assertThat(count("chat_executions")).isEqualTo(inputs.size());
        JsonNode restricted = send(sid, "출시 발표 언제예요? 씨발");
        assertThat(restricted.at("/result/inputGuard/action").asText()).isEqualTo("RESTRICTED");
        assertThat(restricted.at("/result/inputGuard/violationCount").asInt()).isEqualTo(3);
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
        assertThat(count("chat_executions")).isEqualTo(inputs.size());
    }

    @Test
    @DisplayName("피해 인용은 기존 경고를 늘리지 않고 상담으로 전달하며 웃음 자모 욕설은 한 번만 누적한다")
    void 인용_문의와_자모_욕설의_제재를_구분한다() throws Exception {
        long sid = session(identity);
        send(sid, "씨발");
        send(sid, "ㅅㅂ");
        List<String> reports =
                List.of(
                        "'씨발'이라고 들었는데 어떻게 신고하나요?",
                        "상담원이 저한테 '병신'이라고 했어요. 신고 방법 알려주세요");
        for (String input : reports) {
            JsonNode accepted = body(request(sid, identity, input, UUID.randomUUID(), 201));
            waitCompleted(accepted.at("/result/executionId").asLong());
            assertThat(analyzedContent.get()).isEqualTo(input);
            assertThat(count("chat_input_guard_events")).isEqualTo(2);
            assertThat(restrictionUntil()).isNull();
        }
        JsonNode restricted = send(sid, "씨발ㅋㅋ");
        assertThat(restricted.at("/result/inputGuard/action").asText()).isEqualTo("RESTRICTED");
        assertThat(restricted.at("/result/inputGuard/violationCount").asInt()).isEqualTo(3);
        assertThat(count("chat_executions")).isEqualTo(reports.size());
        assertThat(count("chat_input_guard_events")).isEqualTo(3);
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "시4111111111111111발 | 시***발",
        "씨990101-1234567발 | 씨***발",
        "ㅅ4111111111111111ㅂ | ㅅ***ㅂ"
    })
    @DisplayName("번호를 끼운 욕설은 상담을 생성하지 않고 같은 요청 재전송도 한 번만 경고한다")
    void 번호를_끼운_욕설을_차단하고_재전송을_유지한다(String input, String safe) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, input, key, 200));
        var repeated = body(request(sid, identity, input, key, 200));

        assertThat(first.at("/result/inputGuard/action").asText()).isEqualTo("WARNED");
        assertThat(first.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(first.at("/result").hasNonNull("executionId")).isFalse();
        assertThat(repeated).isEqualTo(first);
        assertThat(count("chat_executions")).isZero();
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM chat_messages WHERE session_id=?", String.class, sid))
                .isEqualTo(safe);
        assertThat(jdbc.queryForObject(
                "SELECT message_type FROM chat_messages WHERE session_id=?", String.class, sid))
                .isEqualTo("BLOCKED");
        verifyNoInteractions(analyzer, answers);
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "주민번호 990101-1234567 납부 문의 | 주민번호 [주민등록번호] 납부 문의",
        "카드 4111111111111111 납부 문의 | 카드 [카드번호] 납부 문의",
        "990101-1234567 4111111111111111 납부 문의 | [주민등록번호] [카드번호] 납부 문의",
        "카드 4111111111111111 12개월 약정 문의 | 카드 [카드번호] 12개월 약정 문의"
    })
    @DisplayName("유형 문구를 사용하던 배포 전 요청도 같은 요청 ID로 재전송하면 원래 실행을 반환한다")
    void 기존_마스킹_요청의_해시와_재전송을_유지한다(String input, String legacyContent) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, input, key, 201));
        long eid = first.at("/result/executionId").asLong();
        long messageId = first.at("/result/messageId").asLong();
        waitCompleted(eid);
        String legacyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((sid + "\n" + legacyContent + "\nnull\nnull").getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject(
                "SELECT request_fingerprint FROM chat_input_guard_events WHERE session_id=?",
                String.class, sid)).isEqualTo(legacyHash);

        // 독립 검증 DB에서만 배포 전 저장된 마스킹 문구와 해시를 재현한다.
        jdbc.update("UPDATE chat_input_guard_events SET policy_version='chat-input-guard-v2',"
                + " sanitized_content=?,request_fingerprint=?"
                + " WHERE session_id=?", legacyContent, legacyHash, sid);
        jdbc.update("UPDATE chat_messages SET content=? WHERE message_id=?", legacyContent, messageId);

        var replay = body(request(sid, identity, input, key, 201));
        assertThat(replay).isEqualTo(first);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText())
                .isEqualTo(legacyContent);
        request(sid, identity, input + " 다른 문의", key, 409);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 411111 1111111 111 납부 문의 | 카드번호 [주민등록번호] 111 납부 문의",
        "카드 4111111111111111 18개월 약정 문의 | 카드 [카드번호]개월 약정 문의",
        "제 카드번호는 4111111111111111인데요 | 제 카드번호는 [카드번호]인데요"
    })
    @DisplayName("검사 결과가 변경된 입력도 저장된 v2 영수증의 해시로 비교해 기존 접수를 재사용한다")
    void 이전_정책의_실제_경계로_기존_접수를_재전송한다(String input, String legacyContent) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, "카드 4111111111111111 납부 문의", key, 201));
        waitCompleted(first.at("/result/executionId").asLong());
        String legacyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((sid + "\n" + legacyContent + "\nnull\nnull").getBytes(StandardCharsets.UTF_8)));
        // 실제 v2가 접수하던 응답 영수증을 전용 DB에서만 재현한다. 새 정책 판정으로 덮어쓰지 않는다.
        jdbc.update("UPDATE chat_input_guard_events SET policy_version='chat-input-guard-v2',"
                + " request_fingerprint=? WHERE session_id=?", legacyHash, sid);

        assertThat(body(request(sid, identity, input, key, 201))).isEqualTo(first);
        request(sid, identity, input + " 다른 문의", key, 409);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_executions")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드 4111111111111111 18개월로 약정 문의 | 카드 ***개월로 약정 문의"
                + " | 카드 [카드번호]개월로 약정 문의",
        "주민번호 990101-1234567 4개월로 약정 문의 | 주민번호 ***개월로 약정 문의"
                + " | 주민번호 [카드번호]개월로 약정 문의",
        "카드 4111111111111111 003원짜리 납부 문의 | 카드 ***원짜리 납부 문의"
                + " | 카드 [카드번호]원짜리 납부 문의",
        "카드 4111111111111111 18개월간 약정 문의 | 카드 ***개월간 약정 문의"
                + " | 카드 [카드번호]개월간 약정 문의",
        "카드 4111111111111111 18개월째 약정 문의 | 카드 ***개월째 약정 문의"
                + " | 카드 [카드번호]개월째 약정 문의",
        "카드 4111111111111111 18개월치 납부 문의 | 카드 ***개월치 납부 문의"
                + " | 카드 [카드번호]개월치 납부 문의",
        "카드 4111111111111111 18개월씩 납부 문의 | 카드 ***개월씩 납부 문의"
                + " | 카드 [카드번호]개월씩 납부 문의",
        "카드 4111111111111111 18개월이요 | 카드 ***개월이요 | 카드 [카드번호]개월이요",
        "카드 4111111111111111 3만원씩 납부 문의 | 카드 ***만원씩 납부 문의"
                + " | 카드 [카드번호]만원씩 납부 문의"
    })
    @DisplayName("옛 v3 영수증은 같은 응답을 유지하고 새 요청만 재안내하며 해시·중복 방지를 유지한다")
    void 기존_v3_영수증과_새_재입력_안내를_구분한다(
            String input, String oldContent, String fingerprintContent) throws Exception {
        long sid = session(identity);
        UUID oldKey = UUID.randomUUID();
        var first = body(request(sid, identity, "카드 4111111111111111 납부 문의", oldKey, 201));
        waitCompleted(first.at("/result/executionId").asLong());
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((sid + "\n" + fingerprintContent + "\nnull\nnull").getBytes(StandardCharsets.UTF_8)));
        int maskStart = oldContent.indexOf("***");
        String spans = mapper.writeValueAsString(List.of(Map.of("start", maskStart, "end", maskStart + 3)));
        // 변경 전 v3의 안전한 문구·위치·영수증을 전용 DB에 재현한다. 원문 번호는 저장하지 않는다.
        jdbc.update("UPDATE chat_messages SET content=?, input_mask_spans=?::jsonb WHERE message_id=?",
                oldContent, spans, first.at("/result/messageId").asLong());
        jdbc.update("UPDATE chat_input_guard_events SET policy_version='chat-input-guard-v3',"
                + " sanitized_content=?, request_fingerprint=? WHERE session_id=?", oldContent, oldHash, sid);
        clearInvocations(analyzer, answers);

        assertThat(body(request(sid, identity, input, oldKey, 201))).isEqualTo(first);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText()).isEqualTo(oldContent);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        request(sid, identity, input + " 다른 문의", oldKey, 409);

        UUID newKey = UUID.randomUUID();
        var rewritten = body(request(sid, identity, input, newKey, 200));
        assertThat(rewritten.at("/result/inputGuard/action").asText()).isEqualTo("REWRITE_REQUIRED");
        assertThat(rewritten.at("/result/inputGuard/violationCount").asInt()).isZero();
        assertThat(rewritten.at("/result").has("executionId")).isFalse();
        assertThat(body(request(sid, identity, input, newKey, 200))).isEqualTo(rewritten);
        assertThat(count("chat_messages")).isEqualTo(3);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT request_fingerprint FROM chat_input_guard_events WHERE session_id=?",
                String.class, sid)).containsOnly(oldHash);
        verifyNoInteractions(analyzer, answers);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 411111 1111111 111 납부 문의 | 카드번호 *** 납부 문의",
        "고객번호 1234567890123 4111 1111 1111 1111 납부 문의 | 고객번호 1234567890123 *** 납부 문의",
        "제 카드번호는 4111111111111111인데요 납부 방법은요? | 제 카드번호는 ***인데요 납부 방법은요?",
        "주민번호 990101-1234567 4개월 약정 문의 | 주민번호 *** 4개월 약정 문의",
        "카드 4111111111111111 약정 18개월 요금 5만원 문의 | 카드 *** 약정 18개월 요금 5만원 문의",
        "카드 4111111111111111 12개월로 약정 문의 | 카드 *** 12개월로 약정 문의",
        "카드 4111111111111111 12개월짜리는 얼마예요? | 카드 *** 12개월짜리는 얼마예요?",
        "카드 4111111111111111 7700원으로 납부 문의 | 카드 *** 7700원으로 납부 문의",
        "카드 4111111111111111, 18개월로 약정 문의 | 카드 ***, 18개월로 약정 문의",
        "카드 4111111111111111 약정 18개월동안 납부 문의 | 카드 *** 약정 18개월동안 납부 문의",
        "카드번호 4111111111111111 18분실 신고 | 카드번호 ***분실 신고",
        "카드번호 4111111111111111 003원룸 주소 변경 | 카드번호 ***원룸 주소 변경",
        "카드번호 4111111111111111 18일반 문의 | 카드번호 ***일반 문의",
        "카드 4111111111111111 12개월간 약정 문의 | 카드 *** 12개월간 약정 문의",
        "카드 4111111111111111 12개월째 납부 문의 | 카드 *** 12개월째 납부 문의",
        "카드 4111111111111111 12개월치 금액 문의 | 카드 *** 12개월치 금액 문의",
        "카드 4111111111111111 12개월씩 납부 문의 | 카드 *** 12개월씩 납부 문의",
        "카드 4111111111111111 12개월이요 | 카드 *** 12개월이요",
        "카드 4111111111111111 7700원씩 납부 문의 | 카드 *** 7700원씩 납부 문의",
        "카드 4111111111111111, 18개월간 약정 문의 | 카드 ***, 18개월간 약정 문의",
        "카드 4111111111111111 약정 18개월째 문의 | 카드 *** 약정 18개월째 문의",
        "카드 4111111111111111 / 18개월치 납부 문의 | 카드 *** / 18개월치 납부 문의",
        "카드 4111111111111111 기간 18개월씩 납부 문의 | 카드 *** 기간 18개월씩 납부 문의",
        "카드 4111111111111111, 18개월이요 | 카드 ***, 18개월이요",
        "카드 4111111111111111 금액 3만원씩 납부 문의 | 카드 *** 금액 3만원씩 납부 문의"
    })
    @DisplayName("누락을 보완한 마스크와 명확한 상담 조건을 저장·분석하며 재전송해도 중복 생성하지 않는다")
    void 보완한_마스킹과_정상_문의의_저장과_재전송을_유지한다(String input, String expected) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, input, key, 201));
        waitCompleted(first.at("/result/executionId").asLong());

        assertThat(analyzedContent.get()).isEqualTo(expected);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT sanitized_content FROM chat_input_guard_events WHERE session_id=?",
                String.class, sid)).isEqualTo(expected);
        assertThat(body(request(sid, identity, input, key, 201))).isEqualTo(first);
        request(sid, identity, input + " 다른 문의", key, 409);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "제 카드번호는 4111111111111111인데요", "주민번호는 990101-1234567이고요",
        "카드 4111111111111111 18개월 약정 문의", "카드 4111-1111-1111-1111-18개월 문의",
        "카드 4111-1111-1111-1111-18개월", "카드 4111111111111111 18개월입니다",
        "카드 4111111111111111 18개월로 약정 문의", "카드 4111111111111111 18개월동안 납부 문의",
        "카드 4111111111111111 18개월짜리 약정 문의", "카드 4111111111111111 18개월로는 변경할 수 있나요?",
        "카드 4111111111111111 18개월동안만 납부 문의", "카드 4111111111111111 18개월짜리로는요 문의",
        "카드 4111-1111-1111-1111-18개월로 문의", "카드 4111111111111111 003원짜리 납부 문의",
        "카드 4111111111111111 18개월간 약정 문의", "카드 4111111111111111 18개월째 약정 문의",
        "카드 4111111111111111 18개월치 납부 문의", "카드 4111111111111111 18개월씩 납부 문의",
        "카드 4111111111111111 18개월이요", "카드 4111111111111111 3만원씩 납부 문의",
        "카드 4111111111111111 18개월간은 약정 문의", "카드 4111111111111111 18개월째는 약정 문의",
        "카드 4111111111111111 18개월치로 납부 문의", "카드 4111111111111111 18개월씩만 납부 문의",
        "카드 4111111111111111 18개월이요?", "카드 4111111111111111 3원씩 납부 문의",
        "카드 4111111111111111 3년째 납부 문의", "카드 4111111111111111 18일간 납부 문의",
        "카드 4111111111111111 18시간째 납부 문의", "카드 4111111111111111 18분씩 통화 문의"
    })
    @DisplayName("소개만 남거나 번호·기간이 모호한 요청은 재입력을 안내하며 경고·실행·중복 메시지를 만들지 않는다")
    void 재입력이_필요한_요청을_중복_실행하지_않는다(String input) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, input, key, 200));

        assertThat(first.at("/result/inputGuard/action").asText()).isEqualTo("REWRITE_REQUIRED");
        assertThat(first.at("/result/inputGuard/violationCount").asInt()).isZero();
        assertThat(first.at("/result").has("executionId")).isFalse();
        if (input.contains("18개월")) {
            assertThat(first.at("/result/inputGuard/message").asText()).contains("경계가 불명확", "번호를 제외");
        }
        assertThat(body(request(sid, identity, input, key, 200))).isEqualTo(first);
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_executions")).isZero();
        assertThat(history(sid).toString()).doesNotContain("4111", "990101", "1234567");
        verifyNoInteractions(analyzer, answers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"18개월로", "18개월간", "3만원씩"})
    @DisplayName("추가 어미의 모호한 조건이 있어도 실제 욕설은 재입력 안내보다 먼저 한 번 경고한다")
    void 모호한_조건의_추가_어미도_욕설_차단을_완화하지_않는다(String condition) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        String input = "씨발 카드 4111111111111111 " + condition + " 약정 문의";
        var first = body(request(sid, identity, input, key, 200));

        assertThat(first.at("/result/inputGuard/action").asText()).isEqualTo("WARNED");
        assertThat(first.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(body(request(sid, identity, input, key, 200))).isEqualTo(first);
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_executions")).isZero();
        verifyNoInteractions(analyzer, answers);
    }

    @Test
    @DisplayName("새로 저장한 요청은 카드 뒤 기간을 해시에 보존해 같은 요청 ID의 다른 조건을 거절한다")
    void 새_요청_해시는_마스킹_뒤_정상_조건을_구분한다() throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var first = body(request(sid, identity, "카드 4111111111111111 약정 18개월 문의", key, 201));
        waitCompleted(first.at("/result/executionId").asLong());
        request(sid, identity, "카드 4111111111111111 약정 27개월 문의", key, 409);
        request(session(identity), identity, "카드 4111111111111111 약정 18개월 문의", key, 409);
        request(sid, identity, "주민번호 990101-1234567 약정 18개월 문의", key, 409);
        mvc.perform(post("/api/v1/chat/sessions/{id}/messages", sid).session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new ChatMessageSendRequest(
                                "카드 4111111111111111 약정 18개월 문의", 37.5, 127.0, key))))
                .andExpect(status().isConflict());

        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_executions")).isEqualTo(1);
    }

    @Test
    @DisplayName("유효한 19자리 카드 끝자리도 채팅·감지 이력·분석 입력에 남기지 않는다")
    void 긴_카드의_끝자리를_상담_조건으로_남기지_않는다() throws Exception {
        long sid = session(identity);
        var accepted = body(request(sid, identity,
                "카드 4111111111111111 003 개인정보를 가려주세요", UUID.randomUUID(), 201));
        waitCompleted(accepted.at("/result/executionId").asLong());
        assertThat(analyzedContent.get()).isEqualTo("카드 *** 개인정보를 가려주세요");
        var messages = history(sid).at("/result/messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("content").asText()).isEqualTo("카드 *** 개인정보를 가려주세요");
        // 시각·식별자의 우연한 숫자 일치가 아니라 실제 표시 문구의 번호 노출을 확인한다.
        messages.forEach(message -> assertThat(message.path("content").asText()).doesNotContain("4111", "003"));
        assertThat(jdbc.queryForObject(
                "SELECT sanitized_content FROM chat_input_guard_events WHERE session_id=?",
                String.class, sid)).isEqualTo("카드 *** 개인정보를 가려주세요");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 | 개 *** 새끼",
        "씨 990101-1234567 발 | 씨 *** 발"
    })
    @DisplayName("마스킹으로 만들어진 욕설 오탐은 실제 접수에서 경고 횟수를 늘리지 않는다")
    void 마스킹_오탐으로_제재하지_않는다(String input, String expected) throws Exception {
        long sid = session(identity);
        assertThat(send(sid, "씨발").at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        UUID key = UUID.randomUUID();
        var accepted = body(request(sid, identity, input, key, 201));
        long eid = accepted.at("/result/executionId").asLong();
        waitCompleted(eid);

        assertThat(accepted.at("/result/inputGuard/action").asText()).isEqualTo("MASKED");
        assertThat(accepted.at("/result/inputGuard/message").asText())
                .isEqualTo("민감정보를 가리고 문의를 처리합니다.");
        assertThat(accepted.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(analyzedContent.get()).isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                "SELECT counted_violation FROM chat_input_guard_events WHERE request_id=?", Boolean.class, key))
                .isFalse();
        assertThat(history(sid).at("/result/messages").findValuesAsText("content").toString())
                .contains(expected).doesNotContain("4111", "990101", "1234567", "\uFFFC");
        assertThat(jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?", String.class, eid))
                .doesNotContain("4111", "990101", "1234567", "\uFFFC");
        assertThat(send(sid, "개새끼").at("/result/inputGuard/violationCount").asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("같은 표시의 별표 욕설은 마스킹 요청을 재사용하지 못하며 별도 경고로 한 번 집계한다")
    void 같은_표시의_별표_욕설을_정상_마스킹_요청으로_재사용하지_않는다(
            String original, String literal) throws Exception {
        long sid = session(identity);
        UUID acceptedKey = UUID.randomUUID();
        var accepted = body(request(sid, identity, original, acceptedKey, 201));
        waitCompleted(accepted.at("/result/executionId").asLong());

        assertThat(accepted.at("/result/inputGuard/action").asText()).isEqualTo("MASKED");
        assertThat(accepted.at("/result/inputGuard/violationCount").asInt()).isZero();
        assertThat(analyzedContent.get()).isEqualTo(literal);
        request(sid, identity, literal, acceptedKey, 409);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);

        UUID warnedKey = UUID.randomUUID();
        var warned = body(request(sid, identity, literal, warnedKey, 200));
        assertThat(warned.at("/result/inputGuard/action").asText()).isEqualTo("WARNED");
        assertThat(warned.at("/result/inputGuard/violationCount").asInt()).isEqualTo(1);
        assertThat(warned.at("/result/executionId").isMissingNode()).isTrue();
        assertThat(body(request(sid, identity, literal, warnedKey, 200))).isEqualTo(warned);
        assertThat(count("chat_input_guard_events")).isEqualTo(2);
        assertThat(count("chat_messages")).isEqualTo(3);
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_input_guard_events WHERE session_id=? AND counted_violation",
                Integer.class, sid)).isEqualTo(1);
        assertThat(history(sid).toString()).doesNotContain("4111", "990101", "1234567", "\uFFFC");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드 4111 1111 1111 1111 3번 문의 | 카드 ***번 문의",
        "카드 4111111111111111 약정 18개월 문의 | 카드 *** 약정 18개월 문의"
    })
    @DisplayName("숫자 묶음 전체 보호와 명확한 조건 보존이 접수·저장·상담에서 일치한다")
    void 숫자_경계_기준을_접수와_저장에_적용한다(String input, String expected) throws Exception {
        long sid = session(identity);
        var accepted = send(sid, input);
        waitCompleted(accepted.at("/result/executionId").asLong());

        assertThat(analyzedContent.get()).isEqualTo(expected);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText()).isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                "SELECT sanitized_content FROM chat_input_guard_events WHERE session_id=?", String.class, sid))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("표시가 같은 별표여도 서로 다른 개인정보 유형에 요청 ID를 재사용할 수 없다")
    void 같은_별표_표시로_다른_유형의_요청을_허용하지_않는다() throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        request(sid, identity, "990101-1234567", key, 200);
        request(sid, identity, "4111111111111111", key, 409);

        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_executions")).isZero();
        verifyNoInteractions(analyzer, answers);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "990101-1234567", "제 주민번호는 990101-1234567입니다",
        "4111111111111111", "제 카드번호는 4111111111111111입니다",
        "4111 1111 1111 1111 3번",
        "주민번호 990101-1234567, 카드번호 4111111111111111"
    })
    @DisplayName("번호·소개·단위만 남는 입력은 별표로 저장하고 검색·생성 없이 재입력을 안내한다")
    void 별표만_남는_요청으로_상담을_시작하지_않는다(String input) throws Exception {
        long sid = session(identity);
        UUID key = UUID.randomUUID();
        var result = body(request(sid, identity, input, key, 200));
        assertThat(result.at("/result/inputGuard/action").asText()).isEqualTo("REWRITE_REQUIRED");
        assertThat(result.at("/result/inputGuard/message").asText())
                .isEqualTo("민감정보를 제외한 문의 내용을 입력해 주세요.");
        assertThat(result.at("/result/inputGuard/violationCount").asInt()).isZero();
        assertThat(result.at("/result").has("executionId")).isFalse();
        assertThat(body(request(sid, identity, input, key, 200))).isEqualTo(result);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText()).contains("***");
        assertThat(history(sid).toString()).doesNotContain("990101", "1234567", "4111");
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
        assertThat(count("chat_messages")).isEqualTo(1);
        assertThat(count("chat_executions")).isZero();
        verifyNoInteractions(analyzer, answers);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "990101-1234567 유심 재발급 방법 알려주세요 | *** 유심 재발급 방법 알려주세요",
        "로밍 신청 방법 알려주세요 4111111111111111 | 로밍 신청 방법 알려주세요 ***",
        "주민번호 990101-1234567 카드 4111111111111111 약정 18개월 문의"
                + " | 주민번호 *** 카드 *** 약정 18개월 문의"
    })
    @DisplayName("주민번호·카드번호와 함께 보낸 문의는 별표 상태로 접수·분석·저장·조회된다")
    void 별표로_가린_문의가_정상_접수_계약을_유지한다(String input, String expected) throws Exception {
        long sid = session(identity);
        var accepted = body(request(sid, identity, input, UUID.randomUUID(), 201));
        long eid = accepted.at("/result/executionId").asLong();
        waitCompleted(eid);

        assertThat(accepted.at("/result/inputGuard/action").asText()).isEqualTo("MASKED");
        assertThat(accepted.at("/result/inputGuard/violationCount").asInt()).isZero();
        assertThat(analyzedContent.get()).isEqualTo(expected);
        assertThat(history(sid).at("/result/messages").get(0).path("content").asText()).isEqualTo(expected);
        assertThat(history(sid).toString()).doesNotContain("990101", "1234567", "4111");
        assertThat(jdbc.queryForObject(
                "SELECT sanitized_content FROM chat_input_guard_events WHERE session_id=?",
                String.class, sid)).isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?",
                String.class, eid)).doesNotContain("990101", "1234567", "4111", "[주민등록번호]", "[카드번호]");
        assertThat(count("chat_executions")).isEqualTo(1);
        assertThat(count("chat_messages")).isEqualTo(2);
        assertThat(count("chat_input_guard_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 마스킹해 저장된 문의는 다음 턴 문맥과 내부 요약에도 별표 상태로 전달된다")
    void 문맥과_내부_요약에_마스킹된_문의만_전달한다() throws Exception {
        long sid = session(identity);
        waitCompleted(send(sid, "주민번호 990101-1234567 카드 4111111111111111 유심 재발급")
                .at("/result/executionId").asLong());
        long eid = send(sid, "로밍 신청 방법").at("/result/executionId").asLong();
        waitCompleted(eid);
        long current = message(sid, 5, "USER", "QUESTION", "다음 문의", null);
        long currentExecution = jdbc.queryForObject(
                "INSERT INTO chat_executions(session_id,input_message_id,status)"
                        + " VALUES (?,?,'RUNNING') RETURNING execution_id", Long.class, sid, current);
        var context = contexts.build(
                new ChatProcessingCommand(currentExecution, sid, current, "다음 문의"), 2000);
        assertThat(context.history()).extracting(ChatContextMessage::content)
                .contains("주민번호 *** 카드 *** 유심 재발급");

        var summary = new ChatSummaryStore(
                sessionRepository, messageRepository, executionRepository,
                new ChatSummaryProperties(4, 2048, 2, 1024, 16, 3072, 512),
                tokenEstimator, summaryConverter);
        var snapshot = new TransactionTemplate(transactions).execute(status ->
                summary.prepare(new ChatSummaryRequested(eid, sid, 4)).orElseThrow());
        assertThat(snapshot.messages()).extracting(ChatContextMessage::content)
                .contains("주민번호 *** 카드 *** 유심 재발급");
        assertThat(snapshot.messages().toString())
                .doesNotContain("990101", "1234567", "4111", "[주민등록번호]", "[카드번호]");

        var client = org.mockito.Mockito.mock(com.telme.llm.service.LlmClient.class);
        when(client.generate(any())).thenReturn("**주민번호 *** 카드 *** 유심 재발급**");
        var summaryService = new ChatSummaryService(summary, client,
                new ChatSummaryProperties(4, 2048, 2, 1024, 16, 3072, 512), summaryConverter, inputMasks);
        Boolean saved = new TransactionTemplate(transactions).execute(status ->
                summaryService.summarizeIfNeeded(new ChatSummaryRequested(eid, sid, 4)));
        assertThat(saved).isTrue();
        assertThat(jdbc.queryForObject("SELECT summary FROM chat_sessions WHERE session_id=?", String.class, sid))
                .isEqualTo("주민번호 *** 카드 *** 유심 재발급");
        var afterSummary = contexts.build(
                new ChatProcessingCommand(currentExecution, sid, current, "다음 문의"), 2000);
        // 자유문장 요약은 원문 출처가 없어 문맥에 재투입하지 않는 기존 정책을 유지한다.
        assertThat(afterSummary.summary()).isNull();
        assertThat(afterSummary.history()).extracting(ChatContextMessage::content)
                .contains("주민번호 *** 카드 *** 유심 재발급");
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
        assertThat(analyzedContent.get()).isEqualTo("카드 *** 123원 요금 납부 방법 알려주세요");
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
    @DisplayName("새 FAQ 되묻기 계획·선택지·대기 질문은 욕설 제한 중 보존하고 만료 후 같은 상담에서 완료한다")
    void 새_FAQ_되묻기를_제한_후에도_이어간다() throws Exception {
        ClarificationPlan plan = new ClarificationPlan(List.of(new MissingCondition(
                "delivery_method",
                "청구서는 어떻게 받으시나요?",
                List.of("이메일", "우편"),
                "검증용 고정 근거: 청구서 수령 방식은 이메일 또는 우편이다.")));
        when(answers.clarifies()).thenReturn(true);
        when(answers.prepare(any(), anyBoolean())).thenReturn(new Prepared(List.of(), plan));
        when(answers.generate(any(), any())).thenReturn(GeneratedAnswer.withoutSources(
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        "우편 수령 기준을 안내합니다.",
                        null,
                        List.of(),
                        null)));
        doAnswer(invocation -> {
            ChatProcessingCommand command = invocation.getArgument(0);
            var context = consultContexts.loadVerified(command);
            if (context.candidates().isEmpty()) {
                long id = jdbc.queryForObject(
                        "INSERT INTO consult_requests"
                                + " (session_id,origin_message_id,subquery_order,intent,query_text)"
                                + " VALUES (?,?,1,'FAQ','청구서 수령') RETURNING consult_request_id",
                        Long.class,
                        command.sessionId(),
                        command.inputMessageId());
                return new AnalyzedTurn(
                        turns.prepareAnalysis(command.sessionId(), new IntentSubQueryResponse(
                                id, (short) 1, ConsultRequest.Intent.FAQ, "청구서 수령", Map.of()),
                                LocationStatus.MISSING),
                        null,
                        Purpose.GENERAL_FAQ,
                        command.content(),
                        "청구서 수령");
            }
            var candidate = context.candidates().getFirst();
            var followup = turns.prepareFollowup(
                    context,
                    new Selection(
                            candidate.consultRequestId(),
                            candidate.questionMessageId(),
                            candidate.field(),
                            Map.of(candidate.field(), Condition.filled("우편"))),
                    LocationStatus.MISSING);
            return new AnalyzedTurn(
                    followup.preparation(),
                    followup.followup().answeredField(),
                    followup.purpose(),
                    followup.originalUserQuery(),
                    followup.searchQuery());
        }).when(analyzer).analyze(any());
        long sid = session(identity);
        waitCompleted(send(sid, "청구서 수령 기준 알려주세요").at("/result/executionId").asLong());
        long consultId = jdbc.queryForObject(
                "SELECT consult_request_id FROM consult_requests WHERE session_id=?",
                Long.class,
                sid);
        var before = consultStates.load(sid, consultId);
        var pending = consultStates.findPendingClarificationMessageId(sid, consultId, "delivery_method");
        assertThat(before.status()).isEqualTo("WAITING_CONDITION");
        assertThat(before.clarificationPlan()).isEqualTo(plan);
        assertThat(pending).isPresent();
        send(sid, "씨발");
        send(sid, "ㅅㅂ");
        send(sid, "지랄");
        send(sid, "우편");
        assertThat(consultStates.load(sid, consultId)).isEqualTo(before);
        assertThat(consultStates.findPendingClarificationMessageId(sid, consultId, "delivery_method"))
                .isEqualTo(pending);
        assertThat(count("chat_executions")).isEqualTo(1);
        now.set(START.plusSeconds(60));
        waitCompleted(send(sid, "우편").at("/result/executionId").asLong());
        var completed = consultStates.load(sid, consultId);
        assertThat(completed.conditions().get("delivery_method")).isEqualTo(Condition.filled("우편"));
        assertThat(completed.status()).isEqualTo("DONE");
        assertThat(count("consult_requests")).isEqualTo(1);
        assertThat(count("chat_executions")).isEqualTo(2);
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
                        tokenEstimator,
                        summaryConverter);
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
                                    id,
                                    (short) 1,
                                    ConsultRequest.Intent.STORE,
                                    "매장",
                                    // 업무를 말한 질문으로 보아 지역만 되묻기
                                    Map.of("serviceType", "USIM_REISSUE")),
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("실제 접수의 마스크 출처로 인용 답변을 완료하고 영수증 정리 후 다음 답변에도 유지한다")
    void 마스크_출처가_실제_답변과_다음_턴까지_유지된다(String original, String visible) throws Exception {
        doAnswer(invocation -> AnalyzedTurn.direct(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "확인한 문의: " + visible, null, List.of(visible), null)))
                .when(analyzer).analyze(any());
        long sid = session(identity);
        var sent = send(sid, original);
        waitCompleted(sent.at("/result/executionId").asLong());
        long mid = sent.at("/result/messageId").asLong();
        String positions = jdbc.queryForObject("SELECT input_mask_spans::text FROM chat_messages WHERE message_id=?",
                String.class, mid);
        assertThat(mapper.readTree(positions)).hasSize(1);
        assertThat(positions).doesNotContain("4111", "990101", "1234567");
        jdbc.update("DELETE FROM chat_input_guard_events WHERE session_id=?", sid);

        var next = send(sid, "이전 문의도 확인해 주세요");
        waitCompleted(next.at("/result/executionId").asLong());
        assertThat(history(sid).at("/result/messages").findValuesAsText("content").toString())
                .contains("확인한 문의: " + visible)
                .doesNotContain("4111", "990101", "1234567", "\uFFFC", "inputMaskSpans", "input_mask_spans");
        assertThat(count("chat_input_guard_events")).isZero();
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
