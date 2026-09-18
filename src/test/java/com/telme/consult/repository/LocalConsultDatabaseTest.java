package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.CompoundDialoguePlanner;
import com.telme.consult.service.CompoundDialoguePlanner.Request;
import com.telme.consult.service.CompoundDialoguePlanner.Status;
import com.telme.consult.service.DialogueService;
import com.telme.global.common.exception.GeneralException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 기존 로컬 DB 안의 무작위 전용 스키마만 생성/삭제한다. public 테이블은 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named = "TELME_DB_TESTS", matches = "true")
class LocalConsultDatabaseTest {
    JdbcTemplate admin, jdbc;
    JdbcConsultStateStore states;
    TransactionTemplate transaction;
    String schema;
    long session, answer;

    @BeforeEach
    void setup() throws Exception {
        String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
        if (!port.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid local port");
        }
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/telme";
        String user = System.getenv().getOrDefault("POSTGRES_USER", "telme");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "telme");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "seohee_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
        admin.execute("CREATE SCHEMA " + schema);
        var ds =
                new DriverManagerDataSource(
                        url + "?currentSchema=" + schema + ",public", user, password);
        jdbc = new JdbcTemplate(ds);
        for (String file : List.of("init-schema.sql")) {
            try (var stream = getClass().getResourceAsStream("/consult-fixtures/" + file)) {
                jdbc.execute(
                        new String(
                                Objects.requireNonNull(stream).readAllBytes(),
                                StandardCharsets.UTF_8));
            }
        }
        jdbc.execute("INSERT INTO users(user_id,name) VALUES (1,'test owner'),(2,'test other')");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction = tx;
        states = new JdbcConsultStateStore(jdbc, tx);
        session = session(1L, null);
        answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED");
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    long session(Long user, UUID guest) {
        if (guest != null) {
            jdbc.update(
                    "INSERT INTO guests(guest_id,expires_at) VALUES (?,now()+interval '1 day') ON"
                            + " CONFLICT DO NOTHING",
                    guest);
        }
        return jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,guest_id) VALUES (?,?) RETURNING session_id",
                Long.class,
                user,
                guest);
    }

    long message(long sid, String role, String type, String status) {
        return jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,status,content)"
                    + " VALUES (?,(SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE"
                    + " session_id=?),?,?,?,'test') RETURNING message_id",
                Long.class,
                sid,
                sid,
                role,
                type,
                status);
    }

    long request(long sid) {
        long origin = message(sid, "USER", "QUESTION", "COMPLETED");
        return jdbc.queryForObject(
                "INSERT INTO"
                    + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                    + " VALUES (?,?,0,'STORE','nearby') RETURNING consult_request_id",
                Long.class,
                sid,
                origin);
    }

    @Test
    void missingOrDifferentSessionConsultationReturnsNotFound() {
        long rid = request(session);
        for (long sid : new long[] {session, session(2L, null)}) {
            long id = sid == session ? Long.MAX_VALUE : rid;
            var error = assertThrows(GeneralException.class, () -> states.load(sid, id));
            assertEquals(ConsultErrorCode.REQUEST_NOT_FOUND, error.getErrorCode());
            assertEquals(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    error.getErrorCode().getStatus());
        }
    }

    @Test
    void oldUserMessageCannotAnswerNewClarificationAndRollbackPreservesWaiting() {
        long rid = request(session);
        long stale = message(session, "USER", "QUESTION", "COMPLETED");
        var service = new DialogueService(p -> p.fallbackText());
        var ask =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of(),
                                LocationStatus.MISSING));
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting = states.save(session, 1, ask, new MessageLinks(question, null, null));
        var decision =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                waiting.conditions(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.MISSING));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        states.save(
                                session,
                                waiting.version(),
                                decision,
                                new MessageLinks(null, stale, "location")));
        var preserved = states.load(session, rid);
        assertEquals(waiting.version(), preserved.version());
        assertEquals("WAITING_CONDITION", preserved.status());
        assertEquals(ConditionStatus.PENDING, preserved.conditions().get("location").status());
        long fresh = message(session, "USER", "QUESTION", "COMPLETED");
        var saved =
                states.save(
                        session,
                        preserved.version(),
                        decision,
                        new MessageLinks(null, fresh, "location"));
        assertEquals("강남역", saved.conditions().get("location").value());
        assertEquals("PENDING", saved.status());
    }

    @Test
    void latestSchemaRejectsUnknownOwnerReferences() {
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> session(999L, null));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO chat_sessions(guest_id) VALUES (?)",
                                UUID.randomUUID()));
    }

    @Test
    void askSaveReloadAnswerAndProceedWithoutMarkingDone() {
        long rid = request(session);
        var initial = states.load(session, rid);
        var service = new DialogueService(p -> p.fallbackText());
        var ask =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                initial.conditions(),
                                Map.of(),
                                LocationStatus.MISSING));
        long asked = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting =
                states.save(session, initial.version(), ask, new MessageLinks(asked, null, null));
        assertEquals("WAITING_CONDITION", waiting.status());
        assertEquals(ConditionStatus.PENDING, waiting.conditions().get("location").status());
        long replied = message(session, "USER", "QUESTION", "COMPLETED");
        var next =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                states.load(session, rid).conditions(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.MISSING));
        assertEquals(Action.PROCEED, next.action());
        var saved =
                states.save(
                        session,
                        waiting.version(),
                        next,
                        new MessageLinks(null, replied, "location"));
        assertEquals("PENDING", saved.status());
        assertEquals("강남역", saved.conditions().get("location").value());
        assertEquals(
                replied,
                jdbc.queryForObject(
                        "SELECT answered_message_id FROM consult_conditions WHERE"
                                + " consult_request_id=?",
                        Long.class,
                        rid));
        assertEquals(
                asked,
                jdbc.queryForObject(
                        "SELECT asked_message_id FROM consult_conditions WHERE"
                                + " consult_request_id=?",
                        Long.class,
                        rid));
        var correction =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                saved.conditions(),
                                Map.of("location", Condition.filled("홍대입구")),
                                LocationStatus.MISSING));
        states.save(session, saved.version(), correction, MessageLinks.none());
        assertNull(
                jdbc.queryForObject(
                        "SELECT answered_message_id FROM consult_conditions WHERE"
                                + " consult_request_id=?",
                        Long.class,
                        rid));
        assertEquals(
                "EXTRACTED",
                jdbc.queryForObject(
                        "SELECT source FROM consult_conditions WHERE consult_request_id=?",
                        String.class,
                        rid));
    }

    @Test
    void staleUpdateAndCrossSessionMessageAreRejectedWithoutChanges() {
        long rid = request(session);
        var initial = states.load(session, rid);
        var ask =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        long wrong = message(session(2L, null), "ASSISTANT", "CLARIFICATION", "COMPLETED");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        states.save(
                                session,
                                initial.version(),
                                ask,
                                new MessageLinks(wrong, null, null)));
        assertEquals(initial.version(), states.load(session, rid).version());
        long correct = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        states.save(session, initial.version(), ask, new MessageLinks(correct, null, null));
        assertThrows(
                JdbcConsultStateStore.StateConflict.class,
                () ->
                        states.save(
                                session,
                                initial.version(),
                                ask,
                                new MessageLinks(correct, null, null)));
    }

    @Test
    void twoSubrequestsKeepIndependentConditions() {
        long a = request(session), b = request(session);
        var decision =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        a,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of("location", Condition.filled("강남역")),
                                        LocationStatus.MISSING));
        states.save(session, states.load(session, a).version(), decision, MessageLinks.none());
        assertTrue(states.load(session, b).conditions().isEmpty());
    }

    @Test
    void duplicateClarificationRollsBackNewMessageInSharedTransaction() {
        long rid = request(session);
        var ask =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        long firstMessage = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting =
                states.save(
                        session,
                        states.load(session, rid).version(),
                        ask,
                        new MessageLinks(firstMessage, null, null));
        var repeatedAsk =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        waiting.conditions(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        int count = jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class);
        var error =
                assertThrows(
                        JdbcConsultStateStore.ClarificationAlreadyPending.class,
                        () ->
                                transaction.executeWithoutResult(
                                        tx -> {
                                            long duplicate =
                                                    message(
                                                            session,
                                                            "ASSISTANT",
                                                            "CLARIFICATION",
                                                            "COMPLETED");
                                            states.save(
                                                    session,
                                                    waiting.version(),
                                                    repeatedAsk,
                                                    new MessageLinks(duplicate, null, null));
                                        }));
        assertEquals(firstMessage, error.messageId());
        assertEquals(
                count, jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class));
        assertEquals(waiting.version(), states.load(session, rid).version());
    }

    @Test
    void completeRequiresLaterCompletedAnswerAndBlocksFurtherUpdates() {
        long rid = request(session);
        var initial = states.load(session, rid);
        assertThrows(
                IllegalArgumentException.class,
                () -> states.complete(session, rid, initial.version(), answer));
        long generating = message(session, "ASSISTANT", "ANSWER", "GENERATING");
        assertThrows(
                IllegalArgumentException.class,
                () -> states.complete(session, rid, initial.version(), generating));
        long finalAnswer = message(session, "ASSISTANT", "ANSWER", "COMPLETED");
        var done = states.complete(session, rid, initial.version(), finalAnswer);
        assertEquals("DONE", done.status());
        var service =
                new com.telme.consult.service.ConsultService(
                        states, new DialogueService(p -> fail("종료된 상담은 모델을 호출하지 않음")));
        var error =
                assertThrows(
                        GeneralException.class,
                        () ->
                                service.prepare(
                                        session,
                                        rid,
                                        Purpose.GENERAL_FAQ,
                                        Map.of(),
                                        LocationStatus.MISSING));
        assertEquals(ConsultErrorCode.REQUEST_CLOSED, error.getErrorCode());
        assertEquals(
                org.springframework.http.HttpStatus.CONFLICT, error.getErrorCode().getStatus());
        assertThrows(GeneralException.class, () -> states.cancel(session, rid, done.version()));
        var next =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.GENERAL_FAQ,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        assertThrows(
                GeneralException.class,
                () -> states.save(session, done.version(), next, MessageLinks.none()));
    }

    @Test
    void waitingCannotCompleteAndCancelPreservesConditions() {
        long rid = request(session), other = request(session);
        var ask =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        long asked = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting =
                states.save(
                        session,
                        states.load(session, rid).version(),
                        ask,
                        new MessageLinks(asked, null, null));
        long finalAnswer = message(session, "ASSISTANT", "ANSWER", "COMPLETED");
        assertThrows(
                IllegalStateException.class,
                () -> states.complete(session, rid, waiting.version(), finalAnswer));
        var cancelled = states.cancel(session, rid, waiting.version());
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(waiting.conditions(), cancelled.conditions());
        assertEquals("PENDING", states.load(session, other).status());
    }

    @Test
    void completionRejectsOtherSessionAndStaleVersion() {
        long rid = request(session);
        var initial = states.load(session, rid);
        long wrong = message(session(2L, null), "ASSISTANT", "ANSWER", "COMPLETED");
        assertThrows(
                IllegalArgumentException.class,
                () -> states.complete(session, rid, initial.version(), wrong));
        assertThrows(
                JdbcConsultStateStore.StateConflict.class,
                () -> states.cancel(session, rid, initial.version() + 1));
        assertEquals(initial, states.load(session, rid));
    }

    @Test
    void clarificationBeforeOriginIsRejectedWithoutStateChanges() {
        long oldQuestion = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        long rid = request(session);
        var initial = states.load(session, rid);
        var ask =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        states.save(
                                session,
                                initial.version(),
                                ask,
                                new MessageLinks(oldQuestion, null, null)));
        assertEquals(initial, states.load(session, rid));
    }

    @Test
    void compoundConsultationPersistsPartialCompletionAndResumesOnlyStore() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var planner =
                new CompoundDialoguePlanner(
                        new DialogueService(
                                p -> {
                                    calls.incrementAndGet();
                                    return p.fallbackText();
                                }));
        long origin = message(session, "USER", "QUESTION", "COMPLETED");
        long faq =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                            + " VALUES (?,?,0,'FAQ','서류 안내') RETURNING consult_request_id",
                        Long.class,
                        session,
                        origin);
        long store =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                            + " VALUES (?,?,1,'STORE','가까운 매장') RETURNING consult_request_id",
                        Long.class,
                        session,
                        origin);
        var first =
                planner.plan(
                        List.of(
                                planned(faq, Purpose.GENERAL_FAQ, null, Map.of()),
                                planned(store, Purpose.NEARBY_STORE, null, Map.of())));
        assertEquals(faq, first.ready().getFirst().consultRequestId());
        assertEquals(store, first.clarification().consultRequestId());
        long asked =
                transaction.execute(
                        tx -> {
                            long faqAnswer = message(session, "ASSISTANT", "ANSWER", "COMPLETED");
                            states.complete(session, faq, 1, faqAnswer);
                            long question =
                                    message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
                            states.save(
                                    session,
                                    1,
                                    first.clarification(),
                                    new MessageLinks(question, null, null));
                            return question;
                        });
        assertEquals("DONE", states.load(session, faq).status());
        assertEquals("WAITING_CONDITION", states.load(session, store).status());
        var waiting =
                planner.plan(
                        List.of(
                                planned(faq, Purpose.GENERAL_FAQ, null, Map.of()),
                                planned(store, Purpose.NEARBY_STORE, asked, Map.of())));
        assertNull(waiting.clarification());
        assertTrue(waiting.ready().isEmpty());
        assertEquals(1, calls.get());

        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var resumed =
                planner.plan(
                        List.of(
                                planned(faq, Purpose.GENERAL_FAQ, null, Map.of()),
                                planned(
                                        store,
                                        Purpose.NEARBY_STORE,
                                        asked,
                                        Map.of("location", Condition.filled("강남역")))));
        assertEquals(1, resumed.ready().size());
        assertEquals(store, resumed.ready().getFirst().consultRequestId());
        var saved =
                states.save(
                        session,
                        states.load(session, store).version(),
                        resumed.ready().getFirst(),
                        new MessageLinks(null, reply, "location"));
        assertEquals("PENDING", saved.status());
        assertEquals("강남역", saved.conditions().get("location").value());
        assertTrue(states.load(session, faq).conditions().isEmpty());
        long storeAnswer = message(session, "ASSISTANT", "STORE_RESULT", "COMPLETED");
        states.complete(session, store, saved.version(), storeAnswer);
        assertEquals("DONE", states.load(session, store).status());
        assertEquals(1, calls.get());
    }

    private Request planned(
            long id, Purpose purpose, Long pending, Map<String, Condition> updates) {
        var snapshot = states.load(session, id);
        return new Request(
                new DialogueInput(
                        id, purpose, snapshot.conditions(), updates, LocationStatus.MISSING),
                Status.valueOf(snapshot.status()),
                pending);
    }

    @Test
    void concurrentConditionUpdatesAllowOneWriterAndRejectStaleWriter() throws Exception {
        long rid = request(session);
        var initial = states.load(session, rid);
        var service = new DialogueService(p -> p.fallbackText());
        var a =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.MISSING));
        var b =
                service.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of("location", Condition.filled("홍대입구역")),
                                LocationStatus.MISSING));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var outcomes = new ArrayList<Future<Boolean>>();
            for (var decision : List.of(a, b)) {
                outcomes.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!go.await(5, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Start barrier timed out");
                                    }
                                    try {
                                        states.save(
                                                session,
                                                initial.version(),
                                                decision,
                                                MessageLinks.none());
                                        return true;
                                    } catch (JdbcConsultStateStore.StateConflict expected) {
                                        return false;
                                    }
                                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            int successes = 0;
            for (var outcome : outcomes) {
                if (outcome.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }
        var saved = states.load(session, rid);
        assertEquals(initial.version() + 1, saved.version());
        assertTrue(Set.of("강남역", "홍대입구역").contains(saved.conditions().get("location").value()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM consult_conditions WHERE consult_request_id=?",
                        Integer.class,
                        rid));
    }

    @Test
    void consultationServicePersistsQuestionAndResumesFromReloadedState() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    calls.incrementAndGet();
                                    assertFalse(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive());
                                    return prompt.fallbackText();
                                }));
        long rid = request(session);
        var prepared =
                service.prepare(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        assertEquals(Action.ASK, prepared.decision().action());
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting = service.persist(prepared, new MessageLinks(question, null, null));
        assertEquals("WAITING_CONDITION", states.load(session, rid).status());
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var resumed =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        assertEquals(Action.PROCEED, resumed.decision().action());
        var saved = service.persist(resumed, new MessageLinks(null, reply, "location"));
        assertEquals(waiting.version() + 1, saved.version());
        assertEquals("PENDING", saved.status());
        assertEquals("강남역", states.load(session, rid).conditions().get("location").value());
        assertEquals(1, calls.get());
        var correction =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("홍대입구역")),
                        LocationStatus.MISSING);
        service.persist(correction, MessageLinks.none());
        assertEquals("홍대입구역", states.load(session, rid).conditions().get("location").value());
        assertEquals(1, calls.get());
    }

    @Test
    void consultationServiceRecordsRefusalWithoutAnotherQuestion() {
        var service =
                new com.telme.consult.service.ConsultService(
                        states, new DialogueService(prompt -> prompt.fallbackText()));
        long rid = request(session);
        var first =
                service.prepare(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        service.persist(first, new MessageLinks(question, null, null));
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var refused =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.declined()),
                        LocationStatus.DECLINED);
        assertEquals(Action.ALTERNATIVE_GUIDANCE, refused.decision().action());
        service.persist(refused, new MessageLinks(null, reply, "location"));
        assertEquals(
                ConditionStatus.DECLINED,
                states.load(session, rid).conditions().get("location").status());
    }

    @Test
    void consultationServiceRejectsPreparedTurnWhenStateChangedDuringGeneration() {
        var service =
                new com.telme.consult.service.ConsultService(
                        states, new DialogueService(prompt -> prompt.fallbackText()));
        long rid = request(session);
        var stale =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        var fresh =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("역삼역")),
                        LocationStatus.MISSING);
        service.persist(fresh, MessageLinks.none());
        assertThrows(
                JdbcConsultStateStore.StateConflict.class,
                () -> service.persist(stale, MessageLinks.none()));
        assertEquals("역삼역", states.load(session, rid).conditions().get("location").value());
    }

    @Test
    void serviceDoesNotRegenerateUnansweredClarification() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    calls.incrementAndGet();
                                    return prompt.fallbackText();
                                }));
        long rid = request(session);
        var first =
                service.prepare(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var waiting = service.persist(first, new MessageLinks(question, null, null));

        var pending =
                assertThrows(
                        JdbcConsultStateStore.ClarificationAlreadyPending.class,
                        () ->
                                service.prepare(
                                        session,
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        LocationStatus.MISSING));
        assertEquals(question, pending.messageId());
        assertEquals(1, calls.get());
        assertEquals(waiting.version(), states.load(session, rid).version());
        assertEquals("WAITING_CONDITION", states.load(session, rid).status());

        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var next =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        assertEquals(Action.PROCEED, next.decision().action());
        service.persist(next, new MessageLinks(null, reply, "location"));
        assertEquals("강남역", states.load(session, rid).conditions().get("location").value());
        assertEquals(1, calls.get());
    }

    @Test
    void conditionWithoutSavedQuestionStillGeneratesClarification() {
        long rid = request(session);
        jdbc.update(
                "INSERT INTO consult_conditions(consult_request_id,condition_key,status) "
                        + "VALUES (?,'location','PENDING')",
                rid);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    calls.incrementAndGet();
                                    return prompt.fallbackText();
                                }));

        assertEquals(
                Action.ASK,
                service.prepare(
                                session,
                                rid,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                LocationStatus.MISSING)
                        .decision()
                        .action());
        assertEquals(1, calls.get());
    }

    @Test
    void pendingQuestionLookupIsScopedToConversationAndRequest() {
        long rid = request(session);
        var dialogue = new DialogueService(prompt -> prompt.fallbackText());
        var ask =
                dialogue.decide(
                        new DialogueInput(
                                rid,
                                Purpose.NEARBY_STORE,
                                Map.of(),
                                Map.of(),
                                LocationStatus.MISSING));
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        states.save(session, 1, ask, new MessageLinks(question, null, null));

        assertEquals(
                Optional.of(question),
                states.findPendingClarificationMessageId(session, rid, "location"));
        assertTrue(
                states.findPendingClarificationMessageId(session(2L, null), rid, "location")
                        .isEmpty());
        assertTrue(
                states.findPendingClarificationMessageId(session, request(session), "location")
                        .isEmpty());
        assertTrue(states.findPendingClarificationMessageId(session, rid, "serviceType").isEmpty());
    }

    @Test
    void prepareRejectsOuterTransactionBeforeModelInvocation() {
        long rid = request(session);
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    throw new AssertionError("모델을 호출하면 안 됨");
                                }));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status ->
                                        service.prepare(
                                                session,
                                                rid,
                                                Purpose.NEARBY_STORE,
                                                Map.of(),
                                                LocationStatus.MISSING)));
        assertEquals(1, states.load(session, rid).version());
    }

    @Test
    void pendingTurnReturnsExistingQuestionWithoutGeneratingOrChangingState() {
        long rid = request(session);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    calls.incrementAndGet();
                                    return prompt.fallbackText();
                                }));
        var first =
                service.prepareTurn(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        assertFalse(first.waitingForReply());
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var saved = service.persist(first.prepared(), new MessageLinks(question, null, null));
        var pending =
                service.prepareTurn(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        assertTrue(pending.waitingForReply());
        assertNull(pending.prepared());
        assertEquals(question, pending.pendingMessageId());
        assertEquals(saved, states.load(session, rid));
        assertEquals(1, calls.get());
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var resumed =
                service.prepareTurn(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        assertFalse(resumed.waitingForReply());
        var ready = service.persist(resumed.prepared(), new MessageLinks(null, reply, "location"));
        assertEquals("강남역", ready.conditions().get("location").value());
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentPreparedQuestionsPersistOnlyOneWithAtomicMessageSave() throws Exception {
        long rid = request(session);
        var service =
                new com.telme.consult.service.ConsultService(
                        states, new DialogueService(prompt -> prompt.fallbackText()));
        var first =
                service.prepare(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        var second =
                service.prepare(
                        session, rid, Purpose.NEARBY_STORE, Map.of(), LocationStatus.MISSING);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var outcomes = new ArrayList<Future<Boolean>>();
            for (var prepared : List.of(first, second)) {
                outcomes.add(
                        pool.submit(
                                () -> {
                                    if (!start.await(5, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Start timed out");
                                    }
                                    try {
                                        transaction.executeWithoutResult(
                                                status -> {
                                                    // Chat 쪽 순번 발급과 같은 채팅방 잠금을 사용한다.
                                                    jdbc.queryForObject(
                                                            "SELECT session_id FROM chat_sessions"
                                                                + " WHERE session_id=? FOR UPDATE",
                                                            Long.class,
                                                            session);
                                                    long question =
                                                            message(
                                                                    session,
                                                                    "ASSISTANT",
                                                                    "CLARIFICATION",
                                                                    "COMPLETED");
                                                    service.persist(
                                                            prepared,
                                                            new MessageLinks(question, null, null));
                                                });
                                        return true;
                                    } catch (JdbcConsultStateStore.StateConflict conflict) {
                                        assertEquals(
                                                "CONSULT409-0", conflict.getErrorCode().getCode());
                                        return false;
                                    }
                                }));
            }
            start.countDown();
            int succeeded = 0;
            for (var result : outcomes) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages WHERE session_id=? AND"
                                + " message_type='CLARIFICATION'",
                        Integer.class,
                        session));
        assertEquals("WAITING_CONDITION", states.load(session, rid).status());
    }

    @Test
    void correctionWhileWaitingPreservesQuestionAndResumesWithUpdatedService() {
        long rid = request(session);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                prompt -> {
                                    calls.incrementAndGet();
                                    return prompt.fallbackText();
                                }));
        var first =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("USIM_REISSUE")),
                        LocationStatus.MISSING);
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        service.persist(first, new MessageLinks(question, null, null));
        var correction =
                service.prepareTurn(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("NUMBER_TRANSFER")),
                        LocationStatus.MISSING);
        assertTrue(correction.waitingForReply());
        assertEquals(question, correction.pendingMessageId());
        var saved = service.persistWaitingChanges(correction);
        assertEquals("NUMBER_TRANSFER", saved.conditions().get("serviceType").value());
        assertEquals("WAITING_CONDITION", saved.status());
        assertEquals(
                question,
                states.findPendingClarificationMessageId(session, rid, "location").orElseThrow());
        assertThrows(
                JdbcConsultStateStore.StateConflict.class,
                () -> service.persistWaitingChanges(correction));
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var resumed =
                service.prepareTurn(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        var ready = service.persist(resumed.prepared(), new MessageLinks(null, reply, "location"));
        assertEquals("NUMBER_TRANSFER", ready.conditions().get("serviceType").value());
        assertEquals("강남역", ready.conditions().get("location").value());
        assertEquals(1, calls.get());
    }

    @Test
    void waitingCorrectionRejectsWrongQuestionAndKeepsOldValues() {
        long rid = request(session);
        var service =
                new com.telme.consult.service.ConsultService(
                        states,
                        new DialogueService(
                                com.telme.consult.service.ClarificationTextGenerator.template()));
        var first =
                service.prepare(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("USIM_REISSUE")),
                        LocationStatus.MISSING);
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        service.persist(first, new MessageLinks(question, null, null));
        var correction =
                service.prepareTurn(
                        session,
                        rid,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("NUMBER_TRANSFER")),
                        LocationStatus.MISSING);
        assertThrows(
                JdbcConsultStateStore.StateConflict.class,
                () ->
                        states.saveWhileWaiting(
                                session,
                                correction.prepared().expectedVersion(),
                                correction.prepared().decision(),
                                question + 999));
        assertEquals(
                "USIM_REISSUE", states.load(session, rid).conditions().get("serviceType").value());
    }
}
