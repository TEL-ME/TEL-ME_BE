package com.telme.chat.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.exception.StoreErrorCode;

public record ChatCoordinates(double latitude, double longitude) {
    public ChatCoordinates {
        if (!isValid(latitude, longitude)) {
            throw new GeneralException(StoreErrorCode.INVALID_COORDINATE);
        }
    }

    public static boolean isValid(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return latitude == null && longitude == null;
        }
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static ChatCoordinates optional(Double latitude, Double longitude) {
        if (!isValid(latitude, longitude)) {
            throw new GeneralException(StoreErrorCode.INVALID_COORDINATE);
        }
        return latitude == null ? null : new ChatCoordinates(latitude, longitude);
    }

    @Override
    public String toString() {
        return "ChatCoordinates[REDACTED]";
    }
}
