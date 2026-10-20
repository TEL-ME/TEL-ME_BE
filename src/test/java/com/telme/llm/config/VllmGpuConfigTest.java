package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.llm.service.GpuInstanceStarter;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.ec2.Ec2Client;

// GPU 자동 기동은 운영(vLLM + 인스턴스 ID)에서만 켜지고, 로컬·CI에서는 EC2 클라이언트도 만들지 않는지 확인한다
class VllmGpuConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(VllmGpuConfig.class);

    @Test
    @DisplayName("vLLM이고 인스턴스 ID가 있으면 기동기를 만든다")
    void vLLM과_인스턴스_ID가_있으면_켠다() {
        runner.withPropertyValues("llm.provider=vllm", "vllm.gpu.instance-id=i-0123456789abcdef0")
                .run(context -> {
                    assertThat(context).hasSingleBean(GpuInstanceStarter.class);
                    assertThat(context).hasSingleBean(Ec2Client.class);
                    VllmGpuProperties properties = context.getBean(VllmGpuProperties.class);
                    assertThat(properties.region()).isEqualTo("ap-northeast-2");
                    assertThat(properties.startInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.startMinFallbacks()).isEqualTo(2);
                    assertThat(properties.startWindow()).isEqualTo(Duration.ofMinutes(5));
                    // EC2 API가 응답하지 않아도 기동 스레드가 오래 묶이지 않는다
                    var override = context.getBean(Ec2Client.class).serviceClientConfiguration().overrideConfiguration();
                    assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(10));
                    assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
                });
    }

    @Test
    @DisplayName("인스턴스 ID가 비어 있으면(로컬·CI) 만들지 않는다")
    void 인스턴스_ID가_없으면_끈다() {
        runner.withPropertyValues("llm.provider=vllm", "vllm.gpu.instance-id=")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GpuInstanceStarter.class);
                    assertThat(context).doesNotHaveBean(Ec2Client.class);
                });
        runner.withPropertyValues("llm.provider=vllm")
                .run(context -> assertThat(context).doesNotHaveBean(GpuInstanceStarter.class));
    }

    @Test
    @DisplayName("vLLM이 아니면(Ollama·fake) 인스턴스 ID가 있어도 만들지 않는다")
    void vLLM이_아니면_끈다() {
        runner.withPropertyValues("llm.provider=ollama", "vllm.gpu.instance-id=i-0123456789abcdef0")
                .run(context -> assertThat(context).doesNotHaveBean(GpuInstanceStarter.class));
        runner.withPropertyValues("vllm.gpu.instance-id=i-0123456789abcdef0")
                .run(context -> assertThat(context).doesNotHaveBean(GpuInstanceStarter.class));
    }
}
