package com.telme.global.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

@Configuration
public class LocaleConfig {

    // 요청 밖(채팅 백그라운드 처리, 스케줄러)에서는 요청 언어가 없어 JVM 기본 언어가 쓰인다.
    // 서버 OS가 영어여도 한국어가 기본이 되게 고정한다
    @PostConstruct
    void useKoreanByDefault() {
        LocaleContextHolder.setDefaultLocale(Locale.KOREAN);
    }
}
