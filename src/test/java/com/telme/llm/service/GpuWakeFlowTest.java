package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.ConditionExtractor;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.VllmGpuProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceStatus;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;

// 실제 배선과 같게 조립해 어떤 채팅 흐름에서 GPU가 켜지는지 확인한다(기본값: 5분 안에 Bedrock 실패 대체 2번)
// 조건 추출: ConsultRagAnswerConfiguration.extractionClient = FallbackLlmClient(Bedrock, 엑사원), parseFallback = 엑사원
// 문맥 해석·라우팅·답변 등: FallbackLlmClient(Bedrock, 엑사원)
class GpuWakeFlowTest {

    private static final String CONDITIONS = """
            {"conditions":[{"key":"plan_type","question":"어떤 요금제를 쓰고 계신가요?",
             "options":["5G","LTE"],"evidence":"5G 요금제는 앱에서, LTE 요금제는 고객센터에서"}]}""";

    private final LlmClient bedrock = mock(LlmClient.class);
    private final LlmClient vllm = mock(LlmClient.class);
    private final Ec2Client ec2 = mock(Ec2Client.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
    private final GpuInstanceStarter starter = new GpuInstanceStarter(ec2,
            new VllmGpuProperties("i-0123456789abcdef0", "ap-northeast-2", Duration.ofMinutes(1), 2,
                    Duration.ofMinutes(5)),
            clock, Runnable::run);
    // 운영의 vllmLlmClient와 같은 위치(엑사원 바깥)에 감싼다
    private final LlmClient exaone = new GpuWakingLlmClient(vllm, starter);
    private final LlmClient fallbackClient = new FallbackLlmClient(bedrock, exaone);
    private final LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

    GpuWakeFlowTest() {
        when(ec2.describeInstanceStatus(any(DescribeInstanceStatusRequest.class)))
                .thenReturn(DescribeInstanceStatusResponse.builder()
                        .instanceStatuses(InstanceStatus.builder()
                                .instanceState(InstanceState.builder().name(InstanceStateName.STOPPED).build())
                                .build())
                        .build());
    }

    @Test
    @DisplayName("Bedrock 일시 오류(시간 초과·429)로 한 번 대체된 걸로는 켜지 않는다")
    void 일시_오류_한_번은_켜지_않는다() {
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.TIMEOUT));
        when(vllm.generate(any())).thenReturn("{}");

        fallbackClient.generate(request);

        verifyNotStarted();
    }

    @Test
    @DisplayName("일시 오류가 5분보다 떨어져 두 번 나도 켜지 않는다")
    void 떨어진_일시_오류는_켜지_않는다() {
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.TIMEOUT));
        when(vllm.generate(any())).thenReturn("{}");

        fallbackClient.generate(request);
        clock.advance(Duration.ofMinutes(5));
        fallbackClient.generate(request);

        verifyNotStarted();
    }

    @Test
    @DisplayName("이어 묻는 질문은 문맥 해석 실패로 턴당 대체 1번이지만, 두 번째 턴에 켠다")
    void 이어_묻는_질문은_두_번째_턴에_켠다() {
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
        when(vllm.generate(any())).thenThrow(new GeneralException(LlmErrorCode.CONNECTION_FAILED));

        contextResolutionTurn();
        verifyNotStarted();

        clock.advance(Duration.ofSeconds(30));
        contextResolutionTurn();
        verify(ec2, times(1)).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("한 턴에 대체가 2번 나오면(세션 첫 질문의 라우팅 + 제목 생성 등) 첫 턴에 켠다")
    void 한_턴에_대체_2번이면_첫_턴에_켠다() {
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
        when(vllm.generate(any())).thenReturn("{}");

        fallbackClient.generate(request);
        fallbackClient.generate(request);

        verify(ec2, times(1)).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("조건 추출에서 Bedrock 응답 JSON 파싱만 실패해 엑사원으로 다시 뽑는 건 몇 번이어도 세지 않는다")
    void 파싱_재시도는_세지_않는다() {
        ConditionExtractor extractor = new ConditionExtractor(fallbackClient, exaone, new ObjectMapper());
        when(bedrock.generate(any())).thenReturn("조건은 다음과 같습니다: plan_type");
        when(vllm.generate(any())).thenReturn(CONDITIONS);

        for (int turn = 0; turn < 3; turn++) {
            assertThat(extractor.extract(1L, "로밍 신청하고 싶어요", List.of(source()))).isNotEmpty();
        }

        verify(vllm, times(3)).generate(any());
        verifyNotStarted();
    }

    @Test
    @DisplayName("스트리밍 답변이 첫 글자 전에 Bedrock 실패로 대체된 것도 센다")
    void 스트리밍_대체도_센다() {
        doAnswer(invocation -> {
            invocation.<LlmStreamHandler>getArgument(1).onError(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
            return null;
        }).when(bedrock).stream(any(), any());
        when(bedrock.generate(any())).thenThrow(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
        when(vllm.generate(any())).thenReturn("{}");

        fallbackClient.generate(request);
        fallbackClient.stream(request, mock(LlmStreamHandler.class));

        verify(vllm).stream(any(), any());
        verify(ec2, times(1)).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("Bedrock이 성공하면 엑사원도 GPU 기동도 부르지 않는다")
    void Bedrock_성공이면_켜지_않는다() {
        when(bedrock.generate(any())).thenReturn("{}");

        fallbackClient.generate(request);
        fallbackClient.generate(request);

        verify(vllm, never()).generate(any());
        verifyNotStarted();
    }

    // ChatQuestionResolver는 모델 연결 실패면 예외를 다시 던져 턴이 거기서 끝난다
    private void contextResolutionTurn() {
        try {
            fallbackClient.generate(request);
        } catch (GeneralException expected) {
            // 꺼진 GPU라 대체도 실패
        }
    }

    private void verifyNotStarted() {
        verify(ec2, never()).describeInstanceStatus(any(DescribeInstanceStatusRequest.class));
        verify(ec2, never()).startInstances(any(StartInstancesRequest.class));
    }

    private FaqSearchResponse source() {
        return new FaqSearchResponse(1L, null, "로밍", "로밍은 어떻게 신청하나요?",
                "5G 요금제는 앱에서, LTE 요금제는 고객센터에서 신청할 수 있습니다.", 0.9, 1, null, 1, null);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
