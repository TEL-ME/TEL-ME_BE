package com.telme.store.service;

import com.telme.store.converter.StoreRegionSearchConverter;
import com.telme.store.dto.req.StoreRegionSearchRequest;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import com.telme.store.repository.StoreRepository;
import com.telme.store.repository.StoreSpecifications;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreRegionSearchService {

    private final StoreRepository storeRepository;
    private final StoreRegionSearchConverter storeRegionSearchConverter;

    public StoreRegionSearchResponse search(StoreRegionSearchRequest request) {
        Specification<Store> condition = StoreSpecifications.hasStatus(Store.Status.OPEN)
                .and(StoreSpecifications.regionCodeStartsWith(request.region()))
                .and(StoreSpecifications.providesService(request.serviceTypeCode()));

        Page<Store> page = storeRepository.findAll(
                condition, PageRequest.of(request.page(), request.size(), Sort.by("storeId")));
        return storeRegionSearchConverter.toResponse(page, withServices(page.getContent()));
    }

    private List<Store> withServices(List<Store> stores) {
        if (stores.isEmpty()) {
            return List.of();
        }
        List<Long> storeIds = stores.stream().map(Store::getStoreId).toList();
        Map<Long, Store> byId = storeRepository.findAllWithServicesByIdIn(storeIds).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));
        return storeIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
