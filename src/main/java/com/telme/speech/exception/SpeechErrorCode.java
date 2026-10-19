package com.telme.speech.exception;

import com.telme.global.common.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SpeechErrorCode implements BaseErrorCode {

    NOT_SPEAKABLE(HttpStatus.BAD_REQUEST, "SPEECH400-0", "음성으로 들려줄 수 없는 메시지입니다."),
    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "SPEECH400-1", "모노 16bit PCM WAV 음성만 인식할 수 있습니다."),
    INVALID_AUDIO_LENGTH(HttpStatus.BAD_REQUEST, "SPEECH400-2", "녹음이 너무 짧거나 깁니다. 질문을 짧게 다시 말씀해 주세요."),
    NOT_RECOGNIZED(HttpStatus.UNPROCESSABLE_ENTITY, "SPEECH422-0", "말씀을 알아듣지 못했습니다. 다시 한 번 말씀해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "SPEECH429-0", "음성 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SPEECH429-1", "오늘 사용할 수 있는 음성 변환량을 모두 사용했습니다."),
    SYNTHESIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH503-0",
            "음성 변환 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    TRANSCRIPTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH503-1",
            "음성 인식 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
