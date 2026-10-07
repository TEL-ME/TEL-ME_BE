package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        org.mockito.Mockito.lenient().when(queryRoutingRepository.saveAndFlush(any()))
                .thenAnswer(inv -> inv.getArgument(0));
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
        @DisplayName("단일 상담 진입점은 BOTH를 저장 전에 차단한다")
        void singleConsultBlocksBothBeforeSaving() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"BOTH","confidence":0.99,
                 "refinedQuery":"5G 요금제와 신촌 매장",
                 "extractedConditions":{"location":"신촌"},
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제","conditions":{}},
                   {"order":2,"intent":"STORE","queryText":"신촌 매장","conditions":{"location":"신촌"}}
                 ]}
                """);
            ChatMessage message = msg("5G 요금제와 신촌 매장 알려줘");

            assertThatThrownBy(() -> service.routeSingleConsult(message, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAQ와 매장 복합 질문");

            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("상담 연결에서도 여러 FAQ 하위 질문을 각각 유지한다")
        void singleConsultPreservesFaqSubQueries() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,
                 "refinedQuery":"5G, LTE, 알뜰 요금제 종류를 각각 알려줘",
                 "extractedConditions":{},
                 "subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제 종류","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"LTE 요금제 종류","conditions":{}},
                   {"order":3,"intent":"FAQ","queryText":"알뜰 요금제 종류","conditions":{}}
                 ]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(
                    msg("5G, LTE, 알뜰 요금제 종류를 각각 알려주세요"), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly("5G 요금제 종류", "LTE 요금제 종류", "알뜰 요금제 종류");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "5G와 LTE 요금제 종류를 비교해줘",
                "너겟 5G 요금제와 LTE 요금제의 데이터 제공량 차이를 비교해줘",
                "월 5만 원대 요금제에서 데이터와 속도 제한 조건을 비교해줘",
                "eSIM과 유심의 개통 절차를 비교해줘",
                "명의 변경과 번호 이동의 필요 서류를 비교해줘",
                "선택약정과 공시지원금 할인 차이를 비교해줘",
                "해외 로밍 패스와 데이터 로밍 종량제의 요금 및 제공량을 비교해줘",
                "일반 요금제와 청소년 요금제의 가입 조건을 비교해줘",
                "인터넷 결합 할인과 가족 결합 할인 차이를 비교해서 알려줘",
                "분실 신고와 일시 정지의 차이와 이용 제한을 비교해줘",
                "청소년 요금제하고 시니어 요금제 차이가 뭐야",
                "청소년 요금제랑 시니어 요금제 차이가 뭐야"
        })
        @DisplayName("명확한 비교 요청은 LLM이 나누더라도 원문 한 건으로 보정한다")
        void keepsStandaloneComparisonAsOneFaqQuestion(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"두 항목 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"첫 항목 안내","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"둘째 항목 안내","conditions":{}}
                 ]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.refinedQuery()).isEqualTo(question);
            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly(question);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "5G와 LTE 요금제 비교해주고 로밍 신청 방법도 알려줘",
                "로밍 신청 방법 알려주고 5G와 LTE 요금제 종류를 비교해줘"
        })
        @DisplayName("비교와 독립 요청이 섞이면 전체를 한 질문으로 합치지 않는다")
        void keepsIndependentRequestSeparateFromComparison(String question) {
            boolean comparisonLast = question.startsWith("로밍");
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 비교와 로밍 신청",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"%s","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"%s","conditions":{}}
                 ]}
                """.formatted(comparisonLast ? "로밍 신청 방법" : "5G와 LTE 요금제 종류 비교",
                    comparisonLast ? "5G와 LTE 요금제 종류 비교" : "로밍 신청 방법"));

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.subQueries()).hasSize(2);
            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly(comparisonLast ? "로밍 신청 방법" : "5G와 LTE 요금제 종류 비교",
                            comparisonLast ? "5G와 LTE 요금제 종류를 비교해줘" : "로밍 신청 방법");
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘 | 5G와 LTE 요금제 종류를 비교해줘",
                "요금제 변경 방법 알려주세요. 5G와 LTE 요금제 종류를 비교해주세요 | 5G와 LTE 요금제 종류를 비교해주세요",
                "요금제 변경 방법은 뭐야? 5G와 LTE 요금제 종류를 비교해줘 | 5G와 LTE 요금제 종류를 비교해줘"
        })
        void preservesIndependentRequestAndComparisonAsTwoFaqQueries(String question, String comparison) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 변경 방법과 5G LTE 종류 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"요금제 변경 방법","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"5G와 LTE 요금제 종류 비교","conditions":{}}
                 ]}
                """);

            var result = service.routeSingleConsult(msg(question), null);

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly("요금제 변경 방법", comparison);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘",
                "요금제 변경 방법 알려줘\n5G와 LTE 요금제 종류를 비교해줘"
        })
        void restoresOnlyComparisonPartsWithoutConsumingIndependentRequest(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"변경 방법과 요금제 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"요금제 변경 방법","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"5G 요금제 종류","conditions":{}},
                   {"order":3,"intent":"FAQ","queryText":"LTE 요금제 종류","conditions":{}}
                 ]}
                """);

            var result = service.routeSingleConsult(msg(question), null);

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly("요금제 변경 방법", "5G와 LTE 요금제 종류를 비교해줘");
        }

        @Test
        void ambiguousComparisonPartsDoNotConsumeAnIndependentRequest() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 종류와 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제 종류","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"LTE 요금제 종류","conditions":{}}
                 ]}
                """);

            assertThatThrownBy(() -> service.routeSingleConsult(msg(
                    "5G 요금제 종류 알려줘. 5G와 LTE 요금제 종류를 비교해줘"), null))
                    .isInstanceOf(UnsupportedCompoundQuestionException.class);
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "요금제 변경 방법",
                "5G와 LTE 요금제 종류 비교",
                "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘"
        })
        void doesNotCompleteMixedRequestWhenModelReturnsOnlyOneFaqQuery(String queryText) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"%s",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"%s","conditions":{}}
                 ]}
                """.formatted(queryText, queryText));

            assertThatThrownBy(() -> service.routeSingleConsult(msg(
                    "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘"), null))
                    .isInstanceOf(UnsupportedCompoundQuestionException.class);
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        void keepsAllIndependentRequestsBeforeTrailingComparisonInOriginalOrder() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"로밍, 유심, 요금제 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"유심 재발급 방법","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"5G 요금제 종류","conditions":{}},
                   {"order":3,"intent":"FAQ","queryText":"로밍 신청 방법","conditions":{}},
                   {"order":4,"intent":"FAQ","queryText":"LTE 요금제 종류","conditions":{}}
                 ]}
                """);

            var result = service.routeSingleConsult(msg(
                    "로밍 신청 방법 알려줘. 유심 재발급 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘"), null);

            assertThat(result.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .containsExactly("로밍 신청 방법", "유심 재발급 방법", "5G와 LTE 요금제 종류를 비교해줘");
        }

        @Test
        void missingIndependentRequestBeforeTrailingComparisonGetsGuidanceBeforeSaving() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"로밍과 요금제 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"로밍 신청 방법","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"5G와 LTE 요금제 종류 비교","conditions":{}}
                 ]}
                """);

            assertThatThrownBy(() -> service.routeSingleConsult(msg(
                    "로밍 신청 방법 알려줘. 유심 재발급 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘"), null))
                    .isInstanceOf(UnsupportedCompoundQuestionException.class);
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        void blankModelSubQueryInMixedRequestGetsGuidanceBeforeSaving() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제 변경 방법과 비교",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":null,"conditions":{}}
                 ]}
                """);

            assertThatThrownBy(() -> service.routeSingleConsult(msg(
                    "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘"), null))
                    .isInstanceOf(UnsupportedCompoundQuestionException.class);
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        void singleConsultRejectsMoreThanThreeFaqQuestionsBeforeSaving() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제, 로밍, 명의변경, 유심",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"요금제 종류","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"로밍 요금","conditions":{}},
                   {"order":3,"intent":"FAQ","queryText":"명의변경 방법","conditions":{}},
                   {"order":4,"intent":"FAQ","queryText":"유심 재발급 방법","conditions":{}}
                 ]}
                """);

            assertThatThrownBy(() -> service.routeSingleConsult(
                    msg("요금제, 로밍, 명의변경, 유심 알려줘"), null))
                    .isInstanceOf(TooManyFaqQuestionsException.class)
                    .hasMessageContaining("최대 3개");
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        void unsafeFaqSubQueryDoesNotFallBackToWholeCompoundQuestion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"요금제와 로밍 방법",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"5G 요금제 종류","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"로밍 신청 방법","conditions":{}}
                 ]}
                """);

            assertThatThrownBy(() -> service.routeSingleConsult(
                    msg("요금제 종류와 로밍 신청 방법 알려줘"), null))
                    .isInstanceOf(UnsupportedCompoundQuestionException.class)
                    .hasMessageContaining("안전하게 검색");
            verify(queryRoutingRepository, never()).saveAndFlush(any());
            verify(consultRequestRepository, never()).save(any());
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

        @ParameterizedTest
        @ValueSource(strings = {
                "노트북 테더링하면 요금 따로 나와요?",
                "강아지 위치추적기 가입 되나요?",
                "헬스장 제휴 할인 받을 수 있어요?"
        })
        @DisplayName("통신과 무관해 보이는 단어가 있어도 LLM의 FAQ 분류를 바꾸지 않는다")
        void keepsLlmFaqForQuestionWithUnrelatedLookingWord(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.97,"refinedQuery":"통신 질문",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"통신 질문","conditions":{}}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "매장은 보통 평일 몇 시까지 운영하나요?",
                "평일 저녁 7시 이후에 매장 방문이 가능한가요?"
        })
        @DisplayName("특정 지점이 없는 일반 매장 운영 질문은 FAQ로 보정한다")
        void correctsGeneralStorePolicy(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.92,"refinedQuery":"매장 운영시간",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"STORE","queryText":"매장 운영시간","conditions":{}}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.subQueries()).singleElement()
                    .satisfies(sub -> {
                        assertThat(sub.intent()).isEqualTo(ConsultRequest.Intent.FAQ);
                        assertThat(sub.queryText()).isEqualTo(question);
                    });
        }

        @Test
        @DisplayName("특정 지점의 영업시간 질문은 STORE로 유지한다")
        void keepsSpecificStoreHoursLookup() {
            assertThat(RoutingIntentCorrection.isGeneralStorePolicy(
                    "신촌 매장은 보통 평일 몇 시까지 운영하나요?", null, null)).isFalse();
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.95,"refinedQuery":"신촌 매장 영업시간",
                 "extractedConditions":{"location":"신촌"},"subQueries":[
                   {"order":1,"intent":"STORE","queryText":"신촌 매장 영업시간",
                    "conditions":{"location":"신촌"}}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(
                    msg("신촌 매장은 보통 평일 몇 시까지 운영하나요?"), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.STORE);
            assertThat(result.subQueries()).singleElement()
                    .extracting(IntentRouteResponse.IntentSubQueryResponse::intent)
                    .isEqualTo(ConsultRequest.Intent.STORE);
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
        @DisplayName("LLM이 업무 코드를 최상위 intent로 쓰면 원문으로 FAQ 분류를 다시 한다")
        void invalidServiceTypeIntentFallsBackToOriginalQuestion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"NAME_CHANGE","confidence":0.99,
                 "refinedQuery":"명의변경 절차","extractedConditions":{},"subQueries":[]}
                """);

            String question = "아버지 명의에서 제 명의로 바꾸려면 어떻게 해야 하나요?";
            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.subQueries()).singleElement()
                    .extracting(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .isEqualTo(question);
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
        @DisplayName("하위 의도가 비어 있으면 상위 의도로 보정하고 순서를 다시 매긴다")
        void subQueryWithNullFields_handledSafely() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.90,"refinedQuery":"매장 안내",
                 "subQueries":[{"order":99,"intent":null,"queryText":null,
                  "conditions":{"": "val", "key": null, "validKey": "validVal"}}]}
                """);

            IntentRouteResponse r = service.route(msg("매장 찾아줘"));

            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().get(0).order()).isEqualTo((short) 1);
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.STORE);
            assertThat(r.subQueries().get(0).queryText()).isEqualTo("매장 안내");
            assertThat(r.subQueries().get(0).conditions()).isEmpty();
        }

        @Test
        void conflictingTopLevelAndSubQueryFallsBackBeforeSaving() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.99,"refinedQuery":"명의변경 절차",
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"명의변경 매장"}]}
                """);

            IntentRouteResponse result = service.route(msg("명의변경 절차 알려줘"));

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.subQueries()).singleElement()
                    .extracting(IntentRouteResponse.IntentSubQueryResponse::intent)
                    .isEqualTo(ConsultRequest.Intent.FAQ);
        }

        @Test
        void incompleteBothFallsBackInsteadOfSavingPartialConsult() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"BOTH","confidence":0.99,"refinedQuery":"요금제와 근처 매장",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"요금제"}]}
                """);

            IntentRouteResponse result = service.route(msg("요금제 알려주고 근처 매장도 찾아줘"));

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.BOTH);
            assertThat(result.subQueries()).hasSize(2);
        }

        @Test
        void unknownWithSubQueryFallsBackToOriginalTelecomQuestion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"UNKNOWN","confidence":0.9,"subQueries":[
                 {"order":1,"intent":"FAQ","queryText":"로밍 요금"}]}
                """);

            IntentRouteResponse result = service.route(msg("로밍 요금 알려줘"));

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
        }

        @Test
        void validQuestionWithoutRuleKeywordKeepsModelClassification() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.99,"refinedQuery":"가상계좌란 무엇인가",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"가상계좌란 무엇인가"}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg("가상계좌가 무엇인가요?"), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
            assertThat(result.subQueries()).hasSize(1);
        }

        @Test
        void lowConfidenceDoesNotCreateConsultRequest() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.3,"refinedQuery":"로밍 요금",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"로밍 요금"}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg("로밍 요금 알려줘"), null);

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.UNKNOWN);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
            assertThat(result.subQueries()).isEmpty();
            verify(consultRequestRepository, never()).save(any());
        }

        @Test
        void outOfRangeConfidenceFallsBackToRule() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":1.3,"refinedQuery":"로밍 요금",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"로밍 요금"}]}
                """);

            IntentRouteResponse result = service.route(msg("로밍 요금 알려줘"));

            assertThat(result.method()).isEqualTo(QueryRouting.Method.RULE);
            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.FAQ);
        }

        @Test
        void ungroundedAndUnsupportedConditionsAreRemoved() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"부산역","serviceType":"MADE_UP","branch":"강남역"},
                 "subQueries":[{"order":7,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역","serviceType":"MADE_UP","branch":"강남역"}}]}
                """);

            IntentRouteResponse result = service.route(msg("강남역 매장 찾아줘"));

            assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
            assertThat(result.extractedConditions()).isEmpty();
            assertThat(result.subQueries().getFirst().order()).isEqualTo((short) 1);
            assertThat(result.subQueries().getFirst().conditions())
                    .containsExactlyEntriesOf(java.util.Map.of("location", "강남역"));
        }

        @Test
        void relativeLocationIsNotTreatedAsRegion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.95,"refinedQuery":"현재 위치 가까운 매장",
                 "extractedConditions":{"location":"현재 위치"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"현재 위치 가까운 매장",
                  "conditions":{"location":"현재 위치"}}]}
                """);

            IntentRouteResponse result = service.route(msg("현재 위치에서 가까운 매장을 찾아줘"));

            assertThat(result.intent()).isEqualTo(QueryRouting.Intent.STORE);
            assertThat(result.extractedConditions()).isEmpty();
            assertThat(result.subQueries().getFirst().conditions()).isEmpty();
        }

        @Test
        void topLevelStoreConditionsReachSingleStoreConsultRequest() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 유심 매장",
                 "extractedConditions":{"location":"강남역","serviceType":"USIM_REISSUE"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 유심 매장",
                  "conditions":{}}]}
                """);

            IntentRouteResponse result = service.route(msg("강남역 유심 재발급 매장 찾아줘"));

            assertThat(result.subQueries().getFirst().conditions())
                    .containsEntry("location", "강남역")
                    .containsEntry("serviceType", "USIM_REISSUE");
        }

        @Test
        void inventedPriceAndPlaceAreNotUsedAsSearchQuery() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"강남역 5G 요금제 39000원",
                 "subQueries":[{"order":1,"intent":"FAQ",
                  "queryText":"강남역 5G 요금제 39000원"}]}
                """);
            String original = "5G 요금제 가격 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(msg(original), null);

            assertThat(result.refinedQuery()).isEqualTo(original);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(original);
        }

        @Test
        void omittedNumericConstraintUsesOriginalQuestion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"할부 수수료 차이",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"할부 수수료 차이"}]}
                """);
            String original = "24개월과 30개월 할부 수수료 차이 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(msg(original), null);

            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(original);
        }

        @Test
        void numericTokenMustMatchCompletely() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"15GB와 5GB 요금제 비교",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"15GB와 5GB 요금제 비교"}]}
                """);
            String original = "15GB 요금제 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(msg(original), null);

            assertThat(result.refinedQuery()).isEqualTo(original);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(original);
        }

        @Test
        void numericUnitChangeUsesOriginalQuestion() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"15개월 요금제",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"15개월 요금제"}]}
                """);
            String original = "15GB 요금제 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(msg(original), null);

            assertThat(result.refinedQuery()).isEqualTo(original);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(original);
        }

        @Test
        void spacingInNumericUnitDoesNotChangeMeaning() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"15 GB 요금제 종류",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"15 GB 요금제 종류"}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(msg("15GB 요금제 알려줘"), null);

            assertThat(result.refinedQuery()).isEqualTo("15 GB 요금제 종류");
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo("15 GB 요금제 종류");
        }

        @Test
        void currentLocationOverridesHistoricalLocation() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"강남역"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역"}}]}
                """);
            String original = "부산역 매장 알려줘";
            ChatContext context = context(original, "강남역 매장 알려줘");

            IntentRouteResponse result = service.routeSingleConsult(msg(original), context);

            assertThat(result.refinedQuery()).isEqualTo(original);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(original);
            assertThat(result.extractedConditions()).doesNotContainKey("location");
            assertThat(result.subQueries().getFirst().conditions()).doesNotContainKey("location");
        }

        @ParameterizedTest
        @ValueSource(strings = {"PORT_IN", "NAME_CHANGE", "USIM_REISSUE", "NEW_LINE"})
        void unsupportedServiceTypeIsRemovedEvenIfItIsKnownCode(String serviceType) {
            given(llmClient.generate(any())).willReturn(("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"강남역","serviceType":"%s"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역","serviceType":"%s"}}]}
                """).formatted(serviceType, serviceType));

            IntentRouteResponse result = service.routeSingleConsult(msg("강남역 매장 알려줘"), null);

            assertThat(result.extractedConditions()).containsEntry("location", "강남역")
                    .doesNotContainKey("serviceType");
            assertThat(result.subQueries().getFirst().conditions()).containsEntry("location", "강남역")
                    .doesNotContainKey("serviceType");
        }

        @Test
        void currentServiceTypeOverridesHistoricalServiceType() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"번호이동 매장",
                 "extractedConditions":{"serviceType":"USIM_REISSUE"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"번호이동 매장",
                  "conditions":{"serviceType":"USIM_REISSUE"}}]}
                """);
            String original = "그거 번호이동 매장 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(
                    msg(original), context(original, "유심 재발급 매장 알려줘"));

            // 이전 대화의 유심 업무는 버리고, 현재 질문의 번호이동으로 채운다
            assertThat(result.extractedConditions()).containsEntry("serviceType", "PORT_IN");
            assertThat(result.subQueries().getFirst().conditions()).containsEntry("serviceType", "PORT_IN");
        }

        @Test
        void referentialQuestionMayReuseHistoricalLocation() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"강남역"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역"}}]}
                """);
            String original = "거기 매장 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(
                    msg(original), context(original, "강남역 매장 알려줘"));

            assertThat(result.refinedQuery()).isEqualTo("강남역 매장");
            assertThat(result.subQueries().getFirst().conditions()).containsEntry("location", "강남역");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "거기 매장 가면 몇 시까지 해?",
                "거기서 유심 재발급하면 돼?",
                "거기서 요금제 바꾸면 돼?",
                "그 지역 매장 알려줘",
                "거기 통화내역 알려줘",
                "거기서 요금제 변경하면 매장 할인돼?",
                "거기서 매장에 들어가면 매장 혜택 있어?"
        })
        void conditionalEndingOrGenericRegionDoesNotHideHistoricalLocation(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"강남역"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역"}}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(
                    msg(question), context(question, "강남역 매장 알려줘"));

            assertThat(result.refinedQuery()).isEqualTo("강남역 매장");
            assertThat(result.extractedConditions()).containsEntry("location", "강남역");
            assertThat(result.subQueries().getFirst().conditions()).containsEntry("location", "강남역");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "그거 부산역 매장 가면 몇 시까지 해?",
                "그거 양평군 용문면 매장 알려줘",
                "그거 대가면에서 매장 알려줘"
        })
        void explicitNewPlaceInReferentialQuestionOverridesHistoricalLocation(String question) {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"강남역 매장",
                 "extractedConditions":{"location":"강남역"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장",
                  "conditions":{"location":"강남역"}}]}
                """);

            IntentRouteResponse result = service.routeSingleConsult(
                    msg(question), context(question, "강남역 매장 알려줘"));

            assertThat(result.refinedQuery()).isEqualTo(question);
            assertThat(result.extractedConditions()).doesNotContainKey("location");
            assertThat(result.subQueries().getFirst().conditions()).doesNotContainKey("location");
        }

        @Test
        void inventedBareMyeonIsNotUsedAsSearchQuery() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"가상면 매장",
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"가상면 매장"}]}
                """);
            String question = "강남역 매장 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.refinedQuery()).isEqualTo(question);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(question);
        }

        @ParameterizedTest
        @CsvSource({
                "유심을 바꾸려면 어떻게 해?, 유심을 바꾸려면 필요한 절차",
                "유심 개통되면 요금은 어떻게 확인해?, 유심 개통되면 요금 확인 방법",
                "할인이 있으면 어떻게 신청해?, 할인 있으면 신청 방법"
        })
        void conditionalEndingsInSearchQueryAreNotInventedPlaces(String question, String refinedQuery) {
            given(llmClient.generate(any())).willReturn(("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"%s",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"%s"}]}
                """).formatted(refinedQuery, refinedQuery));

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.refinedQuery()).isEqualTo(refinedQuery);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(refinedQuery);
        }

        @Test
        void conditionalPhraseCopiedFromQuestionIsNotTreatedAsInventedPlace() {
            String question = "문의해주시면 요금제 변경 방법 알려주세요";
            String refinedQuery = "문의해주시면 요금제 변경 절차";
            given(llmClient.generate(any())).willReturn(("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"%s",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"%s"}]}
                """).formatted(refinedQuery, refinedQuery));

            IntentRouteResponse result = service.routeSingleConsult(msg(question), null);

            assertThat(result.refinedQuery()).isEqualTo(refinedQuery);
            assertThat(result.subQueries().getFirst().queryText()).isEqualTo(refinedQuery);
        }

        @Test
        void explicitAdministrativeMyeonCanBeUsedAsCurrentLocation() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"용문면 매장",
                 "extractedConditions":{"location":"용문면"},
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"용문면 매장",
                  "conditions":{"location":"용문면"}}]}
                """);
            String question = "양평군 용문면 매장 알려줘";

            IntentRouteResponse result = service.routeSingleConsult(
                    msg(question), context(question, "강남역 매장 알려줘"));

            assertThat(result.refinedQuery()).isEqualTo("용문면 매장");
            assertThat(result.extractedConditions()).containsEntry("location", "용문면");
            assertThat(result.subQueries().getFirst().conditions()).containsEntry("location", "용문면");
        }

        private ChatContext context(String question, String previousQuestion) {
            return new ChatContext(1L, 1L, null, java.util.List.of(
                    new ChatContextMessage(2L, 1, ChatMessage.Role.USER,
                            ChatMessage.MessageType.QUESTION, previousQuestion, null)), question, 100);
        }

        @Test
        void concurrentRoutingReusesCommittedWinnerAfterInsertConflict() {
            ChatMessage message = msg("요금제 알려줘");
            QueryRouting winner = QueryRouting.builder()
                    .routingId(44L)
                    .message(message)
                    .intent(QueryRouting.Intent.FAQ)
                    .refinedQuery("요금제 알려줘")
                    .confidence(new java.math.BigDecimal("0.9"))
                    .method(QueryRouting.Method.LLM)
                    .build();
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.9,"refinedQuery":"요금제 알려줘",
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"요금제 알려줘"}]}
                """);
            given(queryRoutingRepository.findByMessage_MessageId(1L))
                    .willReturn(java.util.Optional.empty(), java.util.Optional.of(winner));
            given(queryRoutingRepository.saveAndFlush(any()))
                    .willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
            given(consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(1L))
                    .willReturn(java.util.List.of());

            IntentRouteResponse result = service.route(message);

            assertThat(result.routingId()).isEqualTo(44L);
            assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
        }
    }

    @Nested
    @DisplayName("상위 의도는 있는데 subQueries가 비어 온 경우")
    class EmptySubQueries {

        @Test
        @DisplayName("FAQ → refinedQuery로 FAQ 하위 질의 1건을 만든다")
        void faq_createsDefaultSubQuery() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.95,"refinedQuery":"위약금 계산 기준",
                 "extractedConditions":{},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("위약금 어떻게 계산돼?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.FAQ);
            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.FAQ);
            assertThat(r.subQueries().get(0).queryText()).isEqualTo("위약금 계산 기준");
        }

        @Test
        @DisplayName("STORE → 추출된 조건을 그대로 실은 STORE 하위 질의를 만든다")
        void store_carriesExtractedConditions() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.94,"refinedQuery":"강남역 유심 재발급 매장",
                 "extractedConditions":{"location":"강남역","serviceType":"USIM_REISSUE"},
                 "subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("강남역에서 유심 재발급 되는 매장 알려줘"));

            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().get(0).intent()).isEqualTo(ConsultRequest.Intent.STORE);
            assertThat(r.subQueries().get(0).conditions())
                .containsEntry("location", "강남역")
                .containsEntry("serviceType", "USIM_REISSUE");
        }

        @Test
        @DisplayName("BOTH → 한쪽 상담이 누락되지 않도록 FAQ·STORE 하위 질의를 모두 만든다")
        void both_createsFaqAndStoreSubQueries() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"BOTH","confidence":0.97,"refinedQuery":"5G 요금제 및 신촌 매장",
                 "extractedConditions":{"location":"신촌"},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("5G 요금제 알려주고 신촌 매장도 찾아줘"));

            assertThat(r.subQueries()).hasSize(2);
            assertThat(r.subQueries()).extracting(IntentRouteResponse.IntentSubQueryResponse::intent)
                .containsExactly(ConsultRequest.Intent.FAQ, ConsultRequest.Intent.STORE);
            assertThat(r.subQueries().get(1).conditions()).containsEntry("location", "신촌");
        }

        @Test
        @DisplayName("refinedQuery가 비면 사용자 원문으로 하위 질의를 만든다")
        void fallsBackToRawContent() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.90,"refinedQuery":"","extractedConditions":{},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("로밍 요금 알려줘"));

            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().get(0).queryText()).isEqualTo("로밍 요금 알려줘");
        }

        @Test
        @DisplayName("UNKNOWN은 상담할 내용이 없어 하위 질의를 만들지 않는다")
        void unknown_createsNothing() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"UNKNOWN","confidence":0.99,"refinedQuery":"","extractedConditions":{},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("오늘 날씨 어때?"));

            assertThat(r.intent()).isEqualTo(QueryRouting.Intent.UNKNOWN);
            assertThat(r.subQueries()).isEmpty();
        }
    }

    @Nested
    @DisplayName("매장 찾기 조건 보완")
    class StoreConditions {

        private String store(String extracted, String subConditions) {
            return """
                {"intent":"STORE","confidence":0.9,"refinedQuery":"매장 찾기",
                 "extractedConditions":%s,
                 "subQueries":[{"order":1,"intent":"STORE","queryText":"매장 찾기","conditions":%s}]}
                """.formatted(extracted, subConditions);
        }

        // LLM이 유심 외 업무를 비워 보내는 경우가 있어 질문 문장의 업무 표현으로 채운다
        @ParameterizedTest
        @CsvSource({
            "번호이동 가능한 매장 찾아줘, PORT_IN",
            "명의변경 가능한 매장 찾아줘, NAME_CHANGE",
            "신규 개통 가능한 매장 찾아줘, NEW_LINE",
            "유심 재발급 가능한 매장 찾아줘, USIM_REISSUE"
        })
        void fillsMissingServiceTypeFromQuestion(String question, String serviceType) {
            given(llmClient.generate(any())).willReturn(store("{}", "{}"));

            IntentRouteResponse r = service.routeSingleConsult(msg(question), null);

            assertThat(r.extractedConditions()).containsEntry("serviceType", serviceType);
            assertThat(r.subQueries().getFirst().conditions()).containsEntry("serviceType", serviceType);
        }

        @Test
        void fillsServiceTypeWhenLlmLeavesSubQueriesEmpty() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"STORE","confidence":0.9,"refinedQuery":"번호이동 매장","extractedConditions":{},"subQueries":[]}
                """);

            IntentRouteResponse r = service.route(msg("번호이동 가능한 매장 찾아줘"));

            assertThat(r.subQueries()).hasSize(1);
            assertThat(r.subQueries().getFirst().conditions()).containsEntry("serviceType", "PORT_IN");
        }

        @Test
        void keepsServiceTypeChosenByLlm() {
            given(llmClient.generate(any())).willReturn(
                    store("{\"serviceType\":\"PORT_IN\"}", "{\"serviceType\":\"PORT_IN\"}"));

            IntentRouteResponse r = service.routeSingleConsult(msg("번호이동 가능한 매장 찾아줘"), null);

            assertThat(r.subQueries().getFirst().conditions()).containsEntry("serviceType", "PORT_IN");
        }

        // 업무 표현이 여럿이면 LLM이 그중 하나를 줘도 부정된 업무일 수 있어 버린다
        @ParameterizedTest
        @ValueSource(strings = {"번호이동 말고 신규 개통 가능한 매장 찾아줘", "명의변경하고 번호이동 되는 매장 찾아줘"})
        void dropsLlmServiceTypeWhenSeveralServiceWords(String question) {
            given(llmClient.generate(any())).willReturn(
                    store("{\"serviceType\":\"PORT_IN\"}", "{\"serviceType\":\"PORT_IN\"}"));

            IntentRouteResponse r = service.routeSingleConsult(msg(question), null);

            assertThat(r.extractedConditions()).doesNotContainKey("serviceType");
            assertThat(r.subQueries().getFirst().conditions()).doesNotContainKey("serviceType");
        }

        // 업무 표현이 여럿이면 어느 쪽인지 알 수 없어 임의로 고르지 않는다
        @ParameterizedTest
        @ValueSource(strings = {"번호이동 말고 신규 개통 가능한 매장 찾아줘", "명의변경하고 번호이동 되는 매장 찾아줘"})
        void doesNotFillWhenSeveralServiceWords(String question) {
            given(llmClient.generate(any())).willReturn(store("{}", "{}"));

            IntentRouteResponse r = service.routeSingleConsult(msg(question), null);

            assertThat(r.extractedConditions()).doesNotContainKey("serviceType");
            assertThat(r.subQueries().getFirst().conditions()).doesNotContainKey("serviceType");
        }

        @Test
        void doesNotFillWithoutServiceWords() {
            given(llmClient.generate(any())).willReturn(store("{}", "{}"));

            IntentRouteResponse r = service.routeSingleConsult(msg("가까운 매장 찾아줘"), null);

            assertThat(r.subQueries().getFirst().conditions()).doesNotContainKey("serviceType");
        }

        // FAQ 질문의 업무 표현은 매장 조건이 아니고, BOTH는 FAQ 쪽 표현 때문에 매장 업무가 잘못 걸릴 수 있다
        @Test
        void doesNotFillForFaqOrBoth() {
            given(llmClient.generate(any())).willReturn("""
                {"intent":"FAQ","confidence":0.95,"refinedQuery":"번호이동 구비 서류","extractedConditions":{},
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"번호이동 구비 서류","conditions":{}}]}
                """);
            IntentRouteResponse faq = service.route(msg("번호이동 하려면 뭐 필요해?"));
            assertThat(faq.extractedConditions()).doesNotContainKey("serviceType");
            assertThat(faq.subQueries().getFirst().conditions()).isEmpty();

            given(llmClient.generate(any())).willReturn("""
                {"intent":"BOTH","confidence":0.95,"refinedQuery":"번호이동 위약금과 강남역 매장",
                 "extractedConditions":{"location":"강남역"},
                 "subQueries":[{"order":1,"intent":"FAQ","queryText":"번호이동 위약금","conditions":{}},
                   {"order":2,"intent":"STORE","queryText":"강남역 매장","conditions":{"location":"강남역"}}]}
                """);
            IntentRouteResponse both = service.route(msg("번호이동 위약금 알려주고 강남역 매장도 찾아줘"));
            assertThat(both.subQueries().get(1).conditions()).doesNotContainKey("serviceType");
        }
    }
}
