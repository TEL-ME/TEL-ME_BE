package com.telme.feedback.service;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.global.common.exception.GeneralException;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 인증 어댑터가 있을 때만 설정에서 Bean으로 등록한다. 회원의 평가 등록과, 회원·비회원의 평가 조회·취소를 다룬다 */
@Transactional(readOnly = true)
public class FeedbackService {
    private final FeedbackStore store;

    public FeedbackService(FeedbackStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Transactional
    public Feedback save(long messageId, Actor actor, Input input) {
        validate(messageId, actor);
        // 소유권보다 먼저 막는다. 남의 메시지인지 알려주지 않고, 비회원은 어떤 메시지든 같은 응답을 받는다
        if (actor.userId() == null) {
            throw new GeneralException(FeedbackErrorCode.MEMBER_ONLY);
        }
        return store.upsert(messageId, actor, Objects.requireNonNull(input));
    }

    public Optional<Feedback> get(long messageId, Actor actor) {
        validate(messageId, actor);
        return store.find(messageId, actor);
    }

    public Map<Long, Feedback> getAll(List<Long> messageIds, Actor actor) {
        Objects.requireNonNull(actor, "authenticated actor");
        return store.findByMessageIds(messageIds, actor);
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
