package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.service.LlmClient;
import com.telme.chat.guard.ChatInputInspector;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"chat.end-summary.enabled=true", "chat.end-summary.dispatch-enabled=false",
        "chat.end-summary.scheduler-enabled=false"})
@AutoConfigureMockMvc
class ChatSessionEndSummaryApiIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ChatSessionEndSummaryService service;
    @Autowired
    private ChatSessionEndSummaryStore store;
    @MockitoBean
    private LlmClient client;
    @MockitoBean
    private Clock clock;
    private AtomicReference<Instant> time;

    @BeforeEach
    void 시각과_모델을_준비한다() {
        time = new AtomicReference<>(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        when(clock.instant()).thenAnswer(invocation -> time.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        reset(client);
    }

    @Test
    @DisplayName("종료 후 결과를 저장하고 새로고침 조회와 반복 종료에서 같은 결과를 제공한다")
    void 종료_결과를_저장하고_반복_조회한다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "홈페이지를 확인해 주세요.");
        mockResult(fixture);
        close(fixture);

        assertState(fixture, "PENDING");
        service.process(fixture.sessionId());
        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.topics[0]").value("로밍 신청 방법 문의"));
        close(fixture);
        service.process(fixture.sessionId());
        assertState(fixture, "COMPLETED");
        verify(client).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_session_end_summaries WHERE session_id=?",
                Integer.class, fixture.sessionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",
                Integer.class, fixture.sessionId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT summary FROM chat_sessions WHERE session_id=?",
                String.class, fixture.sessionId())).isEqualTo("기존 내부 기억");
    }

    @Test
    @DisplayName("진행 중 답변이 끝날 때까지 대기하고 마지막으로 저장한 답변을 포함한다")
    void 진행_중인_마지막_답변을_기다린다() throws Exception {
        var fixture = fixture(true, "로밍 신청 방법", null);
        close(fixture);
        service.process(fixture.sessionId());
        assertState(fixture, "PENDING");
        verify(client, never()).generate(any());

        long answer = message(fixture.sessionId(), 2, "ASSISTANT", "ANSWER", "마지막 안내 내용",
                fixture.questionId());
        jdbc.update("UPDATE chat_executions SET status='COMPLETED', output_message_id=?, ended_at=? "
                + "WHERE session_id=?", answer, Timestamp.from(time.get()), fixture.sessionId());
        var completed = new Fixture(fixture.actor(), fixture.sessionId(), fixture.questionId(), answer);
        mockResult(completed);
        service.process(fixture.sessionId());
        assertState(fixture, "COMPLETED");
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("마지막 안내 내용");
    }

    @Test
    @DisplayName("인사와 고정 응답만 있는 상담은 모델 호출 없이 빈 결과를 제공한다")
    void 인사만_있으면_모델을_호출하지_않는다() throws Exception {
        var fixture = fixture(false, "안녕하세요", "안녕하세요. 무엇을 도와드릴까요?");
        close(fixture);
        service.process(fixture.sessionId());
        assertState(fixture, "EMPTY");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("정상 상담 뒤 차단과 오류 원문은 모델 입력에서 제외한다")
    void 차단과_오류_원문은_입력에서_제외한다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "홈페이지 안내");
        message(fixture.sessionId(), 3, "USER", "BLOCKED", "제외할 차단 원문", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ERROR", "제외할 오류 원문", null);
        mockResult(fixture);
        close(fixture);
        service.process(fixture.sessionId());

        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        assertThat(captor.getValue().userPrompt()).doesNotContain("제외할 차단 원문", "제외할 오류 원문");
    }

    @Test
    @DisplayName("과거 원문의 번호를 다시 마스킹한 입력만 모델에 전달한다")
    void 저장된_원문_번호는_모델에_전달하지_않는다() throws Exception {
        var fixture = fixture(false, "번호 900101-1000000이고 로밍 신청 방법 문의", "신청 경로 안내");
        mockResult(fixture);
        close(fixture);
        service.process(fixture.sessionId());

        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("***");
        assertThat(captor.getValue().userPrompt()).doesNotContain("900101-1000000");
    }

    @Test
    @DisplayName("잘못된 생성 결과는 상담 종료를 유지하고 안전한 실패 상태만 저장한다")
    void 생성_실패가_종료를_되돌리지_않는다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "안내 내용");
        when(client.generate(any())).thenReturn("잘못된 후보 원문");
        close(fixture);
        service.process(fixture.sessionId());
        assertState(fixture, "FAILED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.sessionStatus").value("CLOSED"))
                .andExpect(jsonPath("$.result.summary").doesNotExist())
                .andExpect(jsonPath("$.result.message").value("상담 요약을 제공하지 못했습니다. 대화 기록을 확인해 주세요."));
        assertThat(jdbc.queryForObject("SELECT result::text FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId())).isNull();
    }

    @Test
    @DisplayName("다른 사용자는 결과를 읽거나 원래 상담을 종료할 수 없다")
    void 다른_사용자의_요약은_조회하지_못한다() throws Exception {
        var fixture = fixture(false, "문의", "안내");
        close(fixture);
        mvc.perform(get(path(fixture)).session(new MockHttpSession()))
                .andExpect(status().isNotFound());
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("작업 소유권이 바뀌면 중단 전 작업의 늦은 결과를 저장하지 않는다")
    void 오래된_작업_결과는_덮어쓰지_않는다() throws Exception {
        var fixture = fixture(false, "문의", "안내");
        close(fixture);
        var old = store.claim(fixture.sessionId()).orElseThrow();
        time.set(time.get().plusSeconds(301));
        var current = store.claim(fixture.sessionId()).orElseThrow();

        assertThat(store.finish(old, null, null, "EMPTY", null)).isFalse();
        assertThat(store.finish(current, null, null, "EMPTY", null)).isTrue();
        assertState(fixture, "EMPTY");
    }

    @Test
    @DisplayName("진행 중 실행이 계속 남아도 요약 요청은 무한 대기하지 않는다")
    void 대기_기한이_지나면_안전하게_실패한다() throws Exception {
        var fixture = fixture(true, "문의", null);
        close(fixture);
        time.set(time.get().plusSeconds(901));
        service.process(fixture.sessionId());
        assertState(fixture, "FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM chat_executions WHERE session_id=?",
                String.class, fixture.sessionId())).isEqualTo("RUNNING");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("제어 가능한 느린 모델에서도 반복 처리와 조회는 생성 호출을 중복하지 않는다")
    void 느린_모델_호출을_중복하지_않는다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "안내 내용");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.generate(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1);
        });
        close(fixture);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> service.process(fixture.sessionId()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertState(fixture, "GENERATING");
                service.process(fixture.sessionId());
            } finally {
                release.countDown();
            }
            running.get(10, TimeUnit.SECONDS);
        }
        assertState(fixture, "COMPLETED");
        verify(client).generate(any());
    }

    @Test
    @DisplayName("활성 상담 조회와 이전 종료 상담 조회는 요약 요청을 만들지 않는다")
    void 조회는_요청을_생성하지_않는다() throws Exception {
        var fixture = fixture(false, "문의", "안내");
        assertState(fixture, "NOT_REQUESTED");
        jdbc.update("UPDATE chat_sessions SET status='CLOSED' WHERE session_id=?", fixture.sessionId());
        close(fixture);
        assertState(fixture, "NOT_REQUESTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_session_end_summaries WHERE session_id=?",
                Integer.class, fixture.sessionId())).isZero();
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("생성된 API 명세는 종료 요약의 상태 조회와 응답 필드를 포함한다")
    void 생성된_API_명세를_확인한다() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        var operation = json.path("paths").path("/api/v1/chat/sessions/{sessionId}/summary").path("get");
        assertThat(operation.path("description").asText()).contains("COMPLETED", "조회");
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    @Test
    @DisplayName("로그인 승계 뒤 같은 상담의 요약을 회원이 조회하고 옛 게스트는 읽지 못한다")
    void 승계된_회원이_요약을_조회한다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "안내 내용");
        mockResult(fixture);
        close(fixture);
        service.process(fixture.sessionId());
        long user = jdbc.queryForObject("INSERT INTO users DEFAULT VALUES RETURNING user_id", Long.class);
        var guest = fixture.actor().getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        jdbc.update("UPDATE guests SET merged_user_id=? WHERE guest_id=?", user, guest);
        jdbc.update("UPDATE chat_sessions SET user_id=? WHERE session_id=?", user, fixture.sessionId());
        var member = new MockHttpSession();
        member.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user);

        mvc.perform(get(path(fixture)).session(member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"));
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("복합 질문의 완료 안내와 남은 되묻기를 한 메시지에서 함께 요약한다")
    void 복합_질문의_답변과_남은_사항을_구분한다() throws Exception {
        var fixture = fixture(false, "로밍 방법과 유심 재발급 문의", "로밍 안내와 유심 추가 조건 질문");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?",
                fixture.answerId());
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', '유심 재발급 문의', 'WAITING_CONDITION')
                """, fixture.sessionId(), fixture.questionId());
        String output = ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1);
        output = output.replace("\"pendingItems\":[]", "\"pendingItems\":[{\"text\":\"유심 추가 조건 확인이 남았습니다.\","
                + "\"sourceMessageIds\":[" + fixture.questionId() + "]}]");
        when(client.generate(any())).thenReturn(output);
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("확인 필요: 유심 재발급 문의"));
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE session_id=?",
                String.class, fixture.sessionId())).isEqualTo("WAITING_CONDITION");
    }

    @Test
    @DisplayName("응답하지 않은 조건만 남은 질문으로 전달하고 안내와 구분한다")
    void 미응답_조건을_구체적으로_전달한다() throws Exception {
        var fixture = fixture(false, "유심 재발급 서류 문의", "대리인이 방문하시는 건가요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?",
                fixture.answerId());
        long request = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,
                    status,clarification_plan)
                VALUES (?, ?, 1, 'FAQ', '유심 재발급 서류 문의', 'WAITING_CONDITION', CAST(? AS jsonb))
                RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId(), """
                {"conditions":[{"key":"agent_visit","question":"대리인이 방문하시는 건가요?"},
                    {"key":"self_visit","question":"본인이 방문하시는 건가요?"}]}
                """);
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,status,asked_message_id)
                VALUES (?, 'agent_visit', 'PENDING', ?), (?, 'self_visit', 'FILLED', NULL)
                """, request, fixture.answerId(), request);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"유심 재발급 서류 문의"},"selectedSentenceIds":[],
                    "pendingItems":[{"text":"대리인 방문 여부 확인 필요","sourceMessageIds":[%d]}]}
                """.formatted(fixture.questionId(), fixture.questionId()));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.guidance").isEmpty())
                .andExpect(jsonPath("$.result.summary.pendingItems[0]")
                        .value("미확인 조건: 유심 재발급 서류 문의 — 대리인이 방문하시는 건가요?"));
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        assertThat(mapper.readTree(captor.getValue().userPrompt()).path("requests").get(0)
                .path("pendingQuestions").toString()).isEqualTo("[\"대리인이 방문하시는 건가요?\"]");
        assertThat(jdbc.queryForObject("SELECT status FROM consult_requests WHERE consult_request_id=?",
                String.class, request)).isEqualTo("WAITING_CONDITION");
    }

    @Test
    @DisplayName("최신 조건값과 정정한 고객 메시지를 종료 요약 입력에 함께 전달한다")
    void 최신_조건과_정정_출처를_전달한다() throws Exception {
        var fixture = fixture(false, "가입월에 따른 요금제 변경 문의", "지난달 가입 기준 안내");
        long correction = message(fixture.sessionId(), 3, "USER", "QUESTION",
                "지난달이 아니라 이번 달이에요", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "이번 달 가입 기준 안내", correction);
        long request = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', '요금제 변경 문의', 'DONE') RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId());
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,condition_value,source,status,
                    answered_message_id) VALUES (?, 'joined_this_month', 'true', 'ASKED', 'FILLED', ?)
                """, request, correction);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 2));
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        var input = mapper.readTree(captor.getValue().userPrompt());
        assertThat(input.path("currentConditions").get(0).path("value").asText()).isEqualTo("true");
        assertThat(input.path("currentConditions").get(0).path("answeredSequence").intValue()).isEqualTo(3);
        assertThat(input.path("conditionSources").get(0).path("content").asText()).contains("이번 달");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("조건 확인 후 안내: 이번 달 가입 기준 안내"));
    }

    @Test
    @DisplayName("정상 상담 후 감사 인사로 끝나도 실제 안내를 빈 상담으로 바꾸지 않는다")
    void 감사_인사가_기존_안내를_없애지_않는다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법", "홈페이지를 확인해 주세요.");
        long thanks = message(fixture.sessionId(), 3, "USER", "QUESTION", "감사합니다", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "천만에요. 더 궁금한 점이 있으면 말씀해 주세요.", thanks);
        mockResult(fixture);
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("안내받은 내용: 홈페이지를 확인해 주세요."));
    }

    @Test
    @DisplayName("같은 복합 질문의 하위 상담마다 미확인 조건을 분리해 보존한다")
    void 같은_원문이어도_하위_상담의_조건을_섞지_않는다() throws Exception {
        var fixture = fixture(false, "로밍과 유심 서류 문의", "로밍 접수 경로를 안내합니다.");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?",
                fixture.answerId());
        long roaming = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,
                    status,clarification_plan) VALUES (?, ?, 1, 'FAQ', '로밍 문의', 'WAITING_CONDITION', CAST(? AS jsonb))
                RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId(), """
                {"conditions":[{"key":"country","question":"어느 나라로 가시나요?"}]}
                """);
        long sim = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,
                    status,clarification_plan)
                VALUES (?, ?, 2, 'FAQ', '유심 서류 문의', 'WAITING_CONDITION', CAST(? AS jsonb))
                RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId(), """
                {"conditions":[{"key":"self_visit","question":"본인이 방문하시나요?"}]}
                """);
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,status,asked_message_id)
                VALUES (?, 'country', 'PENDING', ?), (?, 'self_visit', 'PENDING', ?)
                """, roaming, fixture.answerId(), sim, fixture.answerId());
        mockResult(fixture);
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        var requests = mapper.readTree(captor.getValue().userPrompt()).path("requests");
        assertThat(requests.get(0).path("pendingQuestions").toString()).isEqualTo("[\"어느 나라로 가시나요?\"]");
        assertThat(requests.get(1).path("pendingQuestions").toString()).isEqualTo("[\"본인이 방문하시나요?\"]");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(2));
    }

    @Test
    @DisplayName("선택 8개 뒤 최신 조건 안내를 보정해도 요약이 실패하지 않고 저장·조회된다")
    void 여덟_선택과_최신_안내를_안전하게_저장한다() throws Exception {
        String earlier = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> "이전 조건 안내 " + index + "입니다.")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        var fixture = fixture(false, "가입월에 따른 요금제 변경 문의", earlier);
        long correction = message(fixture.sessionId(), 3, "USER", "QUESTION", "이번 달 가입입니다.", null);
        long latestAnswer = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "이번 달 조건 안내입니다.", correction);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id IN (?,?)",
                fixture.answerId(), latestAnswer);
        long request = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', '요금제 변경 문의', 'DONE') RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId());
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,condition_value,source,status,
                    answered_message_id) VALUES (?, 'joined_this_month', 'true', 'ASKED', 'FILLED', ?)
                """, request, correction);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(
                fixture.questionId(), java.util.stream.IntStream.rangeClosed(1, 8).toArray()));
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.guidance.length()").value(8))
                .andExpect(jsonPath("$.result.summary.guidance[7]")
                        .value("조건 확인 후 안내: 이번 달 조건 안내입니다."));
        var stored = mapper.readTree(jdbc.queryForObject(
                "SELECT result::text FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId()));
        assertThat(stored.path("guidance")).hasSize(8);
        assertThat(stored.path("guidance").get(7).path("sourceMessageIds").get(0).asLong())
                .isEqualTo(latestAnswer);
    }

    @Test
    @DisplayName("DB에 저장된 필수 확인 항목 9개도 종료 요약의 저장·조회에서 누락하지 않는다")
    void 아홉_필수_확인_항목을_모두_저장한다() throws Exception {
        var fixture = fixture(false, "로밍 조건 문의", "홈페이지를 확인해 주세요.");
        for (int index = 1; index <= 9; index++) {
            long request = jdbc.queryForObject("""
                    INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,
                        status,clarification_plan) VALUES (?, ?, ?, 'FAQ', ?, 'WAITING_CONDITION', ?::jsonb)
                    RETURNING consult_request_id
                    """, Long.class, fixture.sessionId(), fixture.questionId(), index, "로밍 확인 " + index,
                    "{\"conditions\":[{\"key\":\"country\",\"question\":\"어느 나라로 가시나요?\"}]}");
            jdbc.update("""
                    INSERT INTO consult_conditions(consult_request_id,condition_key,status,asked_message_id)
                    VALUES (?, 'country', 'PENDING', ?)
                    """, request, fixture.answerId());
        }
        var model = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        model.withArray("pendingItems").addObject().put("text", "목적지 확인 필요")
                .putArray("sourceMessageIds").add(fixture.questionId());
        when(client.generate(any())).thenReturn(model.toString());
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(9));
        var stored = mapper.readTree(jdbc.queryForObject(
                "SELECT result::text FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId()));
        assertThat(stored.path("pendingItems")).hasSize(9);
        assertThat(stored.path("pendingItems")).allSatisfy(item ->
                assertThat(item.path("sourceMessageIds").get(0).asLong()).isEqualTo(fixture.questionId()));
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        assertThat(mapper.valueToTree(captor.getValue().responseSchema()).path("properties").path("pendingItems")
                .path("maxItems").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("일반 생성 오류의 진단 로그를 추가해도 안전한 실패 저장·조회 계약을 유지한다")
    void 진단_로그가_생성_실패_응답을_바꾸지_않는다() throws Exception {
        var fixture = fixture(false, "로밍 문의", "신청 경로 안내");
        when(client.generate(any())).thenThrow(new IllegalStateException("합성 오류 990101-1234567"));
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT failure_code FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId())).isEqualTo("GENERATION_FAILED");
        var response = mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary").doesNotExist()).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("990101-1234567", "합성 오류");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("저장된 서버 마스크 인용과 미확인 조건을 종료 요약에서 완료·조회한다")
    void 저장된_마스크_출처로_종료_요약을_완료한다(String original, String visible) throws Exception {
        var inspection = new ChatInputInspector(mapper).inspect(original);
        var fixture = fixture(false, visible, "가입 시점이 확인되어야 안내할 수 있습니다.");
        jdbc.update("UPDATE chat_messages SET input_mask_spans=?::jsonb WHERE message_id=?",
                mapper.writeValueAsString(inspection.maskSpans()), fixture.questionId());
        long request = jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', ?, 'WAITING_CONDITION') RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId(), visible);
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,status)
                VALUES (?, 'joined_this_month', 'PENDING')
                """, request);
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        response.withObject("topics").put(Long.toString(fixture.questionId()), visible);
        when(client.generate(any())).thenReturn(response.toString());
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        var result = mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(visible).doesNotContain("4111", "990101", "1234567", "\uFFFC", "input_mask_spans");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "씨 990101-1234567 발 요금 안내입니다. | 씨 *** 발 요금 안내입니다.",
        "개 4111111111111111 새끼 유심 안내입니다. | 개 *** 새끼 유심 안내입니다."
    })
    @DisplayName("과거 안내 원문을 읽으며 새로 가린 마스크 위치도 종료 요약 저장·조회까지 보존한다")
    void 과거_안내의_첫_마스크_검사_결과를_보존한다(String original, String expected) throws Exception {
        var fixture = fixture(false, "유심 요금 문의", original);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        mockResult(fixture);
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        var result = mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(expected).doesNotContain("990101", "1234567", "4111", "\\uFFFC");
        var prompt = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(prompt.capture());
        assertThat(prompt.getValue().userPrompt()).contains(expected).doesNotContain("990101", "1234567", "4111");
    }

    @Test
    @DisplayName("기록된 하위 질문 결과로 복합 답변의 안내 문구와 답하지 못한 하위 질문을 나눈다")
    void 기록된_결과로_안내와_답하지_못한_하위_질문을_나눈다() throws Exception {
        var fixture = fixture(false, "로밍 신청 방법이랑 유심 재발급 비용", "1. 로밍 신청 방법\n홈페이지에서 신청할 수 있습니다."
                + "\n\n2. 유심 재발급 비용\n" + com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "ANSWERED", "홈페이지에서 신청할 수 있습니다.", fixture.answerId(), "로밍 신청 방법");
        request(fixture, 2, "DONE", "NO_EVIDENCE", null, fixture.answerId(), "유심 재발급 비용");
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        close(fixture);
        service.process(fixture.sessionId());

        var prompt = mapper.readTree(captureRequest().userPrompt());
        assertThat(prompt.path("guidanceCandidates").toString()).contains("홈페이지에서 신청할 수 있습니다.")
                .doesNotContain("1. 로밍", com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_MESSAGE);
        assertThat(prompt.path("pendingIds").toString()).isEqualTo("[" + fixture.questionId() + "]");
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("답변을 받지 못한 문의: 유심 재발급 비용"));
    }

    @Test
    @DisplayName("기록된 결과가 있으면 실제로 물어본 질문만 미확인 조건으로 적고 범위 밖 잡담은 문의로 만들지 않는다")
    void 물어본_질문만_미확인_조건이고_범위_밖_잡담은_뺀다() throws Exception {
        var fixture = fixture(false, "번호이동 방법이랑 근처 매장",
                "1. 번호이동 방법\n신분증을 챙기세요.\n\n2. 근처 매장\n어느 지역에서 찾으세요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "ANSWERED", "신분증을 챙기세요.", fixture.answerId(), "번호이동 방법");
        long store = request(fixture, 2, "WAITING_CONDITION", "ASKED", "어느 지역에서 찾으세요?", fixture.answerId(),
                "근처 매장");
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,status,asked_message_id)
                VALUES (?, 'location', 'PENDING', ?), (?, 'serviceType', 'PENDING', NULL)
                """, store, fixture.answerId(), store);
        long chat = message(fixture.sessionId(), 3, "USER", "QUESTION", "오늘 날씨 어때요", null);
        long outOfScope = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "저는 통신 서비스와 매장 관련 질문을 도와드려요.",
                chat);
        jdbc.update("UPDATE chat_messages SET answer_basis='OUT_OF_SCOPE' WHERE message_id=?", outOfScope);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        close(fixture);
        service.process(fixture.sessionId());

        // 범위 밖 발화는 정정 맥락을 위해 입력에 남지만 남은 사항·주제 대상이 아니다
        var prompt = mapper.readTree(captureRequest().userPrompt());
        assertThat(prompt.path("pendingIds").toString()).isEqualTo("[" + fixture.questionId() + "]");
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("안내받은 내용: 신분증을 챙기세요."))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("미확인 조건: 근처 매장 — 어느 지역에서 찾으세요?"));
    }

    @Test
    @DisplayName("기록된 상담에서 상담 요청 없이 주제 확인 안내만 받은 질문은 모델이 주제를 붙이면 그 주제로 남은 문의에 적고 안내로 쓰지 않는다")
    void 주제_확인_안내만_받은_질문은_남은_문의다() throws Exception {
        var fixture = fixture(false, "요금 납부일 바꾸고 싶어요", "매월 말일로 바꿀 수 있습니다.");
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "ANSWERED", "매월 말일로 바꿀 수 있습니다.", fixture.answerId(), null);
        long vague = message(fixture.sessionId(), 3, "USER", "QUESTION", "그거 말고 그거요", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "어떤 내용에 대한 질문인지 확인이 필요합니다. 상품이나 상담 주제를 조금 더 구체적으로 알려주세요.", vague);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"요금 납부일 변경","%d":"납부일 외 다른 문의"},"selectedSentenceIds":[1],"pendingItems":[]}"""
                .formatted(fixture.questionId(), vague));
        close(fixture);
        service.process(fixture.sessionId());

        var prompt = mapper.readTree(captureRequest().userPrompt());
        assertThat(prompt.path("guidanceCandidates").toString()).doesNotContain("확인이 필요합니다");
        assertThat(prompt.path("pendingIds").toString()).isEqualTo("[" + vague + "]");
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("답변을 받지 못한 문의: 납부일 외 다른 문의"));
    }

    @Test
    @DisplayName("상담 요청이 하나도 없는 상담에서 인사와 범위 밖 잡담만 있으면 요약하지 않는다")
    void 인사와_범위_밖_잡담만_있으면_빈_상담이다() throws Exception {
        var fixture = fixture(false, "안녕하세요", ChatPersona.GREETING);
        long chat = message(fixture.sessionId(), 3, "USER", "QUESTION", "그냥 한번 눌러본거예요", null);
        long outOfScope = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "저는 통신 서비스와 매장 관련 질문을 도와드려요.",
                chat);
        jdbc.update("UPDATE chat_messages SET answer_basis='OUT_OF_SCOPE' WHERE message_id=?", outOfScope);
        close(fixture);
        service.process(fixture.sessionId());

        assertState(fixture, "EMPTY");
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("감사·작별 인사가 후속 질문으로 접수돼 근거 없음으로 끝나도 문의로 요약하지 않는다")
    void 감사_인사는_후속_질문으로_접수돼도_문의가_아니다() throws Exception {
        var fixture = fixture(false, "요금 납부일 바꾸고 싶어요", "매월 말일로 바꿀 수 있습니다.");
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "ANSWERED", "매월 말일로 바꿀 수 있습니다.", fixture.answerId(), null);
        long thanks = message(fixture.sessionId(), 3, "USER", "QUESTION", "네 감사합니다 수고하세요~", null);
        long reply = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER, thanks);
        jdbc.update("UPDATE chat_messages SET answer_basis='NO_EVIDENCE' WHERE message_id=?", reply);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_message_id) VALUES (?, ?, 1, 'FAQ', '감사 인사', 'DONE', 'NO_EVIDENCE', ?)
                """, fixture.sessionId(), thanks, reply);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        close(fixture);
        service.process(fixture.sessionId());

        var prompt = mapper.readTree(captureRequest().userPrompt());
        assertThat(prompt.toString()).doesNotContain("수고하세요");
        assertThat(prompt.path("pendingIds").toString()).isEqualTo("[]");
        assertState(fixture, "COMPLETED");
    }

    @Test
    @DisplayName("정정한 문의를 모델이 같은 주제로 묶으면 답하지 못한 문의를 정정 뒤 값의 주제로 한 번만 적는다")
    void 정정한_문의는_정정_뒤_주제로_한_번만_남긴다() throws Exception {
        var fixture = fixture(false, "일본 5일 로밍 얼마예요", com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        jdbc.update("UPDATE chat_messages SET answer_basis='NO_EVIDENCE' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "NO_EVIDENCE", null, fixture.answerId(), null);
        long correction = message(fixture.sessionId(), 3, "USER", "QUESTION", "아 아니다 일주일 가요", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "어떤 내용에 대한 질문인지 확인이 필요합니다. 상품이나 상담 주제를 조금 더 구체적으로 알려주세요.", correction);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"일본 일주일 로밍 요금","%d":"일본 일주일 로밍 요금"},"selectedSentenceIds":[],
                 "pendingItems":[]}""".formatted(fixture.questionId(), correction));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.topics.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("답변을 받지 못한 문의: 일본 일주일 로밍 요금"));
    }

    @Test
    @DisplayName("되묻다 취소된 문의가 같은 주제의 뒤 질문에서 답을 받았으면 남은 사항에 넣지 않는다")
    void 뒤에서_답을_받은_취소_문의는_남은_사항이_아니다() throws Exception {
        var fixture = fixture(false, "eSIM으로 바꿀수 있나요?", "사용하시는 단말기가 eSIM을 지원하나요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "CANCELLED", "ASKED", "사용하시는 단말기가 eSIM을 지원하나요?", fixture.answerId(), null);
        long next = message(fixture.sessionId(), 3, "USER", "QUESTION", "그거 아이폰도 돼요?", null);
        long answer = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "아이폰도 eSIM을 쓸 수 있습니다.", next);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", answer);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id)
                VALUES (?, ?, 1, 'FAQ', '아이폰 eSIM 지원', 'DONE', 'ANSWERED', '아이폰도 eSIM을 쓸 수 있습니다.', ?)
                """, fixture.sessionId(), next, answer);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"eSIM 전환","%d":"eSIM 전환"},"selectedSentenceIds":[1],"pendingItems":[]}"""
                .formatted(fixture.questionId(), next));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(0));
    }

    @Test
    @DisplayName("하위 질문이 하나인 문의의 남은 사항은 고객 원문 대신 정정 뒤 값이 담긴 주제로 적는다")
    void 남은_사항은_정정_뒤_주제로_적는다() throws Exception {
        var fixture = fixture(false, "70대 어머니 쓰실 요금제 추천좀 해주세요", "나이에 따라 추천하는 요금제가 달라져요. 나이대가 어떻게 되세요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?", fixture.answerId());
        long correction = message(fixture.sessionId(), 3, "USER", "QUESTION", "아 잘못말했다 엄마 올해 65세세요", null);
        long asked = message(fixture.sessionId(), 4, "ASSISTANT", "CLARIFICATION", "데이터를 많이 쓰시는 편인가요?", correction);
        request(fixture, 1, "WAITING_CONDITION", "ASKED", "데이터를 많이 쓰시는 편인가요?", asked, null);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"65세 어머니 요금제 추천"},"selectedSentenceIds":[],"pendingItems":[]}"""
                .formatted(fixture.questionId()));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]")
                        .value("미확인 조건: 65세 어머니 요금제 추천 — 데이터를 많이 쓰시는 편인가요?"));
    }

    @Test
    @DisplayName("매장 찾기를 되묻는 중에 바로 다시 매장 찾기를 하면 지역 정정으로 보고 앞 요청을 남기지 않는다")
    void 매장_지역_정정은_앞_요청을_남기지_않는다() throws Exception {
        var fixture = fixture(false, "부산 서면쪽 매장 알려주세요", "어떤 업무로 매장을 찾으시나요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?", fixture.answerId());
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id)
                VALUES (?, ?, 1, 'STORE', '부산 서면 매장', 'CANCELLED', 'ASKED', '어떤 업무로 매장을 찾으시나요?', ?)
                """, fixture.sessionId(), fixture.questionId(), fixture.answerId());
        long correction = message(fixture.sessionId(), 3, "USER", "QUESTION", "아 서면 말고 해운대요", null);
        long asked = message(fixture.sessionId(), 4, "ASSISTANT", "CLARIFICATION", "어떤 업무로 매장을 찾으시나요?",
                correction);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id)
                VALUES (?, ?, 1, 'STORE', '해운대 매장', 'WAITING_CONDITION', 'ASKED', '어떤 업무로 매장을 찾으시나요?', ?)
                """, fixture.sessionId(), correction, asked);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"부산 서면 매장 위치","%d":"해운대 매장 위치"},"selectedSentenceIds":[],"pendingItems":[]}"""
                .formatted(fixture.questionId(), correction));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems[0]")
                        .value("미확인 조건: 해운대 매장 위치 — 어떤 업무로 매장을 찾으시나요?"));
    }

    @Test
    @DisplayName("정정한 뒤 질문이 답을 받았으면 같은 주제의 정정 전 질문을 남은 사항에 넣지 않는다")
    void 정정_뒤_답을_받으면_정정_전_문의는_해결이다() throws Exception {
        var fixture = fixture(false, "지난달 요금 청구서 다시 볼수 있어요?",
                com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        jdbc.update("UPDATE chat_messages SET answer_basis='NO_EVIDENCE' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "NO_EVIDENCE", null, fixture.answerId(), null);
        long next = message(fixture.sessionId(), 3, "USER", "QUESTION", "아 지난달 말고 3개월 전꺼요", null);
        long answer = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "홈페이지에서 최근 3년 이내 청구서를 볼 수 있습니다.",
                next);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", answer);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id)
                VALUES (?, ?, 1, 'FAQ', '3개월 전 청구서', 'DONE', 'ANSWERED', '홈페이지에서 최근 3년 이내 청구서를 볼 수 있습니다.', ?)
                """, fixture.sessionId(), next, answer);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"3개월 전 요금 청구서 조회","%d":"3개월 전 요금 청구서 조회"},"selectedSentenceIds":[1],
                 "pendingItems":[]}""".formatted(fixture.questionId(), next));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.topics.length()").value(1))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(0));
    }

    @Test
    @DisplayName("상담 요청 없이 주제 확인 안내만 받은 잡담은 모델이 주제를 붙이지 않으면 남은 사항이 아니다")
    void 주제_없는_잡담은_남은_사항이_아니다() throws Exception {
        var fixture = fixture(false, "데이터 선물하기 어케함", "앱에서 받는 분 번호와 용량을 입력하면 됩니다.");
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "ANSWERED", "앱에서 받는 분 번호와 용량을 입력하면 됩니다.", fixture.answerId(), null);
        long chat = message(fixture.sessionId(), 3, "USER", "QUESTION", "점심 뭐먹을지 추천좀 ㅋㅋ", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "어떤 내용에 대한 질문인지 확인이 필요합니다. 상품이나 상담 주제를 조금 더 구체적으로 알려주세요.", chat);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(fixture.questionId(), 1));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(0));
    }

    @Test
    @DisplayName("되묻는 중에 한 새 질문이 다시 묻기만 받았으면 모델이 주제를 붙인 경우 남은 문의로 적는다")
    void 다시_묻기만_받은_새_질문은_남은_문의다() throws Exception {
        var fixture = fixture(false, "신촌 근처 매장 알려주세요", "어떤 업무로 매장을 찾으시나요?");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "WAITING_CONDITION", "ASKED", "어떤 업무로 매장을 찾으시나요?", fixture.answerId(), null);
        long next = message(fixture.sessionId(), 3, "USER", "QUESTION", "유심 비번 까먹었는데 어떡해요", null);
        message(fixture.sessionId(), 4, "ASSISTANT", "CLARIFICATION",
                "답변을 잘 이해하지 못했어요. 다시 한 번 알려주세요.\n어떤 업무로 매장을 찾으시나요?", next);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"신촌 근처 매장","%d":"유심 비밀번호 분실"},"selectedSentenceIds":[],"pendingItems":[]}"""
                .formatted(fixture.questionId(), next));
        close(fixture);
        service.process(fixture.sessionId());

        assertThat(mapper.readTree(captureRequest().userPrompt()).path("pendingIds").toString())
                .isEqualTo("[" + fixture.questionId() + "," + next + "]");
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(2))
                .andExpect(jsonPath("$.result.summary.pendingItems[1]").value("답변을 받지 못한 문의: 유심 비밀번호 분실"));
    }

    @Test
    @DisplayName("감사 인사로 접수된 요청에 실제 안내가 나갔으면 인사로 빼지 않고 그 안내를 남긴다")
    void 안내가_나간_감사_인사는_빼지_않는다() throws Exception {
        var fixture = fixture(false, "해외에서 폰 잃어버리면 어떻게 해요?", com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        jdbc.update("UPDATE chat_messages SET answer_basis='NO_EVIDENCE' WHERE message_id=?", fixture.answerId());
        request(fixture, 1, "DONE", "NO_EVIDENCE", null, fixture.answerId(), null);
        long thanks = message(fixture.sessionId(), 3, "USER", "QUESTION", "ㅇㅋ 땡큐", null);
        long answer = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER", "현지 통신사에 연락해 이용 정지를 하세요.", thanks);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", answer);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id)
                VALUES (?, ?, 1, 'FAQ', '해외 분실 조치', 'DONE', 'ANSWERED', '현지 통신사에 연락해 이용 정지를 하세요.', ?)
                """, fixture.sessionId(), thanks, answer);
        when(client.generate(any())).thenReturn("""
                {"topics":{"%d":"해외 폰 분실 조치","%d":"해외 폰 분실 조치"},"selectedSentenceIds":[1],"pendingItems":[]}"""
                .formatted(fixture.questionId(), thanks));
        close(fixture);
        service.process(fixture.sessionId());

        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("안내받은 내용: 현지 통신사에 연락해 이용 정지를 하세요."))
                .andExpect(jsonPath("$.result.summary.pendingItems.length()").value(0));
    }

    private long request(Fixture fixture, int order, String status, String outcome, String text, Long messageId,
            String display) {
        return jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status,
                    answer_outcome,answer_text,answer_message_id,display_question)
                VALUES (?, ?, ?, 'FAQ', ?, ?, ?, ?, ?, ?) RETURNING consult_request_id
                """, Long.class, fixture.sessionId(), fixture.questionId(), order,
                display == null ? "하위 질문" : display.replace(" ", ""), status, outcome, text, messageId, display);
    }

    private com.telme.llm.dto.req.LlmRequest captureRequest() {
        var captor = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(captor.capture());
        return captor.getValue();
    }

    private Fixture fixture(boolean running, String question, String answer) throws Exception {
        var actor = new MockHttpSession();
        var response = mvc.perform(post("/api/v1/chat/sessions").session(actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).path("result").path("sessionId")
                .longValue();
        jdbc.update("UPDATE chat_sessions SET summary='기존 내부 기억' WHERE session_id=?", id);
        long q = message(id, 1, "USER", "QUESTION", question, null);
        Long a = answer == null ? null : message(id, 2, "ASSISTANT", "ANSWER", answer, q);
        jdbc.update("""
                INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                VALUES (?,?,?,?,?)
                """, id, q, a, running ? "RUNNING" : "COMPLETED",
                running ? null : Timestamp.from(time.get()));
        return new Fixture(actor, id, q, a);
    }

    private long message(long session, int sequence, String role, String type, String text, Long reply) {
        return jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,reply_to_id,
                    status,completed_at) VALUES (?,?,?,?,?,?,'COMPLETED',?) RETURNING message_id
                """, Long.class, session, sequence, role, type, text, reply, Timestamp.from(time.get()));
    }

    private void mockResult(Fixture fixture) {
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(
                fixture.questionId(), 1));
    }

    private void close(Fixture fixture) throws Exception {
        mvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", fixture.sessionId()).session(fixture.actor()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CLOSED"));
    }

    private String path(Fixture fixture) {
        return "/api/v1/chat/sessions/" + fixture.sessionId() + "/summary";
    }

    private void assertState(Fixture fixture, String expected) throws Exception {
        mvc.perform(get(path(fixture)).session(fixture.actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summaryStatus").value(expected));
    }

    private record Fixture(MockHttpSession actor, long sessionId, long questionId, Long answerId) {}
}
