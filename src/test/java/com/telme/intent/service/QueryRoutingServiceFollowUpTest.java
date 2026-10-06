package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.service.LlmClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueryRoutingService 후속 답변 분석")
class QueryRoutingServiceFollowUpTest {

    private static final Long SESSION_ID = 10L;
    private static final Long WAITING_CONSULT_REQUEST_ID = 42L;

    @Mock private LlmClient llmClient;

    @Mock private QueryRoutingRepository queryRoutingRepository;

    @Mock private ConsultRequestRepository consultRequestRepository;

    @Mock private IntentConverter intentConverter;

    @Mock private TransactionTemplate transactionTemplate;

    private QueryRoutingService service;

    @BeforeEach
    void setUp() {
        given(transactionTemplate.execute(any()))
                .willAnswer(
                        invocation -> {
                            TransactionCallback<?> callback = invocation.getArgument(0);
                            return callback.doInTransaction(mock(TransactionStatus.class));
                        });

        service =
                new QueryRoutingService(
                        llmClient,
                        new ObjectMapper(),
                        queryRoutingRepository,
                        consultRequestRepository,
                        new RuleBasedRoutingFallback(),
                        intentConverter,
                        transactionTemplate);
    }

    private void givenWaitingConsultExists() {
        ConsultRequest waiting =
                ConsultRequest.builder()
                        .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                        .subqueryOrder((short) 1)
                        .intent(ConsultRequest.Intent.STORE)
                        .status(ConsultRequest.Status.WAITING_CONDITION)
                        .build();

        given(
                        consultRequestRepository
                                .findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                                        SESSION_ID, ConsultRequest.Status.WAITING_CONDITION))
                .willReturn(Optional.of(waiting));
    }

    @Test
    @DisplayName("LLM이 조건을 추출하면 기존 consultRequestId와 조건 Map을 함께 반환한다")
    void analyzeFollowUp_withLlmExtraction() {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "강남역이요");

        assertThat(response.consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(response.method()).isEqualTo(QueryRouting.Method.LLM);
        // 조건은 Map<String, String>으로만 전달한다(상태 변환은 상담 도메인 담당)
        assertThat(response.conditions()).containsExactly(entry("location", "강남역"));
        assertThat(response.declinedKeys()).isEmpty();
    }

    @Test
    @DisplayName("새 상담 요청을 만들지 않고 기존 상담만 재사용한다")
    void analyzeFollowUp_doesNotCreateNewConsultRequest() {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"신촌\"}]}");

        service.analyzeFollowUp(SESSION_ID, "신촌이요");

        verify(consultRequestRepository, never()).save(any());
        verify(queryRoutingRepository, never()).save(any());
    }

    @Test
    @DisplayName("되묻기 대기 중인 상담이 없으면 consultRequestId 없이 반환해 임의 상담 갱신을 막는다")
    void analyzeFollowUp_withoutWaitingConsult() {
        given(
                        consultRequestRepository
                                .findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                                        SESSION_ID, ConsultRequest.Status.WAITING_CONDITION))
                .willReturn(Optional.empty());

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "강남역");

        assertThat(response.hasTarget()).isFalse();
        assertThat(response.consultRequestId()).isNull();
        assertThat(response.conditions()).isEmpty();
    }

    @Test
    @DisplayName("LLM 오류 시 규칙 기반으로 전환해 거절 의사를 DECLINED로 잡는다")
    void analyzeFollowUp_fallsBackToRuleOnLlmFailure() {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willThrow(new GeneralException(IntentErrorCode.LLM_CONNECTION_FAILED));

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "그냥 알려주기 싫어요");

        assertThat(response.consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        // 거절은 값을 못 찾은 경우와 구분되도록 declinedKeys로 온다
        assertThat(response.conditions()).isEmpty();
        assertThat(response.declinedKeys()).containsExactly("location");
    }

    @Test
    @DisplayName("LLM이 빈 응답(fake 프로바이더)을 주면 규칙 기반으로 한 번 더 시도한다")
    void analyzeFollowUp_retriesWithRuleWhenLlmExtractsNothing() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "홍대입구역");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.conditions()).containsEntry("location", "홍대입구역");
    }

    @Test
    @DisplayName("조건이 아닌 짧은 답변은 지역으로 오인하지 않고 기존 질문 대기를 유지한다")
    void analyzeFollowUp_doesNotTreatPauseAsLocation() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "잠깐만요");

        assertThat(response.consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(response.conditions()).isEmpty();
        assertThat(response.declinedKeys()).isEmpty();
        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.DEFERRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5G 요금제는 얼마예요?", "로밍 요금", "해지 위약금"})
    @DisplayName("LLM이 새 질문으로 판정한 결과를 규칙 폴백이 조건값으로 덮지 않는다")
    void analyzeFollowUp_marksNewQuestion(String reply) {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willReturn("{\"responseType\":\"NEW_QUESTION\",\"conditions\":[]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(response.method()).isEqualTo(QueryRouting.Method.LLM);
        assertThat(response.disposition())
                .isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
        assertThat(response.conditions()).isEmpty();
    }

    @Test
    @DisplayName("기존 형식의 빈 LLM 응답도 규칙이 새 질문으로 판정하면 다시 라우팅한다")
    void analyzeFollowUp_usesRuleDispositionForLegacyEmptyResponse() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "5G 요금제는 얼마예요?");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.disposition())
                .isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
    }

    @Test
    @DisplayName("정의되지 않은 조건 키는 상담 모듈로 넘기지 않는다")
    void analyzeFollowUp_ignoresUnknownConditionKeys() {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"판교역\"},"
                                + "{\"key\":\"hallucinatedKey\",\"status\":\"FILLED\",\"value\":\"아무값\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "판교역이요");

        assertThat(response.conditions()).containsOnlyKeys("location");
    }

    @Test
    @DisplayName("세션 ID가 없으면 분석을 시도하지 않는다")
    void analyzeFollowUp_requiresSessionId() {
        assertThatThrownBy(() -> service.analyzeFollowUp(null, "강남역"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeFollowupCannotStoreLocationInventedByModel() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "네");
        assertThat(result.conditions()).isEmpty();
    }

    @Test
    void storeFollowupCannotStoreInventedServiceOrDecline() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[{\"key\":\"serviceType\",\"status\":\"FILLED\",\"value\":\"USIM_REISSUE\"},{\"key\":\"location\",\"status\":\"DECLINED\",\"value\":null}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "강남역이요");
        assertThat(result.conditions()).containsExactly(entry("location", "강남역"));
        assertThat(result.declinedKeys()).isEmpty();
    }

    private void givenWaitingPlan(String key) {
        var waiting =
                ConsultRequest.builder()
                        .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                        .subqueryOrder((short) 1)
                        .intent(ConsultRequest.Intent.FAQ)
                        .status(ConsultRequest.Status.WAITING_CONDITION)
                        .conditions(
                                List.of(
                                        ConsultCondition.builder()
                                                .conditionKey(key)
                                                .status(ConsultCondition.Status.PENDING)
                                                .build()))
                        .build();
        given(
                        consultRequestRepository
                                .findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                                        SESSION_ID, ConsultRequest.Status.WAITING_CONDITION))
                .willReturn(Optional.of(waiting));
    }

    @Test
    @DisplayName("모델의 추정 요금제 값과 지역값을 저장하지 않는다")
    void planFollowupRejectsModelInventedConditionAndLocation() {
        givenWaitingPlan("joinedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"joinedThisMonth\",\"status\":\"FILLED\","
                                + "\"value\":\"예\"},{\"key\":\"location\",\"status\":\"FILLED\","
                                + "\"value\":\"강남역\"}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "나중에요");
        assertThat(result.conditions()).isEmpty();
        assertThat(result.declinedKeys()).isEmpty();
        assertThat(result.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.DEFERRED);
    }

    @Test
    @DisplayName("모델의 추정 거절보다 명시된 부정값을 우선한다")
    void planFollowupRejectsModelInventedRefusalAndKeepsLiteralNegativeValue() {
        givenWaitingPlan("joinedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"joinedThisMonth\",\"status\":\"DECLINED\",\"value\":null}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "지난달에 가입했어요");
        assertThat(result.conditions()).containsExactly(entry("joinedThisMonth", "아니요"));
        assertThat(result.declinedKeys()).isEmpty();
    }

    @Test
    @DisplayName("명확한 현재 조건의 예아니요 값만 받는다")
    void planFollowupAcceptsOnlyExplicitCanonicalCondition() {
        givenWaitingPlan("changedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\","
                                + "\"value\":\"아니요\"},{\"key\":\"joinedThisMonth\",\"status\":\"FILLED\","
                                + "\"value\":\"아니요\"}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "이번 달에는 아직 안 바꿨어요");
        assertThat(result.conditions()).containsExactly(entry("changedThisMonth", "아니요"));
        assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
    }

    @Test
    @DisplayName("새 질문에는 조건 출력이 있어도 옛 상담을 갱신하지 않는다")
    void explicitNewQuestionNeverUpdatesOldPlanEvenWithConditionsInPayload() {
        givenWaitingPlan("joinedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"responseType\":\"NEW_QUESTION\",\"conditions\":[{\"key\":\"joinedThisMonth\","
                                + "\"status\":\"FILLED\",\"value\":\"아니요\"}]}");
        var result = service.analyzeFollowUp(SESSION_ID, "지난달에 가입했는데 유심 재발급 비용을 알려주세요");
        assertThat(result.conditions()).isEmpty();
        assertThat(result.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
    }

    @Test
    @DisplayName("요금제 대기 키 누락을 지역 질문으로 바꾸지 않는다")
    void missingFaqPendingKeyIsStateErrorRatherThanLocationQuestion() {
        givenWaitingPlan("joinedThisMonth");
        var request =
                ConsultRequest.builder()
                        .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                        .subqueryOrder((short) 1)
                        .intent(ConsultRequest.Intent.FAQ)
                        .status(ConsultRequest.Status.WAITING_CONDITION)
                        .build();
        given(
                        consultRequestRepository
                                .findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                                        SESSION_ID, ConsultRequest.Status.WAITING_CONDITION))
                .willReturn(Optional.of(request));
        assertThatThrownBy(() -> service.analyzeFollowUp(SESSION_ID, "네"))
                .isInstanceOf(IllegalStateException.class);
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"이번 달엔 안 바꿨어요", "아직 안 바꿨어요", "변경 안 했어요"})
    @DisplayName("자연스러운 변경 이력 부정은 현재 대기 조건에 반영한다")
    void 자연스러운_부정_응답을_현재_조건에_반영한다(String reply) {
        givenWaitingPlan("changedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\",\"value\":\"아니요\"}]}");

        var result = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(result.conditions()).containsExactly(entry("changedThisMonth", "아니요"));
        assertThat(result.disposition())
                .isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
    }

    @Test
    @DisplayName("규칙에 없는 명확한 완료 표현은 대기 질문과 LLM 해석으로 보완한다")
    void 규칙이_놓친_명확한_완료를_보완한다() {
        givenWaitingPlan("changedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        var result = service.analyzeFollowUp(SESSION_ID, "변경은 완료했어요");

        assertThat(result.conditions()).containsExactly(entry("changedThisMonth", "예"));
        assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "친구는 이번 달에 변경했어요",
                "이번 달에 변경했다면 어떻게 돼요?",
                "이번 달에 변경한 것 같아요",
                "잘 모르겠어요",
                "나중에요",
                "매장을 변경했어요",
                "변경해도 안 되나요?",
                "이번 달에 변경했어요. 변경 안 했어요",
                "유심 비용 알려주세요"
            })
    @DisplayName("모델이 값을 출력해도 본인 변경 사실이 아닌 응답은 저장하지 않는다")
    void 모델의_추정값을_그대로_저장하지_않는다(String reply) {
        givenWaitingPlan("changedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        var result = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(result.conditions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입한 지 한 달 됐어요", "가입한 지 30일 됐어요", "가입한 지 4주 됐어요"})
    @DisplayName("모델도 불명확한 경과 기간을 달력상 가입월로 추정할 수 없다")
    void 모델의_경과_기간_추정을_차단한다(String reply) {
        givenWaitingPlan("joinedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"joinedThisMonth\",\"status\":\"FILLED\",\"value\":\"아니요\"}]}");

        assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).isEmpty();
    }

    @Test
    @DisplayName("명확한 부정의 말투 변형도 LLM과 현재 질문 문맥으로 해석한다")
    void 부정_말투_변형을_허용한다() {
        givenWaitingPlan("changedThisMonth");
        given(llmClient.generate(any()))
                .willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\",\"value\":\"아니요\"}]}");

        var result = service.analyzeFollowUp(SESSION_ID, "아뇨");

        assertThat(result.conditions()).containsExactly(entry("changedThisMonth", "아니요"));
        assertThat(result.method()).isEqualTo(QueryRouting.Method.LLM);
    }
    @Test
    @DisplayName("안내·신청 완료를 실제 요금제 변경 이력으로 저장하지 않는다")
    void 안내와_신청을_변경_완료로_오인하지_않는다() {
        givenWaitingPlan("changedThisMonth");
        for (String reply : List.of("변경 안내는 완료했어요", "변경 신청만 했어요")) {
            for (String value : List.of("예", "아니요")) {
                given(llmClient.generate(any())).willReturn(
                        "{\"conditions\":[{\"key\":\"changedThisMonth\",\"status\":\"FILLED\",\"value\":\""
                                + value + "\"}]}");

                assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"이번 달 개통이 예정이에요", "이번 달 가입이 무슨 뜻이에요?",
            "이번 달 가입이면 어떻게 돼요?", "이번 달 가입 신청을 완료했어요"})
    @DisplayName("예정·설명 요청·가입 가정을 실제 가입 사실로 저장하지 않는다")
    void 예정과_질문을_실제_가입으로_오인하지_않는다(String reply) {
        givenWaitingPlan("joinedThisMonth");
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"joinedThisMonth\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).isEmpty();
    }

}
