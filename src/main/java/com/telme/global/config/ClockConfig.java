package com.telme.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 여러 도메인(회원 게스트 만료, 매장 영업 중 판단)이 같은 시계를 쓰므로 공용 설정에 둔다.
// 서버 시계는 UTC이고, 한국 시각이 필요한 곳은 쓰는 쪽에서 Asia/Seoul로 바꾼다. 테스트는 Clock.fixed로 대체한다
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
