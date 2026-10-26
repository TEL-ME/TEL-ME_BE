package com.telme.dashboard.dto.res;

import com.telme.intent.entity.QueryRouting.Intent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminIntentDistributionResponse(
        @Schema(description = "집계 시작 시각(포함)") Instant from,
        @Schema(description = "집계 끝 시각(제외)") Instant to,
        @Schema(description = "기간 내 저장된 분류 기록 수. 전체 고객 입력 수와 다를 수 있음") long totalCount,
        List<IntentShare> intents,
        List<MethodShare> methods) {

    public record IntentShare(Intent intent, long count,
            @Schema(description = "전체 분류 기록 대비 백분율. 소수점 둘째 자리 HALF_UP") BigDecimal percentage) {
    }

    public enum ClassificationMethod {
        RULE, LLM, UNRECORDED
    }

    public record MethodShare(
            @Schema(description = "RULE은 장애 대체·규칙 보정을 포함. UNRECORDED는 method null")
            ClassificationMethod method,
            long count,
            @Schema(description = "전체 분류 기록 대비 백분율. 반올림으로 합이 100과 다를 수 있음")
            BigDecimal percentage) {
    }
}
