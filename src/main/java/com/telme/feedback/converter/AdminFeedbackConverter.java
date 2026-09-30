package com.telme.feedback.converter;

import com.telme.chat.entity.ChatMessage;
import com.telme.feedback.dto.res.AdminFeedbackDetailResponse;
import com.telme.feedback.dto.res.AdminFeedbackListItemResponse;
import com.telme.feedback.dto.res.AdminFeedbackListResponse;
import com.telme.feedback.dto.res.AdminFeedbackSourceResponse;
import com.telme.feedback.entity.MessageFeedback;
import com.telme.rag.entity.MessageSource;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AdminFeedbackConverter {

    // 목록은 훑어보는 화면이라 본문을 잘라 보낸다. 전문은 상세에서 본다
    private static final int QUESTION_PREVIEW_LENGTH = 60;
    private static final int COMMENT_PREVIEW_LENGTH = 100;

    public AdminFeedbackListResponse toListResponse(Page<MessageFeedback> feedbacks) {
        List<AdminFeedbackListItemResponse> items = feedbacks.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new AdminFeedbackListResponse(
                items,
                feedbacks.getNumber(),
                feedbacks.getSize(),
                feedbacks.getTotalElements(),
                feedbacks.getTotalPages());
    }

    public AdminFeedbackListItemResponse toListItem(MessageFeedback feedback) {
        return new AdminFeedbackListItemResponse(
                feedback.getFeedbackId(),
                feedback.getReasonCode() == null ? null : feedback.getReasonCode().name(),
                preview(question(feedback), QUESTION_PREVIEW_LENGTH),
                preview(feedback.getComment(), COMMENT_PREVIEW_LENGTH),
                feedback.getCreatedAt(),
                feedback.isHandled());
    }

    public AdminFeedbackDetailResponse toDetail(MessageFeedback feedback, List<MessageSource> sources) {
        ChatMessage answer = feedback.getMessage();
        return new AdminFeedbackDetailResponse(
                feedback.getFeedbackId(),
                feedback.getReasonCode() == null ? null : feedback.getReasonCode().name(),
                feedback.getComment(),
                feedback.getCreatedAt(),
                feedback.isHandled(),
                feedback.getHandledAt(),
                feedback.getHandledBy(),
                feedback.getHandledNote(),
                answer == null ? null : answer.getMessageId(),
                question(feedback),
                answer == null ? null : answer.getContent(),
                answer == null || answer.getAnswerBasis() == null ? null : answer.getAnswerBasis().name(),
                answer == null ? null : answer.getCreatedAt(),
                sources.stream().map(this::toSource).toList());
    }

    public AdminFeedbackSourceResponse toSource(MessageSource source) {
        return new AdminFeedbackSourceResponse(
                source.getFaqId(),
                source.getTitleSnapshot(),
                source.getFaqVersion(),
                source.getSearchRank(),
                source.getScore());
    }

    // 되묻기 없이 시작한 답변은 앞 메시지가 없다. 그때는 질문 칸을 비워 둔다
    private String question(MessageFeedback feedback) {
        ChatMessage answer = feedback.getMessage();
        return answer == null || answer.getReplyTo() == null ? null : answer.getReplyTo().getContent();
    }

    private String preview(String text, int length) {
        if (text == null) {
            return null;
        }
        return text.length() <= length ? text : text.substring(0, length) + "...";
    }
}
