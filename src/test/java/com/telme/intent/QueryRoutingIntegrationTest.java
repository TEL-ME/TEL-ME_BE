package com.telme.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.telme.intent.service.UnsupportedCompoundQuestionException;
import com.telme.llm.service.LlmClient;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class QueryRoutingIntegrationTest {

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
    void explicitFaqRequestsAreStoredInOrderAndOriginalIsPreserved() {
        ChatMessage message = persistQuestion("로밍요금과 유심재발급비용 알려줘");
        given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"로밍 요금과 유심 재발급 비용",
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"로밍 요금","requestQuote":"로밍요금"},
                   {"order":2,"intent":"FAQ","queryText":"유심 재발급 비용","requestQuote":"유심재발급비용"}]}
                """, """
                {"decision":"MULTIPLE","requestCount":2}
                """, """
                {"unsafe":[false,false]}
                """);
        var result = queryRoutingService.routeSingleConsult(message, null);
        entityManager.flush();
        entityManager.clear();
        assertThat(result.subQueries()).hasSize(2);
        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(
                message.getMessageId())).extracting(ConsultRequest::getQueryText)
                .containsExactly("로밍 요금", "유심 재발급 비용");
        assertThat(entityManager.find(ChatMessage.class, message.getMessageId()).getContent())
                .isEqualTo("로밍요금과 유심재발급비용 알려줘");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "청구서 확인부터 요금 납부까지 순서가 어떻게 되나요? | SINGLE",
            "선불과 후불 요금제의 구성을 차례로 소개해줘 | SINGLE",
            "5G, LTE, 알뜰 요금제 구성을 순서대로 안내해 주세요. | SINGLE",
            "요금 아끼려면 정지가 나아요, 해지가 나아요? | COMPARISON"
    })
    void singleDecisionStoresOneWholeOriginalDespiteInventedInitialFacets(String question, String decision) {
        ChatMessage message = persistQuestion(question);
        given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"subQueries":[
                  {"order":1,"intent":"FAQ","queryText":"없는 조건 999원","requestQuote":"없는 원문"},
                  {"order":2,"intent":"FAQ","queryText":"","requestQuote":"없는 원문"}]}
                """, "{\"decision\":\"" + decision + "\",\"requestCount\":1}");
        queryRoutingService.routeSingleConsult(message, null);
        entityManager.flush();
        entityManager.clear();
        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(
                message.getMessageId())).singleElement().satisfies(request ->
                assertThat(request.getQueryText()).isEqualTo(question));
        assertThat(entityManager.find(ChatMessage.class, message.getMessageId()).getContent()).isEqualTo(question);
    }

    @Test
    void explicitTwoActionsOnSameTopicAreStoredAsTwoRequests() {
        ChatMessage message = persistQuestion("부가서비스 가입이랑 해지는 어떻게 해요?");
        given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"subQueries":[
                  {"order":1,"intent":"FAQ","queryText":"부가서비스 가입 방법","requestQuote":"가입"},
                  {"order":2,"intent":"FAQ","queryText":"부가서비스 해지 방법","requestQuote":"해지"}]}
                """, """
                {"decision":"MULTIPLE","requestCount":2}
                """, """
                {"unsafe":[false,false]}
                """);
        queryRoutingService.routeSingleConsult(message, null);
        entityManager.flush();
        entityManager.clear();
        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(
                message.getMessageId())).extracting(ConsultRequest::getQueryText)
                .containsExactly("부가서비스 가입 방법", "부가서비스 해지 방법");
    }

    @Test
    void inventedRequestFacetsLeaveNoPartialRoutingOrConsultation() {
        ChatMessage message = persistQuestion("번호 이동하고 싶어요");
        given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"번호이동 절차와 서류",
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"번호이동 절차","requestQuote":"번호이동 절차"},
                   {"order":2,"intent":"FAQ","queryText":"번호이동 서류","requestQuote":"서류"}]}
                """);
        assertThatThrownBy(() -> queryRoutingService.routeSingleConsult(message, null))
                .isInstanceOf(UnsupportedCompoundQuestionException.class);
        assertThat(queryRoutingRepository.findByMessage_MessageId(message.getMessageId())).isEmpty();
        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(
                message.getMessageId())).isEmpty();
    }

    private ChatMessage persistQuestion(String content) {
        User user = persistUser("spacing");
        ChatSession session = ChatSession.builder().userId(user.getUserId())
                .status(ChatSession.Status.ACTIVE).build();
        entityManager.persist(session);
        ChatMessage message = ChatMessage.builder().session(session).sequenceNo(1)
                .role(ChatMessage.Role.USER).messageType(ChatMessage.MessageType.QUESTION)
                .content(content).status(ChatMessage.Status.COMPLETED).build();
        entityManager.persist(message);
        entityManager.flush();
        return message;
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
                "location": "강남역점"
              },
              "subQueries": [
                {
                  "order": 1,
                  "intent": "STORE",
                  "queryText": "강남역점 위치 안내",
                  "conditions": { "location": "강남역점" }
                },
                {
                  "order": 2,
                  "intent": "STORE",
                  "queryText": "강남역점 주차 가능 여부",
                  "conditions": { "location": "강남역점" }
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
    @DisplayName("비교 질문은 모델이 양쪽 설명으로 나누어도 상담 요청 한 건으로 저장한다")
    void comparisonPersistsOneConsultRequest() {
        User user = persistUser("comparison");
        ChatSession session = ChatSession.builder()
                .userId(user.getUserId())
                .title("비교 질문 테스트")
                .status(ChatSession.Status.ACTIVE)
                .build();
        entityManager.persist(session);
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(1)
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content("5G와 LTE 요금제 종류를 비교해줘")
                .status(ChatMessage.Status.COMPLETED)
                .build();
        entityManager.persist(message);
        entityManager.flush();
        given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"5G와 LTE 요금제 종류 비교",
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제 종류"},
                   {"order":2,"intent":"FAQ","queryText":"LTE 요금제 종류"}]}
                """, "{\"decision\":\"COMPARISON\",\"requestCount\":1}");

        IntentRouteResponse result = queryRoutingService.routeSingleConsult(message, null);

        assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(result.subQueries()).hasSize(1);
        assertThat(result.subQueries().getFirst().queryText()).isEqualTo(message.getContent());
        assertThat(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(
                message.getMessageId()))
                .singleElement()
                .satisfies(request -> assertThat(request.getQueryText()).isEqualTo(message.getContent()));
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
}
