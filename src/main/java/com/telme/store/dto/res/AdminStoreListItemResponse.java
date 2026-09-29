package com.telme.store.dto.res;

import java.util.List;

public record AdminStoreListItemResponse(
        Long storeId,
        String name,
        String address,
        String phone,
        List<AdminStoreServiceResponse> services,
        String status
        ) {

}
