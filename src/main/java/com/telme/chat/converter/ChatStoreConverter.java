package com.telme.chat.converter;

import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChatStoreConverter {
    public ChatStoreResponse fromNearby(StoreNearbyResponse store) {
        return ChatStoreResponse.builder()
                .storeId(store.storeId()).name(store.name()).address(store.address()).phone(store.phone())
                .latitude(store.latitude()).longitude(store.longitude()).distanceMeters(store.distanceMeters())
                .build();
    }

    public ChatStoreResponse fromRegion(StoreRegionSearchResponse.Store store) {
        return ChatStoreResponse.builder()
                .storeId(store.storeId()).name(store.name()).address(store.address()).phone(store.phone())
                .latitude(store.latitude()).longitude(store.longitude()).distanceMeters(null)
                .build();
    }

    // 기존 이력의 배열 계약을 유지해 이전 클라이언트와 이전 스냅샷을 그대로 읽을 수 있게 한다.
    public List<Map<String, Object>> toSnapshots(List<ChatStoreResponse> stores) {
        return stores.stream().map(this::toSnapshot).toList();
    }

    private Map<String, Object> toSnapshot(ChatStoreResponse store) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeId", store.storeId());
        result.put("name", store.name());
        result.put("address", store.address());
        result.put("phone", store.phone());
        result.put("latitude", store.latitude());
        result.put("longitude", store.longitude());
        result.put("distanceMeters", store.distanceMeters());
        return result;
    }
}
