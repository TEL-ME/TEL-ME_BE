package com.telme.store.dto.res;

import java.util.List;

public record AdminStoreListResponse(
        List<AdminStoreListItemResponse> stores,
        int page,
        int size,
        long totalElements,
        int totalPages
        ) {

}
