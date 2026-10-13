package com.telme.usage.service;

// limit·remaining·percent는 무제한이면 null. 데이터는 MB, 통화는 분, 문자는 건 단위
public record ResourceUsage(
        long used,
        Long limit,
        Long remaining,
        Integer percent,
        boolean unlimited,
        boolean exceeded
) {

    public static ResourceUsage of(long used, Long limit) {
        if (limit == null) {
            return new ResourceUsage(used, null, null, null, true, false);
        }
        return new ResourceUsage(used, limit, Math.max(0, limit - used),
                (int) Math.round(used * 100.0 / limit), false, used > limit);
    }
}
