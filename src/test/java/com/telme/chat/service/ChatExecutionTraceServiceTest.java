package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatExecutionTraceRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class ChatExecutionTraceServiceTest {
    @Test
    void traceFailureDoesNotThrowOrLogUserValuesAndDatabaseErrorText(CapturedOutput output) {
        var repository = mock(ChatExecutionTraceRepository.class);
        doThrow(new DataAccessResourceFailureException("PRIVATE_ERROR_TEXT"))
                .when(repository).updateStage(anyLong(), anyString(), anyString(), anyBoolean());
        var traces = new ChatExecutionTraceService(repository, new ObjectMapper(), null, null);

        assertThatCode(() -> traces.stage(1L, "analysis", Map.of("query", "PRIVATE_USER_QUERY")))
                .doesNotThrowAnyException();
        assertThatCode(() -> traces.append(1L, "searchResults", Map.of("answer", "PRIVATE_FAQ")))
                .doesNotThrowAnyException();
        assertThat(output.getAll()).contains("Execution trace write failed")
                .doesNotContain("PRIVATE_USER_QUERY", "PRIVATE_FAQ", "PRIVATE_ERROR_TEXT");
    }

    @Test
    void missingExecutionIdDoesNotWriteAnyRecord() {
        var repository = mock(ChatExecutionTraceRepository.class);
        var traces = new ChatExecutionTraceService(repository, new ObjectMapper(), null, null);

        traces.stage(null, "analysis", Map.of("query", "ignored"));
        traces.append(null, "searchResults", Map.of("status", "EMPTY"));

        verifyNoInteractions(repository);
    }
}
