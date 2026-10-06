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
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LlmFaqCandidateEvidenceResolverTest {
    private final LlmClient llm = mock(LlmClient.class);
    private final LlmFaqCandidateEvidenceResolver resolver =
            new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper());
    private final FaqSearchResponse source = new FaqSearchResponse(65L, null, "test",
            "명의변경 시 필요한 서류", "양도인과 양수인의 신분증이 각각 필요합니다.",
            0.68, 1, null, 3, null);

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
}
