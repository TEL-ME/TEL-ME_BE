package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.exception.StoreErrorCode;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 경로 클라이언트들이 같이 쓰는 호출·실패 처리.
// 키·권한 오류(401/403)·호출 한도(429)·카카오 장애·타임아웃은 사용자가 고칠 수 없는 실패라 하나의 503으로 알리고,
// 원인은 로그로 구분한다. 출발지 좌표는 사용자 위치라 로그에 남기지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public final class KakaoRouteCalls {

    private final StoreRouteUsageGuard usageGuard;

    <T> T call(RouteMode mode, String tag, Supplier<T> request) {
        usageGuard.acquire(mode);
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("[{}] 클라이언트 연동 오류 (401/429 등) status={}, body={}", tag, e.getStatusCode().value(),
                        e.getResponseBodyAsString());
            } else {
                log.warn("[{}] 카카오 서버 오류 status={}, body={}", tag, e.getStatusCode().value(),
                        e.getResponseBodyAsString());
            }
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("[{}] 호출 실패 {}", tag, e.getClass().getSimpleName());
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
    }

    // 문서에 없는 응답이 와서 해석할 수 없을 때. 요청을 잘못 보냈거나 카카오 스펙이 바뀐 경우다
    static GeneralException createUnexpectedException(String tag, String detail) {
        log.error("[{}] 해석할 수 없는 응답 {}", tag, detail);
        return new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
    }
}
