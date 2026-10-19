package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ChatEndSummaryModelClient;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockAvailabilitySdkTest {
    private final LlmAttemptClient fallback = mock(LlmAttemptClient.class);
    private final LlmGenerationRecorder recorder = mock(LlmGenerationRecorder.class);
    private final Clock clock = Clock.systemUTC();
    private final LlmRequest request = LlmRequest.builder().taskType(TaskType.SUMMARY)
            .userPrompt("마스킹된 유심 상담").maxTokens(768).build();

    @Test
    @DisplayName("실제 SDK와 닫힌 로컬 접속 경로의 통신 장애를 엑사원으로 한 번 대체한다")
    void 실제_통신_실패가_대체_모델까지_전달된다() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (var runtime = createRuntime(port, () -> AwsBasicCredentials.create("local-test", "local-test"),
                new BedrockTransportClientBuilder(Apache5HttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(1))))) {
            when(fallback.generate(request, 2)).thenReturn("대체 응답");

            assertThat(createModel(runtime).generate(request, Instant.now().plusSeconds(300))).isEqualTo("대체 응답");

            verify(fallback).generate(request, 2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("인증 정보 조회 실패는 내부에 접속 오류가 있어도 모델 통신 장애로 우회하지 않는다")
    void 인증_정보_조회_실패를_우회하지_않는다(boolean nestedConnection) {
        var transport = mock(SdkHttpClient.class);
        var builder = mock(SdkHttpClient.Builder.class);
        when(builder.buildWithDefaults(any())).thenReturn(transport);
        var failure = SdkClientException.builder().message("합성 인증 정보 조회 실패")
                .cause(nestedConnection ? new ConnectException("합성 인증 서버 접속 실패") : null).build();
        try (var runtime = createRuntime(1, () -> { throw failure; }, new BedrockTransportClientBuilder(builder))) {
            assertThatThrownBy(() -> createModel(runtime).generate(request, Instant.now().plusSeconds(300)))
                    .isInstanceOfSatisfying(GeneralException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LlmErrorCode.CONNECTION_FAILED));

            verify(transport, never()).prepareRequest(any());
            verifyNoInteractions(fallback);
        }
    }

    @Test
    @DisplayName("실제 SDK가 감싼 보안 연결 실패도 대체 모델로 우회하지 않는다")
    void 인증서_실패는_우회하지_않는다() throws IOException {
        var transport = mock(SdkHttpClient.class);
        var builder = mock(SdkHttpClient.Builder.class);
        var prepared = mock(ExecutableHttpRequest.class);
        when(builder.buildWithDefaults(any())).thenReturn(transport);
        when(transport.prepareRequest(any())).thenReturn(prepared);
        when(prepared.call()).thenThrow(new SSLHandshakeException("합성 인증서 실패"));
        try (var runtime = createRuntime(1, () -> AwsBasicCredentials.create("local-test", "local-test"),
                new BedrockTransportClientBuilder(builder))) {
            assertThatThrownBy(() -> createModel(runtime).generate(request, Instant.now().plusSeconds(300)))
                    .isInstanceOf(GeneralException.class);

            verifyNoInteractions(fallback);
            verify(prepared).call();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 401, 403})
    @DisplayName("성공 응답 본문이 끊기면 대체하지만 인증·권한 응답 본문 끊김은 우회하지 않는다")
    void 응답_본문_통신_장애와_인증_오류를_구분한다(int statusCode) throws IOException {
        var transport = mock(SdkHttpClient.class);
        var builder = mock(SdkHttpClient.Builder.class);
        var prepared = mock(ExecutableHttpRequest.class);
        when(builder.buildWithDefaults(any())).thenReturn(transport);
        when(transport.prepareRequest(any())).thenReturn(prepared);
        var broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new SocketException("합성 응답 본문 연결 끊김");
            }
        };
        when(prepared.call()).thenReturn(HttpExecuteResponse.builder()
                .response(SdkHttpResponse.builder().statusCode(statusCode)
                        .putHeader("Content-Type", "application/json").build())
                .responseBody(AbortableInputStream.create(broken)).build());
        try (var runtime = createRuntime(1, () -> AwsBasicCredentials.create("local-test", "local-test"),
                new BedrockTransportClientBuilder(builder))) {
            if (statusCode == 200) {
                when(fallback.generate(request, 2)).thenReturn("대체 응답");

                assertThat(createModel(runtime).generate(request, Instant.now().plusSeconds(300))).isEqualTo("대체 응답");

                verify(fallback).generate(request, 2);
            } else {
                assertThatThrownBy(() -> createModel(runtime).generate(request, Instant.now().plusSeconds(300)))
                        .isInstanceOf(GeneralException.class);

                verifyNoInteractions(fallback);
            }
        }
    }

    @Test
    @DisplayName("실제 SDK의 정상 JSON 본문은 변경 없이 읽고 엑사원을 호출하지 않는다")
    void 정상_응답_본문을_그대로_전달한다() throws IOException {
        var transport = mock(SdkHttpClient.class);
        var builder = mock(SdkHttpClient.Builder.class);
        var prepared = mock(ExecutableHttpRequest.class);
        when(builder.buildWithDefaults(any())).thenReturn(transport);
        when(transport.prepareRequest(any())).thenReturn(prepared);
        String body = """
                {"output":{"message":{"role":"assistant","content":[{"text":"정상 응답"}]}},
                 "stopReason":"end_turn","usage":{"inputTokens":1,"outputTokens":1,"totalTokens":2},
                 "metrics":{"latencyMs":1}}
                """;
        when(prepared.call()).thenReturn(HttpExecuteResponse.builder()
                .response(SdkHttpResponse.builder().statusCode(200)
                        .putHeader("Content-Type", "application/json").build())
                .responseBody(AbortableInputStream.create(new ByteArrayInputStream(
                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).build());
        try (var runtime = createRuntime(1, () -> AwsBasicCredentials.create("local-test", "local-test"),
                new BedrockTransportClientBuilder(builder))) {
            assertThat(createModel(runtime).generate(request, Instant.now().plusSeconds(300)))
                    .isEqualTo("정상 응답");

            verifyNoInteractions(fallback);
        }
    }

    private BedrockRuntimeClient createRuntime(int port, AwsCredentialsProvider credentials,
            SdkHttpClient.Builder<?> httpBuilder) {
        return BedrockRuntimeClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials)
                .endpointOverride(URI.create("http://127.0.0.1:" + port)).httpClientBuilder(httpBuilder)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(3))
                        .retryStrategy(AwsRetryStrategy.doNotRetry())).build();
    }

    private ChatEndSummaryModelClient createModel(BedrockRuntimeClient runtime) {
        var converter = new BedrockRequestConverter(new BedrockProperties(true, "us-east-1",
                "openai.gpt-oss-120b-1:0", Duration.ofSeconds(3), "low", 768, 2, Duration.ofSeconds(1)));
        var bedrock = new RecordingLlmClient(new BedrockClient(runtime, converter, true), recorder,
                "openai.gpt-oss-120b-1:0", "bedrock");
        return new ChatEndSummaryModelClient(bedrock, fallback, clock, Duration.ofSeconds(3), Duration.ofSeconds(65));
    }
}
