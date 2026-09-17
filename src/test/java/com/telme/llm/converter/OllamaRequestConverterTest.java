package com.telme.llm.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.telme.llm.config.LlmProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.OllamaChatRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;

class OllamaRequestConverterTest {

    private final OllamaRequestConverter converter = new OllamaRequestConverter(
            new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60)));

    @Test
    @DisplayName("규칙과 질문을 system, user 순서로 담는다")
    void 규칙과_질문을_순서대로_담는다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .systemPrompt("FAQ 내용만 보고 답해")
                .userPrompt("유심이 뭐야?")
                .build(), false);

        assertThat(result.messages())
                .extracting(OllamaChatRequest.Message::role, OllamaChatRequest.Message::content)
                .containsExactly(
                        tuple("system", "FAQ 내용만 보고 답해"),
                        tuple("user", "유심이 뭐야?"));
    }

    @Test
    @DisplayName("규칙이 없으면 질문만 담는다")
    void 규칙이_없으면_질문만_담는다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("유심이 뭐야?")
                .build(), false);

        assertThat(result.messages())
                .extracting(OllamaChatRequest.Message::role)
                .containsExactly("user");
    }

    @Test
    @DisplayName("JSON 요청이면 format을 json으로 보낸다")
    void JSON_요청이면_format이_json이다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("분류해줘")
                .format(ResponseFormat.JSON)
                .build(), false);

        assertThat(result.format()).isEqualTo("json");
    }

    @Test
    @DisplayName("TEXT 요청이면 format을 비워서 보낸다")
    void TEXT_요청이면_format이_비어있다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("유심이 뭐야?")
                .build(), false);

        assertThat(result.format()).isNull();
    }

    @Test
    @DisplayName("자유도와 최대 길이를 안 적으면 작업 종류별 기본값을 쓴다")
    void 값이_없으면_작업_종류별_기본값을_쓴다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt("유심이 뭐야?")
                .build(), false);

        assertThat(result.options().temperature()).isEqualTo(0.2);
        assertThat(result.options().numPredict()).isEqualTo(1024);
    }

    @Test
    @DisplayName("직접 적은 값이 기본값보다 우선한다")
    void 직접_적은_값이_우선한다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .taskType(TaskType.RAG_ANSWER)
                .userPrompt("유심이 뭐야?")
                .temperature(0.7)
                .maxTokens(100)
                .build(), false);

        assertThat(result.options().temperature()).isEqualTo(0.7);
        assertThat(result.options().numPredict()).isEqualTo(100);
    }

    @Test
    @DisplayName("설정의 모델명과 스트리밍 여부를 담는다")
    void 모델명과_스트리밍_여부를_담는다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("유심이 뭐야?")
                .build(), true);

        assertThat(result.model()).isEqualTo("exaone3.5:7.8b");
        assertThat(result.stream()).isTrue();
    }
}
