package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.converter.ChatSessionConverter;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.dto.res.ChatSessionCreateResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatExecutionRepository chatExecutionRepository;

    @Mock
    private ChatMessageAppender chatMessageAppender;

    @Mock
    private ChatExecutionProperties chatExecutionProperties;

    @Mock
    private ChatSessionConverter chatSessionConverter;

    @Mock
    private ChatMessageConverter chatMessageConverter;

    @InjectMocks
    private ChatSessionService chatSessionService;

    @Test
    void createsMemberSessionWithNormalizedTitle() {
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionConverter.toCreateResponse(any(ChatSession.class)))
                .thenAnswer(invocation -> {
                    ChatSession session = invocation.getArgument(0);
                    return new ChatSessionCreateResponse(
                            session.getSessionId(), session.getTitle(), session.getStatus().name(),
                            session.getCreatedAt(), session.getLastActiveAt());
                });

        ChatSessionCreateResponse response = chatSessionService.createSession(
                new ChatActor(7L, null),
                new ChatSessionCreateRequest("  요금제 상담  ")
        );

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        ChatSession saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getGuestId()).isNull();
        assertThat(saved.getTitle()).isEqualTo("요금제 상담");
        assertThat(response.title()).isEqualTo("요금제 상담");
    }

    @Test
    void savesUserMessageAndExecutionWithNextSequence() {
        ChatSession session = ChatSession.builder()
                .sessionId(10L)
                .userId(7L)
                .lastActiveAt(Instant.parse("2026-09-17T00:00:00Z"))
                .build();
        when(chatSessionRepository.findMemberSessionByIdForUpdate(10L, 7L)).thenReturn(Optional.of(session));
        when(chatExecutionProperties.runningTimeout()).thenReturn(Duration.ofMinutes(5));
        when(chatMessageAppender.append(eq(session), any(ChatMessage.ChatMessageBuilder.class)))
                .thenAnswer(invocation -> invocation.<ChatMessage.ChatMessageBuilder>getArgument(1)
                        .session(session)
                        .sequenceNo(3)
                        .build());
        when(chatExecutionRepository.save(any(ChatExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMessageConverter.toSendResponse(any(ChatMessage.class), any(ChatExecution.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ChatExecution execution = invocation.getArgument(1);
                    return new ChatMessageSendResponse(
                            message.getSession().getSessionId(), message.getMessageId(), message.getSequenceNo(),
                            execution.getExecutionId(), execution.getStatus().name(), message.getCreatedAt());
                });

        ChatMessageSendResponse response = chatSessionService.sendMessage(
                new ChatActor(7L, null),
                10L,
                new ChatMessageSendRequest("가까운 매장 알려줘")
        );

        ArgumentCaptor<ChatExecution> executionCaptor = ArgumentCaptor.forClass(ChatExecution.class);
        verify(chatExecutionRepository).save(executionCaptor.capture());
        ChatExecution execution = executionCaptor.getValue();
        ChatMessage message = execution.getInputMessage();
        assertThat(message.getSequenceNo()).isEqualTo(3);
        assertThat(message.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(message.getMessageType()).isEqualTo(ChatMessage.MessageType.QUESTION);
        assertThat(message.getStatus()).isEqualTo(ChatMessage.Status.COMPLETED);
        assertThat(message.getCompletedAt()).isNotNull();
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.RUNNING);
        assertThat(session.getLastActiveAt()).isEqualTo(message.getCompletedAt());
        assertThat(response.sequenceNo()).isEqualTo(3);
        assertThat(response.executionStatus()).isEqualTo("RUNNING");
    }

    @Test
    void rejectsMessageToClosedSession() {
        ChatSession session = ChatSession.builder()
                .sessionId(10L)
                .userId(7L)
                .status(ChatSession.Status.CLOSED)
                .build();
        when(chatSessionRepository.findMemberSessionByIdForUpdate(10L, 7L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatSessionService.sendMessage(
                new ChatActor(7L, null),
                10L,
                new ChatMessageSendRequest("질문")
        ))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.SESSION_CLOSED);
    }

    @Test
    void hidesSessionWhenActorIsNotOwner() {
        when(chatSessionRepository.findMemberSessionByIdForUpdate(10L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.closeSession(new ChatActor(7L, null), 10L))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.SESSION_NOT_FOUND);
    }
}
