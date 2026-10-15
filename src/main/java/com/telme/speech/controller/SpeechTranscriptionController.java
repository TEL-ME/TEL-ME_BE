package com.telme.speech.controller;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.dto.res.SpeechTranscriptionResponse;
import com.telme.speech.exception.SpeechErrorCode;
import com.telme.speech.service.SpeechTranscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Speech", description = "챗봇 음성 듣기·말하기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/speech")
public class SpeechTranscriptionController {

    // 이보다 많이 넘치는 본문은 읽어 버리지 않고 연결을 끊는다. 정상 녹음은 이 크기에 닿지 않는다
    private static final long DISCARD_LIMIT = 16L * 1024 * 1024;

    private final ChatActorProvider chatActorProvider;
    private final SpeechTranscriptionService speechTranscriptionService;

    @Operation(summary = "음성 질문 인식",
            description = "녹음한 WAV(모노·16bit PCM, 8~48kHz, 권장 16kHz, 최대 30초)를 요청 본문에 그대로 담아 보내면 인식한 한국어 문장을 돌려줍니다. "
                    + "결과는 입력창에 채우는 용도이고, 전송은 기존 메시지 전송 API로 합니다. "
                    + "오류: SPEECH400-1(형식), SPEECH400-2(길이), SPEECH422-0(인식 결과 없음), SPEECH429-0/1, SPEECH503-1")
    @RequestBody(required = true, description = "WAV 파일 원본 (Content-Type: audio/wav)",
            content = @Content(mediaType = "audio/wav", schema = @Schema(type = "string", format = "binary")))
    @PostMapping("/transcriptions")
    public CustomResponse<SpeechTranscriptionResponse> transcribe(HttpServletRequest servletRequest) throws IOException {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        byte[] wav = readLimited(servletRequest, speechTranscriptionService.maxRequestBytes());
        return CustomResponse.onSuccess(new SpeechTranscriptionResponse(speechTranscriptionService.transcribe(actor, wav)));
    }

    // multipart 대신 본문을 직접 읽어, 너무 큰 녹음도 공통 500이 아니라 SPEECH400-2로 알린다
    private static byte[] readLimited(HttpServletRequest request, long maxBytes) throws IOException {
        long declared = request.getContentLengthLong();
        try (InputStream body = request.getInputStream()) {
            if (declared > maxBytes) {
                throw rejectOversized(body, declared - maxBytes);
            }
            byte[] wav = body.readNBytes((int) maxBytes + 1);
            if (wav.length > maxBytes) {
                throw rejectOversized(body, -1);
            }
            return wav;
        }
    }

    // Tomcat은 응답 뒤 남은 본문을 2MB까지만 대신 읽고 그 이상이면 연결을 끊어 오류 JSON이 전달되지 않으므로 남은 본문을 읽어 버린다
    private static GeneralException rejectOversized(InputStream body, long declaredExcess) throws IOException {
        if (declaredExcess <= DISCARD_LIMIT) {
            byte[] buffer = new byte[8192];
            long discarded = 0;
            int read;
            while (discarded < DISCARD_LIMIT && (read = body.read(buffer)) != -1) {
                discarded += read;
            }
        }
        return new GeneralException(SpeechErrorCode.INVALID_AUDIO_LENGTH);
    }
}
