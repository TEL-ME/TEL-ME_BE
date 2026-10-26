package com.telme.store.dto.res;

import java.time.LocalTime;

public record StoreHoursResponse(String dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {

}
