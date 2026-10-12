package com.telme.intent.repository;

import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;

public record RoutingCount(Intent intent, Method method, long count) {
}
