package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.req.AdminFaqStatusRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqCategory;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 임베딩 동기화는 Ollama를 호출하므로 목으로 둔다. 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminFaqRestoreAndPurgeTest {

    // faqs.created_by·updated_by에 users FK가 있어 시드에 실재하는 계정만 쓸 수 있다
    private static final Long ADMIN_ID = 2L;
    private static final Long OTHER_ADMIN_ID = 1L;
    private static final String QUESTION = "복구 테스트 질문";
    private static final String ANSWER = "복구 테스트 답변.";

    @Autowired
    private AdminFaqCommandService service;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private FaqEmbeddingSyncService embeddingSyncService;

    @Test
    @DisplayName("삭제한 FAQ를 ACTIVE로 되돌리면 공개되고 재임베딩하지 않는다")
    void 복구하면_공개되고_재임베딩하지_않는다() {
        Long faqId = deleted();

        AdminFaqDetailResponse restored = service.changeStatus(faqId, status(Faq.Status.ACTIVE, null), ADMIN_ID);

        assertThat(restored.status()).isEqualTo(Faq.Status.ACTIVE.name());
        assertThat(restored.updatedBy()).isEqualTo(ADMIN_ID);
        verify(embeddingSyncService, never()).upsert(faqId);
    }

    @Test
    @DisplayName("상태만 바꾸면 내용 버전은 그대로다")
    void 상태_변경은_버전을_올리지_않는다() {
        Long faqId = created();

        AdminFaqDetailResponse hidden = service.changeStatus(faqId, status(Faq.Status.HIDDEN, null), ADMIN_ID);

        assertThat(hidden.status()).isEqualTo(Faq.Status.HIDDEN.name());
        assertThat(hidden.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("화면을 연 뒤 다른 관리자가 저장했으면 FAQ409-1로 막는다")
    void 낡은_lockVersion은_막힌다() {
        Long faqId = created();
        Integer opened = faqRepository.findById(faqId).orElseThrow().getLockVersion();
        service.changeStatus(faqId, status(Faq.Status.HIDDEN, null), OTHER_ADMIN_ID);

        assertThatThrownBy(() -> service.changeStatus(faqId, status(Faq.Status.ACTIVE, opened), ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("lockVersion을 생략하면 검사하지 않는다")
    void lockVersion을_생략하면_검사하지_않는다() {
        Long faqId = created();
        service.changeStatus(faqId, status(Faq.Status.HIDDEN, null), OTHER_ADMIN_ID);

        AdminFaqDetailResponse restored = service.changeStatus(faqId, status(Faq.Status.ACTIVE, null), ADMIN_ID);

        assertThat(restored.status()).isEqualTo(Faq.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("지운 사이 같은 내용이 등록되면 복구가 FAQ409-0으로 막힌다")
    void 같은_내용이_생기면_복구할_수_없다() {
        Long faqId = deleted();
        service.create(request(QUESTION, ANSWER), ADMIN_ID);

        assertThatThrownBy(() -> service.changeStatus(faqId, status(Faq.Status.ACTIVE, null), ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.DUPLICATE_CONTENT);
    }

    @Test
    @DisplayName("삭제 처리하지 않은 FAQ는 영구 삭제할 수 없다")
    void 지우지_않은_FAQ는_영구_삭제할_수_없다() {
        Long faqId = created();

        assertThatThrownBy(() -> service.purge(faqId, ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.PURGE_NOT_DELETED);
    }

    @Test
    @DisplayName("근거로 쓰인 적 없으면 행과 임베딩이 함께 사라진다")
    void 영구_삭제하면_임베딩도_사라진다() {
        Long faqId = deleted();
        insertEmbedding(faqId);

        service.purge(faqId, ADMIN_ID);

        assertThat(faqRepository.findById(faqId)).isEmpty();
        assertThat(countEmbeddings(faqId)).isZero();
    }

    @Test
    @DisplayName("답변 근거로 쓰인 FAQ는 영구 삭제할 수 없다")
    void 근거로_쓰인_FAQ는_영구_삭제할_수_없다() {
        Long faqId = deleted();
        insertCitation(faqId);

        assertThatThrownBy(() -> service.purge(faqId, ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.PURGE_CITED);
    }

    @Test
    @DisplayName("근거로 쓰인 FAQ를 거절한 뒤에도 같은 트랜잭션을 이어 쓸 수 있다")
    void 거절_뒤에도_트랜잭션이_살아_있다() {
        Long faqId = deleted();
        insertCitation(faqId);

        assertThatThrownBy(() -> service.purge(faqId, ADMIN_ID)).isInstanceOf(GeneralException.class);

        // 외래키까지 가서 막히면 DELETE가 실패한 채로 남아 뒤 작업이 모두 깨진다.
        // 앱에서 먼저 거르는지 보는 자리라 이 조회가 성공해야 한다
        assertThat(faqRepository.findById(faqId)).isPresent();
    }

    @Test
    @DisplayName("없는 FAQ는 상태 변경도 영구 삭제도 FAQ404-0을 던진다")
    void 없는_FAQ는_예외를_던진다() {
        assertThatThrownBy(() -> service.changeStatus(-1L, status(Faq.Status.ACTIVE, null), ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.FAQ_NOT_FOUND);
        assertThatThrownBy(() -> service.purge(-1L, ADMIN_ID))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", FaqErrorCode.FAQ_NOT_FOUND);
    }

    // 등록도 upsert를 부르므로, 상태 변경이 부른 것만 세도록 호출 기록을 비운다
    private Long created() {
        Long faqId = service.create(request(QUESTION, ANSWER), ADMIN_ID).faqId();
        clearInvocations(embeddingSyncService);
        return faqId;
    }

    private Long deleted() {
        Long faqId = created();
        service.delete(faqId, ADMIN_ID, null);
        return faqId;
    }

    // 임베딩 생성은 목이라 CASCADE를 보려면 행을 직접 넣어야 한다
    private void insertEmbedding(Long faqId) {
        entityManager.createNativeQuery(
                        "insert into faq_embeddings (faq_id, embedding, faq_version)"
                                + " values (:faqId, array_fill(0.0, array[1024])::vector, 1)")
                .setParameter("faqId", faqId)
                .executeUpdate();
    }

    private long countEmbeddings(Long faqId) {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from faq_embeddings where faq_id = :faqId")
                .setParameter("faqId", faqId)
                .getSingleResult()).longValue();
    }

    // 근거 기록은 답변 메시지에 달려 세션·메시지까지 함께 만든다. 시드에 있는 행에 기대지 않는다
    private void insertCitation(Long faqId) {
        Number sessionId = (Number) entityManager.createNativeQuery(
                        "insert into chat_sessions (user_id, title) values (:userId, '근거 테스트')"
                                + " returning session_id")
                .setParameter("userId", ADMIN_ID)
                .getSingleResult();
        Number messageId = (Number) entityManager.createNativeQuery(
                        "insert into chat_messages (session_id, sequence_no, role, message_type, content)"
                                + " values (:sessionId, 1, 'ASSISTANT', 'ANSWER', '근거 테스트 답변')"
                                + " returning message_id")
                .setParameter("sessionId", sessionId.longValue())
                .getSingleResult();
        entityManager.createNativeQuery(
                        "insert into message_sources (message_id, faq_id, title_snapshot, faq_version)"
                                + " values (:messageId, :faqId, '근거 테스트', 1)")
                .setParameter("messageId", messageId.longValue())
                .setParameter("faqId", faqId)
                .executeUpdate();
    }

    private AdminFaqSaveRequest request(String question, String answer) {
        return new AdminFaqSaveRequest(FaqCategory.SERVICE, question, answer, null, null, null);
    }

    private AdminFaqStatusRequest status(Faq.Status status, Integer lockVersion) {
        return new AdminFaqStatusRequest(status, lockVersion);
    }
}
