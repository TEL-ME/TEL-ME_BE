package com.telme.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.telme.feedback.dto.FeedbackModels.*;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.feedback.service.FeedbackService;

import org.junit.jupiter.api.Test;

/** 잘못된 입력이 DB 처리로 전달되지 않는지 검증한다. */
class FeedbackServiceTest {
    private final FeedbackStore store = mock(FeedbackStore.class);
    private final FeedbackService service = new FeedbackService(store);
    private final Actor owner = new Actor(1L, null);
    private final Input like = new Input(Rating.LIKE, null, null);

    @Test
    void invalidMessageIdsNeverReachDatabase() {
        for (long id : new long[] {0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> service.save(id, owner, like));
            assertThrows(IllegalArgumentException.class, () -> service.get(id, owner));
            assertThrows(IllegalArgumentException.class, () -> service.cancel(id, owner));
        }
        verifyNoInteractions(store);
    }

    @Test
    void missingActorNeverReachesDatabase() {
        assertThrows(NullPointerException.class, () -> service.save(1L, null, like));
        assertThrows(NullPointerException.class, () -> service.get(1L, null));
        assertThrows(NullPointerException.class, () -> service.cancel(1L, null));
        verifyNoInteractions(store);
    }

    @Test
    void missingFeedbackInputNeverReachesDatabase() {
        assertThrows(NullPointerException.class, () -> service.save(1L, owner, null));
        verifyNoInteractions(store);
    }
}
