package com.telme.faq.service;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.entity.FaqCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// CATEGORY_Q_A는 임베딩 텍스트에 카테고리를 넣는다. 기본값 Q_A에서는 드러나지 않는 경로라 설정을 바꿔 확인한다
@SpringBootTest
@TestPropertySource(properties = "faq.embedding-text.variant=CATEGORY_Q_A")
@Transactional
class AdminFaqCategoryReembedTest {

    private static final Long ADMIN_ID = 2L;
    private static final String QUESTION = "카테고리 재임베딩 테스트 질문";
    private static final String ANSWER = "카테고리 재임베딩 테스트 답변.";

    @Autowired
    private AdminFaqCommandService service;

    @MockitoBean
    private FaqEmbeddingSyncService embeddingSyncService;

    @Test
    @DisplayName("임베딩에 카테고리가 들어가는 구성이면 카테고리만 바꿔도 다시 만든다")
    void 카테고리가_임베딩에_들어가면_재임베딩한다() {
        Long faqId = service.create(request(FaqCategory.SERVICE), ADMIN_ID).faqId();
        clearInvocations(embeddingSyncService);

        service.update(faqId, request(FaqCategory.PLAN), ADMIN_ID);

        verify(embeddingSyncService).upsert(faqId);
    }

    private AdminFaqSaveRequest request(FaqCategory category) {
        return new AdminFaqSaveRequest(category, QUESTION, ANSWER, null, null);
    }
}
