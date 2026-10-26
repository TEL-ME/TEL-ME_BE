package com.telme.subscription.dto.res;

// 가입하지 않은 사용자는 오류가 아니라 subscribed=false, subscription=null
public record SubscriptionMeResponse(boolean subscribed, SubscriptionResponse subscription) {

    public static SubscriptionMeResponse notSubscribed() {
        return new SubscriptionMeResponse(false, null);
    }

    public static SubscriptionMeResponse of(SubscriptionResponse subscription) {
        return new SubscriptionMeResponse(true, subscription);
    }
}
