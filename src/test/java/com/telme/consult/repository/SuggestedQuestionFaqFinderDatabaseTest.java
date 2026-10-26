package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class SuggestedQuestionFaqFinderDatabaseTest extends LocalConsultDatabaseTest {
    private SuggestedQuestionFaqFinder finder;

    @BeforeEach
    void addSlotId() {
        // 상담 테스트 스키마에는 원본 FAQ 식별자 칸이 없어 이 테스트에서만 더한다
        jdbc.execute("ALTER TABLE faqs ADD COLUMN slot_id VARCHAR(50) UNIQUE");
        finder = new SuggestedQuestionFaqFinder(jdbc, "bge-m3");
    }

    private long faq(String slotId, String question, String status, String syncStatus, int embeddedVersion) {
        return faq(slotId, question, status, syncStatus, embeddedVersion, "bge-m3");
    }

    private long faq(
            String slotId, String question, String status, String syncStatus, int embeddedVersion, String model) {
        Long faqId = jdbc.queryForObject(
                "INSERT INTO faqs(category,question,answer,policy_ref,status,slot_id) "
                        + "VALUES ('USIM',?,'답변','USIM-02',?,?) RETURNING faq_id",
                Long.class, question, status, slotId);
        jdbc.update(
                "INSERT INTO faq_embeddings(faq_id,embedding,faq_version,sync_status,model_name) "
                        + "VALUES (?, array_fill(0.1::real, ARRAY[1024])::vector, ?, ?, ?)",
                faqId, embeddedVersion, syncStatus, model);
        return faqId;
    }

    @Test
    void readsPolicyRefByFaqId() {
        long faqId = faq("USIM-0010", "유심 재발급 시 필요한 서류를 알려주세요.", "ACTIVE", "SYNCED", 1);

        assertEquals("USIM-02", finder.policyRef(faqId));
        assertNull(finder.policyRef(faqId + 1000));
    }

    // 검색(FaqEmbeddingRepository)과 같은 조건: ACTIVE, 임베딩 SYNCED, 임베딩 버전 = FAQ 버전, 현재 임베딩 모델
    @Test
    void returnsOnlyFaqsThatSearchCanFind() {
        faq("ACTIVE-1", "살아 있는 질문", "ACTIVE", "SYNCED", 1);
        faq("HIDDEN-1", "숨긴 질문", "HIDDEN", "SYNCED", 1);
        faq("PENDING-1", "재임베딩 대기 질문", "ACTIVE", "PENDING", 1);
        faq("STALE-1", "예전 벡터 질문", "ACTIVE", "SYNCED", 0);
        faq("OLD-MODEL-1", "다른 모델 벡터 질문", "ACTIVE", "SYNCED", 1, "old-model");

        Map<String, String> found = finder.searchableQuestions(
                List.of("ACTIVE-1", "HIDDEN-1", "PENDING-1", "STALE-1", "OLD-MODEL-1", "MISSING-1"));

        assertEquals(Map.of("ACTIVE-1", "살아 있는 질문"), found);
    }

    @Test
    void returnsCurrentQuestionAfterEdit() {
        long faqId = faq("USIM-0010", "예전 질문", "ACTIVE", "SYNCED", 2);
        jdbc.update("UPDATE faqs SET question = '고친 질문', version = 2 WHERE faq_id = ?", faqId);

        assertEquals(Map.of("USIM-0010", "고친 질문"), finder.searchableQuestions(List.of("USIM-0010")));
    }

    @Test
    void emptySlotIdsSkipQuery() {
        assertTrue(finder.searchableQuestions(List.of()).isEmpty());
    }
}
