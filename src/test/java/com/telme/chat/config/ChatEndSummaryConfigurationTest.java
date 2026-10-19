package com.telme.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.telme.chat.service.ChatEndSummaryModel;
import com.telme.chat.service.ChatEndSummaryModelClient;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class ChatEndSummaryConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatEndSummaryConfiguration.class)
            .withBean(LlmClient.class, AnswerClient::new)
            .withBean(LlmGenerationRecorder.class, () -> mock(LlmGenerationRecorder.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withPropertyValues("ollama.url=http://127.0.0.1:1");

    @Test
    @DisplayName("베드락 선택 시 요약 전용 모델과 독립된 엑사원 대체 설정을 사용한다")
    void 베드락_선택은_요약_전용으로_동작한다() {
        runner.withPropertyValues("chat.end-summary.enabled=true", "chat.end-summary.model.provider=bedrock")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ChatEndSummaryModel.class))
                            .isInstanceOf(ChatEndSummaryModelClient.class);
                    var properties = context.getBean(ChatEndSummaryModelProperties.class);
                    assertThat(properties.provider()).isEqualTo(ChatEndSummaryModelProperties.Provider.BEDROCK);
                    assertThat(properties.model()).isEqualTo("openai.gpt-oss-120b-1:0");
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(8));
                    assertThat(properties.fallbackModel()).isEqualTo("exaone3.5:7.8b");
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnswerClient.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "ollama", "judge-enabled"})
    @DisplayName("기본값과 엑사원 선택은 답변·판정 모델과 분리된 로컬 요약 모델을 호출한다")
    void 기본_요약은_답변_모델과_분리된_엑사원을_사용한다(String selection) throws IOException {
        var received = new AtomicReference<JsonNode>();
        var server = createOllamaServer(received);
        server.start();
        try {
            var selected = runner.withPropertyValues("chat.end-summary.enabled=true",
                    "ollama.url=http://127.0.0.1:" + server.getAddress().getPort());
            if (selection.equals("ollama")) {
                selected = selected.withPropertyValues("chat.end-summary.model.provider=ollama");
            } else if (selection.equals("judge-enabled")) {
                selected = selected.withUserConfiguration(BedrockConfig.class)
                        .withPropertyValues("llm.bedrock.enabled=true");
            }
            selected.run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(ChatEndSummaryModelProperties.class);
                assertThat(properties.provider()).isEqualTo(ChatEndSummaryModelProperties.Provider.OLLAMA);
                var request = LlmRequest.builder().taskType(TaskType.SUMMARY).userPrompt("상담 기록").build();
                assertThat(context.getBean(ChatEndSummaryModel.class).generate(request,
                        Instant.now().plusSeconds(300))).isEqualTo("엑사원 요약 응답");
                assertThat(received.get().path("model").asText()).isEqualTo("exaone3.5:7.8b");
                assertThat(received.get().path("options").path("num_ctx").asInt()).isEqualTo(8192);
                assertThat(context.getBeanFactory().containsSingleton("chatEndSummaryBedrockRuntime")).isFalse();
                assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnswerClient.class);
                if (selection.equals("judge-enabled")) {
                    assertThat(context).hasBean(BedrockConfig.JUDGE_LLM_CLIENT);
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("요약이 꺼져 있으면 자격 증명과 네트워크 없이 전용 런타임 생성을 생략한다")
    void 요약이_꺼져_있으면_런타임을_만들지_않는다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanFactory().containsSingleton("chatEndSummaryBedrockRuntime")).isFalse();
        });
    }

    @Test
    @DisplayName("기본 모델로 되돌리는 설정은 기존 모델을 사용하고 베드락을 생성하지 않는다")
    void 기본_모델_복구_설정은_독립적으로_동작한다() {
        runner.withPropertyValues("chat.end-summary.enabled=true", "chat.end-summary.model.provider=primary")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var request = LlmRequest.builder().taskType(TaskType.SUMMARY).userPrompt("상담 기록").build();
                    assertThat(context.getBean(ChatEndSummaryModel.class).generate(request,
                            Instant.now().plusSeconds(300))).isEqualTo("기본 모델 응답");
                    assertThat(context.getBeanFactory().containsSingleton("chatEndSummaryBedrockRuntime")).isFalse();
                });
    }

    @Test
    @DisplayName("판정기와 요약을 함께 켜도 각자 런타임을 쓰고 기본 모델 주입을 바꾸지 않는다")
    void 판정기와_요약_연결을_분리한다() {
        runner.withUserConfiguration(BedrockConfig.class)
                .withPropertyValues("chat.end-summary.enabled=true", "llm.bedrock.enabled=true",
                        "chat.end-summary.model.provider=bedrock")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnswerClient.class);
                    var summary = context.getBean(ChatEndSummaryModel.class);
                    Object summaryRecording = ReflectionTestUtils.getField(summary, "bedrock");
                    Object summaryClient = ReflectionTestUtils.getField(summaryRecording, "delegate");
                    Object judge = context.getBean(BedrockConfig.JUDGE_LLM_CLIENT);
                    Object judgeClient = ReflectionTestUtils.getField(judge, "delegate");
                    assertThat(ReflectionTestUtils.getField(summaryClient, "client"))
                            .isSameAs(context.getBean("chatEndSummaryBedrockRuntime"));
                    assertThat(ReflectionTestUtils.getField(judgeClient, "client"))
                            .isSameAs(context.getBean("bedrockRuntimeClient"));
                    assertThat(ReflectionTestUtils.getField(summaryClient, "requireCompleteResponse")).isEqualTo(true);
                    assertThat(ReflectionTestUtils.getField(judgeClient, "requireCompleteResponse")).isEqualTo(false);
                    assertThat(context.getBean("chatEndSummaryBedrockRuntime"))
                            .isNotSameAs(context.getBean("bedrockRuntimeClient"));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout=0s", "timeout=-1s", "timeout=1ns", "fallback-connect-timeout=0s",
            "fallback-read-timeout=-1s", "fallback-read-timeout=1ns", "fallback-context-size=0", "provider=unknown"})
    @DisplayName("알 수 없는 모델 선택과 잘못된 호출 예산은 기동 시 거부한다")
    void 잘못된_설정을_거부한다(String value) {
        runner.withPropertyValues("chat.end-summary.model." + value)
                .run(context -> assertThat(context).hasFailed());
    }

    private HttpServer createOllamaServer(AtomicReference<JsonNode> received) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            received.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = """
                    {"message":{"role":"assistant","content":"엑사원 요약 응답"},"done":true}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        return server;
    }

    private static final class AnswerClient implements LlmClient {
        @Override
        public String generate(LlmRequest request) {
            return "기본 모델 응답";
        }

        @Override
        public void stream(LlmRequest request, com.telme.llm.service.LlmStreamHandler handler) {
            handler.onToken(generate(request));
            handler.onComplete();
        }
    }
}
