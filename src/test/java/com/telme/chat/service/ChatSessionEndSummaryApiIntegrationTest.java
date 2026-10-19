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
import com.telme.chat.repository.ChatEndSummaryInquiryRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
    @Autowired
    private ChatEndSummaryInquiryRepository inquiryRepository;
    @MockitoBean
    private LlmClient client;
    @MockitoBean
    private Clock clock;
    private AtomicReference<Instant> time;
    private final List<Long> createdCategoryFaqs = new ArrayList<>();

    @AfterEach
    void 분류_검증에서_생성한_근거만_정리한다() {
        // 다른 FAQ 테스트의 검색·집계에 이번 테스트의 합성 근거가 섞이지 않도록 한다.
        for (long faq : createdCategoryFaqs) {
            jdbc.update("DELETE FROM message_sources WHERE faq_id=?", faq);
            jdbc.update("DELETE FROM faqs WHERE faq_id=?", faq);
        }
    }

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
                .andExpect(jsonPath("$.result.summary.topics[0]").value("해외 로밍 문의"));
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
                .andExpect(jsonPath("$.result.summary.pendingItems[0]").value("추가 확인이 완료되지 않은 문의: ‘유심 재발급 문의’"));
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
                        .value("방문자가 본인인지 대리인인지 아직 확인되지 않았습니다."));
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
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("이번 달 가입 기준 안내"));
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
                .andExpect(jsonPath("$.result.summary.guidance[0]").value("홈페이지를 확인해 주세요."));
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
        selectOfferedGuidance();
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.guidance.length()").value(1))
                .andExpect(jsonPath("$.result.summary.guidance[0]")
                        .value(org.hamcrest.Matchers.containsString("이번 달 조건 안내입니다.")))
                .andExpect(jsonPath("$.result.summary.inquiries.length()").value(1));
        var stored = mapper.readTree(jdbc.queryForObject(
                "SELECT result::text FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId()));
        assertThat(stored.path("guidance")).hasSize(1);
        for (int index = 1; index <= 8; index++) {
            assertThat(stored.path("guidance").get(0).path("text").asText())
                    .contains("이전 조건 안내 " + index + "입니다.");
        }
        assertThat(stored.path("guidance").get(0).path("text").asText()).contains("이번 달 조건 안내입니다.");
        assertThat(stored.path("guidance").get(0).path("sourceMessageIds"))
                .anySatisfy(id -> assertThat(id.asLong()).isEqualTo(latestAnswer));
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
                .path("maxItems").asInt()).isZero();
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
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("input_mask_spans");
        // 생성 시각의 소수점 숫자가 카드번호 조각과 우연히 겹칠 수 있어 요약 내용만 검사한다.
        assertThat(mapper.readTree(body).path("result").path("summary").toString())
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
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(mapper.readTree(body).path("result").path("summary").toString())
                .contains(expected).doesNotContain("990101", "1234567", "4111", "\\uFFFC");
        var prompt = ArgumentCaptor.forClass(com.telme.llm.dto.req.LlmRequest.class);
        verify(client).generate(prompt.capture());
        assertThat(prompt.getValue().userPrompt()).contains(expected).doesNotContain("990101", "1234567", "4111");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PORTING | 번호이동 문의", "NAME_CHANGE | 명의변경 문의", "SUBSCRIBE | 가입·개통 문의",
        "BILLING | 요금·청구 문의", "DEVICE | 단말·할부 문의", "SERVICE | 부가서비스·기타 문의",
        "PLAN | 요금제 문의", "USIM | 유심 문의", "ROAMING | 해외 로밍 문의", "TERMINATE | 해지 문의"
    })
    @DisplayName("실제 저장된 FAQ 분류로 두 안내 문단을 묶어 종료 요약 저장과 재조회에 보존한다")
    void 저장된_근거_분류로_종료_요약을_묶어_조회한다(String category, String title) throws Exception {
        String question = "준비 서류와 처리 시간을 알려주세요.";
        String documents = "본인 신분증이 필요합니다.";
        String duration = "처리에는 2영업일이 걸립니다.";
        var fixture = fixture(false, question, documents + "\n\n" + duration);
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', ?, 'DONE')
                """, fixture.sessionId(), fixture.questionId(), question);
        addFaqSource(fixture, category, 1);
        when(client.generate(any())).thenReturn(ChatSessionEndSummaryGeneratorTest.selection(
                fixture.questionId(), 1, 2));
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        for (int read = 0; read < 2; read++) {
            mvc.perform(get(path(fixture)).session(fixture.actor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.summary.inquiries.length()").value(1))
                    .andExpect(jsonPath("$.result.summary.inquiries[0].title").value(title))
                    .andExpect(jsonPath("$.result.summary.inquiries[0].text").value(documents + " " + duration))
                    .andExpect(jsonPath("$.result.summary.guidance.length()").value(1));
        }
        verify(client).generate(any());
    }

    @Test
    @DisplayName("단일 STORE 요청의 저장된 의도를 읽어 문의 분류에 사용한다")
    void 저장된_매장_요청_의도를_읽는다() throws Exception {
        var fixture = fixture(false, "안내를 부탁드립니다.", "방문 가능한 곳을 안내했습니다.");
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'STORE', '안내를 부탁드립니다.', 'DONE')
                """, fixture.sessionId(), fixture.questionId());
        mockResult(fixture);
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.inquiries[0].title").value("매장 문의"));
    }

    @Test
    @DisplayName("상담 세션이 다른 FAQ 분류는 함께 읽지 않는다")
    void 다른_세션의_분류를_읽지_않는다() throws Exception {
        var first = fixture(false, "진행 방법 문의", "서류 안내입니다.");
        var second = fixture(false, "진행 방법 문의", "시간 안내입니다.");
        addFaqSource(first, "PORTING", 1);
        addFaqSource(second, "NAME_CHANGE", 1);

        var metadata = inquiryRepository.findBySessionId(first.sessionId());

        assertThat(metadata.keySet()).containsExactlyInAnyOrder(first.questionId(), first.answerId());
        assertThat(metadata.get(first.answerId()).faqCategories()).containsExactly("PORTING");
    }

    @Test
    @DisplayName("답변 당시 버전과 다른 FAQ의 현재 분류를 과거 상담에 적용하지 않는다")
    void 버전이_달라진_근거_분류는_추정하지_않는다() throws Exception {
        var fixture = fixture(false, "진행 방법 문의", "서류 안내입니다.");
        addFaqSource(fixture, "NAME_CHANGE", 2);

        var metadata = inquiryRepository.findBySessionId(fixture.sessionId());

        assertThat(metadata.get(fixture.answerId()).faqCategories()).isEmpty();
    }

    @Test
    @DisplayName("분리된 최신 기간 정정을 종료 요약의 같은 문의에 저장하고 재조회한다")
    void 최신_가입_기간을_종료_저장과_조회에_반영한다() throws Exception {
        var fixture = fixture(false, "제 회선 가입 기간은 18개월입니다. 유심 재발급 비용은요?",
                "유심 재발급 비용은 7,700원입니다.");
        long question = message(fixture.sessionId(), 3, "USER", "QUESTION",
                "같은 유심 문의의 가입 기간을 정정할게요. 18개월이 아니라 24개월입니다.", null);
        long answer = message(fixture.sessionId(), 4, "ASSISTANT", "ANSWER",
                "신분증을 지참해 매장을 방문합니다. 신청은 접수되지 않았습니다.", question);
        jdbc.update("""
                INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                VALUES (?,?,?,'COMPLETED',?)
                """, fixture.sessionId(), question, answer, Timestamp.from(time.get()));
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE session_id=? AND role='ASSISTANT'",
                fixture.sessionId());
        when(client.generate(any())).thenAnswer(invocation -> {
            com.telme.llm.dto.req.LlmRequest request = invocation.getArgument(0);
            var prompt = mapper.readTree(request.userPrompt());
            var selected = mapper.createObjectNode();
            var topics = selected.putObject("topics");
            prompt.path("requiredTopicMessageIds").forEach(id -> topics.put(id.asText(), "유심 문의"));
            var ids = selected.putArray("selectedSentenceIds");
            prompt.path("guidanceCandidates").forEach(item -> ids.add(item.path("sentenceId").asInt()));
            selected.putArray("pendingItems");
            return selected.toString();
        });
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        mvc.perform(get(path(fixture)).session(fixture.actor()))
                .andExpect(jsonPath("$.result.summary.inquiries.length()").value(1))
                .andExpect(jsonPath("$.result.summary.inquiries[0].text")
                        .value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("24개월"),
                                org.hamcrest.Matchers.containsString("신분증"),
                                org.hamcrest.Matchers.containsString("매장"),
                                org.hamcrest.Matchers.containsString("접수되지 않았습니다"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                        "제 회선 가입 기간은 18개월입니다.")))));
    }

    private void addFaqSource(Fixture fixture, String category, int citedVersion) {
        long faq = jdbc.queryForObject("""
                INSERT INTO faqs(category,question,answer,version)
                VALUES (?, ?, ?, 1) RETURNING faq_id
                """, Long.class, category, "합성 분류 검증 질문 " + fixture.sessionId(),
                "합성 분류 검증 답변 " + fixture.answerId());
        createdCategoryFaqs.add(faq);
        jdbc.update("INSERT INTO message_sources(message_id,faq_id,faq_version) VALUES (?,?,?)",
                fixture.answerId(), faq, citedVersion);
    }

    @Test
    @DisplayName("부분 요약 둘의 병합 입력이 초과해도 원문을 서버에서 합쳐 종료 저장과 재조회에 보존한다")
    void 병합_입력_초과_상담의_종료_요약을_저장하고_재조회한다() throws Exception {
        String question = "유심 재발급 절차와 준비물을 확인하는 문의입니다 ".substring(0, 12);
        var fixture = fixture(false, question + " 0", mergeLimitAnswer(0));
        for (int index = 1; index < 9; index++) {
            long q = message(fixture.sessionId(), index * 2 + 1, "USER", "QUESTION", question + " " + index, null);
            long a = message(fixture.sessionId(), index * 2 + 2, "ASSISTANT", "ANSWER", mergeLimitAnswer(index % 8), q);
            jdbc.update("""
                    INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                    VALUES (?,?,?,'COMPLETED',?)
                    """, fixture.sessionId(), q, a, Timestamp.from(time.get()));
        }
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE session_id=? AND role='ASSISTANT'",
                fixture.sessionId());
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                SELECT session_id,message_id,1,'FAQ',content,'DONE' FROM chat_messages
                WHERE session_id=? AND role='USER'
                """, fixture.sessionId());
        when(client.generate(any())).thenAnswer(invocation -> {
            var prompt = mapper.readTree(((com.telme.llm.dto.req.LlmRequest) invocation.getArgument(0)).userPrompt());
            var result = mapper.createObjectNode();
            var topics = result.putObject("topics");
            prompt.path("requests").forEach(request -> topics.put(request.path("originMessageId").asText(),
                    "유심 재발급 문의"));
            var ids = result.putArray("selectedSentenceIds");
            prompt.path("guidanceCandidates").forEach(candidate -> ids.add(candidate.path("sentenceId").asInt()));
            result.putArray("pendingItems");
            return result.toString();
        });
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        String previous = null;
        for (int read = 0; read < 2; read++) {
            String response = mvc.perform(get(path(fixture)).session(fixture.actor()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var summary = mapper.readTree(response).path("result").path("summary");
            for (int index = 0; index < 8; index++) {
                assertThat(summary.toString()).contains("안내 항목 " + index);
            }
            if (previous != null) {
                assertThat(summary.toString()).isEqualTo(previous);
            }
            previous = summary.toString();
        }
        verify(client, org.mockito.Mockito.times(2)).generate(any());
    }

    @Test
    @DisplayName("480자 초과 문단과 9개 안내를 종료 처리부터 저장·재조회까지 모두 보존한다")
    void 긴_문단과_아홉_안내를_누락하지_않고_저장한다() throws Exception {
        var paragraphs = new java.util.ArrayList<String>();
        String preparation = java.util.stream.IntStream.rangeClosed(1, 40)
                .mapToObj(index -> "방문 전 준비 항목 " + index + "의 서류와 조건을 확인해야 합니다.")
                .collect(java.util.stream.Collectors.joining(" "));
        paragraphs.add("유심 재발급 비용은 7,700원이며 기기 불량 확인 시에만 무료입니다. "
                + preparation + " "
                + "명의자 본인이 신분증을 지참하고 매장을 방문해야 합니다. 신청은 접수되지 않았습니다.");
        for (int index = 2; index <= 9; index++) {
            paragraphs.add("유심 추가 안내 " + index + "의 조건은 별도로 확인해야 합니다.");
        }
        var fixture = fixture(false, "유심 재발급 조건과 절차를 알려주세요.", String.join("\n\n", paragraphs));
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", fixture.answerId());
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?, ?, 1, 'FAQ', '유심 재발급 절차', 'DONE')
                """, fixture.sessionId(), fixture.questionId());
        selectOfferedGuidance();
        close(fixture);

        service.process(fixture.sessionId());

        assertState(fixture, "COMPLETED");
        String previous = null;
        for (int read = 0; read < 2; read++) {
            String response = mvc.perform(get(path(fixture)).session(fixture.actor()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var summary = mapper.readTree(response).path("result").path("summary");
            assertThat(summary.path("inquiries")).hasSize(1);
            String text = summary.path("inquiries").get(0).path("text").asText();
            assertThat(text).contains("7,700원", "기기 불량 확인 시에만 무료", "명의자 본인", "신분증",
                    "매장을 방문", "신청은 접수되지 않았습니다");
            assertThat(text).contains(preparation);
            for (int index = 2; index <= 9; index++) {
                assertThat(text).contains(paragraphs.get(index - 1));
            }
            if (previous != null) {
                assertThat(summary.toString()).isEqualTo(previous);
            }
            previous = summary.toString();
        }
    }

    private void selectOfferedGuidance() {
        when(client.generate(any())).thenAnswer(invocation -> {
            com.telme.llm.dto.req.LlmRequest request = invocation.getArgument(0);
            var prompt = mapper.readTree(request.userPrompt());
            var result = mapper.createObjectNode();
            var topics = result.putObject("topics");
            mapper.valueToTree(request.responseSchema()).path("properties").path("topics").path("required")
                    .forEach(id -> topics.put(id.asText(), "상담 문의"));
            var ids = result.putArray("selectedSentenceIds");
            assertThat(prompt.path("guidanceCandidates")).hasSizeLessThanOrEqualTo(8);
            prompt.path("guidanceCandidates").forEach(candidate -> ids.add(candidate.path("sentenceId").asInt()));
            result.putArray("pendingItems");
            return result.toString();
        });
    }

    private String mergeLimitAnswer(int index) {
        return "유심 재발급의 안내 항목 " + index + "입니다. "
                + "방문 전 준비물과 이용 절차 및 필요한 조건을 확인하는 안내입니다 ".repeat(20)
                .substring(0, 125) + " 안내를 확인했습니다.";
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
