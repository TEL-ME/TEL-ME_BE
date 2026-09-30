package com.telme.store.repository;

import com.telme.store.entity.Store;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {
    // 검색어가 없으면 "%", 상태 필터가 ALL이면 모든 상태가 넘어온다. 검색어의 %와 _는 호출부에서 역슬래시로 막아 보낸다
    String ADMIN_FILTER = """
            where (lower(s.name) like :keyword escape '\\'
                    or lower(s.address) like :keyword escape '\\')
              and s.status in :statuses
            """;
    
    // 시드 매장은 updated_at 이 모두 같아 id를 보조 정렬로 둬야 페이지 사이에 중복,누락이 없다.
    @Query("select s.storeId from Store s " + ADMIN_FILTER + " order by s.updatedAt desc, s.storeId desc")
    Page<Long> findAdminStoreIds(
            @Param("keyword") String keyword,
            @Param("statuses") Collection<Store.Status> statuses,
            Pageable pageable);
    
    // 페이지 조회에 컬렉션 fetch join을 섞으면 전체를 메모리로 읽어 자르므로, id를 먼저 뽑고 업무까지 한 번에 읽는다
    @Query("""
            select distinct s from Store s
            left join fetch s.services ss
            left join fetch ss.serviceType
            where s.storeId in :storeIds
            """)
    List<Store> findAllWithServicesByIdIn(@Param("storeIds") Collection<Long> storeIds);
}
