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
import com.telme.chat.entity.ChatMessage;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));
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
    @DisplayName("채팅 실행에서 부른 후속 분석은 실행 ID와 상담 요청 ID를 LLM 요청에 담는다")
    void analyzeFollowUp_carriesExecutionIdForGenerationRecord() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}");

        service.analyzeFollowUp(SESSION_ID, "강남역이요", 77L);

        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(request.capture());
        assertThat(request.getValue().executionId()).isEqualTo(77L);
        assertThat(request.getValue().consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.ROUTING);
    }

    @Test
    @DisplayName("여러 조건 중 실제로 물은 현재 조건만 LLM에 넘긴다")
    void analyzeFollowUp_onlyPassesAskedPendingCondition() {
        ChatMessage askedMessage = ChatMessage.builder().messageId(99L).build();
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.FAQ)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .conditions(List.of(
                        ConsultCondition.builder()
                                .conditionKey("joined_this_month")
                                .status(ConsultCondition.Status.PENDING)
                                .askedMessage(askedMessage)
                                .build(),
                        ConsultCondition.builder()
                                .conditionKey("changed_this_month")
                                .status(ConsultCondition.Status.PENDING)
                                .build()))
                .build();
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"joined_this_month\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        service.analyzeFollowUp(SESSION_ID, "예");

        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(request.capture());
        assertThat(request.getValue().userPrompt())
                .contains("되묻는 중인 조건: joined_this_month")
                .doesNotContain("changed_this_month");
    }

    @Test
    @DisplayName("되물은 질문과 선택지를 조건 이름과 함께 LLM에 넘긴다")
    void analyzeFollowUp_passesAskedQuestionAndOptions() {
        // 복합 질문의 되묻기 메시지는 앞부분에 답변이 있고 마지막 줄이 질문이다
        ChatMessage askedMessage = ChatMessage.builder().messageId(99L)
                .content("1. 유심 재발급 비용\n7,700원입니다.\n\n2. 유심 재발급 서류\n대리인이 대신 받으러 오실 예정이신가요?")
                .followUps("[\"예\", \"아니요\"]")
                .build();
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.FAQ)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .conditions(List.of(ConsultCondition.builder()
                        .conditionKey("agent_collecting")
                        .status(ConsultCondition.Status.PENDING)
                        .askedMessage(askedMessage)
                        .build()))
                .build();
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));
        given(llmClient.generate(any())).willReturn(
                "{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":"
                        + "[{\"key\":\"agent_collecting\",\"status\":\"FILLED\",\"value\":\"직접 갈 거예요\"}]}");

        service.analyzeFollowUp(SESSION_ID, "유심을 잃어버렸고 제가 직접 매장에 갈 거예요");

        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(request.capture());
        assertThat(request.getValue().userPrompt())
                .contains("되묻는 중인 조건: agent_collecting")
                .contains("- agent_collecting: \"대리인이 대신 받으러 오실 예정이신가요?\" (선택지: 예, 아니요)")
                .doesNotContain("7,700원");
    }

    @Test
    @DisplayName("업무 선택지를 그대로 누르면 모델을 부르지 않고 코드로 읽는다")
    void analyzeFollowUp_readsServiceTypeOptionWithoutLlm() {
        ChatMessage askedMessage = ChatMessage.builder().messageId(99L).build();
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.STORE)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .conditions(List.of(ConsultCondition.builder()
                        .conditionKey("serviceType")
                        .status(ConsultCondition.Status.PENDING)
                        .askedMessage(askedMessage)
                        .build()))
                .build();
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "유심 재발급");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.conditions()).containsExactly(entry("serviceType", "USIM_REISSUE"));
        verify(llmClient, never()).generate(any());
    }

    @Test
    @DisplayName("데이터 사용량을 숫자로 답하면 모델을 부르지 않고 코드로 읽어 정리한 값으로 넘긴다")
    void analyzeFollowUp_readsDataUsageWithoutLlm() {
        givenWaitingDataUsage();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "하루에 2기가쯤 써요");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.conditions()).containsExactly(entry("monthly_data_usage", "60GB"));
        verify(llmClient, never()).generate(any());
    }

    @Test
    @DisplayName("데이터 사용량을 모른다고 답해도 코드로 읽어 모름으로 넘긴다")
    void analyzeFollowUp_readsUnsureDataUsageWithoutLlm() {
        givenWaitingDataUsage();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "잘 모르겠어요");

        assertThat(response.conditions()).containsExactly(entry("monthly_data_usage", "모름"));
        verify(llmClient, never()).generate(any());
    }

    @Test
    @DisplayName("통화량과 문자량도 숫자로 답하면 모델을 부르지 않고 코드로 읽는다")
    void analyzeFollowUp_readsCallAndTextUsageWithoutLlm() {
        givenWaiting("monthly_voice_usage");
        assertThat(service.analyzeFollowUp(SESSION_ID, "한 5시간쯤 해요").conditions())
                .containsExactly(entry("monthly_voice_usage", "300분"));

        givenWaiting("monthly_sms_usage");
        assertThat(service.analyzeFollowUp(SESSION_ID, "50건이요").conditions())
                .containsExactly(entry("monthly_sms_usage", "50건"));
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"아 잘못말했다 19살이요", "아 나이 잘못 말했어요 스물셋이에요", "정정할게요 20대 중반이에요"})
    @DisplayName("사용량을 묻는 중 사용량 없이 나이만 고쳐 말하면 모델 없이 나이 정정으로 받는다")
    void analyzeFollowUp_readsAgeCorrectionWhileAskingUsage(String reply) {
        givenWaitingDataUsage();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.conditions()).containsExactly(entry("age_group", reply));
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @CsvSource({"나이 31이라고 했어야 했는데 오타, 31살", "정정합니다 나이는 52, 52살", "나이 오타 66이요, 66살"})
    @DisplayName("살·세 없이 '나이'와 숫자 하나로 고쳐 말하면 사용량으로 읽지 않고 그 숫자를 나이로 받는다")
    void analyzeFollowUp_readsBareNumberAgeCorrection(String reply, String age) {
        givenWaitingDataUsage();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.conditions()).containsExactly(entry("age_group", age));
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "monthly_sms_usage|오타 50개요|50건",
        "monthly_data_usage|아 잘못 썼다 50G요|50GB",
        "monthly_data_usage|대학생인데 잘 모르겠어요|모름",
        "monthly_data_usage|25살인데 무제한이요|무제한",
        "monthly_data_usage|아 3기가 아니고 30기가요|30GB",
        "monthly_sms_usage|문자 20건 말고 2건이요|2건",
        "monthly_data_usage|20 아니고 30이요|30GB",
        "monthly_voice_usage|50 말고 100이요|100분",
        "monthly_data_usage|10기가 아니고 20이요|20GB",
        "monthly_sms_usage|50건 아니고 100이요|100건",
        "monthly_data_usage|10기가 아니고 무제한이요|무제한",
        "monthly_data_usage|10기가 말고 잘 모르겠어요|모름",
        "monthly_voice_usage|100분 아니고 거의 안 써요|거의 없음"})
    @DisplayName("정정하는 말이나 신분이 섞여도 단위를 붙여 사용량을 답했으면 나이를 바꾸지 않고 사용량으로 받는다")
    void analyzeFollowUp_keepsUsageAnswerWithCorrectionWords(String key, String reply, String value) {
        givenWaiting(key);

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.conditions()).containsExactly(entry(key, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"어르신이라 거의 안 써요",
        "아 잘못 말했다 95년생이요", "잘못 눌렀어요 24개월 약정이요", "아 잘못 말함 중3이에요", "정정할게요 고1이에요",
        "성인이고 강남역 매장 찾아주세요", "2호선 강남역 근처 매장 찾아줘",
        "아 잘못 눌렀어요 50이요", "아니 38이요", "ㄴㄴ 저 19 맞아요", "조금 전에 고른 거 틀림 예순일곱"})
    @DisplayName("사용 정도를 말한 답, 출생연도, 학년, 다른 수, 매장 요청, 고쳐 말하며 단위 없이 숫자만 말한 답은 가로채지 않고 모델에 넘긴다")
    void analyzeFollowUp_leavesAmbiguousRepliesToLlm(String reply) {
        givenWaitingDataUsage();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        verify(llmClient).generate(any());
        assertThat(response.conditions()).doesNotContainKeys("age_group", "monthly_data_usage");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"17살 아니고 20이에요|20이에요", "청소년 아니고 25예요|25예요",
        "65세 아니고 60이에요|60이에요", "미성년자 아니고 스물넷|스물넷", "나이 22 아니구 32요|32요",
        "아닌데 저 마흔 살이에요|저 마흔 살이에요", "나이 그거 아님 24임|24살", "나이 30 말고 40으로 해주세요|40살",
        "성인 말고 만 14~18세로 바꿀게요|만 14~18세로 바꿀게요"})
    @DisplayName("앞의 나이를 부정하고 고친 답은 부정 표현 뒤의 나이를 받는다")
    void analyzeFollowUp_readsAgeAfterNegation(String reply, String age) {
        givenWaitingDataUsage();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.conditions()).containsExactly(entry("age_group", age));
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"아 잘못 눌렀어요 만 14~18세예요", "몰라서 잘못 눌렀어요 17살이요", "조금 전에 잘못 눌렀어요 17살이요",
        "그건 잘못 눌렀어요 17살이에요", "분명 17살인데 잘못 눌렀어요", "나이 잘못 씀 17살임"})
    @DisplayName("선택지 문구로 고치거나 '몰라서', '조금 전에', '그건', '분명'이 섞여도 살·세를 붙여 고친 나이를 받는다")
    void analyzeFollowUp_readsTypedAgeCorrectionWithOtherWords(String reply) {
        givenWaiting("monthly_voice_usage");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.conditions()).containsOnlyKeys("age_group");
        verify(llmClient, never()).generate(any());
    }

    @ParameterizedTest
    @CsvSource({"그건 잘못 눌렀어요 나이 20이에요, 20살", "나이 잘못 말한 건데 20이에요, 20살"})
    @DisplayName("'그건', '건데'의 건은 사용량 단위가 아니어서 '나이'와 숫자로 고친 답을 사용량으로 읽지 않는다")
    void analyzeFollowUp_doesNotTakeKoreanWordsAsUsageUnit(String reply, String age) {
        givenWaitingDataUsage();

        assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).containsExactly(entry("age_group", age));
    }

    @Test
    @DisplayName("사용량을 묻는 중 숫자만 답하거나 사용량을 함께 말하면 나이 정정으로 읽지 않는다")
    void analyzeFollowUp_doesNotReadBareNumberOrUsageAsAgeCorrection() {
        givenWaitingDataUsage();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[]}");

        assertThat(service.analyzeFollowUp(SESSION_ID, "20").conditions()).doesNotContainKey("age_group");
        assertThat(service.analyzeFollowUp(SESSION_ID, "마흔 기가 정도요").conditions()).doesNotContainKey("age_group");
        assertThat(service.analyzeFollowUp(SESSION_ID, "쉰 정도요").conditions()).doesNotContainKey("age_group");
        assertThat(service.analyzeFollowUp(SESSION_ID, "25살인데 10기가 정도 써요").conditions())
                .containsExactly(entry("monthly_data_usage", "10GB"));
    }

    @Test
    @DisplayName("숫자가 분명하지 않은 사용량 답은 모델이 읽는다")
    void analyzeFollowUp_sendsVagueDataUsageToLlm() {
        givenWaitingDataUsage();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "많이 써요");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.LLM);
        verify(llmClient).generate(any());
    }

    @Test
    @DisplayName("사용량이 들어 있어도 긴 문장이나 물음표가 있는 문장은 새 질문일 수 있어 모델이 읽는다")
    void analyzeFollowUp_sendsQuestionWithDataUsageToLlm() {
        givenWaitingDataUsage();
        given(llmClient.generate(any())).willReturn("{\"conditions\":[]}");

        service.analyzeFollowUp(SESSION_ID, "50기가 쓰는데 요금제 바꾸면 위약금 있나요?");

        verify(llmClient).generate(any());
    }

    @Test
    @DisplayName("모델이 읽을 수 없는 사용량을 값으로 채워도 받지 않아 되묻게 한다")
    void analyzeFollowUp_dropsUnreadableUsageFilledByLlm() {
        givenWaitingDataUsage();
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"monthly_data_usage\",\"status\":\"FILLED\",\"value\":\"많이 써요\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "많이 써요");

        assertThat(response.conditions()).isEmpty();
        assertThat(response.declinedKeys()).isEmpty();
    }

    @Test
    @DisplayName("나이대와 사용량을 함께 기다릴 때 읽을 수 없는 값만 빼고 읽을 수 있는 값은 그대로 받는다")
    void analyzeFollowUp_keepsOnlyReadableRecommendationAnswers() {
        givenWaiting("age_group", "monthly_data_usage");
        given(llmClient.generate(any())).willReturn("{\"conditions\":["
                + "{\"key\":\"age_group\",\"status\":\"FILLED\",\"value\":\"60대예요\"},"
                + "{\"key\":\"monthly_data_usage\",\"status\":\"FILLED\",\"value\":\"40GB\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "60대인데 한 달에 40기가 써요");

        assertThat(response.conditions()).containsExactly(entry("monthly_data_usage", "40GB"));
    }

    @Test
    @DisplayName("읽을 수 있는 나이대와 사용량 값은 모델이 채운 그대로 받는다")
    void analyzeFollowUp_keepsReadableRecommendationAnswers() {
        for (String age : List.of("만 14~18세", "만 19~64세", "만 65세 이상", "17살이에요", "30대", "청소년", "시니어")) {
            givenWaiting("age_group", "monthly_data_usage", "monthly_voice_usage", "monthly_sms_usage");
            given(llmClient.generate(any())).willReturn("{\"conditions\":["
                    + "{\"key\":\"age_group\",\"status\":\"FILLED\",\"value\":\"" + age + "\"},"
                    + "{\"key\":\"monthly_data_usage\",\"status\":\"FILLED\",\"value\":\"잘 모르겠어요\"},"
                    + "{\"key\":\"monthly_voice_usage\",\"status\":\"FILLED\",\"value\":\"5시간\"},"
                    + "{\"key\":\"monthly_sms_usage\",\"status\":\"FILLED\",\"value\":\"50건\"}]}");

            FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "알려드릴게요 나이는 " + age + " 입니다");

            assertThat(response.conditions()).as(age).containsOnly(
                    entry("age_group", age), entry("monthly_data_usage", "잘 모르겠어요"),
                    entry("monthly_voice_usage", "5시간"), entry("monthly_sms_usage", "50건"));
        }
    }

    @Test
    @DisplayName("고객이 숫자로 말한 나이가 여러 구분에 걸치는데 모델이 선택지 하나로 채우면 받지 않아 되묻게 한다")
    void analyzeFollowUp_dropsAgeThatModelSnappedToOption() {
        // 모델이 채운 선택지 값은 모두 읽을 수 있는 값이지만 고객 말과 구분이 다르거나 걸치는 말을 하나로 정한 것이다
        Map<String, String> snapped = Map.of(
                "60대예요", "만 65세 이상",
                "65세 미만이요", "만 19~64세",
                "10대예요", "만 14~18세",
                "17살이에요", "만 19~64세");
        snapped.forEach((reply, option) -> {
            givenWaiting("age_group");
            given(llmClient.generate(any())).willReturn(
                    "{\"conditions\":[{\"key\":\"age_group\",\"status\":\"FILLED\",\"value\":\"" + option + "\"}]}");

            assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).as(reply).isEmpty();
        });
    }

    @Test
    @DisplayName("고객이 말한 나이와 모델이 채운 선택지가 같은 구분이거나 나이를 숫자로 말하지 않았으면 그대로 받는다")
    void analyzeFollowUp_keepsAgeThatMatchesTypedAge() {
        Map<String, String> matching = Map.of(
                "17살이에요", "만 14~18세",
                "30대예요", "만 19~64세",
                "35세", "만 19~64세",
                "70세예요", "만 65세 이상",
                "첫 번째요", "만 14~18세",
                "시니어예요", "만 65세 이상");
        matching.forEach((reply, option) -> {
            givenWaiting("age_group");
            given(llmClient.generate(any())).willReturn(
                    "{\"conditions\":[{\"key\":\"age_group\",\"status\":\"FILLED\",\"value\":\"" + option + "\"}]}");

            assertThat(service.analyzeFollowUp(SESSION_ID, reply).conditions()).as(reply).containsExactly(entry("age_group", option));
        });
    }

    @Test
    @DisplayName("통화·문자도 모델이 읽을 수 없는 값을 채우면 받지 않는다")
    void analyzeFollowUp_dropsUnreadableCallAndTextFilledByLlm() {
        givenWaiting("monthly_voice_usage", "monthly_sms_usage");
        given(llmClient.generate(any())).willReturn("{\"conditions\":["
                + "{\"key\":\"monthly_voice_usage\",\"status\":\"FILLED\",\"value\":\"많이 해요\"},"
                + "{\"key\":\"monthly_sms_usage\",\"status\":\"FILLED\",\"value\":\"50건\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "통화는 많이 하고 문자는 50건쯤 보내요");

        assertThat(response.conditions()).containsExactly(entry("monthly_sms_usage", "50건"));
    }

    @Test
    @DisplayName("거절한 조건과 다른 조건은 사용량 검증과 상관없이 그대로 받는다")
    void analyzeFollowUp_keepsDeclinedAndOtherConditions() {
        givenWaiting("age_group", "location");
        given(llmClient.generate(any())).willReturn("{\"conditions\":["
                + "{\"key\":\"age_group\",\"status\":\"DECLINED\"},"
                + "{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "나이는 말하기 싫고 강남역이요");

        assertThat(response.declinedKeys()).containsExactly("age_group");
        assertThat(response.conditions()).containsExactly(entry("location", "강남역"));
    }

    private void givenWaitingDataUsage() {
        givenWaiting("monthly_data_usage");
    }

    private void givenWaiting(String... keys) {
        ChatMessage askedMessage = ChatMessage.builder().messageId(99L).build();
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.FAQ)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .conditions(java.util.Arrays.stream(keys)
                        .map(key -> ConsultCondition.builder()
                                .conditionKey(key)
                                .status(ConsultCondition.Status.PENDING)
                                .askedMessage(askedMessage)
                                .build())
                        .toList())
                .build();
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));
    }

    @ParameterizedTest
    @DisplayName("매장을 묻는 중이 아닌데 답과 함께 매장 찾기와 별개의 새 질문이 있으면 조건 답으로만 읽지 않고 새 질문으로 처리한다")
    @ValueSource(strings = {
            "아 형 아니고 동생이요 고1인데 미성년자도 되나? 그리고 분당 서현역 근처 매장도",
            "네 같이 갈 수 있어요. 미성년자도 되나요? 그리고 강남역 근처 매장 어디예요",
            "동생이요 고1인데 미성년자도 되나 그리고 서현역 근처 매장 찾아줘"})
    void analyzeFollowUp_treatsStoreRequestWithNewQuestionAsNewQuestion(String reply) {
        givenWaiting("both_present");
        given(llmClient.generate(any())).willReturn("{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":"
                + "[{\"key\":\"both_present\",\"status\":\"FILLED\",\"value\":\"동생이요\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
        assertThat(response.conditions()).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("답과 매장 찾기뿐이면 새 질문으로 돌리지 않고 조건 답으로 읽어 기다리던 상담이 취소되지 않게 한다")
    @ValueSource(strings = {
            "네 같이 갈 수 있어요 그리고 강남역 근처 매장 어디예요",
            "네 그리고 가까운 매장 찾아주세요",
            "아 형 아니고 동생이요 그리고 서현역 근처 매장도 알려주세요",
            "예 같이 가요. 근처 매장 위치도 알려줘"})
    void analyzeFollowUp_keepsAnswerWithStoreRequestOnlyAsConditionResponse(String reply) {
        givenWaiting("both_present");
        given(llmClient.generate(any())).willReturn("{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":"
                + "[{\"key\":\"both_present\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
        assertThat(response.conditions()).containsExactly(entry("both_present", "예"));
    }

    @ParameterizedTest
    @DisplayName("매장이 나와도 되묻는 조건에 답하는 말이면 조건 답으로 읽는다")
    @ValueSource(strings = {
            "네 같이 매장 갈 수 있어요", "유심을 잃어버렸고 제가 직접 매장에 갈 거예요", "아니요 매장에 같이 못 가요",
            "매장에서 바로 받을 수 있어요", "네 매장 찾아갈 수 있어요", "매장 방문은 어려워요", "집 근처 대리점에 갈 거예요"})
    void analyzeFollowUp_keepsAnswerThatMentionsStore(String reply) {
        givenWaiting("both_present");
        given(llmClient.generate(any())).willReturn("{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":"
                + "[{\"key\":\"both_present\",\"status\":\"FILLED\",\"value\":\"예\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, reply);

        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
        assertThat(response.conditions()).containsExactly(entry("both_present", "예"));
    }

    @Test
    @DisplayName("지역을 묻는 중에 매장 찾기 말로 지역을 답하면 조건 답으로 읽는다")
    void analyzeFollowUp_keepsLocationAnswerWithStoreRequest() {
        givenWaiting("location");
        given(llmClient.generate(any())).willReturn(
                "{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"서현역\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "서현역 근처 매장 알려주세요");

        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
        assertThat(response.conditions()).containsExactly(entry("location", "서현역"));
    }

    @Test
    @DisplayName("업무를 직접 풀어 쓴 답은 모델이 읽는다")
    void analyzeFollowUp_sendsFreeServiceTypeTextToLlm() {
        ChatMessage askedMessage = ChatMessage.builder().messageId(99L).build();
        ConsultRequest waiting = ConsultRequest.builder()
                .consultRequestId(WAITING_CONSULT_REQUEST_ID)
                .subqueryOrder((short) 1)
                .intent(ConsultRequest.Intent.STORE)
                .status(ConsultRequest.Status.WAITING_CONDITION)
                .conditions(List.of(ConsultCondition.builder()
                        .conditionKey("serviceType")
                        .status(ConsultCondition.Status.PENDING)
                        .askedMessage(askedMessage)
                        .build()))
                .build();
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of(waiting));
        given(llmClient.generate(any())).willReturn(
                "{\"conditions\":[{\"key\":\"serviceType\",\"status\":\"FILLED\",\"value\":\"USIM_REISSUE\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "유심 재발급 하려고요");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.LLM);
        verify(llmClient).generate(any());
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
        given(consultRequestRepository.findByLatestAskedQuestion(
                SESSION_ID, ConsultRequest.Status.WAITING_CONDITION)).willReturn(List.of());

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
    @DisplayName("LLM이 '현재 위치'를 지역으로 뽑아도 조건으로 받지 않고, 규칙 기반 재시도도 문장을 지역으로 받지 않는다")
    void analyzeFollowUp_ignoresRelativeLocation() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn(
                "{\"responseType\":\"CONDITION_RESPONSE\","
                        + "\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"현재 위치\"}]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "현재 위치에서 가까운 매장을 찾아줘");

        assertThat(response.conditions()).doesNotContainKey("location");
        assertThat(response.declinedKeys()).isEmpty();
    }

    @Test
    @DisplayName("지역을 묻는 중 업무 버튼 문구만 오면 모델 없이 같은 상담의 업무 조건으로 받는다")
    void analyzeFollowUp_acceptsServiceOptionWhileAskingLocation() {
        givenWaitingConsultExists();

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "유심 재발급");

        assertThat(response.consultRequestId()).isEqualTo(WAITING_CONSULT_REQUEST_ID);
        assertThat(response.conditions()).containsExactly(Map.entry("serviceType", "USIM_REISSUE"));
        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
        verify(llmClient, never()).generate(any());
    }

    @Test
    @DisplayName("지역을 묻는 중 업무 이름에 어미만 붙여 답해도 모델 없이 업무 조건으로 받는다")
    void analyzeFollowUp_acceptsShortServiceReplyWhileAskingLocation() {
        givenWaitingConsultExists();

        // TELME-174 대화 흐름에서 지역 값으로 잘못 들어갔던 실제 답변
        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "명의변경이라고");

        assertThat(response.conditions()).containsExactly(Map.entry("serviceType", "NAME_CHANGE"));
        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.CONDITION_RESPONSE);
        verify(llmClient, never()).generate(any());
    }

    @Test
    @DisplayName("지역을 묻는 중이라도 업무가 들어간 문장형 질문은 업무 조건으로 가로채지 않는다")
    void analyzeFollowUp_doesNotHijackSentenceQuestionWithServiceWords() {
        givenWaitingConsultExists();
        given(llmClient.generate(any())).willReturn("{\"responseType\":\"NEW_QUESTION\",\"conditions\":[]}");

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "유심 재발급 어떻게 해요?");

        assertThat(response.conditions()).doesNotContainKey("serviceType");
        assertThat(response.disposition()).isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
        verify(llmClient).generate(any());
    }

    @Test
    @DisplayName("LLM 오류로 규칙 기반에 넘어가도 '신촌 근처요'의 신촌을 지역으로 받는다")
    void analyzeFollowUp_keepsPlaceBeforeRelativeWordOnRuleFallback() {
        givenWaitingConsultExists();
        given(llmClient.generate(any()))
                .willThrow(new GeneralException(IntentErrorCode.LLM_CONNECTION_FAILED));

        FollowUpRouteResponse response = service.analyzeFollowUp(SESSION_ID, "신촌 근처요");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.conditions()).containsEntry("location", "신촌");
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
        given(llmClient.generate(any())).willReturn(
                "{\"responseType\":\"NEW_QUESTION\",\"conditions\":[]}");

        FollowUpRouteResponse response =
                service.analyzeFollowUp(SESSION_ID, reply);

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

        FollowUpRouteResponse response =
                service.analyzeFollowUp(SESSION_ID, "5G 요금제는 얼마예요?");

        assertThat(response.method()).isEqualTo(QueryRouting.Method.RULE);
        assertThat(response.disposition())
                .isEqualTo(FollowUpRouteResponse.Disposition.NEW_QUESTION);
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
