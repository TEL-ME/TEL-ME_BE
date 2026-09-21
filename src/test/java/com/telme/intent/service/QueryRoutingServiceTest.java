package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.service.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryRoutingServiceTest {

    @Mock LlmClient llmClient;
    @Mock QueryRoutingRepository queryRoutingRepository;
    @Mock ConsultRequestRepository consultRequestRepository;
    @Mock org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    QueryRoutingService service;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any()))
            .thenAnswer(inv -> ((org.springframework.transaction.support.TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        IntentConverter intentConverter = new IntentConverter(objectMapper);
        service = new QueryRoutingService(
            llmClient, objectMapper,
            queryRoutingRepository, consultRequestRepository,
            new RuleBasedRoutingFallback(),
            intentConverter,
            transactionTemplate
        );
        org.mockito.Mockito.lenient().when(queryRoutingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(consultRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(queryRoutingRepository.findByMessage_MessageId(any())).thenReturn(java.util.Optional.empty());
    }

    ChatMessage msg(String content) {
        return ChatMessage.builder()
            .messageId(1L)
            .session(ChatSession.builder().build())
            .content(content)
            .build();
    }

    @Nested
    @DisplayName("LLM 정상 응답")
    class LlmSuccess {

        @Test
        @DisplayName("FAQ 단독 → intent=FAQ, 서브질의 1건")
        void faq() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.98,"refinedQuery":"5G 요금제 월 요금",
                 "extractedConditions":{},
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"5G 요금제 월 요금","conditions":{}}]}
                """);

            IntentRouteResponse r = service.route(msg("5G 요금제 월 요금 얼마야?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.LLM);
            assertThat(r.subQueries()).hasSize(1);
        }

        @Test
        @DisplayName("STORE 단독 → intent=STORE, serviceType 추출 확인")
        void store() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.95,"refinedQuery":"강남역 유심 재발급 매장",
                 "extractedConditions":{"location":"강남역","serviceType":"USIM_REISSUE"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 유심 재발급 매장",
                                "conditions":{"location":"강남역","serviceType":"USIM_REISSUE"}}]}
                """);

            IntentRouteResponse r = service.route(msg("강남역 근처 유심 교체 가능한 매장?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.STORE);
            assertThat(r.extractedConditions()).containsEntry("serviceType", "USIM_REISSUE");
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.STORE);
        }

        @Test
        @DisplayName("BOTH → 서브질의 2건으로 분해, FAQ+STORE 타입 정확성")
        void both() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"BOTH","confidence":0.99,
                 "refinedQuery":"5G 요금제 추천 및 신촌 번호이동 매장",
                 "extractedConditions":{"location":"신촌","serviceType":"PORT_IN"},
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제 추천","conditions":{}},
                   {"order":2,"intent":"STORE","queryText":"신촌 번호이동 매장",
                    "conditions":{"location":"신촌","serviceType":"PORT_IN"}}
                 ]}
                """);

            IntentRouteResponse r = service.route(
                msg("5G 요금제 추천해주고 신촌 근처 번호이동 매장 찾아줘"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.BOTH);
            assertThat(r.subQueries()).hasSize(2);
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.FAQ);
            assertThat(r.subQueries().get(1).intent()).isEqualTo(ConsultRequest.Intent.STORE);
            assertThat(r.subQueries().get(1).conditions()).containsEntry("serviceType", "PORT_IN");
        }

        @Test
        @DisplayName("UNKNOWN → 서브질의 0건")
        void unknown() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"UNKNOWN","confidence":0.99,"refinedQuery":"",
                 "extractedConditions":{},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("오늘 점심 뭐 먹지?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.UNKNOWN);
            assertThat(r.subQueries()).isEmpty();
        }
    }

    @Nested
    @DisplayName("LLM 장애 시 Rule Fallback")
    class Fallback {

        @Test
        @DisplayName("타임아웃 → FAQ 키워드 매칭으로 분류")
        void fallbackFaq() {
            given(llmClient.generate(any())).willThrow(new org.springframework.web.client.ResourceAccessException("Ollama timeout"));

            IntentRouteResponse r = service.route(msg("위약금 얼마나 내야 돼?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("연결 실패 → STORE 키워드로 분류")
        void fallbackStore() {
            given(llmClient.generate(any())).willThrow(new org.springframework.web.client.RestClientException("Connection refused"));

            IntentRouteResponse r = service.route(msg("강남역 근처 대리점 어디 있어?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.STORE);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("키워드 없음 → UNKNOWN")
        void fallbackUnknown() {
            given(llmClient.generate(any())).willThrow(new com.telme.global.common.exception.GeneralException(com.telme.intent.exception.IntentErrorCode.LLM_CONNECTION_FAILED));

            IntentRouteResponse r = service.route(msg("안녕 반가워"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.UNKNOWN);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("FAQ+STORE 키워드 혼재 → BOTH, 서브질의 2건")
        void fallbackBoth() {
            given(llmClient.generate(any())).willThrow(new com.telme.global.common.exception.GeneralException(com.telme.intent.exception.IntentErrorCode.LLM_CONNECTION_FAILED));

            IntentRouteResponse r = service.route(msg("요금제 알려주고 근처 대리점도 찾아줘"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.BOTH);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(r.subQueries()).hasSize(2);
        }

        @Test
        @DisplayName("LLM 응답이 비어있는 경우 → Rule Fallback으로 전환")
        void fallbackEmptyResponse() {
            given(llmClient.generate(any())).willReturn("   ");

            IntentRouteResponse r = service.route(msg("위약금 얼마나 내야 돼?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("LLM 응답 JSON 파싱 실패 시 → Rule Fallback으로 전환")
        void fallbackMalformedJson() {
            given(llmClient.generate(any())).willReturn("This is not valid json");

            IntentRouteResponse r = service.route(msg("강남역 근처 대리점 어디 있어?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.STORE);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("예상치 못한 RuntimeException(NPE 등)은 Fallback으로 삼키지 않고 그대로 전파한다")
        void unexpectedException_propagates() {
            given(llmClient.generate(any())).willThrow(new NullPointerException("unexpected null"));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.route(msg("요금제 알려줘")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("unexpected null");
        }
    }

    @Nested
    @DisplayName("Null 방어 및 예외 처리")
    class NullAndDefensiveHandling {

        @Test
        @DisplayName("userMessage가 null이면 IllegalArgumentException을 던진다")
        void nullMessage_throwsException() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.route(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자 메시지는 필수입니다.");
        }

        @Test
        @DisplayName("메시지 내용이 null이거나 공백이면 LLM 호출 없이 UNKNOWN으로 처리한다")
        void emptyContent_routesToUnknown() {
            ChatMessage emptyMsg = ChatMessage.builder()
                    .session(ChatSession.builder().build())
                    .content("   ")
                    .build();

            IntentRouteResponse r = service.route(emptyMsg);

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.UNKNOWN);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
            org.mockito.Mockito.verify(llmClient, org.mockito.Mockito.never()).generate(any());
        }

        @Test
        @DisplayName("LLM 응답이 null이거나 빈 문자열이면 Fallback으로 안전하게 전환된다")
        void nullOrEmptyLlmResponse_fallsBack() {
            given(llmClient.generate(any())).willReturn(null);

            IntentRouteResponse r = service.route(msg("요금제 알려줘"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(r.method()).isEqualTo(QueryRouting.Method.RULE);
        }

        @Test
        @DisplayName("서브질의에 null이나 비정상 값이 포함되어도 안전하게 기본값으로 보정된다")
        void subQueryWithNullFields_handledSafely() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.90,"refinedQuery":"매장 안내",
                 "subQueries":[{"order":null,"intent":null,"queryText":null,"conditions":{"": "val", "key": null, "validKey": "validVal"}}]}
                """);

            IntentRouteResponse r = service.route(msg("매장 찾아줘"));

            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().get(0).order()).isEqualTo((short) 1);
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.FAQ);
            assertThat(r.subQueries().get(0).queryText()).isEqualTo("매장 안내");
        }
    }
}
