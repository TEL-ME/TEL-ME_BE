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
    @DisplayName("상태를 주지 않고 수정하면 숨긴 FAQ가 다시 공개되지 않는다")
    void 상태를_생략한_수정은_기존_상태를_유지한다() {
        Long faqId = created();
        service.update(faqId, new AdminFaqSaveRequest(
                FaqCategory.SERVICE, QUESTION, ANSWER, null, Faq.Status.HIDDEN), ADMIN_ID);

        service.update(faqId, request(FaqCategory.SERVICE, QUESTION, "답변만 고칩니다."), ADMIN_ID);

        assertThat(faqRepository.findById(faqId).orElseThrow().getStatus()).isEqualTo(Faq.Status.HIDDEN);
    }

    @Test
    @DisplayName("상태를 주면 그 상태로 바뀐다")
    void 상태를_주면_바뀐다() {
        Long faqId = created();

        service.update(faqId, new AdminFaqSaveRequest(
                FaqCategory.SERVICE, QUESTION, ANSWER, null, Faq.Status.HIDDEN), ADMIN_ID);

        assertThat(faqRepository.findById(faqId).orElseThrow().getStatus()).isEqualTo(Faq.Status.HIDDEN);
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
    @DisplayName("질문과 답변이 같은 FAQ를 또 등록하면 FAQ409-0을 던진다")
    void 같은_내용을_두_번_등록하면_예외를_던진다() {
        created();

        assertThatThrownBy(() -> service.create(request(FaqCategory.PLAN, QUESTION, ANSWER), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("다른 FAQ와 같은 내용으로 수정해도 FAQ409-0을 던진다")
    void 다른_FAQ와_같은_내용으로는_수정할_수_없다() {
        created();
        Long other = service.create(request(FaqCategory.SERVICE, "다른 질문", "다른 답변."), ADMIN_ID).faqId();

        assertThatThrownBy(() -> service.update(other, request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("자기 자신과 같은 내용으로 다시 저장하는 것은 막지 않는다")
    void 자기_내용_그대로_저장은_막지_않는다() {
        Long faqId = created();

        AdminFaqDetailResponse updated = service.update(
                faqId, request(FaqCategory.PLAN, QUESTION, ANSWER), ADMIN_ID);

        assertThat(updated.category()).isEqualTo(FaqCategory.PLAN.name());
    }

    @Test
    @DisplayName("이어 붙인 해시가 같아도 질문·답변이 바뀌면 버전이 오른다")
    void 경계가_다른_조합도_변경으로_본다() {
        Long faqId = service.create(request(FaqCategory.SERVICE, "가나", "다라"), ADMIN_ID).faqId();
        clearInvocations(embeddingSyncService);

        // "가나"+"다라"와 "가나다"+"라"는 이어 붙이면 같은 글자라 해시가 같다
        AdminFaqDetailResponse updated = service.update(
                faqId, request(FaqCategory.SERVICE, "가나다", "라"), ADMIN_ID);

        assertThat(updated.version()).isEqualTo(2);
        verify(embeddingSyncService).upsert(faqId);
    }

    @Test
    @DisplayName("이어 붙인 해시가 같아도 내용이 다르면 등록을 막지 않는다")
    void 해시만_같은_다른_내용은_등록된다() {
        service.create(request(FaqCategory.SERVICE, "가나", "다라"), ADMIN_ID);

        // "가나"+"다라"와 "가나다"+"라"는 이어 붙이면 같은 글자라 해시가 같다
        AdminFaqDetailResponse other = service.create(request(FaqCategory.SERVICE, "가나다", "라"), ADMIN_ID);

        assertThat(other.faqId()).isNotNull();
        assertThat(other.question()).isEqualTo("가나다");
    }

    @Test
    @DisplayName("삭제한 FAQ와 같은 내용은 다시 등록할 수 있다")
    void 삭제한_내용은_다시_등록할_수_있다() {
        Long faqId = created();
        service.delete(faqId, ADMIN_ID);

        AdminFaqDetailResponse again = service.create(request(FaqCategory.SERVICE, QUESTION, ANSWER), ADMIN_ID);

        assertThat(again.faqId()).isNotEqualTo(faqId);
        assertThat(again.status()).isEqualTo(Faq.Status.ACTIVE.name());
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
