package com.telme.member.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GuestProperties.class)
public class MemberConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
