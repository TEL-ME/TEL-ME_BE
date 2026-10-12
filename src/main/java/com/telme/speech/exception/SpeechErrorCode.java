package com.telme.speech.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SpeechErrorCode implements BaseErrorCode {

    NOT_SPEAKABLE(HttpStatus.BAD_REQUEST, "SPEECH400-0", "음성으로 들려줄 수 없는 메시지입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "SPEECH429-0", "음성 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SPEECH429-1", "오늘 사용할 수 있는 음성 변환량을 모두 사용했습니다."),
    SYNTHESIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH503-0",
            "음성 변환 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
