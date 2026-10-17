package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.consult.service.LlmFaqCandidateEvidenceResolver.Options;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.rag.service.FaqEvidenceJudge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceResolverCandidateOptionsTest {

    private final FaqSearchResponse faq = new FaqSearchResponse(65L, null, "NAME_CHANGE",
            "명의변경 시 필요한 서류", "양도인과 양수인의 신분증이 각각 필요합니다.", 0.6, 1, null, 1, null);

    private static final EvidenceResolverProperties ON = new EvidenceResolverProperties(true, true, true);

    @Test
    @DisplayName("설정이 꺼져 있거나 판정기가 없으면 예전처럼 단어 검사로 막는다")
    void 꺼져_있거나_판정기가_없으면_단어_검사를_유지한다() {
        assertThat(EvidenceResolverConfiguration.candidateOptions(ON, null)).isSameAs(Options.CURRENT);
        assertThat(EvidenceResolverConfiguration.candidateOptions(new EvidenceResolverProperties(true, true, false),
                mock(FaqEvidenceJudge.class))).isSameAs(Options.CURRENT);
    }

    @Test
    @DisplayName("설정을 주지 않으면 켜져 있고, 설정 이름으로 끈다")
    void 기본은_켜져_있고_설정으로_끈다() {
        assertThat(bind(java.util.Map.of()).candidateJudge()).isTrue();
        assertThat(bind(java.util.Map.of("telme.consult.evidence-resolver.candidate-judge", "false")).candidateJudge())
                .isFalse();
    }

    private static EvidenceResolverProperties bind(java.util.Map<String, Object> values) {
        return new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values))
                .bindOrCreate("telme.consult.evidence-resolver", EvidenceResolverProperties.class);
    }

    @Test
    @DisplayName("판정기가 있으면 원문을 함께 주고 고른 FAQ를 원문과 함께 다시 판정한다")
    void 판정기가_있으면_원문과_함께_다시_판정한다() {
        FaqEvidenceJudge judge = mock(FaqEvidenceJudge.class);
        when(judge.judge(any(), any(), any(), any(), any())).thenReturn(FaqEvidenceJudge.Verdict.ANSWERABLE);
        Options options = EvidenceResolverConfiguration.candidateOptions(ON, judge);

        assertThat(options.questionTermCheck()).isFalse();
        assertThat(options.customerMessageContext()).isTrue();
        assertThat(options.verifier().answers(1L, 2L, "서류는요", "명의변경 하려는데 서류는요", faq)).isTrue();
        verify(judge).judge(eq(1L), eq(2L), eq("고객 원문: 명의변경 하려는데 서류는요\n이 중 판단할 질문: 서류는요"),
                eq("명의변경 시 필요한 서류"), eq("양도인과 양수인의 신분증이 각각 필요합니다."));
    }

    @Test
    @DisplayName("판정이 실패하거나 답이 안 되면 고른 FAQ를 버린다")
    void 판정_실패와_답_안_됨은_버린다() {
        FaqEvidenceJudge judge = mock(FaqEvidenceJudge.class);
        Options options = EvidenceResolverConfiguration.candidateOptions(ON, judge);

        when(judge.judge(any(), any(), any(), any(), any())).thenReturn(FaqEvidenceJudge.Verdict.FAILED);
        assertThat(options.verifier().answers(null, null, "서류는요", null, faq)).isFalse();
        when(judge.judge(any(), any(), any(), any(), any())).thenReturn(FaqEvidenceJudge.Verdict.NOT_ANSWERABLE);
        assertThat(options.verifier().answers(null, null, "서류는요", null, faq)).isFalse();
    }

    @Test
    @DisplayName("원문이 없거나 하위 질문과 같으면 하위 질문만 판정한다")
    void 원문이_없으면_질문만_판정한다() {
        assertThat(EvidenceResolverConfiguration.judgeQuestion("명의변경 서류는?", null)).isEqualTo("명의변경 서류는?");
        assertThat(EvidenceResolverConfiguration.judgeQuestion("명의변경 서류는?", "명의변경 서류는? "))
                .isEqualTo("명의변경 서류는?");
    }
}
