package com.telme.chat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatExecutionTraceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 모니터링 API 통합 테스트(실제 SQL + ADMIN 제한)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminChatMonitoringIntegrationTest {

    private static final String URL = "/api/v1/admin/monitoring/executions";
    private static final String LONG_QUESTION =
            "요금제를 다른 걸로 바꾸고 싶은데 어떻게 해야 하나요? 지금 약정 기간이 남아 있어서 위약금도 궁금합니다";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChatExecutionTraceRepository traceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private long adminId;
    private long failedMessageId;
    private long failedExecutionId;
    private long memberId;
    private long completedMessageId;
    private long completedExecutionId;
    private long blockedMessageId;
    private long runningMessageId;
    private long runningExecutionId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("ADMIN", "관리자");
        memberId = insertUser("USER", "테스트회원");
        long sessionId = jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,title) VALUES (?,'모니터링') RETURNING session_id",
                Long.class, memberId);

        // 가장 오래된 행: 출력 검사 차단으로 실패, 재검색에 같은 FAQ 겹침, 판정 verdict null 포함
        failedMessageId = insertMessage(sessionId, 10, "USER", "QUESTION", "해지 위약금");
        failedExecutionId = jdbc.queryForObject("""
                INSERT INTO chat_executions(session_id,input_message_id,status,ended_at,error_code)
                VALUES (?,?,'FAILED',now(),'CHAT500-0') RETURNING execution_id
                """, Long.class, sessionId, failedMessageId);
        traceRepository.updateStage(failedExecutionId, "analysis", "{\"action\":\"PROCEED\"}", false);
        traceRepository.updateStage(failedExecutionId, "searchResults",
                "{\"status\":\"FOUND\",\"sources\":[{\"faqId\":7}]}", true);
        traceRepository.updateStage(failedExecutionId, "refinedQueryChecks", "{\"searched\":true}", true);
        traceRepository.updateStage(failedExecutionId, "searchResults",
                "{\"status\":\"FOUND\",\"sources\":[{\"faqId\":7},{\"faqId\":8}]}", true);
        traceRepository.updateStage(failedExecutionId, "grayZoneJudgments",
                "{\"judgments\":[{\"kept\":true,\"verdict\":null},{\"kept\":false,\"verdict\":\"FAILED\"}]}",
                true);
        traceRepository.updateStage(failedExecutionId, "guard", "{\"outcome\":\"KEPT\"}", false);
        traceRepository.updateStage(failedExecutionId, "outputSafety", "{\"outcome\":\"BLOCKED\"}", false);

        completedMessageId = insertMessage(sessionId, 1, "USER", "QUESTION", LONG_QUESTION);
        long answerId = insertMessage(sessionId, 2, "ASSISTANT", "ANSWER", "요금제 변경은 ...");
        jdbc.update("UPDATE chat_messages SET answer_basis='GROUNDED' WHERE message_id=?", answerId);
        completedExecutionId = jdbc.queryForObject("""
                INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                VALUES (?,?,?,'COMPLETED',now()) RETURNING execution_id
                """, Long.class, sessionId, completedMessageId, answerId);
        jdbc.update("INSERT INTO query_routings(message_id,intent) VALUES (?,'FAQ')", completedMessageId);
        traceRepository.updateStage(completedExecutionId, "analysis", "{\"action\":\"PROCEED\"}", false);
        traceRepository.updateStage(completedExecutionId, "searchResults",
                "{\"status\":\"FOUND\",\"sources\":[{\"faqId\":1},{\"faqId\":2},{\"faqId\":3}]}", true);
        traceRepository.updateStage(completedExecutionId, "grayZoneJudgments",
                "{\"judgments\":[{\"kept\":true,\"verdict\":\"ANSWERABLE\"},"
                        + "{\"kept\":false,\"verdict\":\"NOT_ANSWERABLE\"},{\"kept\":true,\"verdict\":\"ANSWERABLE\"}]}",
                true);
        traceRepository.updateStage(completedExecutionId, "guard", "{\"outcome\":\"KEPT\"}", false);
        traceRepository.updateStage(completedExecutionId, "finalTransmission",
                "{\"status\":\"DISPATCH_RETURNED\"}", false);
        jdbc.update("""
                INSERT INTO llm_generations(execution_id,task_type,attempt,model,status,total_ms)
                VALUES (?,'RAG_ANSWER',1,'test-model','SUCCESS',1200)
                """, completedExecutionId);

        blockedMessageId = insertMessage(sessionId, 3, "USER", "BLOCKED", "(욕설)");
        insertGuardEvent(sessionId, blockedMessageId, "[\"WARNED\"]");

        runningMessageId = insertMessage(sessionId, 4, "USER", "QUESTION", "유심 재발급 비용");
        runningExecutionId = jdbc.queryForObject("""
                INSERT INTO chat_executions(session_id,input_message_id,status) VALUES (?,?,'RUNNING')
                RETURNING execution_id
                """, Long.class, sessionId, runningMessageId);
        traceRepository.updateStage(runningExecutionId, "analysis", "{\"action\":\"PROCEED\"}", false);
        // 판정이 붙는 검색을 요청했고 아직 판정, 결과 기록 전
        traceRepository.updateStage(runningExecutionId, "searchRequests", "{\"grayZone\":true}", true);
        insertGuardEvent(sessionId, runningMessageId, "[\"MASKED\"]");
    }

    @Test
    @DisplayName("로그인하지 않으면 401, ADMIN이 아니면 403")
    void 권한이_없으면_막는다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).with(as(memberId, "ROLE_USER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/{id}", completedExecutionId).with(as(memberId, "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("입력 메시지마다 최신순으로 한 줄씩 내려주고 차단·진행 중 행도 포함한다")
    void 목록을_조회한다() throws Exception {
        mockMvc.perform(admin(get(URL).param("beforeId", String.valueOf(runningMessageId + 1)).param("size", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(3)))
                // 진행 중: 판정 붙는 검색 요청까지 기록 → 검색, 판정 칸 진행 중, 생성은 아직
                .andExpect(jsonPath("$.result.items[0].messageId").value(runningMessageId))
                .andExpect(jsonPath("$.result.items[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.result.items[0].lastStage").value("searchRequests"))
                .andExpect(jsonPath("$.result.items[0].stages.inputCheck.state").value("PARTIAL"))
                .andExpect(jsonPath("$.result.items[0].stages.inputCheck.label").value("마스킹"))
                .andExpect(jsonPath("$.result.items[0].stages.search.state").value("RUNNING"))
                .andExpect(jsonPath("$.result.items[0].stages.judgment.state").value("RUNNING"))
                .andExpect(jsonPath("$.result.items[0].stages.generation.state").value("SKIPPED"))
                .andExpect(jsonPath("$.result.items[0].stages.result.state").value("RUNNING"))
                // 차단: 실행 없음
                .andExpect(jsonPath("$.result.items[1].messageId").value(blockedMessageId))
                .andExpect(jsonPath("$.result.items[1].executionId").value(nullValue()))
                .andExpect(jsonPath("$.result.items[1].status").value("BLOCKED"))
                .andExpect(jsonPath("$.result.items[1].stages.inputCheck.label").value("경고"))
                .andExpect(jsonPath("$.result.items[1].stages.routing.state").value("SKIPPED"))
                .andExpect(jsonPath("$.result.items[1].stages.result.label").value("차단"))
                // 완료: 칸별 요약과 사용자, 질문 앞 40자
                .andExpect(jsonPath("$.result.items[2].executionId").value(completedExecutionId))
                .andExpect(jsonPath("$.result.items[2].userId").value(memberId))
                .andExpect(jsonPath("$.result.items[2].userName").value("테스트회원"))
                .andExpect(jsonPath("$.result.items[2].guestId").value(nullValue()))
                .andExpect(jsonPath("$.result.items[2].intent").value("FAQ"))
                .andExpect(jsonPath("$.result.items[2].questionPreview")
                        .value(LONG_QUESTION.substring(0, 40) + "..."))
                .andExpect(jsonPath("$.result.items[2].stages.routing.label").value("FAQ"))
                .andExpect(jsonPath("$.result.items[2].stages.search.label").value("3건"))
                .andExpect(jsonPath("$.result.items[2].stages.judgment.state").value("PARTIAL"))
                .andExpect(jsonPath("$.result.items[2].stages.judgment.label").value("2/3"))
                .andExpect(jsonPath("$.result.items[2].stages.generation.state").value("PASSED"))
                .andExpect(jsonPath("$.result.items[2].stages.outputCheck.state").value("PASSED"))
                .andExpect(jsonPath("$.result.items[2].stages.result.label").value("근거 답변"));
    }

    @Test
    @DisplayName("beforeId로 과거 페이지를 넘기고 다음 커서를 준다")
    void 커서로_페이지를_넘긴다() throws Exception {
        mockMvc.perform(admin(get(URL).param("beforeId", String.valueOf(runningMessageId + 1)).param("size", "1")))
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.nextBeforeId").value(runningMessageId));
        mockMvc.perform(admin(get(URL).param("beforeId", String.valueOf(runningMessageId)).param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(blockedMessageId));
    }

    @Test
    @DisplayName("상태로 거르면 해당 행만 남는다")
    void 상태로_거른다() throws Exception {
        String before = String.valueOf(runningMessageId + 1);
        mockMvc.perform(admin(get(URL).param("beforeId", before).param("status", "BLOCKED").param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(blockedMessageId));
        mockMvc.perform(admin(get(URL).param("beforeId", before).param("status", "COMPLETED").param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(completedMessageId));
        mockMvc.perform(admin(get(URL).param("beforeId", before).param("status", "RUNNING").param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(runningMessageId));
    }

    @Test
    @DisplayName("실패 행: 겹친 FAQ는 한 번만 세고, verdict null이 섞여도 판정 실패를 FAILED로 본다")
    void 실패_행_요약() throws Exception {
        mockMvc.perform(admin(get(URL).param("beforeId", String.valueOf(completedMessageId)).param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(failedMessageId))
                .andExpect(jsonPath("$.result.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.result.items[0].errorCode").value("CHAT500-0"))
                .andExpect(jsonPath("$.result.items[0].stages.search.state").value("PARTIAL"))
                .andExpect(jsonPath("$.result.items[0].stages.search.label").value("재검색 2건"))
                .andExpect(jsonPath("$.result.items[0].stages.judgment.state").value("FAILED"))
                .andExpect(jsonPath("$.result.items[0].stages.judgment.label").value("판정 실패 1건"))
                .andExpect(jsonPath("$.result.items[0].stages.outputCheck.state").value("FAILED"))
                .andExpect(jsonPath("$.result.items[0].stages.outputCheck.label").value("차단"))
                .andExpect(jsonPath("$.result.items[0].stages.result.label").value("실패"));
    }

    @Test
    @DisplayName("상태 필터와 커서를 함께 쓰면 그 상태에서 beforeId보다 오래된 것만 온다")
    void 상태_필터와_커서() throws Exception {
        String latest = String.valueOf(runningMessageId + 1);
        mockMvc.perform(admin(get(URL).param("beforeId", latest).param("status", "FAILED").param("size", "1")))
                .andExpect(jsonPath("$.result.items[0].messageId").value(failedMessageId));

        // 공유 DB에 다른 테스트 데이터가 있을 수 있어 개수 대신 조건만 확인한다
        assertOnlyOlder("FAILED", failedMessageId);
        assertOnlyOlder("BLOCKED", blockedMessageId);
        assertOnlyOlder("RUNNING", runningMessageId);
    }

    private void assertOnlyOlder(String status, long beforeId) throws Exception {
        String body = mockMvc.perform(admin(get(URL).param("beforeId", String.valueOf(beforeId))
                        .param("status", status).param("size", "50")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(body).at("/result/items");
        for (JsonNode item : items) {
            assertThat(item.path("messageId").asLong()).isLessThan(beforeId);
            assertThat(item.path("status").asText()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("size가 50을 넘거나 status가 enum에 없으면 400")
    void 잘못된_조건은_400() throws Exception {
        mockMvc.perform(admin(get(URL).param("size", "51"))).andExpect(status().isBadRequest());
        mockMvc.perform(admin(get(URL).param("status", "NOPE"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상세는 단계 기록 순서와 모델 호출 시각을 포함한다")
    void 상세를_조회한다() throws Exception {
        mockMvc.perform(admin(get(URL + "/{id}", completedExecutionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.executionId").value(completedExecutionId))
                .andExpect(jsonPath("$.result.originalUserMessage").exists())
                .andExpect(jsonPath("$.result.finalAnswer").value("요금제 변경은 ..."))
                .andExpect(jsonPath("$.result.timeline", hasSize(5)))
                .andExpect(jsonPath("$.result.timeline[0].stage").value("analysis"))
                .andExpect(jsonPath("$.result.timeline[1].stage").value("searchResults"))
                .andExpect(jsonPath("$.result.timeline[4].stage").value("finalTransmission"))
                .andExpect(jsonPath("$.result.timeline[0].at").exists())
                .andExpect(jsonPath("$.result.timeline[0].elapsedMs").isNumber())
                .andExpect(jsonPath("$.result.steps.guard.outcome").value("KEPT"))
                .andExpect(jsonPath("$.result.routing[0].intent").value("FAQ"))
                .andExpect(jsonPath("$.result.modelAttempts[0].model").value("test-model"))
                .andExpect(jsonPath("$.result.modelAttempts[0].createdAt").isString())
                .andExpect(jsonPath("$.result.stages.search.label").value("3건"));
    }

    @Test
    @DisplayName("진행 중 실행도 그때까지 기록된 단계를 보여준다")
    void 진행_중_상세() throws Exception {
        mockMvc.perform(admin(get(URL + "/{id}", runningExecutionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RUNNING"))
                .andExpect(jsonPath("$.result.timeline", hasSize(2)))
                .andExpect(jsonPath("$.result.inputCheckActions[0]").value("MASKED"));
    }

    @Test
    @DisplayName("없는 실행은 404")
    void 없는_실행은_404() throws Exception {
        mockMvc.perform(admin(get(URL + "/{id}", Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-1"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(as(adminId, "ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(long userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private long insertUser(String role, String name) {
        return jdbc.queryForObject("INSERT INTO users(email,name,role) VALUES (?,?,?) RETURNING user_id",
                Long.class, "monitoring-" + UUID.randomUUID() + "@example.com", name, role);
    }

    private long insertMessage(long sessionId, int sequence, String role, String type, String content) {
        return jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)
                VALUES (?,?,?,?,?,'COMPLETED',now()) RETURNING message_id
                """, Long.class, sessionId, sequence, role, type, content);
    }

    private void insertGuardEvent(long sessionId, long messageId, String actions) {
        Long stateId = jdbc.query("SELECT guard_state_id FROM chat_input_guard_states WHERE user_id=?",
                (rs, n) -> rs.getLong(1), memberId).stream().findFirst().orElseGet(() -> jdbc.queryForObject("""
                INSERT INTO chat_input_guard_states(user_id,counting_from_at,updated_at) VALUES (?,now(),now())
                RETURNING guard_state_id
                """, Long.class, memberId));
        jdbc.update("""
                INSERT INTO chat_input_guard_events(guard_state_id,detected_at,user_id,session_id,message_id,
                    sanitized_content,detections,actions,counted_violation,violation_count,policy_version,
                    response_snapshot)
                VALUES (?,now(),?,?,?,'x','[]'::jsonb,?::jsonb,false,0,'test','{}'::jsonb)
                """, stateId, memberId, sessionId, messageId, actions);
    }
}
