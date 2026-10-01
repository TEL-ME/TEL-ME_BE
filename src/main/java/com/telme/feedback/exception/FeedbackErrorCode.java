package com.telme.feedback.exception;

import com.telme.global.common.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FeedbackErrorCode implements BaseErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "FEEDBACK400-0", "피드백 요청값이 올바르지 않습니다."),
    TARGET_UNAVAILABLE(HttpStatus.NOT_FOUND, "FEEDBACK404-0", "평가할 수 있는 메시지를 찾을 수 없습니다."),
    TARGET_NOT_READY(HttpStatus.CONFLICT, "FEEDBACK409-0", "완료된 상담 답변과 매장 추천만 평가할 수 있습니다."),
    // 관리자 화면은 싫어요만 다루므로 좋아요 id로 조회해도 없는 것으로 본다
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "FEEDBACK404-1", "싫어요 피드백을 찾을 수 없습니다."),
    FEEDBACK_CHANGED(HttpStatus.CONFLICT, "FEEDBACK409-1", "그 사이 사용자가 피드백을 수정했습니다. 다시 불러온 뒤 처리해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
