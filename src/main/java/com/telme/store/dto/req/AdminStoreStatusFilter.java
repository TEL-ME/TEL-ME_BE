package com.telme.store.dto.req;

import java.util.List;

import com.telme.store.entity.Store;

public enum AdminStoreStatusFilter {

    OPEN, CLOSED_DOWN, ALL;
    
    private static final List<Store.Status> EVERY_STATUS = List.of(Store.Status.values());
    
    public List<Store.Status> toStatuses() {
        return this == ALL ? EVERY_STATUS : List.of(Store.Status.valueOf(name()));
    }
}
