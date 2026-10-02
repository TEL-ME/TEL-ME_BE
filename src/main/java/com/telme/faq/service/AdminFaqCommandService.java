package com.telme.faq.service;

import com.telme.faq.converter.AdminFaqConverter;
import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.req.AdminFaqStatusRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.FaqCitationCount;
import com.telme.rag.repository.MessageSourceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminFaqCommandService {

    private final FaqRepository faqRepository;
    private final FaqEmbeddingSyncService embeddingSyncService;
    private final MessageSourceRepository messageSourceRepository;
    private final FaqEmbeddingTextAssembler textAssembler;
    private final AdminFaqConverter converter;
    private final EntityManager entityManager;
    private final FaqContentConstraintChecker constraintChecker;

    public AdminFaqDetailResponse create(AdminFaqSaveRequest request, Long adminId) {
        String contentHash = FaqContentHash.of(request.question(), request.answer());
        rejectDuplicate(request.question(), request.answer(), contentHash, null);

        Faq faq = saveOrRejectDuplicate(Faq.builder()
                .category(request.category().name())
                .question(request.question())
                .answer(request.answer())
                .policyRef(request.policyRef())
                .contentHash(contentHash)
                .status(request.status() == null ? Faq.Status.ACTIVE : request.status())
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());

        // 임베딩이 없으면 검색에 안 잡힌다. 실패 시 예외로 등록까지 되돌린다
        embeddingSyncService.upsert(faq.getFaqId());
        log.info("[AdminFaq] 등록 faqId={} adminId={}", faq.getFaqId(), adminId);
        return converter.toDetail(faq, 0L);
    }

    public AdminFaqDetailResponse update(Long faqId, AdminFaqSaveRequest request, Long adminId) {
        Faq faq = findFaq(faqId);
        rejectStaleWrite(faq, request.lockVersion());
        String contentHash = FaqContentHash.of(request.question(), request.answer());
        rejectDuplicate(request.question(), request.answer(), contentHash, faqId);

        String embeddingTextBefore = textAssembler.assemble(faq);
        boolean contentChanged = faq.update(
                request.category().name(),
                request.question(),
                request.answer(),
                request.policyRef(),
                contentHash,
                adminId);
        // 상태를 안 주면 그대로 둔다. 기본값을 ACTIVE로 두면 숨긴 FAQ의 오타만 고쳐도 다시 공개된다
        if (request.status() != null) {
            faq.changeStatus(request.status(), adminId);
        }

        // 충돌을 먼저 확정한다. 뒤로 미루면 이미 진 요청에서 임베딩 서버를 부르고 버린다.
        // 트리거가 채우는 updated_at도 UPDATE가 나간 뒤에야 읽힌다
        flushOrRejectConflict();

        // 버전이 오르면 e.faqVersion = f.version이 어긋나 반드시 다시 만들어야 하고,
        // CATEGORY_Q_A처럼 카테고리를 넣는 구성이면 카테고리만 바뀌어도 벡터가 낡는다
        if (contentChanged || !textAssembler.assemble(faq).equals(embeddingTextBefore)) {
            embeddingSyncService.upsert(faqId);
        }
        entityManager.refresh(faq);
        log.info("[AdminFaq] 수정 faqId={} contentChanged={} version={}", faqId, contentChanged, faq.getVersion());
        return converter.toDetail(faq, citationCount(faqId));
    }

    // 본문이 그대로라 재임베딩하지 않는다. 지운 FAQ도 벡터가 남아 있어 되살리면 바로 검색된다
    public AdminFaqDetailResponse changeStatus(Long faqId, AdminFaqStatusRequest request, Long adminId) {
        Faq faq = findFaq(faqId);
        rejectStaleWrite(faq, request.lockVersion());
        faq.changeStatus(request.status(), adminId);
        flushOrRejectConflict();
        entityManager.refresh(faq);
        log.info("[AdminFaq] 상태 변경 faqId={} status={} adminId={}", faqId, request.status(), adminId);
        return converter.toDetail(faq, citationCount(faqId));
    }

    // 실제로 지우지 않고 상태만 바꾼다. 임베딩은 남겨도 검색이 f.status = 'ACTIVE'로 거른다
    public void delete(Long faqId, Long adminId, Integer lockVersion) {
        Faq faq = findFaq(faqId);
        rejectStaleWrite(faq, lockVersion);
        faq.changeStatus(Faq.Status.DELETED, adminId);
        // 상태 변경도 UPDATE라 잠금에 걸린다. 커밋까지 미루면 충돌이 여기서 안 잡혀 500으로 나간다
        flushOrRejectConflict();
        log.info("[AdminFaq] 삭제 faqId={} adminId={}", faqId, adminId);
    }

    // 되돌릴 수 없어 지운 상태인 것만 받는다. 근거로 쓰인 적 있으면 지울 때 과거 답변의 근거가 끊긴다
    public void purge(Long faqId, Long adminId) {
        Faq faq = findFaq(faqId);
        if (faq.getStatus() != Faq.Status.DELETED) {
            throw new GeneralException(FaqErrorCode.PURGE_NOT_DELETED);
        }
        if (citationCount(faqId) > 0) {
            throw new GeneralException(FaqErrorCode.PURGE_CITED);
        }
        deleteOrRejectCited(faq);
        log.info("[AdminFaq] 영구 삭제 faqId={} adminId={}", faqId, adminId);
    }

    // 검사한 뒤 누가 되살려 근거로 쓰면 외래키가 막는다. 커밋까지 미루면 여기서 안 잡혀 500으로 나간다
    private void deleteOrRejectCited(Faq faq) {
        try {
            faqRepository.delete(faq);
            faqRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(FaqErrorCode.PURGE_CITED);
        }
    }

    // @Version은 같은 시점에 겹친 요청만 잡는다. 화면을 연 뒤 남이 먼저 저장하면
    // 이 요청은 저장 직전에 FAQ를 다시 읽어 최신 lock_version으로 덮어쓴다.
    // 화면이 들고 있던 값을 받아 지금 값과 맞춰 보면 그 경우도 막힌다.
    // 값을 안 보내면 검사하지 않는다 — 프론트가 보내기 전까지 기존 호출을 깨지 않기 위해서다
    private void rejectStaleWrite(Faq faq, Integer lockVersion) {
        if (lockVersion != null && !lockVersion.equals(faq.getLockVersion())) {
            throw new GeneralException(FaqErrorCode.CONCURRENT_UPDATE);
        }
    }

    // 앱 검사는 조회와 저장이 떨어져 있어 동시에 들어오면 둘 다 통과한다.
    // 그때는 uk_faqs_content_active 인덱스가 막고, 여기서 같은 응답으로 바꾼다
    private Faq saveOrRejectDuplicate(Faq faq) {
        try {
            return faqRepository.saveAndFlush(faq);
        } catch (DataIntegrityViolationException e) {
            throw toConflict(e);
        }
    }

    // 수정은 중복 인덱스와 lock_version 둘 다 걸릴 수 있어 함께 받는다.
    // 잠금 충돌은 내가 읽은 뒤 다른 관리자가 먼저 저장했다는 뜻이라 관리자가 할 일이 다르다
    private void flushOrRejectConflict() {
        try {
            faqRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw toConflict(e);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new GeneralException(FaqErrorCode.CONCURRENT_UPDATE);
        }
    }

    // 중복 인덱스가 아니면 원래 예외를 그대로 보낸다. 길이·NOT NULL 오류를 중복이라고 안내하면 안 된다
    private RuntimeException toConflict(DataIntegrityViolationException exception) {
        if (!constraintChecker.isViolation(exception)) {
            return exception;
        }
        return new GeneralException(FaqErrorCode.DUPLICATE_CONTENT);
    }

    // 같은 내용이 두 건이면 검색 top-k를 나눠 먹어 근거가 줄어든다.
    // 해시는 후보만 좁히고 같은 내용인지는 질문·답변으로 판단한다 (Faq.update와 같은 이유)
    private void rejectDuplicate(String question, String answer, String contentHash, Long selfFaqId) {
        boolean duplicated = faqRepository
                .findByContentHashAndStatusNot(contentHash, Faq.Status.DELETED).stream()
                .filter(other -> !other.getFaqId().equals(selfFaqId))
                .anyMatch(other -> other.getQuestion().equals(question) && other.getAnswer().equals(answer));
        if (duplicated) {
            throw new GeneralException(FaqErrorCode.DUPLICATE_CONTENT);
        }
    }

    // 수정 응답도 조회와 같은 모양을 유지한다. 근거로 쓰인 적 없으면 집계에 안 잡혀 0으로 둔다
    private long citationCount(Long faqId) {
        return messageSourceRepository.countByFaqIds(List.of(faqId)).stream()
                .map(FaqCitationCount::citationCount)
                .findFirst()
                .orElse(0L);
    }

    private Faq findFaq(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(() -> new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));
    }
}
