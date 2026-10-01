package com.telme.feedback.service;

import com.telme.feedback.converter.AdminFeedbackConverter;
import com.telme.feedback.dto.req.AdminFeedbackHandleRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.feedback.repository.AdminFeedbackRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.MessageSourceRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminFeedbackCommandService {

    private final AdminFeedbackRepository feedbackRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final AdminFeedbackConverter converter;
    private final EntityManager entityManager;
    private final Clock clock;

    // 같은 건을 다시 눌러도 막지 않는다. 메모만 고치는 경우가 있고, 결과가 달라지지 않는다
    public AdminFeedbackDetailResponse changeHandled(
            Long feedbackId, AdminFeedbackHandleRequest request, Long adminId) {
        MessageFeedback feedback = findDislike(feedbackId);
        rejectStaleHandle(feedback, request.updatedAt());

        if (request.handled()) {
            feedback.markHandled(adminId, request.note(), clock);
        } else {
            feedback.markUnhandled();
        }
        flushOrRejectRatingChange();
        // 트리거가 채운 updated_at을 다시 읽는다. 안 하면 응답이 저장 전 값이라 그대로 되보내면 409가 된다
        entityManager.refresh(feedback);
        log.info("[AdminFeedback] 처리 표시 feedbackId={} handled={} adminId={}",
                feedbackId, request.handled(), adminId);

        return converter.toDetail(
                feedback,
                messageSourceRepository.findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(
                        feedback.getMessage().getMessageId()));
    }

    // 상세를 본 뒤 사용자가 고쳤으면 읽지 않은 내용이 처리 완료로 사라진다.
    // 화면이 들고 있던 수정 시각과 맞춰 본다. 값을 안 보내면 검사하지 않는다
    private void rejectStaleHandle(MessageFeedback feedback, Instant updatedAt) {
        if (updatedAt != null && !updatedAt.equals(feedback.getUpdatedAt())) {
            throw new GeneralException(FeedbackErrorCode.FEEDBACK_CHANGED);
        }
    }

    // 좋아요가 된 행에 처리 표시를 남기면 제약에 걸린다. 커밋까지 미루면 여기서 안 잡혀 500으로 나간다
    private void flushOrRejectRatingChange() {
        try {
            feedbackRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(FeedbackErrorCode.FEEDBACK_CHANGED);
        }
    }

    // 취소돼 사라진 행과 좋아요는 여기서 없는 것으로 걸러진다
    private MessageFeedback findDislike(Long feedbackId) {
        return feedbackRepository.findForHandling(feedbackId)
                .filter(found -> found.getRating() == MessageFeedback.Rating.DISLIKE)
                .orElseThrow(() -> new GeneralException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
    }
}
