package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceStatus;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;

class GpuInstanceStarterTest {

    private static final String INSTANCE_ID = "i-0123456789abcdef0";
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    private final Ec2Client ec2 = mock(Ec2Client.class);
    private final MutableClock clock = new MutableClock(NOW);
    // 테스트에서는 기동 확인을 호출한 스레드에서 바로 실행한다
    private final GpuInstanceStarter starter =
            new GpuInstanceStarter(ec2, INSTANCE_ID, Duration.ofMinutes(1), clock, Runnable::run);

    @Test
    @DisplayName("꺼져 있으면 그 인스턴스를 켠다")
    void 꺼져_있으면_켠다() {
        givenState(InstanceStateName.STOPPED);

        starter.requestStart();

        ArgumentCaptor<StartInstancesRequest> started = ArgumentCaptor.forClass(StartInstancesRequest.class);
        verify(ec2).startInstances(started.capture());
        assertThat(started.getValue().instanceIds()).containsExactly(INSTANCE_ID);
    }

    @Test
    @DisplayName("꺼진 인스턴스도 조회되도록 includeAllInstances를 켜서 묻는다")
    void 꺼진_인스턴스도_조회한다() {
        givenState(InstanceStateName.STOPPED);

        starter.requestStart();

        ArgumentCaptor<DescribeInstanceStatusRequest> described =
                ArgumentCaptor.forClass(DescribeInstanceStatusRequest.class);
        verify(ec2).describeInstanceStatus(described.capture());
        assertThat(described.getValue().includeAllInstances()).isTrue();
        assertThat(described.getValue().instanceIds()).containsExactly(INSTANCE_ID);
    }

    @Test
    @DisplayName("켜져 있거나 켜지는 중이거나 꺼지는 중이면 켜지 않는다")
    void 꺼진_상태가_아니면_켜지_않는다() {
        for (InstanceStateName state : new InstanceStateName[] {
                InstanceStateName.RUNNING, InstanceStateName.PENDING, InstanceStateName.STOPPING}) {
            givenState(state);
            clock.advance(Duration.ofMinutes(2));

            starter.requestStart();
        }

        verify(ec2, times(3)).describeInstanceStatus(any(DescribeInstanceStatusRequest.class));
        verify(ec2, never()).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("조회 결과에 인스턴스가 없으면 켜지 않는다")
    void 조회_결과가_없으면_켜지_않는다() {
        when(ec2.describeInstanceStatus(any(DescribeInstanceStatusRequest.class)))
                .thenReturn(DescribeInstanceStatusResponse.builder().build());

        starter.requestStart();

        verify(ec2, never()).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("간격(1분) 안의 요청은 한 번으로 합치고, 간격이 지나면 다시 확인한다")
    void 간격_안의_요청은_한_번만_보낸다() {
        givenState(InstanceStateName.STOPPED);

        starter.requestStart();
        clock.advance(Duration.ofSeconds(59));
        starter.requestStart();
        starter.requestStart();

        verify(ec2, times(1)).describeInstanceStatus(any(DescribeInstanceStatusRequest.class));

        clock.advance(Duration.ofSeconds(1));
        starter.requestStart();

        verify(ec2, times(2)).describeInstanceStatus(any(DescribeInstanceStatusRequest.class));
    }

    @Test
    @DisplayName("AWS 호출이 실패해도 예외를 밖으로 던지지 않는다")
    void AWS_오류는_밖으로_던지지_않는다() {
        when(ec2.describeInstanceStatus(any(DescribeInstanceStatusRequest.class)))
                .thenThrow(Ec2Exception.builder().message("UnauthorizedOperation").build());

        assertThatCode(starter::requestStart).doesNotThrowAnyException();
        verify(ec2, never()).startInstances(any(StartInstancesRequest.class));
    }

    @Test
    @DisplayName("기동 요청 자체가 실패해도 예외를 밖으로 던지지 않는다")
    void 기동_실패는_밖으로_던지지_않는다() {
        givenState(InstanceStateName.STOPPED);
        when(ec2.startInstances(any(StartInstancesRequest.class)))
                .thenThrow(Ec2Exception.builder().message("IncorrectInstanceState").build());

        assertThatCode(starter::requestStart).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("작업 스레드가 요청을 받지 않아도(앱 종료 중) 예외를 밖으로 던지지 않는다")
    void 실행기가_거절해도_던지지_않는다() {
        Executor rejecting = runnable -> {
            throw new RejectedExecutionException("shutdown");
        };
        GpuInstanceStarter closing = new GpuInstanceStarter(ec2, INSTANCE_ID, Duration.ofMinutes(1), clock, rejecting);

        assertThatCode(closing::requestStart).doesNotThrowAnyException();
        verify(ec2, never()).describeInstanceStatus(any(DescribeInstanceStatusRequest.class));
    }

    private void givenState(InstanceStateName state) {
        when(ec2.describeInstanceStatus(any(DescribeInstanceStatusRequest.class)))
                .thenReturn(DescribeInstanceStatusResponse.builder()
                        .instanceStatuses(InstanceStatus.builder()
                                .instanceId(INSTANCE_ID)
                                .instanceState(InstanceState.builder().name(state).build())
                                .build())
                        .build());
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
