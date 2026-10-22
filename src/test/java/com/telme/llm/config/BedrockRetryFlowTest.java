package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.AnswerRetryGate;
import com.telme.llm.service.BedrockClient;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.llm.service.RecordingLlmClient;
import com.telme.llm.service.RetryingLlmClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

// 운영과 같은 순서로 이은 체인에 실제 AWS SDK와 가짜 서버로 재시도·사용자 재시도·엑사원 대체 흐름 확인
class BedrockRetryFlowTest {

    private static final String MODEL = "openai.gpt-oss-120b-1:0";
    private static final long FIRST_EXECUTION = 1L;
    private static final long RETRIED_EXECUTION = 2L;

    private HttpServer server;
    private BedrockRuntimeClient runtime;
    private final ConcurrentLinkedQueue<Integer> scriptedStatuses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final LlmClient exaone = mock(LlmClient.class);
    private LlmClient answerClient;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            int status = scriptedStatuses.isEmpty() ? 200 : scriptedStatuses.poll();
            byte[] body = (status == 200 ? ok() : error(status)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("x-amzn-ErrorType", errorType(status));
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        BedrockProperties properties = new BedrockProperties(true, "us-east-1", MODEL,
                Duration.ofSeconds(5), "low", 1000, 50, Duration.ofSeconds(1));
        runtime = BedrockConfig.clientBuilder(properties)
                .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        LlmClient bedrock = new RetryingLlmClient(
                new RecordingLlmClient(new BedrockClient(runtime, new BedrockRequestConverter(properties)),
                        mock(LlmGenerationRecorder.class), MODEL, "bedrock"),
                new LlmRetryProperties(2, Duration.ZERO));
        answerClient = new FallbackLlmClient(bedrock, exaone,
                new AnswerRetryGate(executionId -> executionId == RETRIED_EXECUTION));
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        server.stop(0);
    }

    @Test
    @DisplayName("한도 초과(429)는 자동으로 한 번 더 불러 성공하고, 사용자에게 재시도 중임을 알리며 엑사원은 부르지 않는다")
    void 순간_장애는_자동_재시도로_성공한다() {
        scriptedStatuses.add(429);
        Collected result = stream(FIRST_EXECUTION);

        assertThat(result.tokens).containsExactly("정상 답변");
        assertThat(result.error).isNull();
        assertThat(result.retries).isEqualTo(1);
        assertThat(requests).hasValue(2);
        verify(exaone, never()).stream(any(), any());
    }

    @Test
    @DisplayName("자동 재시도까지 실패하면 엑사원으로 넘기지 않고 사용자가 다시 시도할 수 있는 실패로 끝낸다")
    void 연속_실패는_사용자_재시도로_넘긴다() {
        scriptedStatuses.add(429);
        scriptedStatuses.add(503);
        Collected result = stream(FIRST_EXECUTION);

        assertThat(result.tokens).isEmpty();
        assertThat(errorCode(result)).isEqualTo(LlmErrorCode.RETRYABLE_FAILURE);
        assertThat(requests).hasValue(2);
        verify(exaone, never()).stream(any(), any());
    }

    @Test
    @DisplayName("원인 불명 서버 오류(500)는 자동 재시도 없이 바로 사용자 재시도로 넘긴다")
    void 원인_불명_오류는_자동_재시도하지_않는다() {
        scriptedStatuses.add(500);
        Collected result = stream(FIRST_EXECUTION);

        assertThat(errorCode(result)).isEqualTo(LlmErrorCode.RETRYABLE_FAILURE);
        assertThat(requests).hasValue(1);
        verify(exaone, never()).stream(any(), any());
    }

    @Test
    @DisplayName("권한·설정 오류(403)는 재시도하지 않고 바로 엑사원으로 대체한다")
    void 권한_오류는_바로_엑사원이다() {
        scriptedStatuses.add(403);
        stubExaone();
        Collected result = stream(FIRST_EXECUTION);

        assertThat(result.tokens).containsExactly("엑사원 답변");
        assertThat(result.error).isNull();
        assertThat(requests).hasValue(1);
    }

    @Test
    @DisplayName("사용자가 다시 시도한 실행이 또 실패하면 엑사원으로 대체한다")
    void 사용자_재시도_뒤_실패는_엑사원이다() {
        scriptedStatuses.add(503);
        scriptedStatuses.add(503);
        stubExaone();
        Collected result = stream(RETRIED_EXECUTION);

        assertThat(result.tokens).containsExactly("엑사원 답변");
        assertThat(result.error).isNull();
        assertThat(requests).hasValue(2);
    }

    @Test
    @DisplayName("사용자가 다시 시도하면(두 번째 실행) Bedrock이 회복된 경우 Bedrock 답변을 받는다")
    void 사용자_재시도로_회복한다() {
        assertThat(stream(RETRIED_EXECUTION).tokens).containsExactly("정상 답변");
        verify(exaone, never()).stream(any(), any());
    }

    private void stubExaone() {
        doAnswer(invocation -> {
            LlmStreamHandler handler = invocation.getArgument(1);
            handler.onToken("엑사원 답변");
            handler.onComplete();
            return null;
        }).when(exaone).stream(any(), any());
    }

    private Collected stream(long executionId) {
        LlmRequest request = LlmRequest.builder().executionId(executionId).taskType(TaskType.RAG_ANSWER)
                .systemPrompt("s").userPrompt("질문").build();
        Collected collected = new Collected();
        answerClient.stream(request, collected);
        return collected;
    }

    private static LlmErrorCode errorCode(Collected result) {
        assertThat(result.error).isInstanceOf(GeneralException.class);
        return (LlmErrorCode) ((GeneralException) result.error).getErrorCode();
    }

    private static String ok() {
        return """
                {"output":{"message":{"role":"assistant","content":[{"text":"정상 답변"}]}},
                 "stopReason":"end_turn","usage":{"inputTokens":1,"outputTokens":1,"totalTokens":2},
                 "metrics":{"latencyMs":1}}""";
    }

    private static String error(int status) {
        return "{\"message\":\"error " + status + "\"}";
    }

    private static String errorType(int status) {
        return switch (status) {
            case 429 -> "ThrottlingException";
            case 503 -> "ServiceUnavailableException";
            case 403 -> "AccessDeniedException";
            default -> "InternalServerException";
        };
    }

    private static final class Collected implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private Throwable error;
        private int retries;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            retries++;
        }
    }
}
