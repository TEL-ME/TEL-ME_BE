package com.telme.feedback.service;

import com.telme.feedback.dto.FeedbackModels.*;
import com.telme.feedback.repository.FeedbackStore;

import java.util.Objects;
import java.util.Optional;

/** 컨트롤러·인증 어댑터 합의 후 Bean으로 등록한다. 수정/취소·게스트 허용은 초기 제안이다. */
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class FeedbackService {
    private final FeedbackStore store;

    public FeedbackService(FeedbackStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @org.springframework.transaction.annotation.Transactional
    public Feedback save(long messageId, Actor actor, Input input) {
        validate(messageId, actor);
        return store.upsert(messageId, actor, Objects.requireNonNull(input));
    }

    // 소유권 확인 시 행을 잠그므로 읽기 전용으로 실행하지 않는다.
    @org.springframework.transaction.annotation.Transactional
    public Optional<Feedback> get(long messageId, Actor actor) {
        validate(messageId, actor);
        return store.find(messageId, actor);
    }

    @org.springframework.transaction.annotation.Transactional
    public void cancel(long messageId, Actor actor) {
        validate(messageId, actor);
        store.delete(messageId, actor);
    }

    private void validate(long id, Actor actor) {
        if (id <= 0) throw new IllegalArgumentException("Invalid message id");
        Objects.requireNonNull(actor, "authenticated actor");
    }
}
