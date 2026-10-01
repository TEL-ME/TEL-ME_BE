package com.telme.probe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.AnswerPromptTemplates;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptRelationComparisonProbeTest {
    JsonNode row() throws Exception {return PromptRelationComparisonProbe.MAPPER.readTree("""
        {"eval_id":"EXISTING","type":"ANSWER","question":"cost?","sources":[{"question":"FAQ?","answer":"7,700원입니다.","score":0.9}]}
        """);}
    LlmClient emitting(String raw) {
        LlmClient c=mock(LlmClient.class);doAnswer(i->{LlmStreamHandler h=i.getArgument(1);h.onToken(raw);h.onComplete();return null;}).when(c).stream(any(),any());return c;
    }
    @Test void generatedRawAndGuardFinalStaySeparate() throws Exception {
        var result=PromptRelationComparisonProbe.runCase(row(),emitting("7,700원입니다. 홈페이지에서 신청하세요."),"prompt","v",false);
        assertThat(result).containsEntry("raw_answer","7,700원입니다. 홈페이지에서 신청하세요.").containsEntry("answer","7,700원입니다.")
            .containsEntry("observed_stage","ANSWERED");
    }
    @Test void refusalStagesAreObservedWithoutGuessing() throws Exception {
        var pre=emitting("unused");when(pre.generate(any())).thenReturn("{\"answerable\":false}");
        assertThat(PromptRelationComparisonProbe.runCase(row(),pre,"prompt","v",true)).containsEntry("observed_stage","PRECHECK_REJECTED");
        verify(pre,never()).stream(any(),any());
        assertThat(PromptRelationComparisonProbe.runCase(row(),emitting(AnswerPromptTemplates.NO_EVIDENCE_ANSWER),"prompt","v",false)).containsEntry("observed_stage","MODEL_REFUSAL");
        assertThat(PromptRelationComparisonProbe.runCase(row(),emitting("홈페이지에서 신청하세요."),"prompt","v",false)).containsEntry("observed_stage","GUARD_REPLACED");
    }
    @Test void generationFailureAndNoSearchAreRetained() throws Exception {
        LlmClient error=mock(LlmClient.class);doThrow(new IllegalStateException("offline")).when(error).stream(any(),any());
        assertThat(PromptRelationComparisonProbe.runCase(row(),error,"p","v",false)).containsEntry("status","GENERATION_ERROR").containsEntry("raw_answer",null);
        JsonNode empty=PromptRelationComparisonProbe.MAPPER.readTree("{\"eval_id\":\"E\",\"type\":\"ANSWER\",\"question\":\"Q\",\"sources\":[]}");
        assertThat(PromptRelationComparisonProbe.runCase(empty,error,"p","v",false)).containsEntry("status","NO_SEARCH").containsEntry("observed_stage","NO_SEARCH");
    }
    @Test void sameInputAndRequestOptionsArePreservedAcrossPrompts() throws Exception {
        var client=emitting("7,700원입니다.");
        PromptRelationComparisonProbe.runCase(row(),client,"baseline","v3",false);
        PromptRelationComparisonProbe.runCase(row(),client,"candidate","v4",false);
        var capture=org.mockito.ArgumentCaptor.forClass(LlmRequest.class);verify(client,times(2)).stream(capture.capture(),any());
        var requests=capture.getAllValues();assertThat(requests.get(0).userPrompt()).isEqualTo(requests.get(1).userPrompt());
        assertThat(requests.get(0).format()).isEqualTo(requests.get(1).format());
        assertThat(requests.get(0).temperature()).isEqualTo(requests.get(1).temperature());
        assertThat(requests.get(0).maxTokens()).isEqualTo(requests.get(1).maxTokens());
        assertThat(requests.get(0).systemPrompt()).isEqualTo("baseline");assertThat(requests.get(1).systemPrompt()).isEqualTo("candidate");
    }
    @Test void existingTenCasesRemainUnmodifiedAndSubsetDoesNotHideOthers() throws Exception {
        var cases=PromptRelationComparisonProbe.loadCases(PromptRelationComparisonProbe.RESOURCES.resolve("replay-cases.json"));
        assertThat(cases).hasSize(10);
        assertThat(PromptRelationComparisonProbe.selectedIds(cases,"EVAL-001,HALLU-010")).containsExactly("EVAL-001","HALLU-010");
        assertThat(PromptRelationComparisonProbe.pending(cases.get(0),false)).containsEntry("status","NOT_SELECTED");
        assertThatThrownBy(()->PromptRelationComparisonProbe.selectedIds(cases,"FAKE-ID")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void damagedReplayIsRejectedBeforeGeneration() throws Exception {
        var file=Files.createTempFile("prompt-replay", ".json");
        try {
            Files.writeString(file,"[{\"eval_id\":\"E\",\"question\":\"Q\",\"sources\":[{\"question\":\"FAQ\",\"answer\":null,\"score\":\"1\"}]}]");
            assertThatThrownBy(()->PromptRelationComparisonProbe.loadCases(file)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("E sources[0]");
        } finally { Files.delete(file); }
    }
    @Test void punctuationDoesNotHideRefusalAndMixedAnswersRemainUnconfirmed() throws Exception {
        assertThat(PromptRelationComparisonProbe.runCase(row(),emitting("안내드릴 수 있는 정보가 없습니다"),"p","v",false))
            .containsEntry("observed_stage","MODEL_REFUSAL").containsEntry("status","GROUNDED");
        // Keep the original application's status; do not change Judge or Guard rules in this probe.
        assertThat(PromptRelationComparisonProbe.runCase(row(),emitting("7,700원입니다. 안내드릴 수 있는 정보가 없습니다."),"p","v",false))
            .containsEntry("observed_stage","UNCONFIRMED_MIXED_REFUSAL");
    }
    @Test void retriedRawDiscardsThePriorAttemptAndTimeoutKeepsPartialState() throws Exception {
        LlmClient retry=mock(LlmClient.class);
        doAnswer(i->{LlmStreamHandler h=i.getArgument(1);h.onToken("discarded");h.onRetry(2,new IllegalStateException("retry"));
            h.onToken("7,700원입니다.");h.onComplete();return null;}).when(retry).stream(any(),any());
        var success=PromptRelationComparisonProbe.runCase(row(),retry,"p","v",false);
        assertThat(success).containsEntry("raw_answer","7,700원입니다.").containsEntry("answer","7,700원입니다.");
        assertThat((List<?>)success.get("retry_events")).hasSize(1);
        LlmClient timeout=mock(LlmClient.class);
        doAnswer(i->{LlmStreamHandler h=i.getArgument(1);h.onToken("unfinished");
            h.onError(new com.telme.global.common.exception.GeneralException(com.telme.llm.exception.LlmErrorCode.TIMEOUT));return null;
        }).when(timeout).stream(any(),any());
        assertThat(PromptRelationComparisonProbe.runCase(row(),timeout,"p","v",false))
            .containsEntry("status","TIMEOUT").containsEntry("raw_answer",null).containsEntry("partial_raw","unfinished")
            .containsEntry("answer","").containsEntry("raw_state","INCOMPLETE");
    }
}
