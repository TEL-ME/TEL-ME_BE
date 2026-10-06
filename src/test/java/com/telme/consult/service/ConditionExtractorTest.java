package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.MissingCondition;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class ConditionExtractorTest {

    private static final String ROAMING_ANSWER =
            "요금제에 따라 다릅니다. 5G 요금제는 앱에서, LTE 요금제는 고객센터에서 신청하실 수 있습니다.";

    private final LlmClient client = mock(LlmClient.class);
    private final ConditionExtractor extractor = new ConditionExtractor(client, new ObjectMapper());

    @Test
    @DisplayName("근거가 조건에 따라 갈리면 조건과 선택지를 돌려준다")
    void 조건과_선택지를_뽑는다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":["5G","LTE"],"evidence":"5G 요금제는 앱에서, LTE 요금제는 고객센터에서"}]}""");

        List<MissingCondition> conditions = extract();

        assertThat(conditions).singleElement().satisfies(condition -> {
            assertThat(condition.key()).isEqualTo("plan_type");
            assertThat(condition.question()).isEqualTo("어떤 요금제를 쓰고 계신가요?");
            assertThat(condition.options()).containsExactly("5G", "LTE");
            assertThat(condition.selectable()).isTrue();
        });
    }

    @Test
    @DisplayName("더 알 것이 없으면 빈 목록을 돌려준다")
    void 조건이_없으면_비운다() {
        when(client.generate(any())).thenReturn("{\"conditions\":[]}");

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("근거에 없는 조건은 버린다")
    void 근거에_없는_조건을_버린다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"contract","question":"약정이 남아 있으신가요?",
                 "options":[],"evidence":"약정이 남아 있으면 위약금이 발생합니다"}]}""");

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("근거에 없는 선택지만 빼고 나머지는 남긴다")
    void 근거에_없는_선택지를_뺀다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":["5G","LTE","3G"],"evidence":"5G 요금제는 앱에서"}]}""");

        assertThat(extract()).singleElement()
                .extracting(MissingCondition::options)
                .isEqualTo(List.of("5G", "LTE"));
    }

    @Test
    @DisplayName("선택지가 모두 근거에 없으면 사용자가 직접 입력한다")
    void 선택지가_다_빠지면_자유_입력이다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":["3G","2G"],"evidence":"5G 요금제는 앱에서"}]}""");

        assertThat(extract()).singleElement().satisfies(condition -> {
            assertThat(condition.options()).isEmpty();
            assertThat(condition.selectable()).isFalse();
        });
    }

    @Test
    @DisplayName("예·아니요 선택지는 근거에 없어도 남긴다")
    void 예_아니요는_근거를_보지_않는다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"joined_this_month","question":"이번 달에 가입하셨나요?",
                 "options":["예","아니요"],"evidence":"요금제에 따라 다릅니다"}]}""");

        assertThat(extract()).singleElement()
                .extracting(MissingCondition::options)
                .isEqualTo(List.of("예", "아니요"));
    }

    @Test
    @DisplayName("쓸 수 없는 이름의 조건은 건너뛴다")
    void 이름이_틀린_조건을_건너뛴다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"요금제종류","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":[],"evidence":"요금제에 따라 다릅니다"}]}""");

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("뜻을 뒤집어 옮긴 근거는 버린다")
    void 뜻이_뒤집힌_근거를_버린다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"age","question":"나이가 어떻게 되시나요?","options":[],
                 "evidence":"만 14세 미만은 가입할 수 있습니다"}]}""");

        assertThat(extractor.extract(1L, "미성년자 가입", List.of(ageSource()))).isEmpty();
    }

    @Test
    @DisplayName("근거는 모델이 옮긴 문장이 아니라 FAQ 원문으로 남는다")
    void 근거를_원문으로_바꾼다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"age","question":"나이가 어떻게 되시나요?","options":[],
                 "evidence":"만 14세 미만은 가입이 불가능합니다"}]}""");

        assertThat(extractor.extract(1L, "미성년자 가입", List.of(ageSource())))
                .singleElement()
                .extracting(MissingCondition::evidence)
                .isEqualTo("만 14세 미만은 가입할 수 없습니다.");
    }

    @Test
    @DisplayName("근거가 문장부호뿐이면 버린다")
    void 문장부호만_있는_근거를_버린다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
                 "options":[],"evidence":"!!!"}]}""");

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("숫자 중간에 걸린 선택지는 다른 값이라 뺀다")
    void 숫자_중간에_걸린_선택지를_뺀다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[{"key":"fee","question":"얼마를 내셨나요?",
                 "options":["11500원","1500원"],"evidence":"재발급 비용은 11500원입니다"}]}""");

        assertThat(extractor.extract(1L, "유심 재발급 비용", List.of(feeSource())))
                .singleElement()
                .extracting(MissingCondition::options)
                .isEqualTo(List.of("11500원"));
    }

    @Test
    @DisplayName("같은 조건이 두 번 나오면 한 번만 쓴다")
    void 같은_조건을_한_번만_쓴다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[
                 {"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?","options":[],"evidence":"5G 요금제는 앱에서"},
                 {"key":"plan_type","question":"요금제가 무엇인가요?","options":[],"evidence":"5G 요금제는 앱에서"},
                 {"key":"channel","question":"어디에서 신청하시나요?","options":[],"evidence":"LTE 요금제는 고객센터에서"}]}""");

        assertThat(extract()).extracting(MissingCondition::key).containsExactly("plan_type", "channel");
    }

    @Test
    @DisplayName("조건을 세 개 넘겨도 두 개까지만 쓴다")
    void 두_개까지만_쓴다() {
        when(client.generate(any())).thenReturn("""
                {"conditions":[
                 {"key":"a","question":"첫 번째 질문인가요?","options":[],"evidence":"요금제에 따라 다릅니다"},
                 {"key":"b","question":"두 번째 질문인가요?","options":[],"evidence":"5G 요금제는 앱에서"},
                 {"key":"c","question":"세 번째 질문인가요?","options":[],"evidence":"LTE 요금제는 고객센터에서"}]}""");

        assertThat(extract()).hasSize(2).extracting(MissingCondition::key).containsExactly("a", "b");
    }

    @Test
    @DisplayName("응답이 JSON이 아니면 되묻지 않는다")
    void 깨진_응답은_비운다() {
        when(client.generate(any())).thenReturn("조건이 필요합니다");

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("모델 호출이 실패해도 예외를 던지지 않는다")
    void 호출_실패에도_답변을_막지_않는다() {
        when(client.generate(any())).thenThrow(new ResourceAccessException("연결 실패"));

        assertThat(extract()).isEmpty();
    }

    @Test
    @DisplayName("검색 근거가 없으면 모델을 부르지 않는다")
    void 근거가_없으면_부르지_않는다() {
        assertThat(extractor.extract(1L, "로밍 신청하고 싶어요", List.of())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("조건 뽑기는 JSON 형식과 온도 0으로 부른다")
    void 호출_설정을_지킨다() {
        when(client.generate(any())).thenReturn("{\"conditions\":[]}");

        extract();

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).generate(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.taskType()).isEqualTo(TaskType.CONDITION_EXTRACT);
        assertThat(request.format()).isEqualTo(ResponseFormat.JSON);
        assertThat(request.temperature()).isZero();
        assertThat(request.promptVersion()).isEqualTo(ConditionPromptTemplates.PROMPT_VERSION);
        assertThat(request.userPrompt()).contains(ROAMING_ANSWER).contains("로밍 신청하고 싶어요");
    }

    private List<MissingCondition> extract() {
        return extractor.extract(1L, "로밍 신청하고 싶어요", List.of(source()));
    }

    private FaqSearchResponse ageSource() {
        return new FaqSearchResponse(
                3L, null, "가입", "미성년자도 가입할 수 있나요?",
                "만 14세 미만은 가입할 수 없습니다. 만 14세 이상 18세 이하는 동의서가 필요합니다.",
                0.9, 1, null, 1, null);
    }

    private FaqSearchResponse feeSource() {
        return new FaqSearchResponse(
                2L, null, "유심", "유심 재발급 비용은 얼마인가요?", "재발급 비용은 11500원입니다.",
                0.9, 1, null, 1, null);
    }

    private FaqSearchResponse source() {
        return new FaqSearchResponse(
                1L, null, "로밍", "로밍은 어떻게 신청하나요?", ROAMING_ANSWER,
                0.9, 1, null, 1, null);
    }
}
