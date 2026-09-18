package com.telme.intent.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntentConverterTest {

    private IntentConverter converter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        converter = new IntentConverter(objectMapper);
    }

    @Test
    @DisplayName("toJson 및 parseConditions 역직렬화가 상호 일치한다")
    void json_serialization_and_deserialization() {
        Map<String, String> conditions = Map.of("location", "강남역", "serviceType", "USIM_REISSUE");

        String json = converter.toJson(conditions);
        assertThat(json).isNotNull();

        Map<String, String> parsed = converter.parseConditions(json);
        assertThat(parsed).containsEntry("location", "강남역");
        assertThat(parsed).containsEntry("serviceType", "USIM_REISSUE");
    }

    @Test
    @DisplayName("null 또는 빈 Map을 toJson에 전달하면 null을 반환한다")
    void toJson_empty_returns_null() {
        assertThat(converter.toJson(null)).isNull();
        assertThat(converter.toJson(Collections.emptyMap())).isNull();
    }

    @Test
    @DisplayName("직렬화 실패 시 toJson은 IllegalArgumentException을 던진다")
    void toJson_failure_throws_exception() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(failingMapper.writeValueAsString(org.mockito.Mockito.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "error"));
        IntentConverter failingConverter = new IntentConverter(failingMapper);

        assertThatThrownBy(() -> failingConverter.toJson(Map.of("key", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조건 JSON 직렬화 실패");
    }

    @Test
    @DisplayName("null 또는 빈 문자열을 parseConditions에 전달하면 빈 Map을 반환한다")
    void parseConditions_empty_returns_empty_map() {
        assertThat(converter.parseConditions(null)).isEmpty();
        assertThat(converter.parseConditions("")).isEmpty();
        assertThat(converter.parseConditions("   ")).isEmpty();
    }

    @Test
    @DisplayName("깨진 JSON 또는 부적절한 구조가 parseConditions에 전달되면 IllegalArgumentException을 던진다")
    void parseConditions_invalid_json_throws_exception() {
        assertThatThrownBy(() -> converter.parseConditions("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조건 JSON 역직렬화 실패");

        assertThatThrownBy(() -> converter.parseConditions("{\"a\":{\"b\":1}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조건 JSON 역직렬화 실패");
    }

    @Test
    @DisplayName("ChatMessage와 LlmRoutingPayload로부터 QueryRouting 엔티티를 정상 변환한다")
    void toQueryRouting() {
        ChatMessage message = ChatMessage.builder()
                .messageId(100L)
                .content("로밍 요금제 가입 방법")
                .build();

        LlmRoutingPayload payload = new LlmRoutingPayload(
                QueryRouting.Intent.FAQ,
                BigDecimal.valueOf(0.95),
                "로밍 요금제 가입 안내",
                Map.of("serviceType", "ROAMING"),
                Collections.emptyList()
        );

        QueryRouting routing = converter.toQueryRouting(message, payload, QueryRouting.Method.LLM);

        assertThat(routing.getMessage()).isEqualTo(message);
        assertThat(routing.getIntent()).isEqualTo(QueryRouting.Intent.FAQ);
        assertThat(routing.getRefinedQuery()).isEqualTo("로밍 요금제 가입 안내");
        assertThat(routing.getMethod()).isEqualTo(QueryRouting.Method.LLM);
        assertThat(routing.getConfidence()).isEqualTo(BigDecimal.valueOf(0.95));
    }

    @Test
    @DisplayName("ConsultRequest와 조건들로부터 IntentSubQueryResponse DTO를 정상 변환한다")
    void toSubQueryResponse() {
        ChatSession session = ChatSession.builder().sessionId(1L).build();
        ChatMessage message = ChatMessage.builder().messageId(100L).session(session).build();

        ConsultRequest consultRequest = converter.toConsultRequest(
                message, (short) 1, ConsultRequest.Intent.STORE, "강남역 직영점 위치"
        );

        Map<String, String> conditions = Map.of("location", "강남역");
        IntentSubQueryResponse response = converter.toSubQueryResponse(
                consultRequest, (short) 1, ConsultRequest.Intent.STORE, "강남역 직영점 위치", conditions
        );

        assertThat(response.order()).isEqualTo((short) 1);
        assertThat(response.intent()).isEqualTo(ConsultRequest.Intent.STORE);
        assertThat(response.queryText()).isEqualTo("강남역 직영점 위치");
        assertThat(response.conditions()).containsEntry("location", "강남역");
    }

    @Test
    @DisplayName("toConsultCondition은 conditionValue가 있으면 FILLED, 없으면 PENDING 상태로 생성한다")
    void toConsultCondition_status() {
        ConsultRequest request = ConsultRequest.builder().consultRequestId(1L).build();

        ConsultCondition filledCondition = converter.toConsultCondition(request, "location", "강남역");
        assertThat(filledCondition.getStatus()).isEqualTo(ConsultCondition.Status.FILLED);
        assertThat(filledCondition.getSource()).isEqualTo(ConsultCondition.Source.EXTRACTED);

        ConsultCondition pendingCondition = converter.toConsultCondition(request, "location", null);
        assertThat(pendingCondition.getStatus()).isEqualTo(ConsultCondition.Status.PENDING);
    }
}
