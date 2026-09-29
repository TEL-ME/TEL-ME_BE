package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqCategory;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 임베딩 동기화는 Ollama를 호출하므로 목으로 둔다. 여기서 볼 것은 언제 부르는지지 벡터 내용이 아니다.
// 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminFaqCommandServiceTest {

    // faqs.created_by·updated_by에 users FK가 있어 시드에 실재하는 계정만 쓸 수 있다
    private static final Long ADMIN_ID = 2L;
    private static final Long OTHER_ADMIN_ID = 1L;
    private static final String QUESTION = "관리자 등록 테스트 질문";
    private static final String ANSWER = "관리자 등록 테스트 답변.";

    @Autowired
    private AdminFaqCommandService service;

    @Autowired
    private FaqRepository faqRepository;

    @MockitoBean
    private FaqEmbeddingSyncService embeddingSyncService;

    @Test
    @DisplayName("등록하면 해시가 채워지고 버전 1로 시작한다")
    void 등록하면_해시와_버전이_채워진다() {
        AdminFaqDetailResponse created = service.create(request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID);

        assertThat(created.version()).isEqualTo(1);
        assertThat(created.contentHash()).isEqualTo(FaqContentHash.of(QUESTION, ANSWER));
        assertThat(created.status()).isEqualTo(Faq.Status.ACTIVE.name());
        assertThat(created.createdBy()).isEqualTo(ADMIN_ID);
        assertThat(created.updatedBy()).isEqualTo(ADMIN_ID);
        assertThat(created.citationCount()).isZero();
    }

    @Test
    @DisplayName("등록하면 임베딩을 만든다")
    void 등록하면_임베딩을_만든다() {
        AdminFaqDetailResponse created = service.create(request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID);

        verify(embeddingSyncService).upsert(created.faqId());
    }

    @Test
    @DisplayName("답변을 고치면 해시와 버전이 올라가고 임베딩을 다시 만든다")
    void 내용이_바뀌면_버전이_오르고_재임베딩한다() {
        Long faqId = created();

        AdminFaqDetailResponse updated = service.update(
                faqId, request(FaqCategory.SERVICE, QUESTION, "답변을 고쳤습니다."), OTHER_ADMIN_ID);

        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.contentHash()).isEqualTo(FaqContentHash.of(QUESTION, "답변을 고쳤습니다."));
        assertThat(updated.updatedBy()).isEqualTo(OTHER_ADMIN_ID);
        verify(embeddingSyncService).upsert(faqId);
    }

    @Test
    @DisplayName("카테고리만 바꾸면 버전은 그대로고 재임베딩하지 않는다")
    void 카테고리만_바뀌면_재임베딩하지_않는다() {
        Long faqId = created();

        AdminFaqDetailResponse updated = service.update(
                faqId, request(FaqCategory.PLAN, QUESTION, ANSWER), ADMIN_ID);

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.category()).isEqualTo(FaqCategory.PLAN.name());
        verify(embeddingSyncService, never()).upsert(faqId);
    }

    @Test
    @DisplayName("수정 응답에 DB가 채운 시각이 담긴다")
    void 수정_응답에_시각이_담긴다() {
        Long faqId = created();

        AdminFaqDetailResponse updated = service.update(
                faqId, request(FaqCategory.SERVICE, QUESTION, "또 고쳤습니다."), ADMIN_ID);

        // 트리거가 쓰는 now()는 트랜잭션 시작 시각이라 한 트랜잭션 안에서는 값이 앞으로 가지 않는다.
        // 여기서는 시각이 null로 나가지 않는 것까지만 본다
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(updated.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제하면 상태만 DELETED로 바뀌고 임베딩은 지우지 않는다")
    void 삭제는_상태만_바꾼다() {
        Long faqId = created();

        service.delete(faqId, OTHER_ADMIN_ID);

        Faq faq = faqRepository.findById(faqId).orElseThrow();
        assertThat(faq.getStatus()).isEqualTo(Faq.Status.DELETED);
        assertThat(faq.getUpdatedBy()).isEqualTo(OTHER_ADMIN_ID);
        verify(embeddingSyncService, never()).delete(faqId);
    }

    @Test
    @DisplayName("없는 FAQ를 수정하거나 삭제하면 FAQ404-0을 던진다")
    void 없는_FAQ는_예외를_던진다() {
        assertThatThrownBy(() -> service.update(-1L, request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.delete(-1L, ADMIN_ID)).isInstanceOf(GeneralException.class);
    }

    // 등록도 upsert를 부르므로, 수정·삭제가 부른 것만 세도록 호출 기록을 비우고 돌려준다
    private Long created() {
        Long faqId = service.create(request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID).faqId();
        clearInvocations(embeddingSyncService);
        return faqId;
    }

    private AdminFaqSaveRequest request(FaqCategory category, String question, String answer) {
        return new AdminFaqSaveRequest(category, question, answer, null, null);
    }
}
