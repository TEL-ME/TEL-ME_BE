package com.telme.speech.controller;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.speech.service.SpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Speech", description = "챗봇 답변 음성 듣기 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/messages")
public class SpeechController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");
    private static final Duration BROWSER_CACHE = Duration.ofHours(1);

    private final ChatActorProvider chatActorProvider;
    private final SpeechService speechService;

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
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePrivate())
                .body(audio);
    }
}
