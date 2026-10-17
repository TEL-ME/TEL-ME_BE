package com.telme.chat.controller;

import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.chat.dto.res.ChatMessageTranslationResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatActorProvider;
import com.telme.chat.service.ChatMessageSourceService;
import com.telme.chat.translation.ChatMessageTranslationService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat Message", description = "채팅 메시지 부가 정보 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/messages")
public class ChatMessageController {

    private final ChatActorProvider chatActorProvider;
    private final ChatMessageSourceService chatMessageSourceService;
    private final ChatMessageTranslationService chatMessageTranslationService;

    @Operation(summary = "답변 근거 조회", description = "특정 채팅 메시지에 저장된 FAQ 근거를 검색 순위대로 조회합니다.")
    @GetMapping("/{messageId}/sources")
    public CustomResponse<ChatMessageSourcesResponse> getMessageSources(
            HttpServletRequest servletRequest,
            @PathVariable
            @Positive(message = "{chat.message-id.positive}")
            Long messageId
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatMessageSourceService.getMessageSources(actor, messageId));
    }
    
    @Operation(summary = "답변 번역",
            description = "내 대화의 완료된 챗봇 답변을 번역합니다. 같은 답변을 다시 요청하면 저장한 번역을 바로 돌려줍니다. "
                    + "지금은 영어(en)만 지원합니다. 실패하면 TRANSLATION400-0, TRANSLATION429-0, TRANSLATION502-0, "
                    + "CHAT404-2, LLM 오류 코드를 돌려줍니다.")
    @GetMapping("/{messageId}/translation")
    public CustomResponse<ChatMessageTranslationResponse> getMessageTranslation(
            HttpServletRequest servletRequest,
            @PathVariable
            @Positive(message = "{chat.message-id.positive}")
            Long messageId,
            @RequestParam(defaultValue = "en")
            @Pattern(regexp = "en", message = "{chat.translation.language}")
            String lang
    ) {
        ChatActor actor = chatActorProvider.getCurrentActor(servletRequest);
        return CustomResponse.onSuccess(chatMessageTranslationService.translate(actor, messageId, lang));
    }
}
