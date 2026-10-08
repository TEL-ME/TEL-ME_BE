package com.telme.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 각 테스트는 트랜잭션 롤백으로 데이터에 영향 없음
@SpringBootTest
@Transactional
class UnansweredOriginQuestionFinderTest {

    private static final Long USER_ID = 2L;

    @Autowired
    private UnansweredOriginQuestionFinder finder;

    @Autowired
    private EntityManager entityManager;

    private long session;
    private int sequence;

    @BeforeEach
    void setUp() {
        session = insertSession();
        sequence = 0;
    }

    @Test
    @DisplayName("되묻기 뒤 답변은 상담을 시작한 원래 질문을 잇는다")
    void 되묻기_답변은_원래_질문을_잇는다() {
        long origin = question("유심 재발급 가능한 매장을 알려주세요.");
        long request = request(origin);
        long asked = assistant("어느 지역에서 찾으시나요?");
        condition(request, "location", asked);
        long reply = question("서울 강남구");
        long answer = assistant("안내드릴 수 있는 정보가 없습니다.");
        execution(reply, answer);

        assertThat(originOf(answer)).isEqualTo("유심 재발급 가능한 매장을 알려주세요.");
    }

    @Test
    @DisplayName("되묻기가 없던 답변은 그 질문 자신이 원래 질문이다")
    void 되묻기가_없으면_질문_자신이_원문이다() {
        long origin = question("유심 재발급은 어떻게 하나요");
        request(origin);
        long answer = assistant("안내드릴 수 있는 정보가 없습니다.");
        execution(origin, answer);

        assertThat(originOf(answer)).isEqualTo("유심 재발급은 어떻게 하나요");
    }

    @Test
    @DisplayName("앞선 상담이 있어도 상담 없이 끝난 질문은 원래 질문이 없다")
    void 상담_없이_끝난_질문은_원문이_없다() {
        long before = question("요금제 바꾸고 싶어요");
        request(before);
        assistant("요금제 안내입니다.");
        long outOfScope = question("오늘 날씨 알려줘");
        long answer = assistant("안내드릴 수 있는 정보가 없습니다.");
        execution(outOfScope, answer);

        assertThat(originOf(answer)).isNull();
    }

    @Test
    @DisplayName("중간에 다른 FAQ를 물어본 뒤 기존 상담을 재개하면 그 상담의 원문을 쓴다")
    void 다른_질문을_거쳐도_재개한_상담의_원문을_쓴다() {
        long storeOrigin = question("유심 재발급 가능한 매장을 알려주세요.");
        long storeRequest = request(storeOrigin);
        long asked = assistant("어느 지역에서 찾으시나요?");
        condition(storeRequest, "location", asked);
        // 되묻기를 기다리는 중에 끼어든 새 질문. 이 답변의 원문은 자기 자신이다
        long roaming = question("로밍 요금제 알려주세요");
        long roamingAnswer = assistant("안내드릴 수 있는 정보가 없습니다.");
        request(roaming);
        execution(roaming, roamingAnswer);
        long reply = question("강남역이요");
        long storeAnswer = assistant("안내드릴 수 있는 정보가 없습니다.");
        execution(reply, storeAnswer);

        Map<Long, String> origins = finder.findByAnswerIds(List.of(roamingAnswer, storeAnswer));
        assertThat(origins.get(roamingAnswer)).isEqualTo("로밍 요금제 알려주세요");
        assertThat(origins.get(storeAnswer)).isEqualTo("유심 재발급 가능한 매장을 알려주세요.");
    }

    @Test
    @DisplayName("빈 목록은 조회하지 않는다")
    void 빈_목록은_조회하지_않는다() {
        assertThat(finder.findByAnswerIds(List.of())).isEmpty();
    }

    private String originOf(long answerMessageId) {
        return finder.findByAnswerIds(List.of(answerMessageId)).get(answerMessageId);
    }

    private long insertSession() {
        return id("insert into chat_sessions (user_id, title) values (:userId, '원문 조회 테스트')"
                + " returning session_id", Map.of("userId", USER_ID));
    }

    private long question(String content) {
        return message("USER", "QUESTION", content);
    }

    private long assistant(String content) {
        return message("ASSISTANT", "ANSWER", content);
    }

    private long message(String role, String type, String content) {
        return id("insert into chat_messages (session_id, sequence_no, role, message_type, status,"
                        + " content) values (:sessionId, :sequenceNo, :role, :type, 'COMPLETED',"
                        + " :content) returning message_id",
                Map.of("sessionId", session, "sequenceNo", ++sequence,
                        "role", role, "type", type, "content", content));
    }

    private long request(long originMessageId) {
        return id("insert into consult_requests (session_id, origin_message_id, subquery_order,"
                        + " intent, query_text) values (:sessionId, :originMessageId, 0, 'FAQ',"
                        + " '질의') returning consult_request_id",
                Map.of("sessionId", session, "originMessageId", originMessageId));
    }

    private void condition(long requestId, String key, long askedMessageId) {
        id("insert into consult_conditions (consult_request_id, condition_key, source, status,"
                        + " asked_message_id) values (:requestId, :key, 'LLM', 'PENDING',"
                        + " :askedMessageId) returning condition_id",
                Map.of("requestId", requestId, "key", key, "askedMessageId", askedMessageId));
    }

    private void execution(long inputMessageId, long outputMessageId) {
        id("insert into chat_executions (session_id, input_message_id, output_message_id, status)"
                        + " values (:sessionId, :inputMessageId, :outputMessageId, 'COMPLETED')"
                        + " returning execution_id",
                Map.of("sessionId", session, "inputMessageId", inputMessageId,
                        "outputMessageId", outputMessageId));
    }

    private long id(String sql, Map<String, Object> parameters) {
        var query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }
}
