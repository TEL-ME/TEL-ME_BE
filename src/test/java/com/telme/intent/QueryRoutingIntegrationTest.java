package com.telme.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.service.LlmClient;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.HttpSessionChatActorProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QueryRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueryRoutingService queryRoutingService;

    @Autowired
    private QueryRoutingRepository queryRoutingRepository;

    @Autowired
    private ConsultRequestRepository consultRequestRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private LlmClient llmClient;

    private User persistUser(String prefix) {
        User user = User.builder()
            .email(prefix + "-" + UUID.randomUUID() + "@example.com")
            .name(prefix)
            .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    @Test
    @DisplayName("통합 테스트: LLM 정상 응답 시 QueryRouting 및 ConsultRequest DB 영속화와 제약 조건 검증")
    void routeSuccess_persistsEntitiesAndChecksConstraints() {
        User user = persistUser("router");
        ChatSession session = ChatSession.builder()
            .userId(user.getUserId())
            .title("테스트 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("강남역점 위치 알려주고 주차 가능한지도 알려줘")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        String mockLlmJson = """
            {
              "intent": "STORE",
              "confidence": 0.95,
              "refinedQuery": "강남역점 위치 및 주차 안내",
              "extractedConditions": {
                "branch": "강남역점"
              },
              "subQueries": [
                {
                  "order": 1,
                  "intent": "STORE",
                  "queryText": "강남역점 위치 안내",
                  "conditions": { "branch": "강남역점" }
                },
                {
                  "order": 2,
                  "intent": "STORE",
                  "queryText": "강남역점 주차 가능 여부",
                  "conditions": { "branch": "강남역점" }
                }
              ]
            }
            """;
        given(llmClient.generate(any())).willReturn(mockLlmJson);

        IntentRouteResponse response = queryRoutingService.route(message);

        assertThat(response).isNotNull();
        assertThat(response.intent()).isEqualTo(QueryRouting.Intent.STORE);
        assertThat(response.subQueries()).hasSize(2);

        // QueryRouting 영속화 및 UNIQUE 제약 확인
        QueryRouting savedRouting = queryRoutingRepository.findByMessage_MessageId(message.getMessageId()).orElse(null);
        assertThat(savedRouting).isNotNull();
        assertThat(savedRouting.getMethod()).isEqualTo(QueryRouting.Method.LLM);
        assertThat(savedRouting.getMessage().getMessageId()).isEqualTo(message.getMessageId());

        // ConsultRequest 영속화 및 session_id NOT NULL 만족 확인
        List<ConsultRequest> savedRequests = consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(message.getMessageId());
        assertThat(savedRequests).hasSize(2);
        assertThat(savedRequests.get(0).getSession()).isNotNull();
        assertThat(savedRequests.get(0).getSession().getSessionId()).isEqualTo(session.getSessionId());
        assertThat(savedRequests.get(0).getQueryText()).isEqualTo("강남역점 위치 안내");
        assertThat(savedRequests.get(0).getConditions()).hasSize(1);
    }

    @Test
    @DisplayName("통합 테스트: 동일 메시지에 대한 중복 라우팅 요청 시 멱등하게 기존 결과 반환")
    void routeDuplicate_returnsExistingIdempotently() {
        User user = persistUser("idempotent");
        ChatSession session = ChatSession.builder()
            .userId(user.getUserId())
            .title("멱등성 테스트")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("매장 위치 알려줘")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        given(llmClient.generate(any())).willReturn("""
            {
              "intent": "STORE",
              "confidence": 0.9,
              "refinedQuery": "매장 위치",
              "subQueries": [
                { "order": 1, "intent": "STORE", "queryText": "매장 위치" }
              ]
            }
            """);

        IntentRouteResponse first = queryRoutingService.route(message);
        assertThat(first).isNotNull();

        IntentRouteResponse second = queryRoutingService.route(message);
        assertThat(second).isNotNull();
        assertThat(second.routingId()).isEqualTo(first.routingId());
        assertThat(second.intent()).isEqualTo(first.intent());

        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(message.getMessageId())).hasSize(1);
        assertThat(queryRoutingRepository.findByMessage_MessageId(message.getMessageId())).isPresent();
    }

    @Test
    @DisplayName("통합 테스트: LLM 장애 시 Rule Fallback 전환 및 DB 영속화")
    void routeFallback_whenLlmFails_persistsWithRuleMethod() {
        User user = persistUser("fallback");
        ChatSession session = ChatSession.builder()
            .userId(user.getUserId())
            .title("Fallback 테스트")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("위약금 얼마나 발생해?")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        given(llmClient.generate(any())).willThrow(new org.springframework.web.client.RestClientException("LLM down"));

        IntentRouteResponse response = queryRoutingService.route(message);

        assertThat(response).isNotNull();
        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.intent()).isEqualTo(QueryRouting.Intent.FAQ);

        QueryRouting savedRouting = queryRoutingRepository.findByMessage_MessageId(message.getMessageId()).orElse(null);
        assertThat(savedRouting).isNotNull();
        assertThat(savedRouting.getMethod()).isEqualTo(QueryRouting.Method.RULE);
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 멤버 소유자 정상 요청 시 200 OK 반환 (LazyInitializationException 방어 검증)")
    void routeApi_memberOwner_returns200() throws Exception {
        User user = persistUser("member-owner");
        ChatSession session = ChatSession.builder()
            .userId(user.getUserId())
            .title("회원 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("5G 요금제 알려줘")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        given(llmClient.generate(any())).willReturn("""
            {"intent":"FAQ","confidence":0.95,"refinedQuery":"5G 요금제 안내",
             "extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G 요금제 안내"}]}
            """);

        MockHttpSession mockSession = new MockHttpSession();
        mockSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(mockSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "5G 요금제 알려줘"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.intent").value("FAQ"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 게스트 소유자 정상 요청 시 200 OK 반환 (LazyInitializationException 방어 검증)")
    void routeApi_guestOwner_returns200() throws Exception {
        UUID guestId = UUID.randomUUID();
        ChatSession session = ChatSession.builder()
            .guestId(guestId)
            .userId(null)
            .title("게스트 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("강남역 매장 찾아줘")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        given(llmClient.generate(any())).willReturn("""
            {"intent":"STORE","confidence":0.95,"refinedQuery":"강남역 매장 안내",
             "extractedConditions":{},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장 안내"}]}
            """);

        MockHttpSession mockSession = new MockHttpSession();
        mockSession.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(mockSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "강남역 매장 찾아줘"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.intent").value("STORE"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 타인의 세션 메시지 요청 시 403 Forbidden 반환")
    void routeApi_forbiddenWhenNotOwner() throws Exception {
        User owner = persistUser("owner");
        User hacker = persistUser("hacker");

        ChatSession session = ChatSession.builder()
            .userId(owner.getUserId())
            .title("소유자 세션")
            .status(ChatSession.Status.ACTIVE)
            .build();
        entityManager.persist(session);

        ChatMessage message = ChatMessage.builder()
            .session(session)
            .sequenceNo(1)
            .role(ChatMessage.Role.USER)
            .messageType(ChatMessage.MessageType.QUESTION)
            .content("비밀 질문")
            .status(ChatMessage.Status.COMPLETED)
            .build();
        entityManager.persist(message);
        entityManager.flush();

        MockHttpSession hackerSession = new MockHttpSession();
        hackerSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, hacker.getUserId());

        mockMvc.perform(post("/api/v1/intent-routes")
                .session(hackerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageId": %d, "content": "비밀 질문"}
                    """.formatted(message.getMessageId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403-0"));
    }

    @Test
    @DisplayName("MockMvc HTTP 라우팅: 세션 없이 요청 시 401 Unauthorized 반환")
    void routeApi_unauthenticatedWhenNoSession() throws Exception {
        mockMvc.perform(post("/api/v1/intent-routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\": 1, \"content\": \"질문\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CHAT401-0"));
    }
}
