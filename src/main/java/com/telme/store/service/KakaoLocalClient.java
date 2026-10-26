package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.res.KakaoAddressSearchResponse;
import com.telme.store.dto.res.KakaoKeywordSearchResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 카카오 로컬 API 호출만 맡는다. 결과 해석(주소 우선, 없으면 키워드)은 LocationLookupService가 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLocalClient {

    private static final String ADDRESS_PATH = "/v2/local/search/address.json";
    private static final String KEYWORD_PATH = "/v2/local/search/keyword.json";
    // 첫 결과만 쓰므로 응답 크기를 줄인다
    private static final int RESULT_SIZE = 1;

    private final RestClient kakaoLocalRestClient;

    public List<KakaoAddressSearchResponse.Document> searchAddress(String query) {
        KakaoAddressSearchResponse response = call(() -> kakaoLocalRestClient.get()
                .uri(uri -> uri.path(ADDRESS_PATH).queryParam("query", query).queryParam("size", RESULT_SIZE).build())
                .retrieve()
                .body(KakaoAddressSearchResponse.class));
        return response == null || response.documents() == null ? List.of() : response.documents();
    }

    public List<KakaoKeywordSearchResponse.Document> searchKeyword(String query) {
        KakaoKeywordSearchResponse response = call(() -> kakaoLocalRestClient.get()
                .uri(uri -> uri.path(KEYWORD_PATH).queryParam("query", query).queryParam("size", RESULT_SIZE).build())
                .retrieve()
                .body(KakaoKeywordSearchResponse.class));
        return response == null || response.documents() == null ? List.of() : response.documents();
    }

    // 키·권한 오류(401/403)·호출 한도(429)·카카오 장애·타임아웃 모두 사용자가 고칠 수 없는 실패라 하나의 503으로 알린다.
    // 원인은 로그로 구분한다. 검색어는 사용자 입력이라 로그에 남기지 않는다
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("[KakaoLocal] 클라이언트 연동 오류 (401/429 등) status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            } else {
                log.warn("[KakaoLocal] 카카오 서버 오류 status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            }
            throw new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("[KakaoLocal] 호출 실패 {}", e.getClass().getSimpleName());
            throw new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
        }
    }
}
