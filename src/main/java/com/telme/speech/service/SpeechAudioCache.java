package com.telme.speech.service;

import com.telme.speech.config.SpeechTtsProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 같은 답변을 다시 재생할 때 Polly를 또 부르지 않는다. 음성 파일이 커서 개수 대신 전체 바이트로 한도를 둔다
@Component
public class SpeechAudioCache {

    private final Clock clock;
    private final SpeechTtsProperties properties;
    private final Map<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long totalBytes;

    public SpeechAudioCache(Clock clock, SpeechTtsProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public synchronized Optional<byte[]> get(Long messageId) {
        Entry entry = entries.get(messageId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            remove(messageId);
            return Optional.empty();
        }
        return Optional.of(entry.audio());
    }

    public synchronized void put(Long messageId, byte[] audio) {
        long maxBytes = properties.cacheMaxSize().toBytes();
        if (audio.length > maxBytes) {
            return;
        }
        remove(messageId);
        entries.put(messageId, new Entry(audio, clock.instant().plus(properties.cacheTtl())));
        totalBytes += audio.length;
        Iterator<Entry> eldest = entries.values().iterator();
        while (totalBytes > maxBytes && eldest.hasNext()) {
            totalBytes -= eldest.next().audio().length;
            eldest.remove();
        }
    }

    private void remove(Long messageId) {
        Entry removed = entries.remove(messageId);
        if (removed != null) {
            totalBytes -= removed.audio().length;
        }
    }

    private record Entry(byte[] audio, Instant expiresAt) {
    }
}
