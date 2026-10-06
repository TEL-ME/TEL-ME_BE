package com.telme.store.repository;

import com.telme.store.entity.StoreServiceType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreServiceTypeRepository extends JpaRepository<StoreServiceType, Long> {

    // 등록, 수정 요청의 업무 코드를 엔티티로 바꿀 때 사용
    List<StoreServiceType> findByCodeIn(Collection<StoreServiceType.Code> codes);
    
    // 업무 체크박스 선택지. 목록·상세와 같은 id 순으로 보여준다
    List<StoreServiceType> findAllByOrderByServiceTypeIdAsc();
}
