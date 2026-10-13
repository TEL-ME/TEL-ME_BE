package com.telme.speech.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;

@Configuration
@EnableConfigurationProperties(SpeechTtsProperties.class)
public class SpeechConfig {

    // 자격 증명은 첫 호출 때 읽으므로 키가 없는 환경에서도 앱은 뜨고 음성 요청만 SPEECH503-0으로 실패한다
    @Bean
    public PollyClient pollyClient(SpeechTtsProperties properties) {
        return PollyClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.timeout()))
                .build();
    }
}
