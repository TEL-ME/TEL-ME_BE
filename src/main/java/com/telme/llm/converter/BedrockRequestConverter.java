package com.telme.llm.converter;

import com.telme.llm.config.BedrockProperties;
import com.telme.llm.dto.req.LlmRequest;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

// LlmRequest → Bedrock Converse 요청
// Converse에는 JSON 모드가 없어 format·responseSchema는 보내지 않는다. JSON 출력은 프롬프트로 요구한다
public class BedrockRequestConverter {

    // 판정은 같은 입력에 같은 결과가 나와야 해서 요청에 값이 없으면 0으로 보낸다
    private static final float DEFAULT_TEMPERATURE = 0.0f;

    private final BedrockProperties properties;

    public BedrockRequestConverter(BedrockProperties properties) {
        this.properties = properties;
    }

    public ConverseRequest toConverseRequest(LlmRequest request) {
        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(properties.model())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(request.userPrompt()))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(request.maxTokens() == null ? properties.maxTokens() : request.maxTokens())
                        .temperature(request.temperature() == null
                                ? DEFAULT_TEMPERATURE : request.temperature().floatValue())
                        .build());
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            builder.system(SystemContentBlock.fromText(request.systemPrompt()));
        }
        // 요청에 추론 강도가 있으면 그 값을, 없으면 설정값을 보낸다(답변 생성의 금액 재시도가 medium으로 다시 부른다)
        String reasoningEffort = request.reasoningEffort() != null && !request.reasoningEffort().isBlank()
                ? request.reasoningEffort() : properties.reasoningEffort();
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.additionalModelRequestFields(Document.mapBuilder()
                    .putString("reasoning_effort", reasoningEffort)
                    .build());
        }
        return builder.build();
    }
}
