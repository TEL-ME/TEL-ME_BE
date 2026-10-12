package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatSummaryServiceTest {

    private final ChatSummaryStore store = mock(ChatSummaryStore.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ChatSummaryProperties properties =
            new ChatSummaryProperties(16, 2_048, 8, 1_024, 16, 3_072, 512);
    private final ChatSummaryService service = new ChatSummaryService(store, llmClient, properties);
    private ChatSummaryRequested request;
    private ChatSummarySnapshot snapshot;

    @BeforeEach
    void setUp() {
        request = new ChatSummaryRequested(10L, 20L, 6);
        snapshot = new ChatSummarySnapshot(
                10L,
                20L,
                "기존 요약",
                4,
                6,
                List.of(
                        new ChatContextMessage(
                                5L, 5, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                                "강남역 매장을 찾고 있어", null),
                        new ChatContextMessage(
                                6L, 6, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                                "강남역 인근 매장을 안내했어요", null)
                )
        );
    }

    @Test
    void generatesAndSavesSummary() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("  갱신된 요약  ");
        when(store.saveIfCurrent(snapshot, "갱신된 요약")).thenReturn(true);

        assertThat(service.summarizeIfNeeded(request)).isTrue();

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(requestCaptor.capture());
        LlmRequest sent = requestCaptor.getValue();
        assertThat(sent.executionId()).isEqualTo(10L);
        assertThat(sent.taskType()).isEqualTo(TaskType.SUMMARY);
        assertThat(sent.maxTokens()).isEqualTo(512);
        assertThat(sent.userPrompt())
                .contains("기존 요약")
                .contains("강남역 매장을 찾고 있어")
                .contains("강남역 인근 매장을 안내했어요");
        verify(store).saveIfCurrent(snapshot, "갱신된 요약");
    }

    @Test
    void skipsLlmWhenSummaryIsNotNeeded() {
        when(store.prepare(request)).thenReturn(Optional.empty());

        assertThat(service.summarizeIfNeeded(request)).isFalse();

        verify(llmClient, never()).generate(any());
    }

    @Test
    void rejectsBlankLlmResponseWithoutMovingCursor() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn(" ");

        assertThatThrownBy(() -> service.summarizeIfNeeded(request))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
        verify(store, never()).saveIfCurrent(any(), any());
    }

    @Test
    void normalizesLlmOutputBeforeSaving() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("""
                <previous_summary>
                - **고객 요구**: 가족 결합 할인 확인
                - [미해결 질문](https://example.com): 할인 금액 확인 필요
                </previous_summary>
                """);
        when(store.saveIfCurrent(
                snapshot,
                "고객 요구: 가족 결합 할인 확인 미해결 질문: 할인 금액 확인 필요"
        )).thenReturn(true);

        assertThat(service.summarizeIfNeeded(request)).isTrue();

        verify(store).saveIfCurrent(
                snapshot,
                "고객 요구: 가족 결합 할인 확인 미해결 질문: 할인 금액 확인 필요"
        );
    }

    @Test
    void rejectsResponseContainingOnlyFormattingWithoutMovingCursor() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("""
                <summary>
                ```text
                ```
                </summary>
                """);

        assertThatThrownBy(() -> service.summarizeIfNeeded(request))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
        verify(store, never()).saveIfCurrent(any(), any());
    }

    @Test
    void doesNotOverwriteNewerSummary() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("늦게 끝난 요약");
        when(store.saveIfCurrent(snapshot, "늦게 끝난 요약")).thenReturn(false);

        assertThat(service.summarizeIfNeeded(request)).isFalse();
    }

    @Test
    void invalidGroundedSelectionCannotMoveTheCursor() {
        var grounded = new ChatSummaryService(store, llmClient,
                new ChatSummaryProperties(16, 2048, 8, 1024, 16, 3072, 512, true));
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("{\"messageIds\":[999]}");
        assertThatThrownBy(() -> grounded.summarizeIfNeeded(request)).isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).saveIfCurrent(any(), any());
    }

    @Test
    void socialOnlyGroundedBatchAdvancesAtomicallyWithoutModelCall() {
        var socialSnapshot = new ChatSummarySnapshot(10L, 20L, null, 0, 2, List.of(
                new ChatContextMessage(1L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                        "감사합니다", null),
                new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER,
                        "천만에요", null)));
        var grounded = new ChatSummaryService(store, llmClient,
                new ChatSummaryProperties(16, 2048, 8, 1024, 16, 3072, 512, true));
        when(store.prepare(request)).thenReturn(Optional.of(socialSnapshot));
        when(store.saveIfCurrent(any(), any())).thenReturn(true);
        assertThat(grounded.summarizeIfNeeded(request)).isTrue();
        verify(llmClient, never()).generate(any());
        verify(store).saveIfCurrent(org.mockito.ArgumentMatchers.eq(socialSnapshot), any());
    }

    @Test
    void excessiveValidSelectionSavesBudgetedOriginalsWithoutAnotherModelCall() {
        var old = new ChatContextMessage(1L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "부모님 명의예요.".repeat(20), null);
        var corrected = new ChatContextMessage(3L, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "제 명의이고 아직 변경하지 않았어요.", null);
        var prepared = new ChatSummarySnapshot(10L, 20L, null, 0, 4, List.of(old, corrected));
        var grounded = new ChatSummaryService(store, llmClient,
                new ChatSummaryProperties(16, 2048, 8, 1024, 16, 3072, 80, true));
        when(store.prepare(request)).thenReturn(Optional.of(prepared));
        when(llmClient.generate(any())).thenReturn("{\"messageIds\":[1,3]}");
        when(store.saveIfCurrent(any(), any())).thenReturn(true);
        assertThat(grounded.summarizeIfNeeded(request)).isTrue();
        var saved = ArgumentCaptor.forClass(String.class);
        verify(store).saveIfCurrent(org.mockito.ArgumentMatchers.eq(prepared), saved.capture());
        var converter = new com.telme.chat.converter.ChatSummaryConverter(
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(converter.sources(saved.getValue())).containsExactly(corrected);
        verify(llmClient).generate(any());
    }

    @Test
    void aBatchWhoseOversizedOriginalsWereExcludedCanAdvanceWithoutInventingFacts() {
        var prepared = new ChatSummarySnapshot(10L, 20L, null, 0, 2, List.of());
        var grounded = new ChatSummaryService(store, llmClient,
                new ChatSummaryProperties(16, 2048, 8, 1024, 16, 3072, 512, true));
        when(store.prepare(request)).thenReturn(Optional.of(prepared));
        when(store.saveIfCurrent(any(), any())).thenReturn(true);
        assertThat(grounded.summarizeIfNeeded(request)).isTrue();
        verify(llmClient, never()).generate(any());
        verify(store).saveIfCurrent(org.mockito.ArgumentMatchers.eq(prepared), any());
    }
}
