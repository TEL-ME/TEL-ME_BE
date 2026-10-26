package com.telme.chat.guard;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InputGuardProperties.class)
class InputGuardConfiguration {}
