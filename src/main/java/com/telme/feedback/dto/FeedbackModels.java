package com.telme.feedback.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** HTTP 요청·응답 DTO와 구분해 피드백 내부 모델을 한곳에 모은다. Actor는 본문에서 받지 않는다. */
public final class FeedbackModels {
    private FeedbackModels() {}

    public enum Rating {
        LIKE,
        DISLIKE
    }

    public enum Reason {
        WRONG_INFO,
        NOT_RELATED,
        HARD_TO_READ
    }

    public record Actor(Long userId, UUID guestId) {
        public Actor {
            if ((userId == null) == (guestId == null)) {
                throw new IllegalArgumentException("Exactly one actor required");
            }
            if (userId != null && userId <= 0) {
                throw new IllegalArgumentException("Invalid user id");
            }
        }
    }

    public record Input(Rating rating, Reason reason, String comment) {
        public Input {
            Objects.requireNonNull(rating, "rating");
            if (rating == Rating.LIKE && reason != null) {
                throw new IllegalArgumentException("Reason is for DISLIKE only");
            }
            if (comment != null) {
                comment = comment.strip();
                if (comment.length() > 1000) {
                    throw new IllegalArgumentException("Comment exceeds 1000 characters");
                }
                if (comment.isEmpty()) {
                    comment = null;
                }
            }
        }
    }

    public record Feedback(
            Long id,
            Long messageId,
            Actor actor,
            Input input,
            Instant createdAt,
            Instant updatedAt) {}
}
