package com.telme.chat.converter;

import com.telme.chat.dto.req.AdminUnansweredType;
import com.telme.chat.dto.res.AdminUnansweredListItemResponse;
import com.telme.chat.dto.res.AdminUnansweredListResponse;
import com.telme.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AdminUnansweredConverter {

    // 목록은 훑어보는 화면이라 본문을 잘라 보낸다. 전문은 상세에서 본다
    private static final int QUESTION_PREVIEW_LENGTH = 60;

    public AdminUnansweredListResponse toListResponse(Page<ChatMessage> messages) {
        List<AdminUnansweredListItemResponse> items = messages.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new AdminUnansweredListResponse(
                items,
                messages.getNumber(),
                messages.getSize(),
                messages.getTotalElements(),
                messages.getTotalPages());
    }

    public AdminUnansweredListItemResponse toListItem(ChatMessage message) {
        return new AdminUnansweredListItemResponse(
                message.getMessageId(),
                message.getSession().getSessionId(),
                typeOf(message).name(),
                preview(question(message)),
                message.getCreatedAt());
    }

    // 답변을 끝내지 못한 경우는 answer_basis가 비어 있어 status로 판단한다.
    // 둘 다 있으면 근거 쪽이 관리자가 할 일을 더 잘 설명한다
    public AdminUnansweredType typeOf(ChatMessage message) {
        if (message.getAnswerBasis() == ChatMessage.AnswerBasis.NO_EVIDENCE) {
            return AdminUnansweredType.NO_EVIDENCE;
        }
        if (message.getAnswerBasis() == ChatMessage.AnswerBasis.OUT_OF_SCOPE) {
            return AdminUnansweredType.OUT_OF_SCOPE;
        }
        return message.getStatus() == ChatMessage.Status.FAILED
                ? AdminUnansweredType.FAILED
                : AdminUnansweredType.TIMEOUT;
    }

    // 되묻기 없이 시작한 답변은 앞 메시지가 없다. 그때는 질문 칸을 비워 둔다
    private String question(ChatMessage message) {
        ChatMessage replyTo = message.getReplyTo();
        return replyTo == null ? null : replyTo.getContent();
    }

    private String preview(String text) {
        if (text == null || text.length() <= QUESTION_PREVIEW_LENGTH) {
            return text;
        }
        // 이모지는 두 칸을 차지해 경계에서 자르면 앞쪽 절반만 남아 글자가 깨진다
        int end = Character.isHighSurrogate(text.charAt(QUESTION_PREVIEW_LENGTH - 1))
                ? QUESTION_PREVIEW_LENGTH - 1
                : QUESTION_PREVIEW_LENGTH;
        return text.substring(0, end) + "...";
    }
}
