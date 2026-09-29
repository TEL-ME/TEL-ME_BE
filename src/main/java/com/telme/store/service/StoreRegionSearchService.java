package com.telme.store.service;

import com.telme.store.converter.StoreRegionSearchConverter;
import com.telme.store.dto.req.StoreRegionSearchRequest;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import com.telme.store.repository.StoreRepository;
import com.telme.store.repository.StoreSpecifications;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
                .and(StoreSpecifications.providesService(request.serviceType()));

        List<Store> stores = storeRepository.findAll(condition, Sort.by("storeId"));
        return storeRegionSearchConverter.toResponse(stores);
    }
}
