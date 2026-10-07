package com.telme.store.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 카카오 무료 호출 한도를 아끼려고 같은 검색어는 하루 동안 다시 부르지 않는다. 호출 실패(예외)는 저장하지 않는다
@Component
public class LocationLookupCache {

    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_SIZE = 1000;

    private final LocationLookupService locationLookupService;
    private final ExpiringLruCache<Optional<LocationLookupResult>> cache;

    public LocationLookupCache(LocationLookupService locationLookupService, Clock clock) {
        this.locationLookupService = locationLookupService;
        this.cache = new ExpiringLruCache<>(clock, TTL, MAX_SIZE);
    }

    public Optional<LocationLookupResult> lookup(String query) {
        String key = normalize(query);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return cache.getOrLoad(key, () -> locationLookupService.lookup(key));
    }

    // 카카오 클라이언트가 검색어의 중괄호를 URI 변수로 읽어 예외가 나므로 지명에 쓰이지 않는 중괄호를 미리 뺀다
    private static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.replaceAll("[{}]", " ").strip().replaceAll("\\s+", " ");
    }
}
