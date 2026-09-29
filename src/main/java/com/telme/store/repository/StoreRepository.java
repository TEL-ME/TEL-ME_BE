package com.telme.store.repository;

import com.telme.store.entity.Store;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StoreRepository extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {

    @Override
    @EntityGraph(attributePaths = {"services", "services.serviceType"})
    List<Store> findAll(Specification<Store> spec, Sort sort);
}
