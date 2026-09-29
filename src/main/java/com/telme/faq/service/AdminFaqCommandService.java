package com.telme.faq.service;

import com.telme.faq.converter.AdminFaqConverter;
import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.FaqCitationCount;
import com.telme.rag.repository.MessageSourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public AdminFaqDetailResponse create(AdminFaqSaveRequest request, Long adminId) {
        String contentHash = FaqContentHash.of(request.question(), request.answer());
        rejectDuplicate(request.question(), request.answer(), contentHash, null);

        Faq faq = faqRepository.save(Faq.builder()
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

        // 버전이 오르면 e.faqVersion = f.version이 어긋나 반드시 다시 만들어야 하고,
        // CATEGORY_Q_A처럼 카테고리를 넣는 구성이면 카테고리만 바뀌어도 벡터가 낡는다
        if (contentChanged || !textAssembler.assemble(faq).equals(embeddingTextBefore)) {
            embeddingSyncService.upsert(faqId);
        }
        // 트리거가 채우는 updated_at은 UPDATE가 나간 뒤에야 읽힌다. 안 하면 응답에 수정 전 시각이 나간다
        faqRepository.flush();
        log.info("[AdminFaq] 수정 faqId={} contentChanged={} version={}", faqId, contentChanged, faq.getVersion());
        return converter.toDetail(faq, citationCount(faqId));
    }

    // 실제로 지우지 않고 상태만 바꾼다. 임베딩은 남겨도 검색이 f.status = 'ACTIVE'로 거른다
    public void delete(Long faqId, Long adminId) {
        Faq faq = findFaq(faqId);
        faq.changeStatus(Faq.Status.DELETED, adminId);
        log.info("[AdminFaq] 삭제 faqId={} adminId={}", faqId, adminId);
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
