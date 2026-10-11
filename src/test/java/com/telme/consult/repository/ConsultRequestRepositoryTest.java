package com.telme.consult.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.member.entity.Guest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ConsultRequestRepositoryTest {

    @Autowired
    private ConsultRequestRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 대기_상담은_가장_최근에_되물은_것부터_조회한다() {
        ChatSession session = createSession();
        ConsultRequest first = waiting(session, 1, "유심 재발급 가능 매장", "location", 2);
        ConsultRequest second = waiting(session, 3, "번호 이동 시 필요한 서류", "unpaid_bill", 4);

        var found = repository.findByLatestAskedQuestion(
                session.getSessionId(), ConsultRequest.Status.WAITING_CONDITION);

        assertThat(found).extracting(ConsultRequest::getConsultRequestId)
                .containsExactly(second.getConsultRequestId(), first.getConsultRequestId());
    }

    @Test
    void 다른_채팅방과_다른_상태의_상담은_제외한다() {
        ChatSession session = createSession();
        ChatSession other = createSession();
        waiting(other, 1, "다른 방 질문", "location", 2);
        ConsultRequest done = waiting(session, 1, "끝난 상담", "location", 2);
        entityManager.createQuery("update ConsultRequest r set r.status = :s where r = :r")
                .setParameter("s", ConsultRequest.Status.DONE)
                .setParameter("r", done)
                .executeUpdate();
        entityManager.clear();

        var found = repository.findByLatestAskedQuestion(
                session.getSessionId(), ConsultRequest.Status.WAITING_CONDITION);

        assertThat(found).isEmpty();
    }

    private ConsultRequest waiting(
            ChatSession session, int userSeq, String query, String key, int askSeq) {
        ChatMessage user = message(session, userSeq, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, query);
        ChatMessage ask = message(session, askSeq, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.CLARIFICATION, "되묻기");
        ConsultRequest request = ConsultRequest.builder()
                .session(session)
                .originMessage(user)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.FAQ)
                .queryText(query)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .build();
        entityManager.persist(request);
        entityManager.persist(ConsultCondition.builder()
                .consultRequest(request)
                .conditionKey(key)
                .askedMessage(ask)
                .build());
        entityManager.flush();
        return request;
    }

    private ChatMessage message(ChatSession session, int seq, ChatMessage.Role role,
            ChatMessage.MessageType type, String content) {
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(seq)
                .role(role)
                .messageType(type)
                .content(content)
                .status(ChatMessage.Status.COMPLETED)
                .build();
        entityManager.persist(message);
        return message;
    }

    private ChatSession createSession() {
        Guest guest = Guest.issue(Duration.ofDays(30), Clock.systemUTC());
        entityManager.persist(guest);
        ChatSession session = ChatSession.builder().guestId(guest.getGuestId()).build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
