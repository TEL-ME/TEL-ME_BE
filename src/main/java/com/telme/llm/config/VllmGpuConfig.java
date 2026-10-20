package com.telme.llm.config;

import com.telme.llm.service.GpuInstanceStarter;
import java.time.Clock;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

// vLLM을 쓰고 GPU 인스턴스 ID가 주어졌을 때(운영)만 켠다
// 로컬, CI는 ID가 없어 EC2 클라이언트도 만들지 않는다
// 권한은 앱 EC2 역할(ec2:StartInstances는 이 GPU로 제한, ec2:DescribeInstanceStatus)을 쓴다
@Configuration
@EnableConfigurationProperties(VllmGpuProperties.class)
@ConditionalOnExpression("'${llm.provider:fake}' == 'vllm' and !'${vllm.gpu.instance-id:}'.isBlank()")
public class VllmGpuConfig {

    // 클래스패스에 HTTP 구현이 여러 개라 Bedrock과 같은 Apache5를 직접 지정
    @Bean(destroyMethod = "close")
    public Ec2Client gpuEc2Client(VllmGpuProperties properties) {
        return Ec2Client.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(Apache5HttpClient.builder())
                .build();
    }

    // 기동 요청은 드물어 스레드 하나
    // 데몬이라 앱 종료를 막지 않는다
    @Bean
    public GpuInstanceStarter gpuInstanceStarter(Ec2Client gpuEc2Client, VllmGpuProperties properties) {
        return new GpuInstanceStarter(gpuEc2Client, properties.instanceId(), properties.startInterval(),
                Clock.systemUTC(), Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "gpu-instance-starter");
                    thread.setDaemon(true);
                    return thread;
                }));
    }
}
