package com.telme.faq.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Ollama가 OLLAMA_KEEP_ALIVE(10분) 동안 요청이 없으면 모델을 내려서
// 첫 요청이 콜드 로드(약 11초)를 그대로 맞는 걸 막기 위해 기동 시 한 번 미리 호출한다.
// 기본은 꺼짐 — 로컬/테스트/CI에 Ollama가 없어도 안전, 실제 배포 환경에서만 켠다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "embedding.warmup-enabled", havingValue = "true")
public class EmbeddingWarmupRunner {

    private final EmbeddingClient embeddingClient;

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        try {
            embeddingClient.embed("warmup");
        } catch (Exception e) {
            log.warn("[EmbeddingWarmupRunner] 임베딩 워밍업 실패 — 첫 요청에서 콜드 로드가 발생할 수 있습니다.", e);
        }
    }
}
