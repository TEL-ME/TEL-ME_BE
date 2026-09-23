package com.telme.chat.converter;

import com.telme.chat.dto.res.ChatMessageSourceResponse;
import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.rag.entity.MessageSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageSourceConverter {

    public ChatMessageSourcesResponse toResponse(Long messageId, List<MessageSource> sources) {
        List<ChatMessageSourceResponse> items = sources.stream()
                .map(this::toItemResponse)
                .toList();
        return new ChatMessageSourcesResponse(messageId, items);
    }

    private ChatMessageSourceResponse toItemResponse(MessageSource source) {
        return new ChatMessageSourceResponse(
                source.getFaqId(),
                source.getTitleSnapshot(),
                source.getSearchRank(),
                source.getScore()
        );
    }
}
