package com.telme.speech.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.exception.SpeechErrorCode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

// 브라우저가 보낸 WAV에서 Transcribe 실시간 인식이 받는 PCM 본문만 꺼낸다. 압축·다채널·16bit 외 형식은 거절한다
record WavAudio(byte[] pcm, int sampleRate) {

    static final int MIN_SAMPLE_RATE = 8_000;
    static final int MAX_SAMPLE_RATE = 48_000;
    private static final int HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final short PCM_FORMAT = 1;
    private static final short EXTENSIBLE_FORMAT = (short) 0xFFFE;
    private static final short MONO = 1;
    private static final short BITS_PER_SAMPLE = 16;
    private static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;

    static WavAudio parse(byte[] wav) {
        if (wav == null || wav.length < HEADER_SIZE
                || !"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) {
            throw invalid();
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        Integer sampleRate = null;
        long offset = HEADER_SIZE;
        while (offset + CHUNK_HEADER_SIZE <= wav.length) {
            String id = ascii(wav, (int) offset);
            long size = Integer.toUnsignedLong(buffer.getInt((int) offset + 4));
            int body = (int) offset + CHUNK_HEADER_SIZE;
            if ("fmt ".equals(id)) {
                if (size < 16 || body + 16 > wav.length) {
                    throw invalid();
                }
                short format = buffer.getShort(body);
                short channels = buffer.getShort(body + 2);
                int rate = buffer.getInt(body + 4);
                short bits = buffer.getShort(body + 14);
                // 일부 녹음 라이브러리는 같은 PCM도 확장 형식으로 적고 실제 형식은 24바이트 뒤 GUID 앞 2바이트에 둔다
                if (format == EXTENSIBLE_FORMAT && size >= 40 && body + 26 <= wav.length) {
                    format = buffer.getShort(body + 24);
                }
                if (format != PCM_FORMAT || channels != MONO || bits != BITS_PER_SAMPLE
                        || rate < MIN_SAMPLE_RATE || rate > MAX_SAMPLE_RATE) {
                    throw invalid();
                }
                sampleRate = rate;
            } else if ("data".equals(id)) {
                if (sampleRate == null) {
                    throw invalid();
                }
                // 녹음 중 길이를 모르고 만든 WAV는 data 크기가 최댓값이라 실제 남은 길이까지만 읽는다
                int end = (int) Math.min(wav.length, body + size);
                int length = (end - body) / BYTES_PER_SAMPLE * BYTES_PER_SAMPLE;
                return new WavAudio(Arrays.copyOfRange(wav, body, body + length), sampleRate);
            }
            // 크기를 부풀린 덩어리도 위치 계산이 넘치지 않도록 long으로 더하고, 파일 밖을 가리키면 반복이 끝나 거절된다
            offset = body + size + (size & 1);
        }
        throw invalid();
    }

    Duration duration() {
        return Duration.ofMillis(pcm.length * 1000L / ((long) sampleRate * BYTES_PER_SAMPLE));
    }

    static long maxBytes(Duration maxDuration) {
        return maxDuration.toSeconds() * MAX_SAMPLE_RATE * BYTES_PER_SAMPLE + 4096;
    }

    private static String ascii(byte[] bytes, int offset) {
        return offset + 4 <= bytes.length ? new String(bytes, offset, 4, StandardCharsets.US_ASCII) : "";
    }

    private static GeneralException invalid() {
        return new GeneralException(SpeechErrorCode.INVALID_AUDIO);
    }
}
