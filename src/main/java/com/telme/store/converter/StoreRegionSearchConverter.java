package com.telme.store.converter;

import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import com.telme.store.repository.StoreTranslationRepository.Text;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreRegionSearchConverter {

    private final StoreCommonConverter storeCommonConverter;

    public StoreRegionSearchResponse toResponse(Page<?> page, List<Store> stores, Map<Long, Text> translations) {
        return new StoreRegionSearchResponse(
                stores.stream().map(store -> toStore(store, translations.get(store.getStoreId()))).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private StoreRegionSearchResponse.Store toStore(Store store, Text translation) {
        return new StoreRegionSearchResponse.Store(
                store.getStoreId(),
                translation == null ? store.getName() : translation.name(),
                translation == null ? store.getAddress() : translation.address(),
                store.getAddress(),
                store.getPhone(),
                store.getRegionCode(),
                store.getLatitude(),
                store.getLongitude(),
                storeCommonConverter.toServiceTypes(store.getServices())
        );
    }
}
