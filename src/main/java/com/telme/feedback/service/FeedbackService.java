package com.telme.feedback.service;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.repository.FeedbackStore;

import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/** 컨트롤러·인증 어댑터 합의 후 Bean으로 등록한다. 수정/취소·게스트 허용은 초기 제안이다. */
@Transactional(readOnly = true)
public class FeedbackService {
    private final FeedbackStore store;

    public FeedbackService(FeedbackStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Transactional
    public Feedback save(long messageId, Actor actor, Input input) {
        validate(messageId, actor);
        return store.upsert(messageId, actor, Objects.requireNonNull(input));
    }

    public Optional<Feedback> get(long messageId, Actor actor) {
        validate(messageId, actor);
        return store.find(messageId, actor);
    }

    @Transactional
    public void cancel(long messageId, Actor actor) {
        validate(messageId, actor);
        store.delete(messageId, actor);
    }

    private void validate(long id, Actor actor) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid message id");
        }
        Objects.requireNonNull(actor, "authenticated actor");
    }
}
