package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.service.ChatActor;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
class ConsultTurnPreparationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired FollowupContextService contexts;
    @Autowired ConsultService consult;
    @Autowired JdbcConsultStateStore states;
    long userId;
    long sessionId;
    long requestId;
    int sequence;

    @BeforeEach
    void setup() {
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users(email,name) VALUES (?,'연결 검증') RETURNING user_id",
                        Long.class,
                        "turn-" + UUID.randomUUID() + "@example.com");
        sessionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                        Long.class,
                        userId);
        long origin = message("USER", "QUESTION", "유심 매장 알려줘");
        requestId =
                jdbc.queryForObject(
                        """
                        INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)
                        VALUES (?,?,1,'STORE','유심 매장') RETURNING consult_request_id
                        """,
                        Long.class,
                        sessionId,
                        origin);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id=?", requestId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void storedAnalysisWithoutLocationAsksForLocation() {
        var result =
                turns.prepareAnalysis(
                        sessionId,
                        analysis(Map.of("serviceType", "USIM_REISSUE")),
                        LocationStatus.MISSING);
        assertThat(result.prepared().decision().action()).isEqualTo(Action.ASK);
        assertThat(result.prepared().decision().waitingField()).isEqualTo("location");
        assertThat(result.prepared().decision().conditions().get("serviceType"))
                .isEqualTo(Condition.filled("USIM_REISSUE"));
        assertThat(states.load(sessionId, requestId).version()).isEqualTo(1);
    }

    @Test
    void blankExtractionDoesNotEraseStoredFilledValue() {
        jdbc.update(
                """
                INSERT INTO consult_conditions(consult_request_id,condition_key,condition_value,status)
                VALUES (?,'location','강남역','FILLED')
                """,
                requestId);
        var result =
                turns.prepareAnalysis(
                        sessionId, analysis(Map.of("location", " ")), LocationStatus.MISSING);
        assertThat(result.prepared().decision().action()).isEqualTo(Action.PROCEED);
        assertThat(result.prepared().decision().conditions().get("location"))
                .isEqualTo(Condition.filled("강남역"));
    }

    @Test
    void confirmedFollowupResumesOriginalRequestAndLinksAnswer() {
        long askedId = ask();
        long answerId = message("USER", "QUESTION", "강남역이요");
        var context = contexts.prepare(new ChatActor(userId, null), sessionId, answerId);
        var result =
                turns.prepareFollowup(
                        context,
                        new Selection(
                                requestId,
                                askedId,
                                "location",
                                Map.of("location", Condition.filled("강남역"))),
                        LocationStatus.MISSING);
        assertThat(result.preparation().prepared().decision().action()).isEqualTo(Action.PROCEED);
        consult.persist(
                result.preparation().prepared(),
                new MessageLinks(
                        null,
                        result.followup().userMessageId(),
                        result.followup().answeredField()));
        assertThat(states.load(sessionId, requestId).conditions().get("location"))
                .isEqualTo(Condition.filled("강남역"));
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT answered_message_id FROM consult_conditions
                                WHERE consult_request_id=? AND condition_key='location'
                                """,
                                Long.class,
                                requestId))
                .isEqualTo(answerId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM consult_requests WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(1);
    }

    @Test
    void refusalProducesGuidanceAndDoesNotMakeUpLocation() {
        long askedId = ask();
        long answerId = message("USER", "QUESTION", "위치는 알려주기 싫어요");
        var context = contexts.prepare(new ChatActor(userId, null), sessionId, answerId);
        var result =
                turns.prepareFollowup(
                        context,
                        new Selection(
                                requestId,
                                askedId,
                                "location",
                                Map.of("location", Condition.declined())),
                        LocationStatus.MISSING);
        assertThat(result.preparation().prepared().decision().action())
                .isEqualTo(Action.ALTERNATIVE_GUIDANCE);
        assertThat(result.followup().updates().get("location")).isEqualTo(Condition.declined());
    }

    @Test
    void foreignSessionCannotLoadAnalysisConsultation() {
        assertThatThrownBy(
                        () ->
                                turns.prepareAnalysis(
                                        Long.MAX_VALUE, analysis(Map.of()), LocationStatus.MISSING))
                .isInstanceOf(GeneralException.class);
        assertThat(states.load(sessionId, requestId).version()).isEqualTo(1);
    }

    private long ask() {
        var result = turns.prepareAnalysis(sessionId, analysis(Map.of()), LocationStatus.MISSING);
        long asked = message("ASSISTANT", "CLARIFICATION", result.prepared().decision().message());
        consult.persist(result.prepared(), new MessageLinks(asked, null, null));
        return asked;
    }

    private long message(String role, String type, String content) {
        return jdbc.queryForObject(
                """
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)
                VALUES (?,?,?,?,?,'COMPLETED',now()) RETURNING message_id
                """,
                Long.class,
                sessionId,
                ++sequence,
                role,
                type,
                content);
    }

    private IntentSubQueryResponse analysis(Map<String, String> conditions) {
        return new IntentSubQueryResponse(
                requestId, (short) 1, ConsultRequest.Intent.STORE, "유심 매장", conditions);
    }
}
