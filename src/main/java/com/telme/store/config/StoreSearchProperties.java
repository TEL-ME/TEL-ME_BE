package com.telme.store.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 반경 상한 10km(사람이 매장을 찾아 이동할 만한 최대 거리로 정한 값). 지도 화면(카카오 지도 SDK)은 축척에 맞게 변화하고,
// 상한을 넘는 요청은 상한으로 줄여 검색한다
// 반경 안에 매장이 없으면 빈 결과를 돌려주고, 0건 처리는 호출자(API·채팅)에서 정한다.
// 영업 중 필터는 구현만 해 두고 꺼 둔다. 매장 영업시간 데이터(공휴일·임시 휴무 포함)가 갖춰진 뒤 켠다.
@ConfigurationProperties(prefix = "store.search")
public record StoreSearchProperties(
        @DefaultValue("10000") int defaultRadiusMeters,
        @DefaultValue("10000") int maxRadiusMeters,
        @DefaultValue("5") int defaultLimit,
        @DefaultValue("2s") Duration queryTimeout,
        @DefaultValue("false") boolean openNowFilterEnabled) {

    // 매장 개수 상한. 반경(maxRadiusMeters 설정값)과 달리 API 계약(초과 시 400)이라 환경별로 바꾸지 않도록 상수로 둔다.
    // 요청 검증(@Max), 기본 개수 설정 검증, 서비스의 내부 호출 상한이 모두 이 값을 쓴다
    public static final int MAX_LIMIT = 20;

    // 매장 개수 하한. 요청 검증(@Min), 기본 개수 설정 검증, 서비스의 내부 호출 하한이 모두 이 값을 쓴다
    public static final int MIN_LIMIT = 1;

    public StoreSearchProperties {
        if (maxRadiusMeters < 1) {
            throw new IllegalArgumentException("매장 검색 최대 반경은 1m 이상이어야 합니다.");
        }
        if (defaultRadiusMeters < 1 || defaultRadiusMeters > maxRadiusMeters) {
            throw new IllegalArgumentException("매장 검색 기본 반경은 1m 이상, 최대 반경 이하여야 합니다.");
        }
        if (defaultLimit < MIN_LIMIT || defaultLimit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "매장 검색 기본 개수는 " + MIN_LIMIT + " 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        // JDBC 쿼리 타임아웃은 초 단위라 1초 미만은 받지 않는다
        if (queryTimeout == null || queryTimeout.toSeconds() < 1) {
            throw new IllegalArgumentException("매장 검색 쿼리 타임아웃은 1초 이상이어야 합니다.");
        }
    }
}
