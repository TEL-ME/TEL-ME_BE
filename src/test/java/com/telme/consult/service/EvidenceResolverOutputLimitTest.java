package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// gpt-oss(Bedrock)는 답 앞의 내부 추론도 출력 한도를 써서 한도가 작으면 JSON이 끊긴다
class EvidenceResolverOutputLimitTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final FaqSearchResponse faq = new FaqSearchResponse(1L, "S-1", "유심", "유심 재발급 비용은 얼마인가요?",
            "유심 재발급 비용은 7,700원입니다.", 0.9, 1, null, 1, null);

    @Test
    @DisplayName("FAQ 후보 근거 검증은 두 요청 모두 출력 한도가 512 이상이다")
    void 후보_근거_검증의_출력_한도() {
        when(llm.generate(any())).thenReturn(
                "{\"answerable\":true,\"faqId\":1,\"quote\":\"유심 재발급 비용은 7,700원입니다.\"}",
                "{\"answerable\":true,\"faqId\":1,\"quote\":\"유심 재발급 비용은 7,700원입니다.\"}");
        var resolver = new LlmFaqCandidateEvidenceResolver(llm, new ObjectMapper());

        // 질문의 낱말이 FAQ에 없어 인용 재요청까지 가도록 해서 두 요청의 한도를 모두 본다
        resolver.resolve(null, null, "유심 재발급 비용 알려줘", List.of(faq));

        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, org.mockito.Mockito.atLeastOnce()).generate(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> assertThat(request.maxTokens()).isGreaterThanOrEqualTo(512));
    }

    @Test
    @DisplayName("비교 근거 검증은 출력 한도가 512 이상이다")
    void 비교_근거_검증의_출력_한도() {
        when(llm.generate(any())).thenReturn("{\"answerable\":false}");
        var resolver = new LlmComparisonEvidenceResolver(llm, new ObjectMapper());

        resolver.resolveDetailed(null, "청소년 요금제와 시니어 요금제 가입 연령을 비교해줘", List.of(faq), (query, kind) -> List.of(faq));

        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, org.mockito.Mockito.atLeastOnce()).generate(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> assertThat(request.maxTokens()).isGreaterThanOrEqualTo(512));
    }
}
