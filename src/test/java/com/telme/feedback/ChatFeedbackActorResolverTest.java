package com.telme.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.feedback.api.ChatFeedbackActorResolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

class ChatFeedbackActorResolverTest {
    private final ChatActorProvider provider = mock(ChatActorProvider.class);
    private final ChatFeedbackActorResolver resolver = new ChatFeedbackActorResolver(provider);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void usesVerifiedMemberInsteadOfRequestParameter() {
        request.setParameter("userId", "999");
        when(provider.getCurrentActor(request)).thenReturn(new ChatActor(1L, null));
        assertEquals(1L, resolver.resolve(request).userId());
    }

    @Test
    void preservesVerifiedGuest() {
        var id = UUID.randomUUID();
        when(provider.getCurrentActor(request)).thenReturn(new ChatActor(null, id));
        assertEquals(id, resolver.resolve(request).guestId());
    }

    @Test
    void unverifiedIdentityCannotBecomeFeedbackAuthor() {
        request.setParameter("userId", "1");
        assertNull(resolver.resolve(request));
    }

    @Test
    void identityValidationFailureIsNotIgnored() {
        when(provider.getCurrentActor(request)).thenThrow(new IllegalStateException("expired"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(request));
    }
    @Test
    void memberIdentityWinsWhenPreviousGuestIdRemains() {
        when(provider.getCurrentActor(request)).thenReturn(new ChatActor(1L, UUID.randomUUID()));
        var actor = resolver.resolve(request);
        assertEquals(1L, actor.userId());
        assertNull(actor.guestId());
    }
}
