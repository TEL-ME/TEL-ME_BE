package com.telme.llm.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.telme.llm.config.LlmProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.OllamaChatRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;

class OllamaRequestConverterTest {

    private final OllamaRequestConverter converter = new OllamaRequestConverter(
            new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60), 8192));

    @Test
    void contextResolutionKeepsRoutingDefaultsAndExplicitLimits() {
        var defaults = converter.toChatRequest(LlmRequest.builder()
                .taskType(TaskType.CONTEXT_RESOLUTION).userPrompt("question").build(), false);
        assertThat(defaults.options().temperature()).isEqualTo(0.0);
        assertThat(defaults.options().numPredict()).isEqualTo(512);
        var explicit = converter.toChatRequest(LlmRequest.builder()
                .taskType(TaskType.CONTEXT_RESOLUTION).userPrompt("question")
                .temperature(0.0).maxTokens(256).build(), false);
        assertThat(explicit.options().numPredict()).isEqualTo(256);
    }

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
    @DisplayName("설정한 컨텍스트 창 크기를 num_ctx로 담는다")
    void 컨텍스트_창_크기를_담는다() {
        OllamaChatRequest result = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("유심이 뭐야?")
                .build(), false);

        assertThat(result.options().numCtx()).isEqualTo(8192);
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

        assertThat(result.options().temperature()).isEqualTo(0.0);
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

    @Test
    void 현재_대상_판정에만_JSON_Schema를_적용한다() {
        OllamaChatRequest reference = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("{\"currentQuestion\":\"그건 얼마야?\"}").format(ResponseFormat.JSON)
                .promptVersion("multiturn-resolution-v30-reference").build(), false);
        OllamaChatRequest other = converter.toChatRequest(LlmRequest.builder()
                .userPrompt("질문").format(ResponseFormat.JSON)
                .promptVersion("multiturn-resolution-v6").build(), false);

        assertThat(reference.format()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) reference.format()).get("additionalProperties")).isEqualTo(false);
        var properties = (Map<?, ?>) ((Map<?, ?>) reference.format()).get("properties");
        assertThat(properties.keySet().stream().map(Object::toString).toList()).containsExactly("reference");
        assertThat(other.format()).isEqualTo("json");
        assertThat(other.think()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"multiturn-resolution-v30-reference", "multiturn-resolution-v30-sources"})
    void qwen3_문맥_판정은_사고_모드를_끈다(String version) {
        OllamaRequestConverter qwen = new OllamaRequestConverter(
                new LlmProperties("qwen3:14b", Duration.ofSeconds(5), Duration.ofSeconds(60), 8192));
        OllamaChatRequest request = qwen.toChatRequest(LlmRequest.builder()
                .userPrompt("{\"sourceMessages\":[]}").format(ResponseFormat.JSON)
                .promptVersion(version).build(), false);

        assertThat(request.think()).isFalse();
    }

    @Test
    void 문맥_대상과_정정_연결을_구조화한다() {
        var result = converter.toChatRequest(LlmRequest.builder().userPrompt("{\"sourceMessages\":[{\"index\":1,\"content\":\"유심 재발급\"}]}")
                .format(ResponseFormat.JSON).promptVersion("multiturn-resolution-v30-sources").build(), false);
        var schema = (Map<?, ?>) result.format();
        var properties = (Map<?, ?>) schema.get("properties");
        assertThat(properties.keySet().stream().map(Object::toString).toList())
                .containsExactly("sources", "referenceScope", "anchorIndex");
        var sourceItems = (Map<?, ?>) ((Map<?, ?>) properties.get("sources")).get("items");
        assertThat(sourceItems.get("additionalProperties")).isEqualTo(false);
        assertThat(sourceItems.get("required")).isEqualTo(List.of("index", "targetCount", "parentIndex"));
        var array = (Map<?, ?>) properties.get("sources");
        assertThat(array.get("minItems")).isEqualTo(1);
        assertThat(array.get("maxItems")).isEqualTo(1);
    }

    @Test
    void 현재_대상_판정에는_별도_출처_선택을_섞지_않는다() {
        var result = converter.toChatRequest(LlmRequest.builder().userPrompt("질문")
                .format(ResponseFormat.JSON).promptVersion("multiturn-resolution-v30-reference").build(), false);
        var schema = (Map<?, ?>) result.format();
        assertThat(schema.get("required")).isEqualTo(List.of("reference"));
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }
}
