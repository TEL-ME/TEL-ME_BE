package com.telme.feedback.repository;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.global.common.exception.GeneralException;

import java.util.Optional;
import java.util.UUID;

/** 모든 메서드는 현재 대화 소유권을 검증한다. Actor는 인증 계층이 검증한 값이다. */
public interface FeedbackStore {
    Feedback upsert(long messageId, Actor actor, Input input);

    Optional<Feedback> find(long messageId, Actor actor);

    void delete(long messageId, Actor actor);

    // 게스트 시절 남긴 피드백을 회원 계정으로 승계. 소유권 검증 없이 일괄 이관하는 승계 전용 경로
    void succeedGuestFeedback(UUID guestId, long userId);

    /** 존재하지 않거나 접근할 수 없는 메시지를 동일하게 처리한다. */
    class TargetUnavailable extends GeneralException {
        public TargetUnavailable() {
            super(FeedbackErrorCode.TARGET_UNAVAILABLE);
        }
    }

    class TargetNotReady extends GeneralException {
        public TargetNotReady() {
            super(FeedbackErrorCode.TARGET_NOT_READY);
        }
    }
}
