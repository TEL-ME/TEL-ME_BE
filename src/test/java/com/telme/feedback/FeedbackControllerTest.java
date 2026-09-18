package com.telme.feedback;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.telme.feedback.api.*;
import com.telme.feedback.controller.FeedbackController;
import com.telme.feedback.converter.FeedbackConverter;
import com.telme.feedback.dto.FeedbackModels.*;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.feedback.service.FeedbackService;
import com.telme.global.common.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

/** HTTP 계약 테스트. 실제 회원 인증 필터 검증은 통합 시 추가한다. */
class FeedbackControllerTest {
    FeedbackService service;
    VerifiedFeedbackActorResolver resolver;
    MockMvc mvc;
    final Actor actor = new Actor(1L, null);
    final String url = "/api/v1/chat/messages/10/feedback";

    @BeforeEach
    void setup() {
        service = mock(FeedbackService.class);
        resolver = mock(VerifiedFeedbackActorResolver.class);
        when(resolver.resolve(any())).thenReturn(actor);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new FeedbackController(service, resolver, new FeedbackConverter()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void saveUsesVerifiedIdentityAndCommonEnvelope() throws Exception {
        when(service.save(eq(10L), eq(actor), any()))
                .thenAnswer(
                        inv ->
                                new Feedback(
                                        20L,
                                        10L,
                                        actor,
                                        inv.getArgument(2),
                                        Instant.EPOCH,
                                        Instant.EPOCH));
        mvc.perform(
                        put(url).contentType("application/json")
                                .content("{\"rating\":\"LIKE\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.id").value(20))
                .andExpect(jsonPath("$.result.actor").doesNotExist());
        verify(service).save(eq(10L), eq(actor), any());
    }

    @Test
    void missingIdentityIsRejectedBeforeService() throws Exception {
        when(resolver.resolve(any())).thenReturn(null);
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void wrongOwnerIsNotFound() throws Exception {
        when(service.get(10L, actor)).thenThrow(new FeedbackStore.TargetUnavailable());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void noFeedbackIsSuccessfulNull() throws Exception {
        when(service.get(10L, actor)).thenReturn(Optional.empty());
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void invalidRatingMissingRatingAndInconsistentReasonAreBadRequests() throws Exception {
        for (String body :
                new String[] {
                    "{}",
                    "{\"rating\":\"OTHER\"}",
                    "{\"rating\":\"LIKE\",\"reason\":\"WRONG_INFO\"}"
                })
            mvc.perform(put(url).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidMessageIdRejected() throws Exception {
        mvc.perform(get("/api/v1/chat/messages/0/feedback")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/messages/not-a-number/feedback"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void incompleteAnswerConflicts() throws Exception {
        when(service.save(eq(10L), eq(actor), any())).thenThrow(new FeedbackStore.TargetNotReady());
        mvc.perform(put(url).contentType("application/json").content("{\"rating\":\"LIKE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK409-0"));
    }

    @Test
    void cancelUsesVerifiedIdentity() throws Exception {
        mvc.perform(delete(url)).andExpect(status().isOk());
        verify(service).cancel(10L, actor);
    }
}
