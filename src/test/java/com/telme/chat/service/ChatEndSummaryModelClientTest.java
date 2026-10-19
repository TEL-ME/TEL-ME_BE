package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.BedrockTemporaryException;
import com.telme.llm.service.LlmAttemptClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class ChatEndSummaryModelClientTest {
    private final LlmAttemptClient bedrock = mock(LlmAttemptClient.class);
    private final LlmAttemptClient fallback = mock(LlmAttemptClient.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-17T01:00:00Z");
    private final AtomicReference<Instant> time = new AtomicReference<>(now);
    private final ChatEndSummaryModelClient client = new ChatEndSummaryModelClient(bedrock, fallback, clock,
            Duration.ofSeconds(8), Duration.ofSeconds(65));

    @BeforeEach
    void 시각을_준비한다() {
        when(clock.instant()).thenAnswer(invocation -> time.get());
    }

    @Test
    @DisplayName("베드락 성공 시 공통 프롬프트를 그대로 전달하고 엑사원을 호출하지 않는다")
    void 정상_응답은_베드락만_호출한다() {
        when(bedrock.generate(any(), eq(1))).thenReturn("정상 응답");
        var original = request();

        assertThat(client.generate(original, now.plusSeconds(300))).isEqualTo("정상 응답");

        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(bedrock).generate(captor.capture(), eq(1));
        var sent = captor.getValue();
        assertThat(sent.systemPrompt()).isEqualTo(original.systemPrompt());
        assertThat(sent.userPrompt()).isEqualTo(original.userPrompt());
        assertThat(sent.executionId()).isEqualTo(original.executionId());
        assertThat(sent.consultRequestId()).isEqualTo(original.consultRequestId());
        assertThat(sent.maxTokens()).isEqualTo(768);
        assertThat(sent.format()).isEqualTo(ResponseFormat.TEXT);
        assertThat(sent.responseSchema()).isNull();
        assertThat(original.responseSchema()).isNotNull();
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("베드락 시간 초과 때만 원래 스키마와 입력을 유지해 엑사원으로 한 번 대체한다")
    void 시간_초과는_같은_입력으로_한_번만_대체한다() {
        when(bedrock.generate(any(), eq(1))).thenThrow(new GeneralException(LlmErrorCode.TIMEOUT));
        when(fallback.generate(any(), eq(2))).thenReturn("대체 응답");
        var original = request();

        assertThat(client.generate(original, now.plusSeconds(300))).isEqualTo("대체 응답");

        verify(bedrock, times(1)).generate(any(), eq(1));
        verify(fallback, times(1)).generate(original, 2);
    }

    @ParameterizedTest
    @EnumSource(value = LlmErrorCode.class, names = {"CONNECTION_FAILED", "MODEL_ERROR"})
    @DisplayName("분류된 일시 장애만 같은 마스킹 입력과 스키마로 한 번 대체한다")
    void 확인된_일시_장애만_대체한다(LlmErrorCode code) {
        when(bedrock.generate(any(), eq(1))).thenThrow(new BedrockTemporaryException(code));
        when(fallback.generate(any(), eq(2))).thenReturn("대체 응답");
        var original = request();

        assertThat(client.generate(original, now.plusSeconds(300))).isEqualTo("대체 응답");

        verify(bedrock, times(1)).generate(any(), eq(1));
        verify(fallback, times(1)).generate(original, 2);
    }

    @Test
    @DisplayName("분류된 일시 장애라도 대체 모델이 실패하면 반복하거나 주 모델로 돌아가지 않는다")
    void 일시_장애의_대체_실패도_반복하지_않는다() {
        when(bedrock.generate(any(), eq(1)))
                .thenThrow(new BedrockTemporaryException(LlmErrorCode.CONNECTION_FAILED));
        var failure = new GeneralException(LlmErrorCode.MODEL_ERROR);
        when(fallback.generate(any(), eq(2))).thenThrow(failure);

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(300))).isSameAs(failure);

        verify(bedrock, times(1)).generate(any(), eq(1));
        verify(fallback, times(1)).generate(any(), eq(2));
    }

    @Test
    @DisplayName("일시 장애 후에도 대체 예산이 부족하면 엑사원을 호출하지 않는다")
    void 일시_장애의_남은_예산도_확인한다() {
        when(bedrock.generate(any(), eq(1)))
                .thenThrow(new BedrockTemporaryException(LlmErrorCode.CONNECTION_FAILED));

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(64)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("WORK_LIMIT");

        verifyNoInteractions(fallback);
    }

    @ParameterizedTest
    @EnumSource(value = LlmErrorCode.class,
            names = {"INVALID_RESPONSE", "MODEL_ERROR", "CONNECTION_FAILED", "STREAM_INTERRUPTED"})
    @DisplayName("일시 장애로 분류되지 않은 연결·모델·출력 오류는 엑사원으로 우회하지 않는다")
    void 다른_오류는_대체하지_않는다(LlmErrorCode code) {
        var failure = new GeneralException(code);
        when(bedrock.generate(any(), eq(1))).thenThrow(failure);

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(300))).isSameAs(failure);

        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("알 수 없는 예외도 엑사원으로 우회하지 않는다")
    void 알_수_없는_오류는_대체하지_않는다() {
        var failure = new IllegalStateException("합성 오류");
        when(bedrock.generate(any(), eq(1))).thenThrow(failure);

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(300))).isSameAs(failure);

        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("엑사원도 시간 초과가 나면 베드락으로 돌아가거나 반복하지 않는다")
    void 대체_실패는_반복하지_않는다() {
        when(bedrock.generate(any(), eq(1))).thenThrow(new GeneralException(LlmErrorCode.TIMEOUT));
        var failure = new GeneralException(LlmErrorCode.TIMEOUT);
        when(fallback.generate(any(), eq(2))).thenThrow(failure);

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(300))).isSameAs(failure);

        verify(bedrock, times(1)).generate(any(), eq(1));
        verify(fallback, times(1)).generate(any(), eq(2));
    }

    @Test
    @DisplayName("작업 기한이 주 모델의 호출 예산보다 짧으면 모델을 부르지 않는다")
    void 주_모델_호출_예산이_없으면_중단한다() {
        assertThatThrownBy(() -> client.generate(request(), now.plusMillis(7999)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("WORK_LIMIT");

        verifyNoInteractions(bedrock, fallback);
    }

    @Test
    @DisplayName("시간 초과 후 대체 호출 예산이 남지 않으면 엑사원을 부르지 않는다")
    void 대체_호출_예산이_없으면_중단한다() {
        when(bedrock.generate(any(), eq(1))).thenAnswer(invocation -> {
            time.set(now.plusSeconds(8));
            throw new GeneralException(LlmErrorCode.TIMEOUT);
        });

        assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(72)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("WORK_LIMIT");

        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("남은 시간이 대체 호출 예산과 같으면 한 번 대체할 수 있다")
    void 대체_호출_예산_경계를_보존한다() {
        when(bedrock.generate(any(), eq(1))).thenAnswer(invocation -> {
            time.set(now.plusSeconds(8));
            throw new GeneralException(LlmErrorCode.TIMEOUT);
        });
        when(fallback.generate(any(), eq(2))).thenReturn("대체 응답");

        assertThat(client.generate(request(), now.plusSeconds(73))).isEqualTo("대체 응답");
    }

    @Test
    @DisplayName("중단된 작업은 시간 초과가 있어도 대체 호출을 시작하지 않는다")
    void 작업_중단은_대체하지_않는다() {
        when(bedrock.generate(any(), eq(1))).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new GeneralException(LlmErrorCode.TIMEOUT);
        });
        try {
            assertThatThrownBy(() -> client.generate(request(), now.plusSeconds(300)))
                    .isInstanceOfSatisfying(GeneralException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(LlmErrorCode.STREAM_INTERRUPTED));
            verifyNoInteractions(fallback);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"SESSION_TITLE", "RAG_ANSWER", "EVIDENCE_CHECK"})
    @DisplayName("제목·답변·판정 작업을 요약 전용 모델로 전달하지 않는다")
    void 다른_기능은_요약_모델을_사용하지_않는다(TaskType task) {
        var other = LlmRequest.builder().taskType(task).userPrompt("다른 작업").build();

        assertThatThrownBy(() -> client.generate(other, now.plusSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(bedrock, fallback);
    }

    private LlmRequest request() {
        return LlmRequest.builder().executionId(10L).consultRequestId(20L).taskType(TaskType.SUMMARY)
                .systemPrompt("공통 한국어 요약 규칙").userPrompt("마스킹된 상담 기록")
                .temperature(0.0).maxTokens(768).promptVersion("consultation-end-summary-v14")
                .responseSchema(Map.of("type", "object")).think(false).build();
    }
}
