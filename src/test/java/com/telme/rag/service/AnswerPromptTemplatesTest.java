package com.telme.rag.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerPromptTemplatesTest {
    @Test void v4AddsOnlyRelationshipConstraintToVersionedV3() throws Exception {
        String baseline;
        try(var input=getClass().getResourceAsStream("/rag/prompt-relations/rag-answer-v3.txt")) {
            baseline=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        String addition="   FAQ 답변(A)에 각각 나열된 사실을 임의로 원인·결과, 비교 우위, 포함 관계로 연결하지 마십시오.\n"
            +"   해당 관계가 답변(A)에 명시된 경우에만 설명하십시오.\n"
            +"   관계 근거가 없으면 수령 방법을 중복 발송의 원인으로, 이메일 무료를 즉시 확인이라는 장점으로,\n"
            +"   재발급 비용과 배송 기간을 배송비 포함 여부로 연결하지 마십시오.\n";
        // Protects role, refusal, length, output conventions and all other rules as one baseline contract.
        assertThat(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT.replace(addition,"")).isEqualTo(baseline);
        assertThat(AnswerPromptTemplates.PROMPT_VERSION).isEqualTo("rag-answer-v4.1");
        assertThat(AnswerPromptTemplates.NO_EVIDENCE_ANSWER).isEqualTo("안내드릴 수 있는 정보가 없습니다.");
    }
    @Test void runtimeGenerationUsesNewSystemVersionWithoutChangingUserEvidenceOrOptions() {
        var source=new FaqSearchResponse(1L,"slot","cat","FAQ question", "7,700원입니다.",.9,null,null,1,null);
        LlmClient client=mock(LlmClient.class);
        doAnswer(i->{LlmStreamHandler h=i.getArgument(1);h.onToken("7,700원입니다.");h.onComplete();return null;}).when(client).stream(any(),any());
        EvidenceRelevanceChecker relevance=mock(EvidenceRelevanceChecker.class);when(relevance.canAnswer(any(),any(),any())).thenReturn(true);
        var generator=new RagAnswerGenerator(client,new AnswerContextConverter(),new AnswerGuard(),relevance,mock(LlmGenerationRecorder.class),ExecutionTrace.noop());
        var request=AnswerRequest.builder().userQuery("cost?").conditions(Map.of("location","국내")).searchResults(List.of(source)).build();
        LlmStreamHandler handler=mock(LlmStreamHandler.class);var result=generator.generate(request,handler);
        var capture=org.mockito.ArgumentCaptor.forClass(LlmRequest.class);verify(client).stream(capture.capture(),any());
        LlmRequest sent=capture.getValue();
        assertThat(sent.systemPrompt()).isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
        assertThat(sent.promptVersion()).isEqualTo("rag-answer-v4.1");
        assertThat(sent.userPrompt()).isEqualTo(AnswerPromptTemplates.buildUserPrompt(request,new AnswerContextConverter().toContext(List.of(source))));
        assertThat(sent.temperature()).isNull();assertThat(sent.maxTokens()).isNull();
        assertThat(result.answer()).isEqualTo("7,700원입니다.");verify(handler).onToken("7,700원입니다.");verify(handler).onComplete();
    }
    @Test void supportedExistingCostAndIssuanceStatementsStillPassUnmodifiedGuard() {
        var guard=new AnswerGuard();
        String evidence="매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.";
        String answer="매장 재발급 비용은 7,700원이며 즉시 발급됩니다.";
        assertThat(guard.applyEvidencePolicy(answer,evidence,"매장 재발급 비용과 발급 시점은?" )).isEqualTo(answer);
    }
}
