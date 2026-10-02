package com.telme.store.repository;

import com.telme.store.entity.Store;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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
    
    // 영업시간·업무만 바꾸면 stores 행에는 UPDATE가 나가지 않아 updated_at이 그대로다.
    // 저장했으면 수정 시각을 올린다. 대기 중인 변경을 먼저 반영하고, 끝나면 영속성 컨텍스트를 비운다
    // 그 사이 다른 관리자가 저장해 번호가 달라졌으면 0을 반환한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE stores SET updated_at = now(), lock_version = lock_version + 1 "
            + "WHERE store_id = :storeId AND lock_version = :lockVersion", nativeQuery = true)
    int touch(@Param("storeId") Long storeId, @Param("lockVersion") int lockVersion);
    
    // 폐점도 잠금 번호를 올려야, 그 전에 매장을 읽은 수정 요청의 touch가 0행이 된다.
    // 엔티티로 바꾸면 읽어 둔 다른 칸까지 옛 값으로 다시 쓰므로 상태만 바꾼다.
    // 이미 폐점이면 바꾸지 않는다. UPDATE가 나가면 트리거가 수정 시각을 올려 목록 맨 위로 올라온다.
    // 없는 매장과 이미 폐점한 매장 모두 0을 반환한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE stores SET status = 'CLOSED_DOWN', updated_at = now(), lock_version = lock_version + 1 "
            + "WHERE store_id = :storeId AND status <> 'CLOSED_DOWN'", nativeQuery = true)
    int close(@Param("storeId") Long storeId);
}
