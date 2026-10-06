package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.converter.AdminStoreConverter;
import com.telme.store.dto.req.AdminStoreSaveRequest;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.StoreRepository;
import com.telme.store.repository.StoreServiceTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminStoreCommandService {

    private final StoreRepository storeRepository;
    private final StoreServiceTypeRepository serviceTypeRepository;
    private final AdminStoreConverter converter;
    
    public AdminStoreDetailResponse create(AdminStoreSaveRequest request, Long adminId) {
        Store store = Store.builder()
                .name(request.name())
                .address(request.address())
                .phone(request.phone())
                .regionCode(request.regionCode())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
        applyHoursAndServices(store, request);
        storeRepository.saveAndFlush(store);
        log.info("[AdminStore] 등록 storeId={} adminId={}", store.getStoreId(), adminId);
        return converter.toDetail(store);
    }
    
    public AdminStoreDetailResponse update(Long storeId, AdminStoreSaveRequest request, Long adminId) {
        Store store = findStore(storeId);
        // 폐점을 되돌리는 API가 없어 고쳐도 쓰일 곳이 없다. 상세 조회는 폐점 매장도 보여주므로 404가 아니라 409다
        if (store.getStatus() == Store.Status.CLOSED_DOWN) {
            throw new GeneralException(StoreErrorCode.STORE_CLOSED);
        }
        // 수정 요청은 컨트롤러가 lockVersion을 필수로 검증한다. 그 검증이 빠져도 500이 아니라
        // 덮어쓰기를 막는 쪽으로 실패하도록, 번호가 없으면 확인할 수 없는 저장으로 보고 409로 막는다
        Integer expected = request.lockVersion();
        if (expected == null) {
            throw new GeneralException(StoreErrorCode.CONCURRENT_UPDATE);
        }
        store.update(request.name(), request.address(), request.phone(), request.regionCode(), request.latitude(), request.longitude());
        applyHoursAndServices(store, request);
        // 0행이면 예외로 트랜잭션이 롤백돼 앞에서 반영한 매장·영업시간·업무 변경도 함께 취소된다
        if (storeRepository.touch(storeId, expected) == 0) {
            throw new GeneralException(StoreErrorCode.CONCURRENT_UPDATE);
        }
        log.info("[AdminStore] 수정 storeId={} adminId={}", storeId, adminId);
        // touch가 영속성 컨텍스트를 비워서, 다시 읽어야 DB가 채운 수정 시각이 담긴다
        return converter.toDetail(findStore(storeId));
    }
    
    // 실제로 지우지 않고 폐점으로 바꾼다. 이미 폐점이면 바뀌는 값이 없어 그대로 성공한다
    public void delete(Long storeId, Long adminId) {
        if (storeRepository.close(storeId) == 0 && !storeRepository.existsById(storeId)) {
            throw new GeneralException(StoreErrorCode.STORE_NOT_FOUND);
        }
        log.info("[AdminStore] 삭제(폐점) storeId={} adminId={}", storeId, adminId);
    }
    
    private void applyHoursAndServices(Store store, AdminStoreSaveRequest request) {
        request.hours().forEach(h -> store.changeHours(
                (short) h.dayOfWeek().getValue(), h.openTime(), h.closeTime(), h.closed()));
        store.replaceServices(findServiceTypes(request.serviceCodes()));
    }
    
    private List<StoreServiceType> findServiceTypes(List<StoreServiceType.Code> codes) {
        List<StoreServiceType> types = serviceTypeRepository.findByCodeIn(codes);
        if (types.size() != codes.size()) {
            // 코드는 enum으로 검증했으므로 여기 오면 store_service_types 데이터가 빠진 서버 설정 문제다
            throw new IllegalStateException("store_service_types에 없는 업무 코드가 있습니다: " + codes);
        }
        return types;
    }
    
    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new GeneralException(StoreErrorCode.STORE_NOT_FOUND));
    }
}
