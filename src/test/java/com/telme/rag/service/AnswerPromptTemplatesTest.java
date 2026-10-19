package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnswerPromptTemplatesTest {

    @Test
    void promptUsesConsultationBodyWhileRequestKeepsOriginal() {
        String original = "안녕 바라야, 유심 재발급 비용 알려줘. 고마워";
        var request = new AnswerRequest(1L, original, Map.of(), List.of());

        String prompt = AnswerPromptTemplates.buildUserPrompt(request, "유심 재발급 비용은 7,700원입니다.");

        assertThat(prompt).contains("[고객 질문]\n유심 재발급 비용 알려줘.");
        assertThat(prompt).doesNotContain("바라야", "고마워", "[문맥을 반영한 현재 질문]");
        assertThat(request.userQuery()).isEqualTo(original);
    }

    @Test
    @DisplayName("페르소나와 친근한 거절 문구 외에는 기존 근거·출력 계약을 유지한다")
    void 일반_관계_제한_외_v3_전문을_유지한다() throws Exception {
        String baseline;
        try (var input = getClass().getResourceAsStream("/rag/prompt-relations/rag-answer-v3.txt")) {
            assertThat(input).isNotNull();
            baseline = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String relationAddition =
                "   FAQ 답변(A)에 각각 나열된 사실을 임의로 원인·결과, 비교 우위, 포함 관계로 연결하지 마십시오.\n"
                + "   해당 관계가 답변(A)에 명시된 경우에만 설명하십시오.\n";
        String channelAddition =
                "   신청하거나 처리하는 곳은 근거에 적힌 단어를 그대로 쓰고, 브랜드나 페이지 이름을 덧붙이지 마십시오.\n"
                + "   - 근거가 \"홈페이지로 신청하시면 됩니다\"이면 \"홈페이지\"라고만 쓰십시오.\n"
                + "   \"TELME샵\", \"유플러스샵\", \"이벤트 페이지\", \"공식 사이트\"는 어떤 경우에도 쓰지 마십시오.\n"
                + "   근거에 그 말이 없고, 쓰면 답변 전체가 버려집니다.\n";

        String copyAddition =
                "   금액과 기간은 근거에 적힌 글자를 그대로 복사해 쓰고, 다시 계산하거나 비슷한 숫자로 바꿔 쓰지 마십시오.\n";

        // 전문을 대조해 사례 지시와 무관한 역할·거절·길이·출력 규칙 변경도 감지한다.
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT).contains(relationAddition);
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT).contains(channelAddition);
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT).contains(copyAddition);
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT
                .replace(relationAddition, "")
                .replace(channelAddition, "")
                .replace(copyAddition, "")
                .replace("지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요",
                        "안내드릴 수 있는 정보가 없습니다")).isEqualTo(baseline);
        // 정체성 지침이 붙으면 무관 질문에 근거 없는 GROUNDED 답변이 늘어 FAQ 답변 프롬프트에는 넣지 않는다.
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT).doesNotContain("무러바라", "[정체성과 말투]");
        assertThat(AnswerPromptTemplates.PROMPT_VERSION).isEqualTo("rag-answer-v4.9");
        assertThat(AnswerPromptTemplates.NO_EVIDENCE_ANSWER).isEqualTo("지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요.");
    }

    @Test
    @DisplayName("실제 생성 요청에 새 전문과 버전을 전달하고 근거·조건·모델 옵션을 유지한다")
    void 생성_요청에_새_전문과_버전을_전달한다() {
        var source = new FaqSearchResponse(
                1L, "slot", "cat", "FAQ question", "7,700원입니다.", 0.9, null, null, 1, null);
        LlmClient client = mock(LlmClient.class);
        doAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken("7,700원입니다.");
            stream.onComplete();
            return null;
        }).when(client).stream(any(), any());
        EvidenceRelevanceChecker relevance = mock(EvidenceRelevanceChecker.class);
        when(relevance.canAnswer(any(), any(), any())).thenReturn(true);
        var generator = new RagAnswerGenerator(
                client, new AnswerContextConverter(), new AnswerGuard(), relevance,
                mock(LlmGenerationRecorder.class), ExecutionTrace.noop());
        var request = AnswerRequest.builder()
                .userQuery("cost?")
                .conditions(Map.of("location", "국내"))
                .searchResults(List.of(source))
                .build();
        LlmStreamHandler handler = mock(LlmStreamHandler.class);

        var result = generator.generate(request, handler);

        var capture = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).stream(capture.capture(), any());
        LlmRequest sent = capture.getValue();
        assertThat(sent.systemPrompt()).isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
        assertThat(sent.promptVersion()).isEqualTo("rag-answer-v4.9");
        assertThat(sent.userPrompt()).isEqualTo(AnswerPromptTemplates.buildUserPrompt(
                request, new AnswerContextConverter().toContext(List.of(source))));
        assertThat(sent.userPrompt()).contains("7,700원입니다.", "- 지역: 국내", "cost?");
        assertThat(sent.contextCount()).isEqualTo(1);
        assertThat(sent.taskType()).isEqualTo(TaskType.RAG_ANSWER);
        assertThat(sent.temperature()).isNull();
        assertThat(sent.maxTokens()).isNull();
        assertThat(result.answer()).isEqualTo("7,700원입니다.");
        verify(handler).onToken("7,700원입니다.");
        verify(handler).onComplete();
    }

    @Test
    @DisplayName("기존 근거에 있는 비용과 즉시 발급 문장은 동일한 Guard를 통과한다")
    void 근거에_있는_비용과_발급_안내를_보존한다() {
        var guard = new AnswerGuard();
        String evidence = "매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.";
        String answer = "매장 재발급 비용은 7,700원이며 즉시 발급됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, "매장 재발급 비용과 발급 시점은?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("일반·비교 질문 프롬프트 버전명은 llm_generations.prompt_version 길이 안에 들어간다")
    void 프롬프트_버전명은_저장_컬럼_길이를_넘지_않는다() {
        // 길이를 넘으면 생성 기록 저장이 경고 로그만 남기고 빠진다
        int promptVersionColumnLength = 100;

        assertThat(AnswerPromptTemplates.promptVersionFor("유심 재발급 비용 알려줘"))
                .hasSizeLessThanOrEqualTo(promptVersionColumnLength);
        assertThat(AnswerPromptTemplates.promptVersionFor("5G 라이트와 LTE 플러스를 비교해줘"))
                .endsWith("-comparison-v2")
                .hasSizeLessThanOrEqualTo(promptVersionColumnLength);
    }
}
