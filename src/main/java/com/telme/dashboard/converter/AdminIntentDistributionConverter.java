package com.telme.dashboard.converter;

import com.telme.dashboard.dto.res.AdminIntentDistributionResponse;
import com.telme.dashboard.dto.res.AdminIntentDistributionResponse.ClassificationMethod;
import com.telme.dashboard.dto.res.AdminIntentDistributionResponse.IntentShare;
import com.telme.dashboard.dto.res.AdminIntentDistributionResponse.MethodShare;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.repository.RoutingCount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminIntentDistributionConverter {

    public AdminIntentDistributionResponse toResponse(Instant from, Instant to, List<RoutingCount> counts) {
        EnumMap<Intent, Long> intents = new EnumMap<>(Intent.class);
        EnumMap<ClassificationMethod, Long> methods = new EnumMap<>(ClassificationMethod.class);
        long total = 0;
        for (RoutingCount count : counts) {
            total += count.count();
            intents.merge(count.intent(), count.count(), Long::sum);
            ClassificationMethod method = count.method() == null
                    ? ClassificationMethod.UNRECORDED : ClassificationMethod.valueOf(count.method().name());
            methods.merge(method, count.count(), Long::sum);
        }
        final long totalCount = total;
        return new AdminIntentDistributionResponse(from, to, totalCount,
                Arrays.stream(Intent.values()).map(intent -> {
                    long count = intents.getOrDefault(intent, 0L);
                    return new IntentShare(intent, count, percentage(count, totalCount));
                }).toList(),
                Arrays.stream(ClassificationMethod.values()).map(method -> {
                    long count = methods.getOrDefault(method, 0L);
                    return new MethodShare(method, count, percentage(count, totalCount));
                }).toList());
    }

    private BigDecimal percentage(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
