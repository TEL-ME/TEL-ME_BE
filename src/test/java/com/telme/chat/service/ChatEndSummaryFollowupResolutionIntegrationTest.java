package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.service.LlmClient;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"chat.end-summary.enabled=true", "chat.end-summary.dispatch-enabled=false",
        "chat.end-summary.scheduler-enabled=false"})
@AutoConfigureMockMvc
class ChatEndSummaryFollowupResolutionIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ChatSessionEndSummaryService service;
    @Autowired
    private ChatSessionEndSummaryInputReader reader;
    @MockitoBean
    private LlmClient client;

    @BeforeEach
    void 모델_선택_응답을_준비한다() {
        when(client.generate(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, com.telme.llm.dto.req.LlmRequest.class);
            var body = mapper.readTree(request.userPrompt());
            var response = mapper.createObjectNode();
            var topics = response.putObject("topics");
            mapper.valueToTree(request.responseSchema()).path("properties").path("topics").path("required")
                    .forEach(id -> topics.put(id.asText(), "상담 문의"));
            var selected = response.putArray("selectedSentenceIds");
            body.path("guidanceCandidates").forEach(candidate -> selected.add(candidate.path("sentenceId").asInt()));
            response.putArray("pendingItems");
            return response.toString();
        });
    }

    @Test
    @DisplayName("저장된 되묻기 응답과 정상 답변을 연결해 종료 요약의 남은 사항에서 제외한다")
    void 해결된_되묻기의_종료_요약을_저장하고_조회한다() throws Exception {
        var fixture = fixture("GROUNDED");

        var input = reader.read(fixture.sessionId());
        assertThat(input.conditions().getFirst().askedMessageId()).isEqualTo(fixture.askedId());
        assertThat(input.pendingIds()).isEmpty();

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("pendingItems")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("다음 날 00:00", "한 달에 한 번");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_session_end_summaries WHERE session_id=?",
                Integer.class, fixture.sessionId())).isEqualTo(1);
    }

    @Test
    @DisplayName("해결된 요금제 문의 뒤 어머니 회선의 미확인 지역 문의만 남긴다")
    void 다른_사람의_별도_미해결_문의는_유지한다() throws Exception {
        var fixture = fixture("GROUNDED");
        long question = message(fixture.sessionId(), 5, null, "USER", "QUESTION", "어머니 회선 매장을 찾아주세요", null);
        long asked = message(fixture.sessionId(), 6, question, "ASSISTANT", "CLARIFICATION", "방문할 지역은 어디인가요?", null);
        long request = request(fixture.sessionId(), question, 1, "어머니 회선 매장 위치", "WAITING_CONDITION");
        condition(request, "location", "PENDING", asked, null);
        execution(fixture.sessionId(), question, asked);

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").get(0).path("kind").asText()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(summary.path("remaining").get(0).path("text").asText()).contains("방문 지역");
        assertThat(summary.path("remaining").toString()).doesNotContain("처음 가입", "아니요");
    }

    @Test
    @DisplayName("같은 복합 문의에서 해결된 조건은 빼고 남은 방문 지역 조건만 저장한다")
    void 복합_문의의_해결된_부분만_제외한다() throws Exception {
        var fixture = fixture("GROUNDED");
        long request = request(fixture.sessionId(), fixture.questionId(), 2, "매장 위치", "WAITING_CONDITION");
        condition(request, "location", "PENDING", fixture.askedId(), null);

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").get(0).path("text").asText()).contains("방문 지역");
        assertThat(summary.path("remaining").toString()).doesNotContain("처음 가입");
    }

    @Test
    @DisplayName("조건 응답 뒤 검색 실패는 확인 필요로 되돌리지 않고 원래 문의의 미답변으로 남긴다")
    void 검색_실패를_원래_문의의_미답변으로_저장한다() throws Exception {
        var fixture = fixture("NO_EVIDENCE");

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").get(0).path("kind").asText()).isEqualTo("UNANSWERED");
        assertThat(summary.path("remaining").get(0).path("text").asText()).contains("요금제 변경");
        assertThat(summary.path("remaining").toString()).doesNotContain("‘아니요’", "처음 가입하셨나요");
    }

    @Test
    @DisplayName("여러 차례 되묻고 조건을 모두 확인한 뒤의 정상 답변으로 이전 확인 항목을 해소한다")
    void 연속_되묻기의_최종_해결을_저장한다() throws Exception {
        var fixture = fixture("GROUNDED");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION',answer_basis=NULL,content=? "
                + "WHERE message_id=?", "이번 달 변경 이력이 있나요?", fixture.answerId());
        long reply = message(fixture.sessionId(), 5, null, "USER", "QUESTION", "변경한 적 없어요", null);
        long answer = message(fixture.sessionId(), 6, reply, "ASSISTANT", "ANSWER", "이번 달 변경이 가능합니다.", "GROUNDED");
        condition(fixture.requestId(), "changed_this_month", "FILLED", fixture.answerId(), reply);
        execution(fixture.sessionId(), reply, answer);

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("이번 달 변경이 가능합니다");
    }

    @Test
    @DisplayName("같은 조건을 재질문해 연결이 갱신되어도 중간 사용자 응답을 별도 미해결로 남기지 않는다")
    void 같은_조건_재질문_뒤_중간_응답도_해소한다() throws Exception {
        var fixture = fixture("GROUNDED");
        jdbc.update("UPDATE chat_messages SET message_type='CLARIFICATION',answer_basis=NULL,content=? "
                + "WHERE message_id=?", "처음 가입한 달을 다시 알려주시겠어요?", fixture.answerId());
        long reply = message(fixture.sessionId(), 5, null, "USER", "QUESTION", "지난달이에요", null);
        long answer = message(fixture.sessionId(), 6, reply, "ASSISTANT", "ANSWER", "이번 달 변경이 가능합니다.", "GROUNDED");
        jdbc.update("UPDATE consult_conditions SET asked_message_id=?,answered_message_id=? "
                + "WHERE consult_request_id=?", fixture.answerId(), reply, fixture.requestId());
        execution(fixture.sessionId(), reply, answer);

        assertThat(closeAndRead(fixture).path("remaining")).isEmpty();
    }

    @Test
    @DisplayName("다른 세션의 사용자 응답을 잘못 참조해도 해당 세션의 해결 근거로 사용하지 않는다")
    void 다른_세션의_조건_연결은_해결로_인정하지_않는다() throws Exception {
        var fixture = fixture("GROUNDED");
        var other = fixture("GROUNDED");
        jdbc.update("UPDATE consult_conditions SET answered_message_id=? WHERE consult_request_id=?",
                other.replyId(), fixture.requestId());

        var input = reader.read(fixture.sessionId());

        assertThat(input.pendingIds()).contains(fixture.questionId());
        assertThat(input.messages()).noneMatch(message -> message.id() == other.replyId());
        assertThat(closeAndRead(fixture).path("remaining")).isNotEmpty();
    }

    @Test
    @DisplayName("공유된 조건 응답은 통합 답변의 근거 상태로 모든 하위 문의의 해결을 단정하지 않는다")
    void 공유_응답의_모호한_하위_문의는_확인_대상으로_남긴다() throws Exception {
        var fixture = fixture("GROUNDED");
        long request = request(fixture.sessionId(), fixture.questionId(), 2, "유심 재발급 비용", "DONE");
        condition(request, "agent_visit", "FILLED", fixture.askedId(), fixture.replyId());

        var summary = closeAndRead(fixture);

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").get(0).path("kind").asText()).isEqualTo("WAITING_RESPONSE");
    }

    private Fixture fixture(String basis) throws Exception {
        var actor = new MockHttpSession();
        var response = mvc.perform(post("/api/v1/chat/sessions").session(actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        long session = mapper.readTree(response.getResponse().getContentAsString()).path("result").path("sessionId")
                .asLong();
        long question = message(session, 1, null, "USER", "QUESTION", "요금제 변경 횟수와 적용 시점을 알려주세요", null);
        long asked = message(session, 2, question, "ASSISTANT", "CLARIFICATION", "이번 달 처음 가입하셨나요?", null);
        long reply = message(session, 3, null, "USER", "QUESTION", "아니요", null);
        String content = basis.equals("GROUNDED")
                ? "한 달에 한 번 변경 가능하며 다음 날 00:00부터 적용됩니다." : "근거를 찾지 못했습니다.";
        long answer = message(session, 4, reply, "ASSISTANT", "ANSWER",
                content, basis);
        long request = request(session, question, 1, "요금제 변경 횟수와 적용 시점", "DONE");
        condition(request, "joined_this_month", "FILLED", asked, reply);
        execution(session, question, asked);
        execution(session, reply, answer);
        return new Fixture(actor, session, question, asked, reply, answer, request);
    }

    private long message(long session, int sequence, Long reply, String role, String type,
            String content, String basis) {
        return jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,reply_to_id,role,message_type,content,
                    answer_basis,status,completed_at) VALUES (?,?,?,?,?,?,?,'COMPLETED',now()) RETURNING message_id
                """, Long.class, session, sequence, reply, role, type, content, basis);
    }

    private long request(long session, long origin, int order, String query, String status) {
        return jdbc.queryForObject("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?,?,?,'FAQ',?,?) RETURNING consult_request_id
                """, Long.class, session, origin, order, query, status);
    }

    private void condition(long request, String key, String status, Long asked, Long answered) {
        jdbc.update("""
                INSERT INTO consult_conditions(consult_request_id,condition_key,condition_value,source,status,
                    asked_message_id,answered_message_id) VALUES (?,?,'false','ASKED',?,?,?)
                """, request, key, status, asked, answered);
    }

    private void execution(long session, long input, long output) {
        jdbc.update("""
                INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                VALUES (?,?,?,'COMPLETED',now())
                """, session, input, output);
    }

    private JsonNode closeAndRead(Fixture fixture) throws Exception {
        mvc.perform(patch("/api/v1/chat/sessions/{id}/close", fixture.sessionId()).session(fixture.actor()))
                .andExpect(status().isOk());
        service.process(fixture.sessionId());
        var response = mvc.perform(get("/api/v1/chat/sessions/{id}/summary", fixture.sessionId())
                        .session(fixture.actor()))
                .andExpect(status().isOk()).andReturn();
        var result = mapper.readTree(response.getResponse().getContentAsString()).path("result");
        assertThat(result.path("summaryStatus").asText()).isEqualTo("COMPLETED");
        return result.path("summary");
    }

    private record Fixture(MockHttpSession actor, long sessionId, long questionId, long askedId,
            long replyId, long answerId, long requestId) {}
}
