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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ConcurrentMap<Long, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();

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

        // 같은 답변을 동시에 요청하면 먼저 온 요청만 Polly를 부르고 나머지는 그 결과를 기다린다
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> running = inFlight.putIfAbsent(messageId, mine);
        if (running != null) {
            return await(running);
        }
        try {
            byte[] audio = synthesize(actor, messageId, message.getContent());
            mine.complete(audio);
            return audio;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(messageId, mine);
        }
    }

    private byte[] synthesize(ChatActor actor, Long messageId, String content) {
        Optional<byte[]> cached = speechAudioCache.get(messageId);
        if (cached.isPresent()) {
            return cached.get();
        }
        String text = SpeechText.toSpeakable(content, properties.maxTextLength());
        if (text.isEmpty()) {
            throw new GeneralException(SpeechErrorCode.NOT_SPEAKABLE);
        }
        speechUsageLimiter.acquire(actor, text.length());
        byte[] audio = pollySpeechClient.synthesize(text);
        speechAudioCache.put(messageId, audio);
        return audio;
    }

    private static byte[] await(CompletableFuture<byte[]> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private boolean owns(ChatActor actor, ChatSession session) {
        if (actor.isMember()) {
            return Objects.equals(session.getUserId(), actor.userId());
        }
        return session.getUserId() == null && Objects.equals(session.getGuestId(), actor.guestId());
    }
}
