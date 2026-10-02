package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class ChatExecutionTraceServiceTest {
    @Test
    void traceFailureDoesNotThrowOrLogUserValuesAndDatabaseErrorText(CapturedOutput output) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new DataAccessResourceFailureException("PRIVATE_ERROR_TEXT"))
                .when(jdbc).update(anyString(), any(Object[].class));
        var traces = new ChatExecutionTraceService(jdbc, new ObjectMapper(), null);

        assertThatCode(() -> traces.stage(1L, "analysis", Map.of("query", "PRIVATE_USER_QUERY")))
                .doesNotThrowAnyException();
        assertThatCode(() -> traces.append(1L, "searchResults", Map.of("answer", "PRIVATE_FAQ")))
                .doesNotThrowAnyException();
        assertThat(output.getAll()).contains("Execution trace write failed")
                .doesNotContain("PRIVATE_USER_QUERY", "PRIVATE_FAQ", "PRIVATE_ERROR_TEXT");
    }
}
