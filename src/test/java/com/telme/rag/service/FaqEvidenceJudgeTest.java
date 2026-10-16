package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FaqEvidenceJudgeTest {

    @Test
    @DisplayName("판정 프롬프트(v4)·버전·종류와 FAQ 하나를 담아 temperature 0으로 부른다")
    void 판정_요청을_만든다() {
        StubClient client = new StubClient("{\"answerable\": true}", null);

        Verdict verdict = new FaqEvidenceJudge(client, new ObjectMapper())
                .judge(7L, 3L, "부가세 포함인가요?", "eSIM은 얼마예요?", "eSIM 발급 비용은 2,750원입니다.");

        assertThat(verdict).isEqualTo(Verdict.ANSWERABLE);
        LlmRequest sent = client.sent;
        assertThat(sent.executionId()).isEqualTo(7L);
        assertThat(sent.consultRequestId()).isEqualTo(3L);
        assertThat(sent.taskType()).isEqualTo(TaskType.EVIDENCE_CHECK);
        assertThat(sent.promptVersion()).isEqualTo("evidence-check-v4");
        assertThat(sent.systemPrompt()).isEqualTo(FaqEvidencePromptTemplates.SYSTEM_PROMPT);
        assertThat(sent.userPrompt()).isEqualTo(FaqEvidencePromptTemplates.userPrompt(
                "eSIM은 얼마예요?", "eSIM 발급 비용은 2,750원입니다.", "부가세 포함인가요?"));
        assertThat(sent.temperature()).isEqualTo(0.0);
        assertThat(sent.contextCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "{\"asked\": [\"부가세 포함 여부\"], \"found\": [false], \"answerable\": false} | NOT_ANSWERABLE",
            "판정: {\"answerable\": true} 입니다 | ANSWERABLE",
            "{\"answerable\": \"FALSE\"} | NOT_ANSWERABLE",
            "{\"answerable\": \"모름\"} | FAILED",
            "{\"asked\": []} | FAILED",
            "{깨진 JSON | FAILED",
            "JSON 없음 | FAILED"
    })
    @DisplayName("응답 해석: answerable만 보고, 읽을 수 없으면 실패로 돌려준다")
    void 응답을_해석한다(String response, Verdict expected) {
        assertThat(new FaqEvidenceJudge(new StubClient(response, null), new ObjectMapper())
                .judge(1L, null, "질문", "FAQ 질문", "FAQ 답변"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("호출이 실패하면 예외 대신 실패를 돌려준다")
    void 호출_실패는_실패() {
        StubClient client = new StubClient(null, new GeneralException(LlmErrorCode.TIMEOUT));

        assertThat(new FaqEvidenceJudge(client, new ObjectMapper()).judge(1L, null, "질문", "FAQ 질문", "FAQ 답변"))
                .isEqualTo(Verdict.FAILED);
    }

    private static final class StubClient implements LlmClient {

        private final String response;
        private final RuntimeException failure;
        private LlmRequest sent;

        private StubClient(String response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
        }

        @Override
        public String generate(LlmRequest request) {
            sent = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            throw new UnsupportedOperationException();
        }
    }
}
