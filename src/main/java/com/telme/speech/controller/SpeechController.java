package com.telme.speech.controller;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.global.common.CustomResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.global.common.exception.GlobalExceptionHandler;
import com.telme.speech.service.SpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Tag(name = "Chat Speech", description = "챗봇 음성 듣기·말하기 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/messages")
public class SpeechController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");
    private static final Duration BROWSER_CACHE = Duration.ofHours(1);

    private final ChatActorProvider chatActorProvider;
    private final SpeechService speechService;
    private final GlobalExceptionHandler globalExceptionHandler;

    @Operation(summary = "답변 음성 듣기",
            description = "내 대화의 완료된 챗봇 답변을 한국어 음성(mp3)으로 돌려줍니다. 링크 주소와 목록 기호는 읽지 않습니다. "
                    + "실패하면 다른 API와 같은 JSON 오류(SPEECH400-0, SPEECH429-0/1, SPEECH503-0, CHAT404-2)를 돌려줍니다.")
    @ApiResponse(responseCode = "200", description = "mp3 음성",
            content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{messageId}/speech")
    public ResponseEntity<byte[]> getMessageSpeech(
            HttpServletRequest servletRequest,
            @PathVariable
            @Positive(message = "메시지 ID는 1 이상이어야 합니다.")
            Long messageId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        byte[] audio = speechService.synthesizeMessage(actor, messageId);
        // 같은 브라우저에서 계정을 바꾸면 이전 계정이 받아 둔 음성을 쓰지 않도록 쿠키별로 캐시한다
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePrivate())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(audio);
    }

    // Accept: audio/mpeg만 보낸 요청(Swagger 등)도 오류를 받을 수 있게 공통 오류 응답을 그대로 쓰고 형식만 JSON으로 고정한다
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<CustomResponse<Void>> handleGeneralException(GeneralException ex) {
        return asJson(globalExceptionHandler.handleCustomException(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        return asJson(globalExceptionHandler.handleConstraintViolationException(ex));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return asJson(globalExceptionHandler.handleMethodArgumentTypeMismatchException(ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomResponse<Void>> handleUnexpected(Exception ex) {
        return asJson(globalExceptionHandler.handleAllException(ex));
    }

    private static <T> ResponseEntity<T> asJson(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
