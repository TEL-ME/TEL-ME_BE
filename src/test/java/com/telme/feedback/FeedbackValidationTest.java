package com.telme.feedback;

import static org.junit.jupiter.api.Assertions.*;

import com.telme.feedback.dto.FeedbackModels.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class FeedbackValidationTest {
    @Test
    void actorMustBeExactlyOneVerifiedIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new Actor(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Actor(1L, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new Actor(0L, null));
    }

    @Test
    void validatesRatingReasonAndComment() {
        assertThrows(NullPointerException.class, () -> new Input(null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Input(Rating.LIKE, Reason.WRONG_INFO, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Input(Rating.DISLIKE, null, "가".repeat(1001)));
        assertNull(new Input(Rating.LIKE, null, "  ").comment());
    }
}
