package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatQuestionTranslatorTest {

    private static final String ENGLISH =  "How much does a USIM reissue cost?";
    private static final String KOREAN = "유심 재발급 비용은 얼마인가요?";
    
    private final LlmClient llm = mock(LlmClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExecutionTrace trace = mock(ExecutionTrace.class);
    private final ChatQuestionTranslator translator = new ChatQuestionTranslator(llm, jdbc, trace);
    
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "How much does a USIM reissue cost? | true",
            "USIM 재발급 비용 | false",
            "5G 요금제 추천해줘 | false",
            "ㅇㅇ | false",
            "1234 | false",
            "***-****-1234 | false"
    })
    @DisplayName("한글이 한 글자도 없고 영문자가 있을 때만 번역한다")
    void 번역_대상을_고른다(String text, boolean expected) {
        assertThat(ChatQuestionTranslator.needsTranslation(text)).isEqualTo(expected);
    }
    
    @Test
    @DisplayName("한국어 질문은 모델을 부르지 않고 그대로 넘긴다")
    void 한국어_질문은_그대로다() {
        ChatProcessingCommand command = command("USIM 재발급 비용 알려줘");
        
        assertThat(translator.toKorean(command)).isSameAs(command);
        verifyNoInteractions(llm, jdbc, trace);
    }
    
    @Test
    @DisplayName("영어 질문은 한국어로 바꿔 넘기고, DB의 질문을 번역문으로 바꾸며 원문을 옮겨 둔다")
    void 영어_질문을_번역한다() {
        when(llm.generate(any())).thenReturn("  " + KOREAN + "\n");
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        
        ChatProcessingCommand translated = translator.toKorean(command(ENGLISH));
        
        assertThat(translated.content()).isEqualTo(KOREAN);
        assertThat(translated.executionId()).isEqualTo(10L);
        assertThat(translated.inputMessageId()).isEqualTo(30L);
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(request.capture());
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.TRANSLATION);
        assertThat(request.getValue().executionId()).isEqualTo(10L);
        assertThat(request.getValue().userPrompt()).isEqualTo(ENGLISH);
        assertThat(request.getValue().systemPrompt()).contains("plan, rate plan, mobile plan → 요금제");
        verify(jdbc).update(contains("original_content = content"), eq(KOREAN), eq(30L), eq(ENGLISH));
        verify(trace).stage(10L, ChatQuestionTranslator.STAGE, 
                Map.of("outcome", "TRANSLATED", "original", ENGLISH, "translated", KOREAN));
    }
    
    @Test
    @DisplayName("모델 호출이 실패하면 원문으로 상담을 이어 간다")
    void 번역_실패는_원문이다() {
        when(llm.generate(any())).thenThrow(new IllegalStateException("model down"));
        ChatProcessingCommand command = command(ENGLISH);
        
        assertThat(translator.toKorean(command)).isSameAs(command);
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        verify(trace).stage(10L, ChatQuestionTranslator.STAGE, Map.of("outcome", "FAILED"));
    }
    
    @Test
    @DisplayName("한글이 없는 결과는 번역으로 보지 않는다")
    void 한글이_없는_결과는_버린다() {
        when(llm.generate(any())).thenReturn(ENGLISH);
        ChatProcessingCommand command = command(ENGLISH);
        
        assertThat(translator.toKorean(command)).isSameAs(command);
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        verify(trace).stage(10L, ChatQuestionTranslator.STAGE, Map.of("outcome", "INVALID"));
    }
    
    @Test
    @DisplayName("DB의 질문이 이미 바뀌어 저장하지 못하면 원문으로 넘긴다")
    void 저장하지_못하면_원문이다() {
        when(llm.generate(any())).thenReturn(KOREAN);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        ChatProcessingCommand command = command(ENGLISH);
        
        assertThat(translator.toKorean(command)).isSameAs(command);
        verify(trace).stage(10L, ChatQuestionTranslator.STAGE, Map.of("outcome", "NOT_SAVED"));
    }
    
    private static ChatProcessingCommand command(String content) {
        return new ChatProcessingCommand(10L, 20L, 30L, content);
    }
}
