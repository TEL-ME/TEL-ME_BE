package com.telme.intent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import com.telme.global.common.code.CommonErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.req.IntentRouteRequest;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.service.QueryRoutingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class IntentControllerTest {

    @Mock
    private QueryRoutingService queryRoutingService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatActorProvider chatActorProvider;

    @Mock
    private ObjectProvider<ChatActorProvider> objectProvider;

    private IntentController intentController;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(objectProvider.getIfAvailable()).thenReturn(chatActorProvider);
        intentController = new IntentController(queryRoutingService, chatMessageRepository, objectProvider);
    }

    @Test
    @DisplayName("존재하지 않는 messageId 요청 시 404 MESSAGE_NOT_FOUND 예외를 던진다")
    void messageNotFound_throws404() {
        IntentRouteRequest req = new IntentRouteRequest(999L, "테스트 질문");
        given(chatMessageRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> intentController.route(null, req))
            .isInstanceOf(GeneralException.class)
            .satisfies(e -> {
                GeneralException ge = (GeneralException) e;
                assertThat(ge.getErrorCode()).isEqualTo(IntentErrorCode.MESSAGE_NOT_FOUND);
            });
    }

    @Test
    @DisplayName("메시지 소유자가 아닌 다른 사용자가 라우팅 요청 시 403 FORBIDDEN 예외를 던진다")
    void forbiddenWhenNotOwner() {
        Long ownerId = 1L;
        Long hackerId = 2L;

        ChatSession session = ChatSession.builder().userId(ownerId).build();
        ChatMessage message = ChatMessage.builder().messageId(10L).session(session).build();

        given(chatMessageRepository.findById(10L)).willReturn(Optional.of(message));

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession httpSession = mock(HttpSession.class);
        given(servletRequest.getSession(false)).willReturn(httpSession);
        given(chatActorProvider.getCurrentActor(servletRequest)).willReturn(new ChatActor(hackerId, null));

        IntentRouteRequest req = new IntentRouteRequest(10L, "테스트 질문");

        assertThatThrownBy(() -> intentController.route(servletRequest, req))
            .isInstanceOf(GeneralException.class)
            .satisfies(e -> {
                GeneralException ge = (GeneralException) e;
                assertThat(ge.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
            });
    }

    @Test
    @DisplayName("정상 요청 시 영속화된 ChatMessage를 서비스로 전달하고 200 응답을 반환한다")
    void successRoute() {
        Long userId = 1L;
        ChatSession session = ChatSession.builder().userId(userId).build();
        ChatMessage message = ChatMessage.builder().messageId(10L).session(session).content("질문").build();

        given(chatMessageRepository.findById(10L)).willReturn(Optional.of(message));

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession httpSession = mock(HttpSession.class);
        given(servletRequest.getSession(false)).willReturn(httpSession);
        given(chatActorProvider.getCurrentActor(servletRequest)).willReturn(new ChatActor(userId, null));

        IntentRouteResponse expectedResponse = new IntentRouteResponse(
            1L, 10L, QueryRouting.Intent.FAQ, "질문 정제",
            BigDecimal.valueOf(0.95), QueryRouting.Method.LLM,
            Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(message)).willReturn(expectedResponse);

        IntentRouteRequest req = new IntentRouteRequest(10L, "질문");
        CustomResponse<IntentRouteResponse> res = intentController.route(servletRequest, req);

        assertThat(res).isNotNull();
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getResult()).isEqualTo(expectedResponse);
    }
}
