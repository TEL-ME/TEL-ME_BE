package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// 가드 결과(KEPT/MODIFIED/REPLACED) 기록. gpt-oss가 문장 사이에 쓰는 좁은 공백(U+202F)만 바뀐 답은 고친 답이 아니다(TELME-212)
class RagAnswerGuardOutcomeTest {

    private static final String EVIDENCE = "7,700원입니다. 매장에서 바로 받으실 수 있습니다.";

    private final Trace trace = new Trace();

    @Test
    @DisplayName("문장 사이 좁은 공백만 보통 공백으로 바뀐 답은 그대로 둔 답으로 기록한다")
    void 좁은_공백만_바뀐_답은_KEPT다() {
        AnswerResult result = generate("재발급 비용은 7,700원입니다. 매장에서 바로 받으실 수 있습니다.");

        assertThat(result.answer()).isEqualTo("재발급 비용은 7,700원입니다. 매장에서 바로 받으실 수 있습니다.");
        assertThat(trace.guard).containsEntry("outcome", "KEPT");
    }

    @Test
    @DisplayName("문장을 지운 답은 고친 답으로 기록한다")
    void 문장을_지운_답은_MODIFIED다() {
        AnswerResult result = generate("재발급 비용은 7,700원입니다. 택배 배송은 무료입니다.");

        assertThat(result.answer()).isEqualTo("재발급 비용은 7,700원입니다.");
        assertThat(trace.guard).containsEntry("outcome", "MODIFIED");
    }

    private AnswerResult generate(String modelAnswer) {
        LlmClient client = new LlmClient() {
            @Override
            public String generate(LlmRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stream(LlmRequest request, LlmStreamHandler handler) {
                handler.onToken(modelAnswer);
                handler.onComplete();
            }
        };
        EvidenceRelevanceChecker checker = new EvidenceRelevanceChecker(
                client, new ObjectMapper(), new EvidenceCheckProperties(false, 300));
        RagAnswerGenerator generator = new RagAnswerGenerator(client, new AnswerContextConverter(), new AnswerGuard(),
                checker, Mockito.mock(LlmGenerationRecorder.class), trace);
        AnswerRequest request = AnswerRequest.builder()
                .executionId(7L)
                .userQuery("유심 재발급 비용")
                .conditions(Map.of())
                .searchResults(List.of(new FaqSearchResponse(1L, null, "USIM", "유심 재발급 비용이 얼마예요?",
                        EVIDENCE, 0.95, 1, LocalDate.of(2026, 10, 21), 1, null)))
                .build();
        return generator.generate(request, new Handler());
    }

    private static final class Handler implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private static final class Trace implements ExecutionTrace {
        private Map<String, Object> guard = Map.of();

        @Override
        @SuppressWarnings("unchecked")
        public void stage(Long executionId, String stage, Object value) {
            if ("guard".equals(stage)) {
                guard = (Map<String, Object>) value;
            }
        }

        @Override
        public void append(Long executionId, String stage, Object value) {
        }
    }
}
