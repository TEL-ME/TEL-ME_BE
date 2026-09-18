package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.service.OllamaClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

class OllamaClarificationConnectionTest {
    private final RestClient.Builder builder =
            RestClient.builder().baseUrl("http://localhost:11434");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LlmProperties properties =
            new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60));
    private final DialogueService service =
            new DialogueService(
                    new LlmClarificationTextGenerator(
                            new OllamaClient(
                                    builder.build(),
                                    new OllamaRequestConverter(properties),
                                    new ObjectMapper())));

    @Test
    void realClientRequestCreatesQuestionAndFollowupDoesNotCallModelAgain() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(jsonPath("$.model").value("exaone3.5:7.8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_predict").value(128))
                .andRespond(
                        withSuccess(
                                "{\"message\":{\"content\":\"어느 지역의 매장을 찾으시나요?\"},\"done\":true}",
                                MediaType.APPLICATION_JSON));
        var first = service.decide(input(Map.of(), Map.of()));
        assertThat(first.action()).isEqualTo(Action.ASK);
        assertThat(first.messageOrigin()).isEqualTo(MessageOrigin.MODEL);
        var next =
                service.decide(
                        input(first.conditions(), Map.of("location", Condition.filled("강남역"))));
        assertThat(next.action()).isEqualTo(Action.PROCEED);
        assertThat(next.conditions().get("location").value()).isEqualTo("강남역");
        server.verify();
    }

    @Test
    void serverFailureKeepsMissingConditionAndUsesTemplate() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertFallback();
    }

    @Test
    void transportTimeoutUsesTemplateWithoutRetryingQuestion() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withException(new SocketTimeoutException("read timeout")));
        assertFallback();
    }

    @Test
    void completedButBlankModelOutputUsesTemplate() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(
                        withSuccess(
                                "{\"message\":{\"content\":\" \"},\"done\":true}",
                                MediaType.APPLICATION_JSON));
        assertFallback();
    }

    private void assertFallback() {
        var result = service.decide(input(Map.of(), Map.of()));
        assertThat(result.action()).isEqualTo(Action.ASK);
        assertThat(result.waitingField()).isEqualTo("location");
        assertThat(result.messageOrigin()).isEqualTo(MessageOrigin.TEMPLATE);
        assertThat(result.message()).contains("어느 지역");
        assertThat(result.conditions()).doesNotContainKey("location");
        server.verify();
    }

    private DialogueInput input(Map<String, Condition> previous, Map<String, Condition> updates) {
        return new DialogueInput(
                101L, Purpose.NEARBY_STORE, previous, updates, LocationStatus.DECLINED);
    }
}
