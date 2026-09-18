package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

class LlmClarificationTextGeneratorTest {
    private final LlmClient client = mock(LlmClient.class);
    private final DialogueService service =
            new DialogueService(new LlmClarificationTextGenerator(client));

    @Test
    void missingRegionUsesCommonClientAndReplyResumesWithoutAnotherCall() {
        when(client.generate(any())).thenReturn("어느 지역의 매장을 찾으시나요?");
        var first = service.decide(input(Map.of(), Map.of()));
        assertThat(first.action()).isEqualTo(Action.ASK);
        assertThat(first.message()).isEqualTo("어느 지역의 매장을 찾으시나요?");
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.taskType()).isEqualTo(TaskType.CLARIFICATION);
        assertThat(request.format()).isEqualTo(ResponseFormat.TEXT);
        assertThat(request.temperature()).isZero();
        assertThat(request.maxTokens()).isEqualTo(128);
        assertThat(request.userPrompt()).contains("사용자가 거절함");
        assertThat(request.systemPrompt()).contains("위치 권한을 다시 요구");
        var resumed =
                service.decide(
                        input(first.conditions(), Map.of("location", Condition.filled("강남역"))));
        assertThat(resumed.action()).isEqualTo(Action.PROCEED);
        verifyNoMoreInteractions(client);
    }

    @Test
    void connectionFailureUsesTemplate() {
        when(client.generate(any())).thenThrow(new ResourceAccessException("timeout"));
        var result = service.decide(input(Map.of(), Map.of()));
        assertThat(result.action()).isEqualTo(Action.ASK);
        assertThat(result.messageOrigin())
                .isEqualTo(com.telme.consult.dto.DialogueDecision.MessageOrigin.TEMPLATE);
    }

    @Test
    void emptyOutputUsesTemplate() {
        when(client.generate(any())).thenReturn(" ");
        assertThat(service.decide(input(Map.of(), Map.of())).messageOrigin())
                .isEqualTo(com.telme.consult.dto.DialogueDecision.MessageOrigin.TEMPLATE);
    }

    @Test
    void programmingErrorIsNotHiddenAsModelFailure() {
        when(client.generate(any())).thenThrow(new IllegalArgumentException("bad request"));
        assertThatThrownBy(() -> service.decide(input(Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void temporaryServerFailureUsesTemplate() {
        when(client.generate(any()))
                .thenThrow(
                        new org.springframework.web.client.HttpServerErrorException(
                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(service.decide(input(Map.of(), Map.of())).messageOrigin())
                .isEqualTo(com.telme.consult.dto.DialogueDecision.MessageOrigin.TEMPLATE);
    }

    @Test
    void rateLimitUsesTemplate() {
        when(client.generate(any()))
                .thenThrow(
                        new org.springframework.web.client.HttpClientErrorException(
                                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertThat(service.decide(input(Map.of(), Map.of())).messageOrigin())
                .isEqualTo(com.telme.consult.dto.DialogueDecision.MessageOrigin.TEMPLATE);
    }

    @Test
    void invalidModelRequestIsNotHidden() {
        when(client.generate(any()))
                .thenThrow(
                        new org.springframework.web.client.HttpClientErrorException(
                                org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.decide(input(Map.of(), Map.of())))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }

    @Test
    void unrelatedIllegalStateIsNotHidden() {
        when(client.generate(any())).thenThrow(new IllegalStateException("잘못된 클라이언트 설정"));
        assertThatThrownBy(() -> service.decide(input(Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잘못된 클라이언트 설정");
    }

    private DialogueInput input(Map<String, Condition> previous, Map<String, Condition> updates) {
        return new DialogueInput(
                1L, Purpose.NEARBY_STORE, previous, updates, LocationStatus.DECLINED);
    }
}
