package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.telme.chat.converter.ChatContextFormatter;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"chat.summary.grounded-output=true", "chat.summary.trigger-messages=4",
        "chat.summary.retained-messages=2"})
class ChatGroundedSummaryIntegrationTest {
    @Autowired ChatSessionService sessions;
    @Autowired ChatExecutionService executions;
    @Autowired ChatContextBuilder contexts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ChatProcessingPort processing;
    @MockitoBean LlmClient model;

    @Test
    void storesVerifiedOriginalsAndCursorTogetherAndRejectsTamperedMemory() {
        long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'요약 검증') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@example.com");
        var actor = new ChatActor(user, null);
        long session = sessions.createSession(actor, new ChatSessionCreateRequest("요약 검증")).sessionId();
        try {
            when(model.generate(any(LlmRequest.class))).thenAnswer(call -> {
                var input = ((LlmRequest) call.getArgument(0)).userPrompt();
                var matcher = Pattern.compile("\\[(\\d+)/\\d+ 고객 원문]").matcher(input);
                assertThat(matcher.find()).isTrue();
                return "{\"messageIds\":[" + matcher.group(1) + "]}";
            });
            var first = sessions.sendMessage(actor, session, new ChatMessageSendRequest("유심 재발급 문의입니다."));
            executions.completeAnswer(first.executionId(), new ChatAnswer(ChatMessage.MessageType.ANSWER,
                    "99,999원이라고 잘못 안내한 이전 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), null));
            var social = sessions.sendMessage(actor, session, new ChatMessageSendRequest("감사합니다"));
            executions.completeAnswer(social.executionId(), new ChatAnswer(ChatMessage.MessageType.ANSWER,
                    "천만에요", null, List.of(), null));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                    "SELECT summary_through_sequence_no FROM chat_sessions WHERE session_id=?",
                    Integer.class, session)).isEqualTo(2));

            var current = sessions.sendMessage(actor, session, new ChatMessageSendRequest("그건 얼마예요?"));
            var command = new ChatProcessingCommand(current.executionId(), session, current.messageId(),
                    "그건 얼마예요?");
            var context = contexts.build(command, 1024);
            assertThat(context.summarySources()).extracting(ChatContextMessage::messageId)
                    .containsExactly(first.messageId());
            assertThat(context.history()).extracting(ChatContextMessage::sequenceNo).containsExactly(3, 4);
            assertThat(ChatContextFormatter.format(context, 1024, new ChatTokenEstimator()))
                    .contains("유심 재발급").doesNotContain("99,999");

            String stored = jdbc.queryForObject("SELECT summary FROM chat_sessions WHERE session_id=?",
                    String.class, session);
            jdbc.update("UPDATE chat_sessions SET summary=? WHERE session_id=?",
                    stored.replace("유심 재발급 문의입니다.", "모든 업무가 무료입니다."), session);
            var fallback = contexts.build(command, 1024);
            assertThat(fallback.summary()).isNull();
            assertThat(fallback.summarySources()).isEmpty();
            assertThat(fallback.history()).extracting(ChatContextMessage::sequenceNo).containsExactly(1, 2, 3, 4);
        } finally {
            jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", session);
            jdbc.update("DELETE FROM users WHERE user_id=?", user);
        }
    }
}
