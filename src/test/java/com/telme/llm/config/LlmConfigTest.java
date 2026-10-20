package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class LlmConfigTest {

    // 응답하지 않는 사설 주소. 꺼진 GPU EC2처럼 연결 요청에 아무 응답이 없다
    private static final String UNREACHABLE = "http://10.255.255.1:8000";

    @Test
    @DisplayName("vLLM 연결은 llm.connect-timeout(5초)이 아니라 vllm.connect-timeout만큼만 기다린다")
    void vLLM_연결은_vllm_connect_timeout만큼만_기다린다() {
        LlmProperties properties =
                new LlmProperties("exaone3.5:7.8b", Duration.ofSeconds(5), Duration.ofSeconds(60), 8192);
        RestClient client = new LlmConfig().vllmRestClient(
                RestClient.builder(), properties, UNREACHABLE, Duration.ofMillis(500));

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri("/v1/models").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        long waitedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(waitedMs).isLessThan(3_000);
    }
}
