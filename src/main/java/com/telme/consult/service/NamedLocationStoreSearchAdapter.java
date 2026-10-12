package com.telme.consult.service;

import com.telme.chat.converter.ChatStoreConverter;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse.Type;
import com.telme.store.config.StoreSearchProperties;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.req.StoreRegionSearchRequest;
import com.telme.store.dto.res.StoreNearbySearchResponse;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.service.LocationLookupCache;
import com.telme.store.service.LocationLookupResult;
import com.telme.store.service.StoreRegionSearchService;
import com.telme.store.service.StoreSearchService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 카카오 장애와 DB 오류는 잡지 않는다. ChatStoreAnswerProvider가 검색 실패 안내로 바꾼다
@Service
@RequiredArgsConstructor
public class NamedLocationStoreSearchAdapter implements NamedLocationStoreSearchPort {

    private final LocationLookupCache locationLookupCache;
    private final StoreRegionSearchService storeRegionSearchService;
    private final StoreSearchService storeSearchService;
    private final StoreSearchProperties storeSearchProperties;
    private final ChatStoreConverter chatStoreConverter;

    @Override
    public SearchResult search(String location, Set<StoreServiceType.Code> serviceTypes) {
        Optional<LocationLookupResult> found = locationLookupCache.lookup(location);
        if (found.isEmpty()) {
            return new SearchResult(Status.LOCATION_NOT_FOUND, List.of(), null);
        }
        LocationLookupResult place = found.get();
        Set<StoreServiceType.Code> codes = serviceTypes == null ? Set.of() : serviceTypes;
        String label = place.name() == null || place.name().isBlank() ? location.strip() : place.name();
        return searchesRegion(place, codes) ? searchRegion(place, codes, label) : searchNearby(place, codes, label);
    }

    // 지역 검색은 업무를 하나만 받으므로 여러 개면 업무 조건을 그대로 쓸 수 있는 좌표 검색으로 찾는다
    private boolean searchesRegion(LocationLookupResult place, Set<StoreServiceType.Code> codes) {
        return place.type() == LocationLookupResult.Type.REGION && place.regionCode() != null && codes.size() <= 1;
    }

    private SearchResult searchRegion(LocationLookupResult place, Set<StoreServiceType.Code> codes, String label) {
        String serviceType = codes.isEmpty() ? null : codes.iterator().next().name();
        StoreRegionSearchResponse response = storeRegionSearchService.search(new StoreRegionSearchRequest(
                place.regionCode(), serviceType, 0, storeSearchProperties.defaultLimit()));
        return new SearchResult(Status.SUCCESS,
                response.stores().stream().map(chatStoreConverter::fromRegion).toList(),
                new ChatStoreSearchContextResponse(Type.REGION, label, null));
    }

    private SearchResult searchNearby(LocationLookupResult place, Set<StoreServiceType.Code> codes, String label) {
        StoreNearbySearchResponse response = storeSearchService.findNearbyStores(StoreNearbySearchRequest.builder()
                .latitude(place.latitude())
                .longitude(place.longitude())
                .serviceTypes(codes)
                .build());
        Type type = place.type() == LocationLookupResult.Type.ADDRESS ? Type.ADDRESS : Type.PLACE;
        return new SearchResult(Status.SUCCESS,
                response.stores().stream().map(chatStoreConverter::fromNearby).toList(),
                new ChatStoreSearchContextResponse(type, label, response.radiusMeters()));
    }
}
