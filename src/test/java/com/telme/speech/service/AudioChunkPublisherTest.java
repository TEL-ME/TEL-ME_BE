package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

class AudioChunkPublisherTest {

    @Test
    @DisplayName("16kHz PCM은 100ms(3,200바이트) 조각으로 순서대로 보내고 마지막 조각은 남은 만큼만 보낸다")
    void 조각_크기와_순서() {
        byte[] pcm = new byte[7_000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }
        RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);

        new AudioChunkPublisher(pcm, 16_000).subscribe(subscriber);

        assertThat(subscriber.chunkSizes()).containsExactly(3_200, 3_200, 600);
        assertThat(subscriber.joined()).isEqualTo(pcm);
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    @DisplayName("11,025Hz처럼 100ms 분량이 홀수 바이트인 주파수도 2바이트 표본이 갈라지지 않게 짝수로 자른다")
    void 표본이_갈라지지_않는다() {
        RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);

        new AudioChunkPublisher(new byte[5_000], 11_025).subscribe(subscriber);

        assertThat(subscriber.chunkSizes()).containsExactly(2_204, 2_204, 592);
    }

    @Test
    @DisplayName("받는 쪽이 요청한 수만큼만 보내고, 더 요청하면 이어서 보낸다")
    void 요청한_만큼만_보낸다() {
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        new AudioChunkPublisher(new byte[9_600], 16_000).subscribe(subscriber);

        assertThat(subscriber.chunks).hasSize(1);
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(5);

        assertThat(subscriber.chunks).hasSize(3);
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    @DisplayName("취소하면 더 보내지 않는다")
    void 취소하면_멈춘다() {
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        new AudioChunkPublisher(new byte[9_600], 16_000).subscribe(subscriber);

        subscriber.subscription.cancel();
        subscriber.subscription.request(5);

        assertThat(subscriber.chunks).hasSize(1);
        assertThat(subscriber.completed).isFalse();
    }

    private static final class RecordingSubscriber implements Subscriber<AudioStream> {

        private final long initialRequest;
        private final List<byte[]> chunks = new ArrayList<>();
        private Subscription subscription;
        private boolean completed;

        private RecordingSubscriber(long initialRequest) {
            this.initialRequest = initialRequest;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(initialRequest);
        }

        @Override
        public void onNext(AudioStream event) {
            chunks.add(((AudioEvent) event).audioChunk().asByteArray());
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        private List<Integer> chunkSizes() {
            return chunks.stream().map(chunk -> chunk.length).toList();
        }

        private byte[] joined() {
            byte[] all = new byte[chunks.stream().mapToInt(chunk -> chunk.length).sum()];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, all, offset, chunk.length);
                offset += chunk.length;
            }
            return all;
        }
    }
}
