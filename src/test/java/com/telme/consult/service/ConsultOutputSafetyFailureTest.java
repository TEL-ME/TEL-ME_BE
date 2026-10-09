package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultOutputSafetyFailureTest {

    @Test
    @DisplayName("출력 검사 추적 기록이 실패해도 안전 안내의 저장과 오류 전달은 계속한다")
    void 추적_실패가_출력_차단_안내를_막지_않는다() {
        var persistence = mock(ConsultChatPersistenceService.class);
        var events = mock(ConsultChatEvents.class);
        var trace = mock(ExecutionTrace.class);
        var candidate = new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "씨발", null, List.of(), null);
        doThrow(new ChatOutputBlockedException("chat-output-guard-v1", "content", List.of("OUTPUT_SIBAL")))
                .when(persistence).persistDirectAnswer(2L, 1L, candidate);
        doThrow(new IllegalStateException("검증용 기록 실패"))
                .when(trace).stage(eq(2L), eq("outputSafety"), any());
        var processor = new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.direct(candidate),
                ignored -> null,
                persistence,
                new ConfirmedConditionConverter(),
                events,
                trace);

        processor.request(new ChatProcessingCommand(2L, 1L, 3L, "문의"));

        ArgumentCaptor<ChatFailure> failure = ArgumentCaptor.forClass(ChatFailure.class);
        verify(persistence).failAnswer(eq(2L), eq(1L), failure.capture());
        assertThat(failure.getValue().errorCode()).isEqualTo("CHAT500-0");
        assertThat(failure.getValue().message()).doesNotContain("씨발");
        verify(events).failed(2L, failure.getValue());
    }
}
