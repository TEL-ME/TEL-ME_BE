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
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.service.LlmClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueryRoutingService 후속 답변 분석")
class QueryRoutingServiceFollowUpTest {

    private static final Long SESSION_ID = 10L;
    private static final Long WAITING_CONSULT_REQUEST_ID = 42L;

    @Mock
    private LlmClient llmClient;

    @Mock
    private QueryRoutingRepository queryRoutingRepository;

    @Mock
    private ConsultRequestRepository consultRequestRepository;

    @Mock
    private IntentConverter intentConverter;

    @Mock
    private TransactionTemplate transactionTemplate;

    private QueryRoutingService service;

    @BeforeEach
    void setUp() {
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        service = new QueryRoutingService(
                llmClient,
                new ObjectMapper(),
                queryRoutingRepository,
                consultRequestRepository,
                new RuleBasedRoutingFallback(),
                intentConverter,
                transactionTemplate
        );
    }

    private void givenWaitingConsultExists() {
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.STORE)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .build();

        given(consultRequestRepository.findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(Optional.of(waiting));
    }

    @Test
    @DisplayName("LLM이 조건을 추출하면 기존 consultRequestId와 조건 Map을 함께 반환한다")
    void analyzeFollowUp_withLlmExtraction() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn(
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
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"신촌\"}]}");

        service.analyzeFollowUp(SESSION_ID, "신촌이요");

        verify(consultRequestRepository, never()).save(any());
        verify(queryRoutingRepository, never()).save(any());
    }

    @Test
    @DisplayName("되묻기 대기 중인 상담이 없으면 consultRequestId 없이 반환해 임의 상담 갱신을 막는다")
    void analyzeFollowUp_withoutWaitingConsult() {
        given(consultRequestRepository.findFirstBySession_SessionIdAndStatusOrderBySubqueryOrderAsc(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(Optional.empty());

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
    }

    @Test
    @DisplayName("정의되지 않은 조건 키는 상담 모듈로 넘기지 않는다")
    void analyzeFollowUp_ignoresUnknownConditionKeys() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":["
                + "{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"판교역\"},"
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
}
