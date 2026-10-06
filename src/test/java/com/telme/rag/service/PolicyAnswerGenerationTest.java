package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class PolicyAnswerGenerationTest {
    private final LlmClient model = mock(LlmClient.class);
    private final LlmGenerationRecorder recorder = mock(LlmGenerationRecorder.class);
    private final EvidenceRelevanceChecker relevance = mock(EvidenceRelevanceChecker.class);
    private final AnswerGuard guard = spy(new AnswerGuard());
    private final LlmStreamHandler handler = mock(LlmStreamHandler.class);
    private final List<Map<String, Object>> inputs = new ArrayList<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private RagAnswerGenerator generator;

    @BeforeEach
    void 준비한다() {
        when(relevance.canAnswer(any(), any(), any())).thenReturn(true);
        ExecutionTrace trace =
                new ExecutionTrace() {
                    @Override
                    public void stage(Long id, String stage, Object value) {}

                    @Override
                    @SuppressWarnings("unchecked")
                    public void append(Long id, String stage, Object value) {
                        if (stage.equals("generationInputs")) {
                            inputs.add((Map<String, Object>) value);
                        } else if (stage.equals("guardResults")) {
                            results.add((Map<String, Object>) value);
                        }
                    }
                };
        generator =
                new RagAnswerGenerator(
                        model, new AnswerContextConverter(), guard, relevance, recorder, trace);
    }

    @Test
    @DisplayName("정책 답변은 생성 모델과 호출 기록 없이 동일 Guard를 통과한다")
    void 정책_답변은_모델을_부르지_않는다() {
        var answer = generator.generate(request("7,700원입니다."), handler);

        assertThat(answer.answer()).isEqualTo("7,700원입니다.");
        assertThat(inputs.getFirst()).containsEntry("answerSource", "POLICY_COMPOSED");
        assertThat(results.getFirst()).containsEntry("outcome", "KEPT");
        verifyNoInteractions(model, recorder);
        verify(handler).onToken(answer.answer());
        verify(handler).onComplete();
    }

    @Test
    @DisplayName("정책 문장 제거는 원시 모델 문장이 아닌 실제 정책 입력과 비교해 기록한다")
    void 정책_수정_결과를_실제_입력과_비교한다() {
        var answer = generator.generate(request("7,700원입니다. 홈페이지에서 신청하세요."), handler);

        assertThat(answer.answer()).isEqualTo("7,700원입니다.");
        assertThat(results.getFirst()).containsEntry("outcome", "MODIFIED");
        verifyNoInteractions(model, recorder);
    }

    @Test
    @DisplayName("정책 입력 자체가 안전 안내라면 Guard 유지로 기록한다")
    void 같은_안전_안내를_교체로_오인하지_않는다() {
        generator.generate(request(AnswerPromptTemplates.NO_EVIDENCE_ANSWER), handler);

        assertThat(results.getFirst()).containsEntry("outcome", "KEPT");
        verifyNoInteractions(model, recorder);
    }

    @Test
    @DisplayName("정책 답변에도 근거 없는 금액을 차단하고 안전 안내를 전달한다")
    void 정책_답변의_Guard_차단을_유지한다() {
        var answer = generator.generate(request("9,900원입니다."), handler);

        assertThat(answer.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(results.getFirst())
                .containsEntry("outcome", "REPLACED")
                .containsEntry("reason", "GUARD_EXCEPTION");
        verifyNoInteractions(model, recorder);
    }

    @Test
    @DisplayName("정책이 있어도 근거 적합성 검사 결과를 우회하지 않는다")
    void 정책_답변의_생성_전_검사를_유지한다() {
        when(relevance.canAnswer(any(), any(), any())).thenReturn(false);

        var answer = generator.generate(request("7,700원입니다."), handler);

        assertThat(answer.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(guard, never()).applyEvidencePolicy(any(), any(), any());
        verifyNoInteractions(model, recorder);
    }

    @Test
    @DisplayName("정책 답변 취소 시 검증 결과도 토큰·완료로 전달하지 않는다")
    void 정책_답변의_취소를_전달_전에_확인한다() {
        AtomicInteger progress = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (progress.incrementAndGet() == 3) {
                                throw new LlmStreamCancelledException();
                            }
                            return null;
                        })
                .when(handler)
                .onProgress();

        assertThatThrownBy(() -> generator.generate(request("7,700원입니다."), handler))
                .isInstanceOf(LlmStreamCancelledException.class);

        verify(handler, never()).onToken(any());
        verify(handler, never()).onComplete();
        verifyNoInteractions(model, recorder);
    }

    @Test
    @DisplayName("정책 답변이 없으면 기존 생성 모델을 호출하고 출처를 구분한다")
    void 일반_답변은_기존_모델을_호출한다() {
        doAnswer(
                invocation -> {
                    LlmStreamHandler stream = invocation.getArgument(1);
                    stream.onToken("7,700원입니다.");
                    stream.onComplete();
                    return null;
                })
                .when(model)
                .stream(any(), any());
        var policyRequest = request("7,700원입니다.");
        var request =
                new AnswerRequest(
                        policyRequest.executionId(),
                        policyRequest.consultRequestId(),
                        policyRequest.userQuery(),
                        Map.of(),
                        policyRequest.searchResults());

        var answer = generator.generate(request, handler);

        assertThat(answer.answer()).isEqualTo("7,700원입니다.");
        verify(model).stream(any(), any());
        assertThat(inputs.getFirst()).containsEntry("answerSource", "LLM_GENERATED");
        assertThat(results.getFirst()).containsEntry("answerSource", "LLM_GENERATED");
    }

    private AnswerRequest request(String policy) {
        var faq =
                new FaqSearchResponse(
                        1L, "USIM", "USIM", "유심 비용", "7,700원입니다.", 0.9, 1, null, 1, null);
        return AnswerRequest.builder()
                .executionId(1L)
                .consultRequestId(2L)
                .userQuery("유심 비용은 얼마인가요?")
                .searchResults(List.of(faq))
                .conditions(Map.of(AnswerPromptTemplates.PLAN_CHANGE_POLICY_ANSWER_KEY, policy))
                .build();
    }
}
