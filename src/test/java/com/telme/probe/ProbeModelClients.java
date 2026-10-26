package com.telme.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.llm.service.OllamaClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

/**
 * 모델 비교 프로브가 함께 쓰는 클라이언트. 엑사원(Ollama)과 gpt-oss(Bedrock)를 같은 방식으로 부르고 호출마다 기록한다.
 * gpt-oss는 앱의 BedrockClient와 같은 요청 변환을 쓰되 종료 사유·토큰·걸린 시간을 남긴다.
 * 환경 변수: ANSWER_PROBE_OLLAMA_URL, ANSWER_PROBE_OLLAMA_MODEL, LLM_BEDROCK_REGION, ANSWER_PROBE_MAX_TOKENS,
 * ANSWER_PROBE_TIMEOUT_SECONDS, ANSWER_PROBE_REASONING(gpt-oss 추론 강도, 기본 low), ANSWER_PROBE_RECORD_REASONING(true면 생각 과정 기록).
 * Bedrock API 키는 AWS_BEARER_TOKEN_BEDROCK로 준다.
 */
final class ProbeModelClients {
    private static volatile BedrockRuntimeClient sharedRuntime;

    private ProbeModelClients() {
    }

    /** model은 exaone 또는 gptoss. recordOutput이 true이면 호출마다 모델이 돌려준 원문도 기록한다. */
    static LlmClient create(String model, ObjectMapper mapper, List<Map<String, Object>> calls, boolean recordOutput) {
        return switch (model) {
            case "exaone" -> new RecordingClient(exaone(mapper), calls, recordOutput);
            case "gptoss" -> new RecordingBedrockClient(bedrockRuntime(), bedrockConverter(), calls, recordOutput);
            default -> throw new IllegalArgumentException("알 수 없는 모델: " + model);
        };
    }

    private static LlmClient exaone(ObjectMapper mapper) {
        var properties = new LlmProperties(System.getenv().getOrDefault("ANSWER_PROBE_OLLAMA_MODEL", "exaone3.5:7.8b"),
                Duration.ofSeconds(5), Duration.ofSeconds(180), 8192);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        var rest = RestClient.builder()
                .baseUrl(System.getenv().getOrDefault("ANSWER_PROBE_OLLAMA_URL", "http://127.0.0.1:11434"))
                .requestFactory(factory).build();
        return new OllamaClient(rest, new OllamaRequestConverter(properties), mapper);
    }

    // 운영 설정(BedrockConfig·application.yml llm.bedrock)과 같은 값. 지역과 시간 제한은 평가용으로 바꾼다.
    // 운영 시간 제한 8초는 긴 생성에 짧을 수 있어 호출마다 걸린 시간을 결과에 남긴다
    private static BedrockProperties bedrockProperties() {
        return new BedrockProperties(true, System.getenv().getOrDefault("LLM_BEDROCK_REGION", "ap-northeast-1"),
                "openai.gpt-oss-120b-1:0",
                Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("ANSWER_PROBE_TIMEOUT_SECONDS", "60"))),
                System.getenv().getOrDefault("ANSWER_PROBE_REASONING", "low"),
                Integer.parseInt(System.getenv().getOrDefault("ANSWER_PROBE_MAX_TOKENS", "1000")), 50,
                Duration.ofSeconds(1));
    }

    private static BedrockRequestConverter bedrockConverter() {
        return new BedrockRequestConverter(bedrockProperties());
    }

    private static BedrockRuntimeClient bedrockRuntime() {
        if (sharedRuntime == null) {
            synchronized (ProbeModelClients.class) {
                if (sharedRuntime == null) {
                    var properties = bedrockProperties();
                    sharedRuntime = BedrockRuntimeClient.builder()
                            .region(Region.of(properties.region()))
                            .httpClientBuilder(Apache5HttpClient.builder()
                                    .maxConnections(properties.maxConnections())
                                    .connectionAcquisitionTimeout(properties.connectionAcquireTimeout()))
                            .overrideConfiguration(config -> config
                                    .apiCallTimeout(properties.timeout())
                                    .retryStrategy(AwsRetryStrategy.doNotRetry()))
                            .build();
                }
            }
        }
        return sharedRuntime;
    }

    /** 엑사원 호출을 기록한다. 종료 사유와 토큰은 Ollama 클라이언트가 알려 주지 않아 기록하지 않는다. */
    private static final class RecordingClient implements LlmClient {
        private final LlmClient base;
        private final List<Map<String, Object>> calls;
        private final boolean recordOutput;

        private RecordingClient(LlmClient base, List<Map<String, Object>> calls, boolean recordOutput) {
            this.base = base;
            this.calls = calls;
            this.recordOutput = recordOutput;
        }

        @Override
        public String generate(LlmRequest request) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("promptVersion", request.promptVersion());
            call.put("maxTokens", request.maxTokens());
            long started = System.nanoTime();
            try {
                String output = base.generate(request);
                if (recordOutput) {
                    call.put("output", output);
                }
                return output;
            } catch (RuntimeException failure) {
                call.put("error", failure.toString());
                throw failure;
            } finally {
                call.put("ms", (System.nanoTime() - started) / 1_000_000);
                synchronized (calls) {
                    calls.add(call);
                }
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            base.stream(request, handler);
        }
    }

    /** 앱의 BedrockClient와 같은 요청 변환과 응답 처리에, 종료 사유·토큰·걸린 시간 기록을 더했다. */
    private static final class RecordingBedrockClient implements LlmClient {
        private final BedrockRuntimeClient runtime;
        private final BedrockRequestConverter converter;
        private final List<Map<String, Object>> calls;
        private final boolean recordOutput;

        private RecordingBedrockClient(BedrockRuntimeClient runtime, BedrockRequestConverter converter,
                List<Map<String, Object>> calls, boolean recordOutput) {
            this.runtime = runtime;
            this.converter = converter;
            this.calls = calls;
            this.recordOutput = recordOutput;
        }

        @Override
        public String generate(LlmRequest request) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("promptVersion", request.promptVersion());
            long started = System.nanoTime();
            try {
                var converseRequest = converter.toConverseRequest(request);
                call.put("maxTokens", converseRequest.inferenceConfig().maxTokens());
                ConverseResponse response = runtime.converse(converseRequest);
                call.put("stopReason", String.valueOf(response.stopReason()));
                call.put("promptTokens", response.usage().inputTokens());
                call.put("outputTokens", response.usage().outputTokens());
                String output = response.output().message().content().stream().map(ContentBlock::text)
                        .filter(Objects::nonNull).collect(Collectors.joining()).strip();
                // ANSWER_PROBE_RECORD_REASONING=true면 gpt-oss의 생각 과정(reasoningContent)도 남긴다(앱은 버린다)
                if ("true".equals(System.getenv("ANSWER_PROBE_RECORD_REASONING"))) {
                    call.put("reasoning", response.output().message().content().stream()
                            .map(ContentBlock::reasoningContent).filter(Objects::nonNull)
                            .map(block -> block.reasoningText() == null ? "" : block.reasoningText().text())
                            .collect(Collectors.joining()).strip());
                }
                if (recordOutput) {
                    call.put("output", output);
                }
                return output;
            } catch (RuntimeException failure) {
                call.put("error", failure.toString());
                throw failure;
            } finally {
                call.put("ms", (System.nanoTime() - started) / 1_000_000);
                synchronized (calls) {
                    calls.add(call);
                }
            }
        }

        // 앱의 BedrockClient처럼 다 받은 뒤 한 번에 넘긴다
        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            String text;
            try {
                text = generate(request);
            } catch (RuntimeException failure) {
                handler.onError(failure);
                return;
            }
            handler.onToken(text);
            handler.onComplete();
        }
    }
}
