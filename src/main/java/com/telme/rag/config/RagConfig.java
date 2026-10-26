package com.telme.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EvidenceCheckProperties.class)
public class RagConfig {
}
