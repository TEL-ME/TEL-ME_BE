package com.telme.faq.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// scripts/data/faq_*.json 한 건
// 생성, 검증용 메타데이터(slot_id·question_type·persona·trigger 등)는 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record FaqLoadItem(
        String category,
        String question,
        String answer,
        @JsonProperty("policy_ref") String policyRef
) {
}
