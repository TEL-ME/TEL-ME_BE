package com.telme.intent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.llm.service.LlmClient;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// 되묻기 후속 답변이 상담 DB에 실제로 반영되는지 검증한다.
// ConsultService.prepare()가 활성 트랜잭션을 거부하므로 @Transactional 없이 실행하고 직접 정리한다.
// 이 두 플래그 조합은 이 클래스에서만 써서 Spring이 컨텍스트를 새로 캐싱한다.
// 다른 테스트들과 공유 설정을 그대로 두고, 이 클래스가 새로 여는 커넥션 풀만
// 작게 잡아 전체 스위트를 한 번에 돌릴 때 max_connections를 넘기지 않게 한다.
@SpringBootTest(properties = {
        "telme.chat.pipeline.enabled=true",
        "telme.consult.persistence-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
class ClarificationPersistenceIntegrationTest {

    private static final String LOCATION = "location";
    private static final String SERVICE_TYPE = "serviceType";

    @Autowired
    private ChatPipelineProcessor processor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LlmClient llmClient;

    private final List<Long> createdSessionIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long sessionId : createdSessionIds) {
                jdbcTemplate.update("delete from consult_conditions where consult_request_id in"
                        + " (select consult_request_id from consult_requests where session_id = ?)", sessionId);
                jdbcTemplate.update("delete from consult_requests where session_id = ?", sessionId);
                jdbcTemplate.update("delete from query_routings where message_id in"
                        + " (select message_id from chat_messages where session_id = ?)", sessionId);
                jdbcTemplate.update("delete from chat_executions where session_id = ?", sessionId);
                jdbcTemplate.update("delete from chat_messages where session_id = ?", sessionId);
                jdbcTemplate.update("delete from chat_sessions where session_id = ?", sessionId);
            }
            for (Long userId : createdUserIds) {
                jdbcTemplate.update("delete from users where user_id = ?", userId);
            }
        });
        createdSessionIds.clear();
        createdUserIds.clear();
    }

    @Test
    @DisplayName("후속 답변으로 지역을 주면 PENDING 조건이 DB에서 FILLED로 바뀌고 답변 메시지가 연결된다")
    void followUpWithLocation_persistsFilledCondition() {
        Fixture fixture = givenWaitingClarification();
        givenFollowUpAnalysis("""
            {"conditions":[{"key":"location","status":"FILLED","value":"강남역"}]}
            """);

        processor.request(new ChatProcessingCommand(
                fixture.executionId(), fixture.sessionId(), fixture.followUpMessageId(), "강남역이요"));

        // 실행이 실패로 끝나면 상담 저장도 일어나지 않으므로 먼저 확인한다
        assertThat(loadExecution(fixture.executionId()))
                .as("파이프라인 실행 결과")
                .containsEntry("status", "COMPLETED");

        Map<String, Object> condition = loadCondition(fixture.consultRequestId(), LOCATION);
        assertThat(condition).containsEntry("status", "FILLED");
        assertThat(condition).containsEntry("condition_value", "강남역");
        assertThat(condition.get("answered_message_id")).isEqualTo(fixture.followUpMessageId());

        // 조건 저장 후 PENDING으로 돌아왔다가, 매장 안내가 최종 답변으로 저장되면 상담을 완료한다
        assertThat(loadRequestStatus(fixture.consultRequestId())).isEqualTo("DONE");
    }

    @Test
    @DisplayName("후속 답변으로 지역을 거절하면 PENDING 조건이 DB에서 DECLINED로 바뀐다")
    void followUpWithDecline_persistsDeclinedCondition() {
        Fixture fixture = givenWaitingClarification();
        givenFollowUpAnalysis("""
            {"conditions":[{"key":"location","status":"DECLINED","value":null}]}
            """);

        processor.request(new ChatProcessingCommand(
                fixture.executionId(), fixture.sessionId(), fixture.followUpMessageId(), "그냥 알려주기 싫어요"));

        Map<String, Object> condition = loadCondition(fixture.consultRequestId(), LOCATION);
        assertThat(condition).containsEntry("status", "DECLINED");
        assertThat(condition.get("condition_value")).isNull();
        assertThat(condition.get("answered_message_id")).isEqualTo(fixture.followUpMessageId());

        // 지역 없이 안내할 수 없다는 답을 끝으로 이 상담은 닫는다
        assertThat(loadRequestStatus(fixture.consultRequestId())).isEqualTo("DONE");
    }

    @Test
    @DisplayName("되묻는 조건은 그대로 두고 다른 조건만 답하면 기존 질문을 유지한 채 정정된 조건만 저장한다")
    void followUpWithOtherCondition_keepsWaitingAndPersistsCorrection() {
        Fixture fixture = givenWaitingClarification();
        givenFollowUpAnalysis("""
            {"conditions":[{"key":"serviceType","status":"FILLED","value":"PORT_IN"}]}
            """);

        long messageCountBefore = countMessages(fixture.sessionId());

        processor.request(new ChatProcessingCommand(
                fixture.executionId(), fixture.sessionId(), fixture.followUpMessageId(), "번호이동 하려고요"));

        // 정정된 조건은 저장되어야 한다
        Map<String, Object> serviceType = loadCondition(fixture.consultRequestId(), SERVICE_TYPE);
        assertThat(serviceType).containsEntry("status", "FILLED");
        assertThat(serviceType).containsEntry("condition_value", "PORT_IN");

        // 되묻는 중인 조건과 상담 상태는 그대로 유지되어야 한다
        assertThat(loadCondition(fixture.consultRequestId(), LOCATION)).containsEntry("status", "PENDING");
        assertThat(loadRequestStatus(fixture.consultRequestId())).isEqualTo("WAITING_CONDITION");

        // 이미 보낸 되묻기 질문이 중복 발송되면 안 된다
        assertThat(countMessages(fixture.sessionId())).isEqualTo(messageCountBefore);
    }

    private void givenFollowUpAnalysis(String json) {
        given(llmClient.generate(any())).willReturn(json);
    }

    private Map<String, Object> loadCondition(Long consultRequestId, String conditionKey) {
        return jdbcTemplate.queryForMap(
                "select status, condition_value, answered_message_id from consult_conditions"
                        + " where consult_request_id = ? and condition_key = ?",
                consultRequestId, conditionKey);
    }

    private Map<String, Object> loadExecution(Long executionId) {
        return jdbcTemplate.queryForMap(
                "select status, error_code from chat_executions where execution_id = ?", executionId);
    }

    private String loadRequestStatus(Long consultRequestId) {
        return jdbcTemplate.queryForObject(
                "select status from consult_requests where consult_request_id = ?",
                String.class, consultRequestId);
    }

    private long countMessages(Long sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from chat_messages where session_id = ?", Long.class, sessionId);
        return count != null ? count : 0L;
    }

    // 지역을 되묻고 답을 기다리는 상담 세션을 만든다
    private Fixture givenWaitingClarification() {
        Fixture fixture = transactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("clarify-" + UUID.randomUUID() + "@example.com")
                    .name("clarify")
                    .build();
            entityManager.persist(user);

            ChatSession session = ChatSession.builder()
                    .userId(user.getUserId())
                    .title("되묻기 세션")
                    .status(ChatSession.Status.NEED_CLARIFICATION)
                    .build();
            entityManager.persist(session);

            ChatMessage originQuestion = persistMessage(session, 1, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, "가까운 매장 찾아줘");
            ChatMessage clarification = persistMessage(session, 2, ChatMessage.Role.ASSISTANT,
                    ChatMessage.MessageType.CLARIFICATION, "어느 지역의 매장을 찾으시나요?");
            ChatMessage followUp = persistMessage(session, 3, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, "후속 답변");

            ChatExecution execution = ChatExecution.builder()
                    .session(session)
                    .inputMessage(followUp)
                    .status(ChatExecution.Status.RUNNING)
                    .build();
            entityManager.persist(execution);

            ConsultRequest request = ConsultRequest.builder()
                    .session(session)
                    .originMessage(originQuestion)
                    .subqueryOrder((short) 1)
                    .intent(ConsultRequest.Intent.STORE)
                    .queryText("가까운 매장 찾기")
                    .status(ConsultRequest.Status.WAITING_CONDITION)
                    .version(1)
                    .build();
            entityManager.persist(request);

            ConsultCondition pendingLocation = ConsultCondition.builder()
                    .consultRequest(request)
                    .conditionKey(LOCATION)
                    .source(ConsultCondition.Source.ASKED)
                    .status(ConsultCondition.Status.PENDING)
                    .askedMessage(clarification)
                    .build();
            entityManager.persist(pendingLocation);

            entityManager.flush();
            return new Fixture(
                    user.getUserId(), session.getSessionId(), followUp.getMessageId(),
                    execution.getExecutionId(), request.getConsultRequestId());
        });

        if (fixture != null) {
            createdUserIds.add(fixture.userId());
            createdSessionIds.add(fixture.sessionId());
        }
        return fixture;
    }

    private ChatMessage persistMessage(
            ChatSession session, int sequenceNo, ChatMessage.Role role,
            ChatMessage.MessageType messageType, String content) {

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(sequenceNo)
                .role(role)
                .messageType(messageType)
                .content(content)
                .status(ChatMessage.Status.COMPLETED)
                .build();
        entityManager.persist(message);
        return message;
    }

    private record Fixture(
            Long userId, Long sessionId, Long followUpMessageId, Long executionId, Long consultRequestId) {
    }
}
