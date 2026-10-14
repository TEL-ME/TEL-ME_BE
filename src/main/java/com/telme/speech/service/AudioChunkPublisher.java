package com.telme.speech.service;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

// 녹음된 PCM을 Transcribe가 권장하는 짧은 조각(100ms)으로 나눠, 받는 쪽이 요청한 만큼만 보낸다
final class AudioChunkPublisher implements Publisher<AudioStream> {

    private final byte[] pcm;
    private final int chunkSize;

    AudioChunkPublisher(byte[] pcm, int sampleRate) {
        this.pcm = pcm;
        // 16bit 표본(2바이트)이 두 조각으로 갈라지지 않도록 짝수 바이트로 맞춘다
        this.chunkSize = Math.max(2, sampleRate / 10 * 2);
    }

    @Override
    public void subscribe(Subscriber<? super AudioStream> subscriber) {
        subscriber.onSubscribe(new ChunkSubscription(subscriber));
    }

    private final class ChunkSubscription implements Subscription {

        private final Subscriber<? super AudioStream> subscriber;
        private final AtomicLong demand = new AtomicLong();
        private final AtomicInteger offset = new AtomicInteger();
        private final AtomicBoolean emitting = new AtomicBoolean();
        private volatile boolean done;

        private ChunkSubscription(Subscriber<? super AudioStream> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException("요청 수는 1 이상이어야 합니다."));
                return;
            }
            demand.getAndAccumulate(n, (current, add) -> current + add < 0 ? Long.MAX_VALUE : current + add);
            drain();
        }

        @Override
        public void cancel() {
            done = true;
        }

        // 다른 스레드에서 request가 겹쳐 들어와도 한 번에 한 곳에서만 보내도록 한다
        private void drain() {
            if (!emitting.compareAndSet(false, true)) {
                return;
            }
            try {
                while (!done && demand.get() > 0 && offset.get() < pcm.length) {
                    int start = offset.get();
                    int end = Math.min(pcm.length, start + chunkSize);
                    offset.set(end);
                    demand.decrementAndGet();
                    subscriber.onNext(AudioEvent.builder()
                            .audioChunk(SdkBytes.fromByteArray(Arrays.copyOfRange(pcm, start, end)))
                            .build());
                }
                if (!done && offset.get() >= pcm.length) {
                    done = true;
                    subscriber.onComplete();
                }
            } finally {
                emitting.set(false);
            }
            if (!done && demand.get() > 0 && offset.get() < pcm.length) {
                drain();
            }
        }
    }
}
