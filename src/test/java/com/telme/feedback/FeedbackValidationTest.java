package com.telme.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;
import com.telme.feedback.entity.MessageFeedback;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
                () -> new Input(Rating.DISLIKE, Reason.WRONG_INFO, "가".repeat(1001)));
        assertThrows(IllegalArgumentException.class, () -> new Input(Rating.DISLIKE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Input(Rating.LIKE, null, "좋아요"));
        assertNull(new Input(Rating.LIKE, null, "  ").comment());
    }
    
    @Test
    void reasonEnumsStayInSync() {
        assertThat(Arrays.stream(MessageFeedback.ReasonCode.values()).map(Enum::name))
                .containsExactlyElementsOf(Arrays.stream(Reason.values()).map(Enum::name).toList());
    }
}
