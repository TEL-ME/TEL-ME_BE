package com.telme.feedback.service;

import com.telme.feedback.converter.AdminFeedbackConverter;
import com.telme.feedback.dto.req.AdminFeedbackHandleRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.feedback.repository.AdminFeedbackRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.MessageSourceRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final Clock clock;

    // 같은 건을 다시 눌러도 막지 않는다. 메모만 고치는 경우가 있고, 결과가 달라지지 않는다
    public AdminFeedbackDetailResponse changeHandled(
            Long feedbackId, AdminFeedbackHandleRequest request, Long adminId) {
        MessageFeedback feedback = findDislike(feedbackId);

        if (request.handled()) {
            feedback.markHandled(adminId, request.note(), clock);
        } else {
            feedback.markUnhandled();
        }
        flushOrRejectRatingChange();
        log.info("[AdminFeedback] 처리 표시 feedbackId={} handled={} adminId={}",
                feedbackId, request.handled(), adminId);

        return converter.toDetail(
                feedback,
                messageSourceRepository.findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(
                        feedback.getMessage().getMessageId()));
    }

    // 읽은 뒤 사용자가 좋아요로 바꿨으면 ck_feedback_handled_dislike_only에 걸린다.
    // 커밋까지 미루면 여기서 안 잡혀 500으로 나가므로 지금 확정한다
    private void flushOrRejectRatingChange() {
        try {
            feedbackRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(FeedbackErrorCode.RATING_CHANGED);
        }
    }

    // 좋아요는 관리자 화면이 다루지 않아 조회와 같은 응답으로 막는다
    private MessageFeedback findDislike(Long feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .filter(found -> found.getRating() == MessageFeedback.Rating.DISLIKE)
                .orElseThrow(() -> new GeneralException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
    }
}
