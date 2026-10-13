package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

class ChatSessionEndSummaryServiceTest {
    private final ChatSessionEndSummaryStore store = mock(ChatSessionEndSummaryStore.class);
    private final ChatSessionEndSummaryInputReader reader = mock(ChatSessionEndSummaryInputReader.class);
    private final ChatSessionEndSummaryGenerator generator = mock(ChatSessionEndSummaryGenerator.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(ChatSessionEndSummaryService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final ChatEndSummaryData.Input input = new ChatEndSummaryData.Input(1, 1, 10L, List.of(
            new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "합성 상담 문의", null, "COMPLETED")),
            List.of(), Set.of());
    private final ChatEndSummaryData.Lease lease = new ChatEndSummaryData.Lease(1, UUID.randomUUID(),
            Instant.parse("2026-10-13T08:00:00Z"));
    private ChatSessionEndSummaryService service;

    @BeforeEach
    void 생성_실패_검증을_준비한다() {
        logs.start();
        logger.addAppender(logs);
        when(store.claim(1)).thenReturn(Optional.of(lease));
        when(reader.read(1)).thenReturn(input);
        var properties = new ChatEndSummaryProperties(true, false, false, 3072, 768, 400, 8, 2,
                Duration.ofMinutes(5), Duration.ofMinutes(15));
        service = new ChatSessionEndSummaryService(store, reader, generator, properties,
                mock(ApplicationEventPublisher.class));
    }

    @AfterEach
    void 로그_수집기를_정리한다() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("생성 오류 로그에는 세션 번호와 예외 종류만 남기고 본문·원인·스택은 남기지 않는다")
    void 민감정보가_든_예외도_진단_식별자만_기록한다() {
        var failure = new IllegalStateException("카드 4111111111111111 합성 오류",
                new RuntimeException("주민번호 990101-1234567 합성 원인"));
        doThrow(failure).when(generator).generate(input, lease.deadline());

        service.process(1);

        verify(store).finish(lease, input, null, "FAILED", "GENERATION_FAILED");
        assertThat(logs.list).hasSize(1);
        var event = logs.list.getFirst();
        assertThat(event.getFormattedMessage()).contains("sessionId=1", "exceptionType=IllegalStateException")
                .doesNotContain("4111111111111111", "990101-1234567", "합성 오류", "합성 원인");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getArgumentArray()).containsExactly(1L, "IllegalStateException");
    }

    @Test
    @DisplayName("모델 도메인 예외도 종류만 기록하고 기존 생성 실패 상태를 유지한다")
    void 모델_오류의_예외_종류를_확인할_수_있다() {
        doThrow(new GeneralException(LlmErrorCode.INVALID_RESPONSE)).when(generator)
                .generate(input, lease.deadline());

        service.process(1);

        verify(store).finish(lease, input, null, "FAILED", "GENERATION_FAILED");
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains("exceptionType=GeneralException");
        assertThat(logs.list.getFirst().getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("입력 한도 실패의 분류를 일반 생성 오류로 바꾸지 않는다")
    void 입력_한도_실패_분류를_유지한다() {
        doThrow(new IllegalArgumentException("INPUT_LIMIT")).when(generator).generate(input, lease.deadline());

        service.process(1);

        verify(store).finish(lease, input, null, "FAILED", "INPUT_LIMIT");
        assertThat(logs.list).isEmpty();
    }
}
