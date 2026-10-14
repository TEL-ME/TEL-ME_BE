package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.BedrockClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.FaqEvidenceJudge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockConfigTest {

    // 답변 생성용 LlmClient가 하나 있는 상태(Fake 또는 @Primary Ollama)를 흉내 낸다
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BedrockConfig.class)
            .withBean(LlmGenerationRecorder.class, () -> mock(LlmGenerationRecorder.class))
            .withBean("answerLlmClient", LlmClient.class, AnswerClient::new);

    @Test
    @DisplayName("기본은 꺼져 있어 Bedrock 빈을 만들지 않고, 키가 없어도 뜬다")
    void 기본은_꺼져_있다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BedrockRuntimeClient.class);
            assertThat(context).doesNotHaveBean(BedrockConfig.JUDGE_LLM_CLIENT);
        });
    }

    @Test
    @DisplayName("켜면 판정 전용 빈을 만들지만, 타입으로 LlmClient를 받는 곳에는 들어가지 않는다")
    void 켜도_답변용_LlmClient를_대신하지_않는다() {
        runner.withPropertyValues("llm.bedrock.enabled=true", "llm.bedrock.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean(BedrockConfig.JUDGE_LLM_CLIENT);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnswerClient.class);
                });
    }

    @Test
    @DisplayName("켜면 판정기가 만들어지고, 답변용이 아닌 판정 전용 LLM을 받는다")
    void 판정기는_판정_전용_LLM을_받는다() {
        runner.withUserConfiguration(FaqEvidenceJudge.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("llm.bedrock.enabled=true", "llm.bedrock.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(FaqEvidenceJudge.class);
                    assertThat(context.getBean(FaqEvidenceJudge.class))
                            .extracting("llmClient")
                            .isSameAs(context.getBean(BedrockConfig.JUDGE_LLM_CLIENT));
                });
        runner.withUserConfiguration(FaqEvidenceJudge.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).doesNotHaveBean(FaqEvidenceJudge.class));
    }

    @Test
    @DisplayName("호출 제한 기본은 8초, 동시 연결 수는 50, 연결 대기는 1초이고 연결 수가 1 미만이면 기동에 실패한다")
    void 연결_설정_기본값과_검증() {
        runner.run(context -> {
            BedrockProperties properties = context.getBean(BedrockProperties.class);
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(8));
            assertThat(properties.maxConnections()).isEqualTo(50);
            assertThat(properties.connectionAcquireTimeout()).isEqualTo(Duration.ofSeconds(1));
        });
        runner.withPropertyValues("llm.bedrock.max-connections=0").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("연결이 모두 쓰이는 중이면 응답을 기다리지 않고 연결 대기 시간 뒤 바로 실패한다")
    void 연결이_꽉_차면_짧게_기다리고_실패한다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        CountDownLatch firstArrived = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            firstArrived.countDown();
            sleep(1500);
            byte[] body = """
                    {"output":{"message":{"role":"assistant","content":[{"text":"answerable true"}]}},
                     "stopReason":"end_turn","usage":{"inputTokens":1,"outputTokens":1,"totalTokens":2},
                     "metrics":{"latencyMs":1}}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        BedrockProperties properties = new BedrockProperties(true, "us-east-1", "openai.gpt-oss-120b-1:0",
                Duration.ofSeconds(5), "low", 1000, 1, Duration.ofMillis(200));
        // 운영과 같은 설정(연결 수, 연결 대기, 재시도 끔)에 주소와 자격 증명만 바꾼다
        try (BedrockRuntimeClient runtime = BedrockConfig.clientBuilder(properties)
                .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
            BedrockClient client = new BedrockClient(runtime, new BedrockRequestConverter(properties));
            LlmRequest request = LlmRequest.builder().taskType(TaskType.EVIDENCE_CHECK).userPrompt("질문").build();

            CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> client.generate(request));
            // 첫 요청이 서버에 도착해 하나뿐인 연결을 쓰고 있는 것을 확인한 뒤 두 번째를 보낸다
            assertThat(firstArrived.await(5, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.generate(request)).isInstanceOf(GeneralException.class);
            long waitedMs = (System.nanoTime() - started) / 1_000_000;

            assertThat(waitedMs).isLessThan(1000);
            assertThat(first.get(5, TimeUnit.SECONDS)).contains("answerable");
        } finally {
            server.stop(0);
            serverThreads.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class AnswerClient implements LlmClient {

        @Override
        public String generate(LlmRequest request) {
            return "답변";
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onComplete();
        }
    }
}
