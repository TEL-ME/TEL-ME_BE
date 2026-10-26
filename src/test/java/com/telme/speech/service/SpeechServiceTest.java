package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.config.SpeechTtsProperties;
import com.telme.speech.exception.SpeechErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeechServiceTest {

    private static final long MESSAGE_ID = 10L;
    private static final ChatActor OWNER = new ChatActor(1L, null);

    private PollySpeechClient pollySpeechClient;
    private SpeechService speechService;
    private ExecutorService executor;
    private final CountDownLatch pollyStarted = new CountDownLatch(1);
    private final CountDownLatch releasePolly = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        ChatSession session = ChatSession.builder().userId(OWNER.userId()).title("음성").build();
        ChatMessage answer = ChatMessage.builder()
                .session(session)
                .sequenceNo(2)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("동시 요청 테스트입니다.")
                .status(ChatMessage.Status.COMPLETED)
                .build();
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        when(repository.findByIdWithSession(MESSAGE_ID)).thenReturn(Optional.of(answer));

        SpeechTtsProperties properties = SpeechTestSupport.properties(10, 1000, 1000);
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
        pollySpeechClient = mock(PollySpeechClient.class);
        speechService = new SpeechService(repository, pollySpeechClient, new SpeechAudioCache(clock, properties),
                new SpeechUsageLimiter(clock, properties, SpeechTestSupport.inMemoryDailyUsage()), properties);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 답변을 동시에 요청하면 Polly는 한 번만 부르고 두 요청 모두 같은 음성을 받는다")
    void 동시_요청은_한_번만_부른다() throws Exception {
        when(pollySpeechClient.synthesize(anyString())).thenAnswer(invocation -> {
            pollyStarted.countDown();
            releasePolly.await(5, TimeUnit.SECONDS);
            return new byte[] {1, 2};
        });

        Future<byte[]> first = executor.submit(() -> speechService.synthesizeMessage(OWNER, MESSAGE_ID));
        assertThat(pollyStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Future<byte[]> second = executor.submit(() -> speechService.synthesizeMessage(OWNER, MESSAGE_ID));
        Thread.sleep(200);
        releasePolly.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(1, 2);
        assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(1, 2);
        verify(pollySpeechClient, times(1)).synthesize(anyString());
    }

    @Test
    @DisplayName("먼저 온 요청이 실패하면 기다리던 요청도 같은 오류를 받고, 다음 요청은 다시 Polly를 부른다")
    void 실패는_기다리던_요청에도_전달된다() throws Exception {
        when(pollySpeechClient.synthesize(anyString())).thenAnswer(invocation -> {
            pollyStarted.countDown();
            releasePolly.await(5, TimeUnit.SECONDS);
            throw new GeneralException(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
        });

        Future<byte[]> first = executor.submit(() -> speechService.synthesizeMessage(OWNER, MESSAGE_ID));
        assertThat(pollyStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Future<byte[]> second = executor.submit(() -> speechService.synthesizeMessage(OWNER, MESSAGE_ID));
        Thread.sleep(200);
        releasePolly.countDown();

        for (Future<byte[]> request : new Future[] {first, second}) {
            assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(GeneralException.class)
                    .cause()
                    .extracting(e -> ((GeneralException) e).getErrorCode())
                    .isEqualTo(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
        }
        verify(pollySpeechClient, times(1)).synthesize(anyString());

        assertThatThrownBy(() -> speechService.synthesizeMessage(OWNER, MESSAGE_ID)).isInstanceOf(GeneralException.class);
        verify(pollySpeechClient, times(2)).synthesize(anyString());
    }
}
