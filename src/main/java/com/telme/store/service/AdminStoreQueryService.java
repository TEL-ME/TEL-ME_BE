package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.converter.AdminStoreConverter;
import com.telme.store.dto.req.AdminStoreSearchRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.dto.res.StoreServiceTypeResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.StoreRepository;
import com.telme.store.repository.StoreServiceTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdminStoreQueryService {

    private final StoreRepository storeRepository;
    private final StoreServiceTypeRepository serviceTypeRepository;
    private final AdminStoreConverter converter;
    
    public List<StoreServiceTypeResponse> getServiceTypes() {
        return converter.toServiceTypes(serviceTypeRepository.findAllByOrderByServiceTypeIdAsc());
    }
    
    public AdminStoreListResponse getStores(AdminStoreSearchRequest request) {
        Page<Long> storeIds = storeRepository.findAdminStoreIds(
                request.keywordPattern(),
                request.status().toStatuses(),
                PageRequest.of(request.page(), request.size()));
        return converter.toListResponse(storeIds, orderedStores(storeIds.getContent()));
    }
    
    public AdminStoreDetailResponse getStore(long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new GeneralException(StoreErrorCode.STORE_NOT_FOUND));
        return converter.toDetail(store);
    }
    
    // in 조회는 순서를 보장하지 않아 페이지에서 받은 id 순서대로 다시 맞춘다
    private List<Store> orderedStores(List<Long> storeIds) {
        if(storeIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Store> byId = storeRepository.findAllWithServicesByIdIn(storeIds).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));
        return storeIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
