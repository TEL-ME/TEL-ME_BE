package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 실제 기록 경로(재시도 → 기록기)를 거치도록 가장 안쪽 모델 클라이언트만 바꾼다
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "llm.provider=ollama",
        "llm.model=routing-record-test",
        "llm.retry.wait-duration=0ms",
        "telme.routing.request-inventory-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
class RoutingGenerationRecordIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired QueryRoutingService routing;
    @Autowired ChatMessageRepository messages;
    @Autowired ContextProvider contexts;
    // @Primary인 재시도·기록 클라이언트다. 운영과 같은 기록 경로를 거친다
    @Autowired LlmClient recordingClient;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;
    long userId;
    long sessionId;

    @BeforeEach
    void setup() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'라우팅 기록') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                Long.class, userId);
        when(model.generate(any())).thenAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if (RoutingPromptTemplates.REQUEST_INVENTORY_PROMPT.equals(request.systemPrompt())) {
                return "{\"decision\":\"MULTIPLE\",\"requestCount\":2}";
            }
            if (RoutingPromptTemplates.FAQ_QUERY_FAITHFULNESS_PROMPT.equals(request.systemPrompt())) {
                return "{\"unsafe\":[false,false]}";
            }
            return """
                    {"intent":"FAQ","confidence":0.98,"refinedQuery":"유심 비용과 로밍 신청",
                     "subQueries":[
                      {"order":1,"intent":"FAQ","queryText":"유심재발급비용","requestQuote":"유심재발급비용"},
                      {"order":2,"intent":"FAQ","queryText":"로밍신청방법","requestQuote":"로밍신청방법"}]}
                    """;
        });
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN"
                + " (SELECT consult_request_id FROM consult_requests WHERE session_id=?)", sessionId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void recordsChatExecutionRoutingCallsAsRouting() {
        ChatProcessingCommand command = createCommand("유심 재발급 비용과 로밍 신청 방법 알려줘");
        var provider = new QueryRoutingAnalysisProvider(
                messages, routing, new QueryRoutingFollowupAnalysisProvider(routing));

        provider.analyze(contexts.loadVerified(command));

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT task_type, prompt_version, status FROM llm_generations
                WHERE execution_id=? ORDER BY generation_id
                """, command.executionId());
        assertThat(rows).extracting(row -> row.get("task_type")).containsOnly("ROUTING");
        assertThat(rows).extracting(row -> row.get("status")).containsOnly("SUCCESS");
        assertThat(rows).extracting(row -> row.get("prompt_version")).containsExactly(
                "routing-request-v5-single-search",
                "routing-request-inventory-v4",
                "routing-faq-faithfulness-v2-single-search");
    }

    // 원문 검색이 0건이면 FaqSearchAnswerProvider가 재검색 문장 검증을 부른다. 이 호출도 같은 실행에 남아야 한다
    @Test
    void recordsRefinedSearchVerificationCallsAsRouting() {
        doReturn("{\"unsafe\":[true]}").when(model).generate(any());
        ChatProcessingCommand command = createCommand("미납 요금이 있는데 번호이동하고 싶어요");

        routing.refinedSearchQuery(command.content(), "번호이동 신청 방법", command.executionId());

        // 후보가 탈락하면 원문 근거 단어만 남긴 문장을 한 번 더 검증하므로 두 건이 남는다
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT task_type, prompt_version, status FROM llm_generations
                WHERE execution_id=? ORDER BY generation_id
                """, command.executionId());
        assertThat(rows).extracting(row -> row.get("task_type")).containsExactly("ROUTING", "ROUTING");
        assertThat(rows).extracting(row -> row.get("status")).containsOnly("SUCCESS");
        assertThat(rows).extracting(row -> row.get("prompt_version"))
                .containsOnly("routing-faq-faithfulness-single-v3");
    }

    private ChatProcessingCommand createCommand(String content) {
        long messageId = jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)
                VALUES (?,1,'USER','QUESTION',?,'COMPLETED',now()) RETURNING message_id
                """, Long.class, sessionId, content);
        long executionId = jdbc.queryForObject("INSERT INTO chat_executions(session_id,input_message_id,status)"
                + " VALUES (?,?,'RUNNING') RETURNING execution_id", Long.class, sessionId, messageId);
        return new ChatProcessingCommand(executionId, sessionId, messageId, content);
    }
}
