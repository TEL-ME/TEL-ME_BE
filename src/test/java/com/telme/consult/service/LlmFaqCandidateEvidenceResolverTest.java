package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmClient;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class LlmFaqCandidateEvidenceResolverTest {
    private final LlmClient llm = mock(LlmClient.class);
    private final LlmFaqCandidateEvidenceResolver resolver =
            new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper());
    private final FaqSearchResponse source = new FaqSearchResponse(65L, null, "test",
            "명의변경 시 필요한 서류", "양도인과 양수인의 신분증이 각각 필요합니다.",
            0.68, 1, null, 3, null);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void emptyInitialOutputDoesNotRestoreCandidate(String output) {
        when(llm.generate(any())).thenReturn(output);

        assertThat(resolver.resolve(null, "명의 변경 서류는?", List.of(source))).isNull();
        verify(llm).generate(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void emptyRetryOutputDoesNotRestoreCandidate(String output) {
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":65,"quote":"양도와 양수인의 신분증이 각각 필요합니다."}
                """, output);

        assertThat(resolver.resolve(null, "명의 변경 서류는?", List.of(source))).isNull();
        verify(llm, times(2)).generate(any());
    }

    @Test
    void returnsOnlySourceWithExactAnswerQuote() {
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":65,"quote":"양도인과 양수인의 신분증이 각각 필요합니다."}
                """);
        assertThat(resolver.resolve(31L, 11L, "명의 변경 서류는?", List.of(source))).isSameAs(source);
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(requests.capture());
        assertThat(requests.getValue().consultRequestId()).isEqualTo(11L);
    }

    @Test
    void acceptsSpacedCompoundPhraseAfterRoutingNormalizedItsSearchQuery() {
        var porting = new FaqSearchResponse(66L, null, "PORTING", "번호이동 신청 시 필요한 서류",
                "본인 신분증이 필요하며, 미납 요금은 완납하셔야 합니다.",
                0.68, 1, null, 1, null);
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":66,"quote":"본인 신분증이 필요하며, 미납 요금은 완납하셔야 합니다."}
                """);

        assertThat(resolver.resolve(null, "번호 이동 서류는 뭐고", List.of(porting))).isSameAs(porting);
    }

    @Test
    void dropsCompoundCandidateWhenRetryDeniesOrSwitchesCandidate() {
        var porting = new FaqSearchResponse(66L, null, "PORTING", "번호이동 신청 시 필요한 서류",
                "본인 신분증이 필요하며, 미납 요금은 완납하셔야 합니다.",
                0.68, 1, null, 1, null);
        String first = """
                {"answerable":true,"faqId":66,"quote":"본인 신분증과 미납 요금 확인이 필요합니다."}
                """;
        when(llm.generate(any())).thenReturn(first, """
                {"answerable":false,"faqId":null,"quote":null}
                """);
        assertThat(resolver.resolve(null, null, "번호 이동 서류는 뭐야", List.of(porting), true)).isNull();

        when(llm.generate(any())).thenReturn(first, """
                {"answerable":true,"faqId":99,"quote":"원문과 다른 인용문"}
                """);
        assertThat(resolver.resolve(null, null, "번호 이동 서류는 뭐야", List.of(porting), true)).isNull();
    }

    @Test
    void keepsVerifiedCompoundCandidateWhenQuoteRepairFails() {
        var porting = new FaqSearchResponse(66L, null, "PORTING", "번호이동 신청 시 필요한 서류",
                "본인 신분증이 필요하며, 미납 요금은 완납하셔야 합니다.",
                0.68, 1, null, 1, null);
        when(llm.generate(any())).thenReturn(
                """
                {"answerable":true,"faqId":66,"quote":"본인 신분증과 미납 요금 확인이 필요합니다."}
                """,
                """
                {"answerable":true,"faqId":66,"quote":"원문과 다른 인용문"}
                """);

        assertThat(resolver.resolve(null, null, "번호 이동 서류는 뭐야", List.of(porting), true))
                .isSameAs(porting);
        assertThat(resolver.resolve(null, null, "번호 이동 서류는 뭐야", List.of(porting), false))
                .isNull();
    }

    @Test
    void rejectsInventedQuoteAndExplicitAbstention() {
        when(llm.generate(any())).thenReturn(
                """
                {"answerable":true,"faqId":65,"quote":"양도인과 양수인의 여권이 각각 필요합니다."}
                """,
                """
                {"answerable":false,"faqId":null,"quote":null}
                """);
        assertThat(resolver.resolve(null, "명의 변경 서류는?", List.of(source))).isNull();
        assertThat(resolver.resolve(null, "무료 항공권은?", List.of(source))).isNull();
    }

    @Test
    void acceptsPrefixedIdAfterExactQuoteRepair() {
        when(llm.generate(any())).thenReturn(
                """
                {"answerable":true,"faqId":"ID 65","quote":"양도와 양수인의 신분증이 각각 필요합니다."}
                """,
                """
                {"answerable":true,"faqId":"ID 65","quote":"양도인과 양수인의 신분증이 각각 필요합니다."}
                """);

        assertThat(resolver.resolve(31L, 11L, "명의 변경 서류는?", List.of(source))).isSameAs(source);
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).generate(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.consultRequestId()).isEqualTo(11L);
            assertThat(request.taskType()).isEqualTo(TaskType.EVIDENCE_CHECK);
            // llm_generations.prompt_version은 varchar(30)이다.
            assertThat(request.promptVersion().length()).isLessThanOrEqualTo(30);
        });
    }

    @Test
    void rejectsFreeFeeFaqForFreeAirfareQuestion() {
        var fee = new FaqSearchResponse(423L, null, "test", "명의 변경 수수료",
                "네, 무료입니다. 수수료가 없습니다.", 0.6, 1, null, 3, null);
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":423,"quote":"네, 무료입니다. 수수료가 없습니다."}
                """);

        assertThat(resolver.resolve(null, "명의 변경 시 무료 해외 항공권을 주나요?", List.of(fee)))
                .isNull();
    }

    @Test
    void propagatesModelFailureInsteadOfReportingMissingEvidence() {
        var failure = new GeneralException(LlmErrorCode.CONNECTION_FAILED);
        when(llm.generate(any())).thenThrow(failure);

        assertThatThrownBy(() -> resolver.resolve(null, "명의 변경 서류는?", List.of(source)))
                .isSameAs(failure);
    }

    @Test
    void cancellationIsNotConvertedToMissingEvidence() {
        var cancelled = new LlmStreamCancelledException();
        when(llm.generate(any())).thenThrow(cancelled);

        assertThatThrownBy(() -> resolver.resolve(null, "명의 변경 서류는?", List.of(source)))
                .isSameAs(cancelled);
    }

    @Test
    void invalidJudgmentsDoNotRestoreCandidates() {
        when(llm.generate(any())).thenReturn("null", "[]", "{\"answerable\":\"true\"}", "{broken");

        for (int i = 0; i < 4; i++) {
            assertThat(resolver.resolve(null, "명의 변경 서류는?", List.of(source))).isNull();
        }
    }

    // TELME-184: 하위 질문 조각에 고객 원문을 함께 주고, 단어 검사 대신 고른 FAQ를 다시 확인한다
    @Test
    void contextModeSendsCustomerMessageAndSkipsQuestionTermCheck() {
        var verified = new java.util.ArrayList<String>();
        var withContext = new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper(),
                new LlmFaqCandidateEvidenceResolver.Options(false, true,
                        (executionId, consultRequestId, question, customerMessage, candidate) -> {
                            verified.add(question + "|" + customerMessage + "|" + candidate.faqId());
                            return true;
                        }));
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":65,"quote":"양도인과 양수인의 신분증이 각각 필요합니다."}
                """);

        // "궁금해요"는 FAQ 문답에 없어 단어 검사였다면 막혔다
        assertThat(withContext.resolve(31L, 11L, "서류도 궁금해요", "명의변경 하려는데 시간은 얼마나 걸려요? 서류도 궁금해요",
                List.of(source), true)).isSameAs(source);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(request.capture());
        assertThat(request.getValue().userPrompt())
                .startsWith("고객 원문: 명의변경 하려는데 시간은 얼마나 걸려요? 서류도 궁금해요\n이번에 답할 부분: 서류도 궁금해요\n");
        assertThat(request.getValue().systemPrompt()).contains("이번에 답할 부분");
        assertThat(request.getValue().promptVersion()).isEqualTo("faq-candidate-evidence-v3");
        assertThat(verified).containsExactly("서류도 궁금해요|명의변경 하려는데 시간은 얼마나 걸려요? 서류도 궁금해요|65");
    }

    @Test
    void verifierRejectionDropsSelectedCandidate() {
        var rejecting = new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper(),
                new LlmFaqCandidateEvidenceResolver.Options(false, true,
                        (executionId, consultRequestId, question, customerMessage, candidate) -> false));
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":65,"quote":"양도인과 양수인의 신분증이 각각 필요합니다."}
                """);

        assertThat(rejecting.resolve(null, null, "서류는요", "명의변경 서류는요", List.of(source), false)).isNull();
    }

    @Test
    void sameCustomerMessageKeepsSingleQuestionPrompt() {
        var withContext = new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper(),
                new LlmFaqCandidateEvidenceResolver.Options(false, true, null));
        when(llm.generate(any())).thenReturn("""
                {"answerable":false,"faqId":null,"quote":null}
                """);

        assertThat(withContext.resolve(null, null, "명의 변경 서류는?", "명의 변경 서류는?", List.of(source), false))
                .isNull();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(request.capture());
        assertThat(request.getValue().userPrompt()).startsWith("고객 질문: 명의 변경 서류는?\n");
        assertThat(request.getValue().promptVersion()).isEqualTo("faq-candidate-evidence-v2");
    }

    @Test
    void quoteRetryKeepsCustomerMessage() {
        var withContext = new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper(),
                new LlmFaqCandidateEvidenceResolver.Options(false, true, null));
        when(llm.generate(any())).thenReturn("""
                {"answerable":true,"faqId":65,"quote":"신분증 두 개"}
                """, """
                {"answerable":true,"faqId":65,"quote":"양도인과 양수인의 신분증이 각각 필요합니다."}
                """);

        assertThat(withContext.resolve(null, null, "서류는요", "명의변경 서류는요", List.of(source), false))
                .isSameAs(source);
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).generate(requests.capture());
        assertThat(requests.getAllValues().get(1).userPrompt()).startsWith("고객 원문: 명의변경 서류는요\n이번에 답할 부분: 서류는요\n");
    }
}
