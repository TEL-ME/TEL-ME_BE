package com.telme.speech.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpeechTranscriptionResponse(
        @Schema(description = "인식한 문장. 자동 전송하지 않고 입력창에 채우는 용도", example = "요금제 바꾸고 싶어요")
        String text) {
}
