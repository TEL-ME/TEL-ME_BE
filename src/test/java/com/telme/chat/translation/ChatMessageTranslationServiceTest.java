package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.chat.dto.res.ChatMessageTranslationResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.exception.TranslationErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.code.BaseErrorCode;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatMessageTranslationServiceTest {

    private static final long MESSAGE_ID = 10L;
    private static final long EXECUTION_ID = 7L;
    private static final ChatActor OWNER = new ChatActor(1L, null);
    private static final String KOREAN = "재발급 비용은 7,700원입니다.";
    private static final String ENGLISH = "The reissue fee is KRW 7,700.";
    
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatMessageTranslationRepository translations = mock(ChatMessageTranslationRepository.class);
    private final TranslationUsageLimiter limiter = mock(TranslationUsageLimiter.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final ChatMessageTranslationService service = new ChatMessageTranslationService(messages, translations,
            limiter, llm, new LlmProperties("exaone3.5:7.8b",  Duration.ofSeconds(5), Duration.ofSeconds(60), 8192));
    private final AtomicReference<String> saved = new AtomicReference<>();
    private ExecutorService executor;
    
    @BeforeEach
    void setup() {
        when(messages.findByIdWithSession(MESSAGE_ID)).thenReturn(Optional.of(message(ChatMessage.Role.ASSISTANT)));
        when(translations.find(MESSAGE_ID, "en")).thenAnswer(call -> Optional.ofNullable(saved.get()));
        doAnswer(call -> saved.compareAndSet(null, call.getArgument(2))).when(translations)
                .saveIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyString());
        when(translations.findExecutionId(MESSAGE_ID)).thenReturn(EXECUTION_ID);
    }
    
    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
    
    @Test
    @DisplayName("처음 요청하면 모델로 번역해 저장하고, 호출 기록은 답변을 만든 실행에 붙인다")
    void 번역해_저장한다() {
        when(llm.generate(any())).thenReturn("  " + ENGLISH + "\n");
        
        ChatMessageTranslationResponse response = service.translate(OWNER, MESSAGE_ID, "en");
        
        assertThat(response).isEqualTo(new ChatMessageTranslationResponse(MESSAGE_ID, "en", ENGLISH));
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(request.capture());
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.TRANSLATION);
        assertThat(request.getValue().executionId()).isEqualTo(EXECUTION_ID);
        assertThat(request.getValue().userPrompt()).isEqualTo(KOREAN);
        assertThat(request.getValue().systemPrompt()).contains("요금제 → plan", "유심 → USIM");
        verify(translations).saveIfAbsent(MESSAGE_ID, "en", ENGLISH, "exaone3.5:7.8b",
                ChatMessageTranslationService.PROMPT_VERSION);
        verify(limiter).acquire(OWNER);
    }
    
    @Test
    @DisplayName("저장한 번역이 있으면 모델을 부르지 않고 요청 제한에도 세지 않는다")
    void 저장한_번역을_돌려준다() {
        saved.set(ENGLISH);

        assertThat(service.translate(OWNER, MESSAGE_ID, "en").content()).isEqualTo(ENGLISH);
        verifyNoInteractions(llm, limiter);
    }
    
    @Test
    @DisplayName("다른 사람의 답변은 없는 메시지로 답한다")
    void 다른_사람의_답변은_찾을_수_없다() {
        assertError(() -> service.translate(new ChatActor(2L, null), MESSAGE_ID, "en"), ChatErrorCode.MESSAGE_NOT_FOUND);
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("사용자 질문은 번역하지 않는다")
    void 사용자_질문은_번역하지_않는다() {
        when(messages.findByIdWithSession(MESSAGE_ID)).thenReturn(Optional.of(message(ChatMessage.Role.USER)));

        assertError(() -> service.translate(OWNER, MESSAGE_ID, "en"), TranslationErrorCode.NOT_TRANSLATABLE);
        verifyNoInteractions(llm);
    }
    
    @Test
    @DisplayName("숫자가 바뀐 번역은 저장하지 않고 실패로 알린다")
    void 숫자가_바뀌면_실패다() {
        when(llm.generate(any())).thenReturn("The reissue fee is KRW 7,000.");

        assertError(() -> service.translate(OWNER, MESSAGE_ID, "en"), TranslationErrorCode.UNVERIFIED);
        verify(translations, never()).saveIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("한국어를 그대로 돌려준 결과는 번역으로 보지 않는다")
    void 번역하지_않은_결과는_실패다() {
        when(llm.generate(any())).thenReturn(KOREAN);

        assertError(() -> service.translate(OWNER, MESSAGE_ID, "en"), TranslationErrorCode.UNVERIFIED);
        verify(translations, never()).saveIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 답변을 동시에 요청하면 모델은 한 번만 부르고 두 요청 모두 같은 번역을 받는다")
    void 동시_요청은_한_번만_부른다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llm.generate(any())).thenAnswer(call -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ENGLISH;
        });
        executor = Executors.newFixedThreadPool(2);

        Future<ChatMessageTranslationResponse> first = executor.submit(() -> service.translate(OWNER, MESSAGE_ID, "en"));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        Future<ChatMessageTranslationResponse> second = executor.submit(() -> service.translate(OWNER, MESSAGE_ID, "en"));
        Thread.sleep(100);
        release.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS).content()).isEqualTo(ENGLISH);
        assertThat(second.get(5, TimeUnit.SECONDS).content()).isEqualTo(ENGLISH);
        verify(llm, times(1)).generate(any());
    }

    @Test
    @DisplayName("길이가 원문과 크게 다르면 내용을 지어내거나 빼먹은 것으로 본다")
    void 길이를_검사한다() {
        assertThat(ChatMessageTranslationService.rejectReason(KOREAN, ENGLISH)).isNull();
        assertThat(ChatMessageTranslationService.rejectReason(KOREAN, "7,700.")).isEqualTo("LENGTH");
        assertThat(ChatMessageTranslationService.rejectReason(KOREAN, ENGLISH.repeat(4))).isEqualTo("LENGTH");
    }
    
    private static ChatMessage message(ChatMessage.Role role) {
        return ChatMessage.builder()
                .messageId(MESSAGE_ID)
                .session(ChatSession.builder().userId(OWNER.userId()).title("번역").build())
                .sequenceNo(2)
                .role(role)
                .messageType(role == ChatMessage.Role.ASSISTANT ? ChatMessage.MessageType.ANSWER
                        : ChatMessage.MessageType.QUESTION)
                .content(KOREAN)
                .status(ChatMessage.Status.COMPLETED)
                .build();
    }
    
    private static void assertError(Runnable call, BaseErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GeneralException.class, 
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
