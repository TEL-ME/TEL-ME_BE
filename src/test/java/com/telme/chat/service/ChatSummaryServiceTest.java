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
    void doesNotOverwriteNewerSummary() {
        when(store.prepare(request)).thenReturn(Optional.of(snapshot));
        when(llmClient.generate(any())).thenReturn("늦게 끝난 요약");
        when(store.saveIfCurrent(snapshot, "늦게 끝난 요약")).thenReturn(false);

        assertThat(service.summarizeIfNeeded(request)).isFalse();
    }
}
