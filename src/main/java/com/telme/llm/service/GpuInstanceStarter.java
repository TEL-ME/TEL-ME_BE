package com.telme.llm.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;

// Bedrock 장애로 엑사원(vLLM)을 부를 때 꺼져 있는 GPU EC2를 킴
// 끄는 건 GPU 안의 유휴 스크립트(30분)
// 켜지는 데 몇 분 걸리므로 이 요청을 만든 호출은 대개 실패, 그 뒤 호출부터 대체된다
@Slf4j
public class GpuInstanceStarter {

    private final Ec2Client ec2;
    private final String instanceId;
    private final Duration startInterval;
    private final Clock clock;
    private final Executor executor;
    private final AtomicReference<Instant> lastRequestedAt = new AtomicReference<>();

    public GpuInstanceStarter(Ec2Client ec2, String instanceId, Duration startInterval, Clock clock, Executor executor) {
        this.ec2 = ec2;
        this.instanceId = instanceId;
        this.startInterval = startInterval;
        this.clock = clock;
        this.executor = executor;
    }

    // 호출을 막지 않게 상태 확인과 기동은 다른 스레드에서
    // 간격 안의 중복 요청은 한 번으로 합침
    public void requestStart() {
        Instant now = clock.instant();
        Instant last = lastRequestedAt.get();
        if (last != null && now.isBefore(last.plus(startInterval))) {
            return;
        }
        if (!lastRequestedAt.compareAndSet(last, now)) {
            return;
        }
        try {
            executor.execute(this::startIfStopped);
        } catch (RejectedExecutionException e) {
            log.warn("[GPU 자동 기동] 요청을 넣지 못했습니다(앱 종료 중일 수 있음). instanceId={}", instanceId);
        }
    }

    // 실패해도 LLM 호출 흐름에는 영향X
    // 다음 간격에 다시 시도
    void startIfStopped() {
        try {
            InstanceStateName state = currentState();
            if (state != InstanceStateName.STOPPED) {
                // pending, running은 켜는 중이거나 켜져 있고 stopping은 다 꺼진 뒤 다음 요청에서 켠다
                log.info("[GPU 자동 기동] 켜지 않습니다. instanceId={}, state={}", instanceId, state);
                return;
            }
            ec2.startInstances(StartInstancesRequest.builder().instanceIds(instanceId).build());
            log.warn("[GPU 자동 기동] 꺼져 있던 GPU EC2를 켰습니다. instanceId={}", instanceId);
        } catch (RuntimeException e) {
            log.warn("[GPU 자동 기동] 상태 확인 또는 기동에 실패했습니다. instanceId={}", instanceId, e);
        }
    }

    // 꺼진 인스턴스는 기본 조회에 나오지 않아 includeAllInstances를 켠다
    private InstanceStateName currentState() {
        var statuses = ec2.describeInstanceStatus(DescribeInstanceStatusRequest.builder()
                        .instanceIds(instanceId)
                        .includeAllInstances(true)
                        .build())
                .instanceStatuses();
        return statuses.isEmpty() ? null : statuses.get(0).instanceState().name();
    }
}
