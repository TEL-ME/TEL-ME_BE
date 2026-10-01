package com.telme.feedback.service;

import com.telme.feedback.converter.AdminFeedbackConverter;
import com.telme.feedback.dto.req.AdminFeedbackSearchRequest;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.dto.res.AdminFeedbackListResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.feedback.repository.AdminFeedbackRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.MessageSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFeedbackQueryService {

    private final AdminFeedbackRepository feedbackRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final AdminFeedbackConverter converter;

    public AdminFeedbackListResponse getDislikes(AdminFeedbackSearchRequest request) {
        // 시작이 끝보다 뒤면 결과가 늘 비어 조건을 잘못 넣은 것을 알아채기 어렵다
        if (request.periodReversed()) {
            throw new GeneralException(FeedbackErrorCode.INVALID_REQUEST);
        }

        Page<MessageFeedback> feedbacks = feedbackRepository.findDislikes(
                MessageFeedback.Rating.DISLIKE,
                request.reasons(),
                request.handled().mode(),
                request.fromOrMin(),
                request.toOrMax(),
                PageRequest.of(request.page(), request.size()));
        return converter.toListResponse(feedbacks);
    }

    public AdminFeedbackDetailResponse getDislike(long feedbackId) {
        MessageFeedback feedback = feedbackRepository.findById(feedbackId)
                .filter(found -> found.getRating() == MessageFeedback.Rating.DISLIKE)
                .orElseThrow(() -> new GeneralException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
        // 답변을 만들 때 쓴 FAQ는 순위 순으로 저장돼 있다
        return converter.toDetail(
                feedback,
                messageSourceRepository.findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(
                        feedback.getMessage().getMessageId()));
    }
}
