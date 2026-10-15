package com.telme.speech.service;

import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.config.SpeechSttProperties;
import com.telme.speech.exception.SpeechErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 음성 파일은 저장하지 않고 인식한 문장만 돌려준다. 채팅 전송은 사용자가 확인한 뒤 기존 입력 검사를 거친다
@Service
@RequiredArgsConstructor
public class SpeechTranscriptionService {

    // 버튼을 눌렀다 바로 뗀 녹음은 Transcribe를 부르지 않는다
    private static final Duration MIN_DURATION = Duration.ofMillis(300);

    private final TranscribeSpeechClient transcribeSpeechClient;
    private final TranscriptionUsageLimiter transcriptionUsageLimiter;
    private final SpeechSttProperties properties;

    public String transcribe(ChatActor actor, byte[] wav) {
        WavAudio audio = WavAudio.parse(wav);
        Duration duration = audio.duration();
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(properties.maxDuration()) > 0) {
            throw new GeneralException(SpeechErrorCode.INVALID_AUDIO_LENGTH);
        }

        transcriptionUsageLimiter.acquire(actor, duration);
        String text = transcribeSpeechClient.transcribe(audio.pcm(), audio.sampleRate()).strip();
        if (text.isEmpty()) {
            throw new GeneralException(SpeechErrorCode.NOT_RECOGNIZED);
        }
        return text;
    }

    public long maxRequestBytes() {
        return WavAudio.maxBytes(properties.maxDuration());
    }
}
