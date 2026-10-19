package com.telme.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GuestSuccessionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Security → 컨트롤러 → 서비스 → DB 흐름을 실제 컨텍스트로 확인한다. 테스트마다 롤백되고, 회원은 매번 새로 만들어 개수가 섞이지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiIntegrationTest {

    private static final String URL = "/api/v1/chat/notifications";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GuestSuccessionService guestSuccessionService;

    @Autowired
    private UserRepository userRepository;

    private long ownerId;
    private MockHttpSession owner;
    private long room;

    @BeforeEach
    void setUp() {
        ownerId = user("owner");
        owner = memberSession(ownerId);
        room = chatSession(ownerId, null, "요금제 상담");
    }

    @Test
    @DisplayName("답변 알림을 저장하면 목록과 안 읽은 개수에 나온다")
    void 저장하면_목록과_개수에_나온다() throws Exception {
        long answer = message(room, "ASSISTANT", "ANSWER", "5G 요금제는\n세 가지가 있습니다.");

        save(owner, room, answer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.type").value("ANSWER_ARRIVED"))
                .andExpect(jsonPath("$.result.title").value("요금제 상담"))
                .andExpect(jsonPath("$.result.body").value("5G 요금제는 세 가지가 있습니다."))
                .andExpect(jsonPath("$.result.sessionId").value(room))
                .andExpect(jsonPath("$.result.messageId").value(answer))
                .andExpect(jsonPath("$.result.read").value(false));

        mvc.perform(get(URL).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.length()").value(1))
                .andExpect(jsonPath("$.result.notifications[0].messageId").value(answer))
                .andExpect(jsonPath("$.result.hasNext").value(false));
        expectUnread(owner, 1);
    }

    @Test
    @DisplayName("같은 답변을 다시 저장하면 같은 알림을 돌려주고 하나만 남는다")
    void 같은_답변은_하나만_남는다() throws Exception {
        long answer = message(room, "ASSISTANT", "ANSWER", "답변");
        Integer first = notificationId(save(owner, room, answer));

        save(owner, room, answer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notificationId").value(first));
        expectUnread(owner, 1);
    }

    @Test
    @DisplayName("남의 채팅방, 사용자 메시지, 오류 응답, 없는 메시지는 NOTIFICATION404-0")
    void 저장할_수_없으면_404() throws Exception {
        long othersRoom = chatSession(user("other"), null, null);
        long othersAnswer = message(othersRoom, "ASSISTANT", "ANSWER", "답변");
        long question = message(room, "USER", "QUESTION", "요금제 알려줘");
        long error = message(room, "ASSISTANT", "ERROR", "잠시 후 다시 시도해 주세요.");

        expectUnavailable(save(owner, othersRoom, othersAnswer));
        expectUnavailable(save(owner, room, question));
        expectUnavailable(save(owner, room, error));
        expectUnavailable(save(owner, room, Long.MAX_VALUE));
        expectUnread(owner, 0);
    }

    @Test
    @DisplayName("잘못된 요청값은 400")
    void 잘못된_요청값은_400() throws Exception {
        mvc.perform(post(URL).session(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNKNOWN\",\"sessionId\":1,\"messageId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"));
        mvc.perform(post(URL).session(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ANSWER_ARRIVED\",\"sessionId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(get(URL).session(owner).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(patch(URL + "/read").session(owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    @DisplayName("목록은 최신순이고 nextCursor로 다음 페이지를 이어 받는다")
    void 목록_커서() throws Exception {
        long first = message(room, "ASSISTANT", "ANSWER", "첫 답변");
        long second = message(room, "ASSISTANT", "ANSWER", "둘째 답변");
        long third = message(room, "ASSISTANT", "ANSWER", "셋째 답변");
        save(owner, room, first);
        save(owner, room, second);
        save(owner, room, third);

        String body = mvc.perform(get(URL).session(owner).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications[0].messageId").value(third))
                .andExpect(jsonPath("$.result.notifications[1].messageId").value(second))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String nextCursor = JsonPath.read(body, "$.result.nextCursor").toString();

        mvc.perform(get(URL).session(owner).param("size", "2").param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.length()").value(1))
                .andExpect(jsonPath("$.result.notifications[0].messageId").value(first))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("채팅방 읽음 처리는 그 방 알림만 읽고, 남의 채팅방은 CHAT404-0")
    void 채팅방_읽음() throws Exception {
        long otherRoom = chatSession(ownerId, null, null);
        save(owner, room, message(room, "ASSISTANT", "ANSWER", "답변"));
        save(owner, room, message(room, "ASSISTANT", "CLARIFICATION", "어느 지역인가요?"));
        save(owner, otherRoom, message(otherRoom, "ASSISTANT", "STORE_RESULT", "가까운 매장입니다."));

        readRoom(owner, room)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.readCount").value(2));
        expectUnread(owner, 1);
        readRoom(owner, room).andExpect(jsonPath("$.result.readCount").value(0));
        mvc.perform(get(URL).session(owner))
                .andExpect(jsonPath("$.result.notifications[0].sessionId").value(otherRoom))
                .andExpect(jsonPath("$.result.notifications[0].read").value(false))
                .andExpect(jsonPath("$.result.notifications[1].read").value(true));

        long othersRoom = chatSession(user("other"), null, null);
        readRoom(owner, othersRoom)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
    }

    @Test
    @DisplayName("게스트도 쓸 수 있고, 로그인 승계 뒤에는 회원에게 읽음 상태 그대로 보인다")
    void 게스트_알림은_로그인_승계된다() throws Exception {
        UUID guestId = UUID.randomUUID();
        MockHttpSession guest = guestSession(guestId);
        long guestRoom = chatSession(null, guestId, null);
        long readAnswer = message(guestRoom, "ASSISTANT", "ANSWER", "읽은 답변");
        save(guest, guestRoom, readAnswer).andExpect(status().isOk());
        readRoom(guest, guestRoom).andExpect(jsonPath("$.result.readCount").value(1));
        long unreadAnswer = message(guestRoom, "ASSISTANT", "ANSWER", "안 읽은 답변");
        save(guest, guestRoom, unreadAnswer).andExpect(status().isOk());
        expectUnread(guest, 1);

        guestSuccessionService.succeedGuest(guestId, userRepository.findById(ownerId).orElseThrow());

        mvc.perform(get(URL).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.length()").value(2))
                .andExpect(jsonPath("$.result.notifications[0].messageId").value(unreadAnswer))
                .andExpect(jsonPath("$.result.notifications[0].read").value(false))
                .andExpect(jsonPath("$.result.notifications[1].messageId").value(readAnswer))
                .andExpect(jsonPath("$.result.notifications[1].read").value(true));
        expectUnread(owner, 1);
        mvc.perform(get(URL).session(guest))
                .andExpect(jsonPath("$.result.notifications.length()").value(0));
    }

    @Test
    @DisplayName("신원이 없는 요청도 채팅 경로라 게스트가 발급되어 빈 목록을 받는다")
    void 처음_온_사용자는_게스트로_빈_목록() throws Exception {
        mvc.perform(get(URL + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.unreadCount").value(0));
    }

    private ResultActions save(MockHttpSession session, long sessionId, long messageId) throws Exception {
        return mvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ANSWER_ARRIVED\",\"sessionId\":%d,\"messageId\":%d}"
                        .formatted(sessionId, messageId)));
    }

    private ResultActions readRoom(MockHttpSession session, long sessionId) throws Exception {
        return mvc.perform(patch(URL + "/read").session(session).param("sessionId", String.valueOf(sessionId)));
    }

    private void expectUnread(MockHttpSession session, long count) throws Exception {
        mvc.perform(get(URL + "/unread-count").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.unreadCount").value(count));
    }

    private void expectUnavailable(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION404-0"));
    }

    private static Integer notificationId(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andReturn().getResponse().getContentAsString(), "$.result.notificationId");
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO users (name) VALUES (?) RETURNING user_id", Long.class, name);
    }

    private long chatSession(Long userId, UUID guestId, String title) {
        if (guestId != null) {
            jdbc.update("INSERT INTO guests (guest_id, expires_at) VALUES (?, now() + interval '1 day')", guestId);
        }
        return jdbc.queryForObject("INSERT INTO chat_sessions (user_id, guest_id, title) VALUES (?, ?, ?)"
                + " RETURNING session_id", Long.class, userId, guestId, title);
    }

    private long message(long sessionId, String role, String type, String content) {
        return jdbc.queryForObject("INSERT INTO chat_messages (session_id, sequence_no, role, message_type, status,"
                        + " content) VALUES (?, (SELECT coalesce(max(sequence_no), 0) + 1 FROM chat_messages"
                        + " WHERE session_id = ?), ?, ?, 'COMPLETED', ?) RETURNING message_id",
                Long.class, sessionId, sessionId, role, type, content);
    }

    private static MockHttpSession memberSession(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }

    private static MockHttpSession guestSession(UUID guestId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        return session;
    }
}
