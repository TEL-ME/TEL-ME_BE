package com.telme.speech.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.config.SpeechTtsProperties;
import com.telme.speech.exception.SpeechErrorCode;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 임의 문장이 아니라 내 대화의 완료된 챗봇 답변만 읽어 준다. Polly 호출이 길어 DB 트랜잭션은 잡지 않는다
@Service
@RequiredArgsConstructor
public class SpeechService {

    private final ChatMessageRepository chatMessageRepository;
    private final PollySpeechClient pollySpeechClient;
    private final SpeechAudioCache speechAudioCache;
    private final SpeechUsageLimiter speechUsageLimiter;
    private final SpeechTtsProperties properties;

    public byte[] synthesizeMessage(ChatActor actor, Long messageId) {
        ChatMessage message = chatMessageRepository.findByIdWithSession(messageId)
                .filter(found -> owns(actor, found.getSession()))
                .orElseThrow(() -> new GeneralException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (message.getRole() != ChatMessage.Role.ASSISTANT || message.getStatus() != ChatMessage.Status.COMPLETED) {
            throw new GeneralException(SpeechErrorCode.NOT_SPEAKABLE);
        }

        Optional<byte[]> cached = speechAudioCache.get(messageId);
        if (cached.isPresent()) {
            return cached.get();
        }

        String text = SpeechText.toSpeakable(message.getContent(), properties.maxTextLength());
        if (text.isEmpty()) {
            throw new GeneralException(SpeechErrorCode.NOT_SPEAKABLE);
        }
        speechUsageLimiter.acquire(actor, text.length());
        byte[] audio = pollySpeechClient.synthesize(text);
        speechAudioCache.put(messageId, audio);
        return audio;
    }

    private boolean owns(ChatActor actor, ChatSession session) {
        if (actor.isMember()) {
            return Objects.equals(session.getUserId(), actor.userId());
        }
        return session.getUserId() == null && Objects.equals(session.getGuestId(), actor.guestId());
    }
}
