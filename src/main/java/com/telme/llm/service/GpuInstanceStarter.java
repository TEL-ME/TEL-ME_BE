package com.telme.llm.service;

import com.telme.llm.config.VllmGpuProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;

// Bedrock이 실패해 엑사원(vLLM)으로 대체된 호출이(GpuWakingLlmClient) 짧은 시간에 여러 번이면 꺼져 있는 GPU EC2를 킴
// 끄는 건 GPU 안의 유휴 스크립트(30분)
// 켜서 vLLM이 첫 답을 내기까지 약 1분 40초~2분 40초(2026-10-19 실측, gp3 기본 131~156초)
// 그동안의 엑사원 호출은 연결 시간 제한(1초) 뒤 실패하고, 준비된 뒤 호출부터 대체된다
@Slf4j
public class GpuInstanceStarter {

    private final Ec2Client ec2;
    private final VllmGpuProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final Deque<Instant> recentFallbacks = new ArrayDeque<>();
    private final AtomicReference<Instant> lastRequestedAt = new AtomicReference<>();

    public GpuInstanceStarter(Ec2Client ec2, VllmGpuProperties properties, Clock clock, Executor executor) {
        this.ec2 = ec2;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
    }

    // Bedrock 실패 대체 한 번마다 부른다
    // 호출을 막지 않게 상태 확인과 기동은 다른 스레드에서
    // 간격 안의 중복 요청은 한 번으로 합침
    public void requestStart() {
        Instant now = clock.instant();
        if (!enoughRecentFallbacks(now)) {
            return;
        }
        Instant last = lastRequestedAt.get();
        if (last != null && now.isBefore(last.plus(properties.startInterval()))) {
            return;
        }
        if (!lastRequestedAt.compareAndSet(last, now)) {
            return;
        }
        try {
            executor.execute(this::startIfStopped);
        } catch (RejectedExecutionException e) {
            log.warn("[GPU 자동 기동] 요청을 넣지 못했습니다(앱 종료 중일 수 있음). instanceId={}", properties.instanceId());
        }
    }

    // 최근 startWindow 안의 대체 수가 startMinFallbacks 이상인지
    private synchronized boolean enoughRecentFallbacks(Instant now) {
        recentFallbacks.addLast(now);
        Instant windowStart = now.minus(properties.startWindow());
        while (!recentFallbacks.isEmpty() && !recentFallbacks.peekFirst().isAfter(windowStart)) {
            recentFallbacks.pollFirst();
        }
        return recentFallbacks.size() >= properties.startMinFallbacks();
    }

    // 실패해도 LLM 호출 흐름에는 영향X
    // 다음 간격에 다시 시도
    void startIfStopped() {
        String instanceId = properties.instanceId();
        try {
            InstanceStateName state = currentState(instanceId);
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
    private InstanceStateName currentState(String instanceId) {
        var statuses = ec2.describeInstanceStatus(DescribeInstanceStatusRequest.builder()
                        .instanceIds(instanceId)
                        .includeAllInstances(true)
                        .build())
                .instanceStatuses();
        return statuses.isEmpty() ? null : statuses.get(0).instanceState().name();
    }
}
