package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.converter.StoreConverter;
import com.telme.store.dto.res.StoreDetailResponse;
import com.telme.store.dto.res.StoreServiceTypeListResponse;
import com.telme.store.entity.Store;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.StoreRepository;
import com.telme.store.repository.StoreServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final StoreServiceTypeRepository storeServiceTypeRepository;
    private final StoreConverter storeConverter;

    // 폐점 매장은 검색에서 빠지므로 상세도 없는 매장과 같게 404로 응답한다
    public StoreDetailResponse getStore(long storeId) {
        Store store = storeRepository.findWithServicesByStoreIdAndStatus(storeId, Store.Status.OPEN)
                .orElseThrow(() -> new GeneralException(StoreErrorCode.STORE_NOT_FOUND));
        return storeConverter.toDetailResponse(store, storeRepository.findHoursByStoreId(storeId));
    }

    // 매장 응답의 업무 목록과 같은 순서(업무 ID 순)로 내려 칩 순서가 화면마다 달라지지 않게 한다
    public StoreServiceTypeListResponse getServiceTypes() {
        return storeConverter.toServiceTypeListResponse(
                storeServiceTypeRepository.findAll(Sort.by("serviceTypeId")));
    }
}
