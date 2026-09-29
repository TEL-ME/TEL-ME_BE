package com.telme.store.repository;

import com.telme.store.entity.Store;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public final class StoreSpecifications {

    private StoreSpecifications() {
    }

    public static Specification<Store> hasStatus(Store.Status status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Store> regionCodeStartsWith(String regionPrefix) {
        return (root, query, cb) -> regionPrefix == null
                ? null
                : cb.like(root.get("regionCode"), regionPrefix + "%");
    }

    public static Specification<Store> providesService(StoreServiceType.Code code) {
        return (root, query, cb) -> {
            if (code == null) {
                return null;
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<StoreService> service = subquery.from(StoreService.class);
            subquery.select(service.get("store").get("storeId"))
                    .where(cb.equal(service.get("store"), root),
                            cb.equal(service.get("serviceType").get("code"), code));
            return cb.exists(subquery);
        };
    }
}
