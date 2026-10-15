package com.telme.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class WavFixtures {

    private WavFixtures() {
    }

    public static byte[] monoPcm16(int sampleRate, double seconds) {
        return wav(1, 1, sampleRate, 16, new byte[(int) (sampleRate * 2 * seconds)]);
    }

    public static byte[] wav(int format, int channels, int sampleRate, int bits, byte[] pcm) {
        int blockAlign = channels * bits / 8;
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) format).putShort((short) channels).putInt(sampleRate)
                .putInt(sampleRate * blockAlign).putShort((short) blockAlign).putShort((short) bits)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return buffer.array();
    }
}
