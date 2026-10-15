package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.WavFixtures;
import com.telme.speech.exception.SpeechErrorCode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WavAudioTest {

    @Test
    @DisplayName("모노 16bit PCM WAV에서 표본 주파수와 PCM 본문, 길이를 읽는다")
    void 정상_WAV() {
        byte[] pcm = new byte[32_000];
        Arrays.fill(pcm, (byte) 7);

        WavAudio audio = WavAudio.parse(WavFixtures.wav(1, 1, 16_000, 16, pcm));

        assertThat(audio.sampleRate()).isEqualTo(16_000);
        assertThat(audio.pcm()).isEqualTo(pcm);
        assertThat(audio.duration()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("fmt와 data 사이에 다른 덩어리(LIST 등)가 있어도 건너뛴다")
    void 다른_덩어리를_건너뛴다() {
        byte[] base = WavFixtures.monoPcm16(16_000, 0.5);
        byte[] list = ByteBuffer.allocate(8 + 3 + 1).order(ByteOrder.LITTLE_ENDIAN)
                .put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(3).put(new byte[] {1, 2, 3, 0}).array();
        byte[] wav = new byte[base.length + list.length];
        System.arraycopy(base, 0, wav, 0, 36);
        System.arraycopy(list, 0, wav, 36, list.length);
        System.arraycopy(base, 36, wav, 36 + list.length, base.length - 36);

        assertThat(WavAudio.parse(wav).pcm()).hasSize(16_000);
    }

    @Test
    @DisplayName("녹음 중에 만든 WAV처럼 data 크기가 실제보다 크면 남은 길이까지만 읽고, 홀수 바이트는 버린다")
    void data_크기가_실제보다_크다() {
        byte[] wav = WavFixtures.wav(1, 1, 16_000, 16, new byte[101]);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, -1);

        assertThat(WavAudio.parse(wav).pcm()).hasSize(100);
    }

    @Test
    @DisplayName("확장 형식(0xFFFE) 머리말이라도 실제 형식이 PCM이면 받고, PCM이 아니면 거절한다")
    void 확장_형식_머리말() {
        assertThat(WavAudio.parse(extensible(1)).pcm()).hasSize(3_200);
        assertThatThrownBy(() -> WavAudio.parse(extensible(3))).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("스테레오·8bit·압축 형식·범위 밖 주파수·WAV가 아닌 파일은 SPEECH400-1로 거절한다")
    void 받을_수_없는_형식() {
        byte[][] invalid = {
                WavFixtures.wav(1, 2, 16_000, 16, new byte[64]),
                WavFixtures.wav(1, 1, 16_000, 8, new byte[64]),
                WavFixtures.wav(3, 1, 16_000, 16, new byte[64]),
                WavFixtures.wav(1, 1, 7_999, 16, new byte[64]),
                WavFixtures.wav(1, 1, 48_001, 16, new byte[64]),
                "hello".getBytes(StandardCharsets.US_ASCII),
                new byte[0]
        };

        for (byte[] wav : invalid) {
            assertThatThrownBy(() -> WavAudio.parse(wav))
                    .isInstanceOf(GeneralException.class)
                    .extracting(e -> ((GeneralException) e).getErrorCode())
                    .isEqualTo(SpeechErrorCode.INVALID_AUDIO);
        }
    }

    @Test
    @DisplayName("data가 fmt보다 먼저 나오거나 data가 없으면 거절한다")
    void 덩어리_순서가_잘못됐다() {
        byte[] noData = Arrays.copyOf(WavFixtures.monoPcm16(16_000, 0.1), 36);
        byte[] dataFirst = ByteBuffer.allocate(12 + 8 + 4).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(16).put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(4).putInt(0).array();

        assertThatThrownBy(() -> WavAudio.parse(noData)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> WavAudio.parse(dataFirst)).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("덩어리 크기를 4GB 가까이 부풀린 파일도 서버 오류 없이 SPEECH400-1로 거절한다")
    void 부풀린_덩어리_크기() {
        byte[] wav = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(20).put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("JUNK".getBytes(StandardCharsets.US_ASCII)).putInt(0xFFFFFFF0).putInt(0).putInt(0).array();

        assertThatThrownBy(() -> WavAudio.parse(wav))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.INVALID_AUDIO);
    }

    private static byte[] extensible(int subFormat) {
        int pcmLength = 3_200;
        ByteBuffer buffer = ByteBuffer.allocate(12 + 8 + 40 + 8 + pcmLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(4 + 48 + 8 + pcmLength)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(40)
                .putShort((short) 0xFFFE).putShort((short) 1).putInt(16_000).putInt(32_000)
                .putShort((short) 2).putShort((short) 16)
                .putShort((short) 22).putShort((short) 16).putInt(4)
                .putShort((short) subFormat).put(new byte[14])
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcmLength).put(new byte[pcmLength]);
        return buffer.array();
    }
}
