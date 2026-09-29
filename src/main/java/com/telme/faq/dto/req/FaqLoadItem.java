package com.telme.faq.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// scripts/data/faq_*.json 한 건
// slot_id는 FAQ 식별자로 저장하고, 생성, 검증용 메타데이터(question_type·persona·trigger 등)는 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record FaqLoadItem(
        @JsonProperty("slot_id") String slotId,
        String category,
        String question,
        String answer,
        @JsonProperty("policy_ref") String policyRef
) {
}
