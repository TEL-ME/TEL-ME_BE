package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FAQ 1건의 임베딩을 현재 faqs 행 내용으로 다시 만든다
// faq_version에 faqs.version을 맞춰 넣어야 검색(findNearest)이 그 FAQ를 다시 잡는다
@Slf4j
@Service
@RequiredArgsConstructor
public class PgvectorFaqEmbeddingSyncService implements FaqEmbeddingSyncService {

    private final FaqRepository faqRepository;
    private final FaqEmbeddingRepository faqEmbeddingRepository;
    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingTextAssembler textAssembler;
    private final EmbeddingProperties embeddingProperties;

    @Override
    @Transactional
    public void upsert(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));

        float[] vector = embeddingClient.embed(textAssembler.assemble(faq));
        // 이중 벡터: 질문만 구성으로 한 번 더. 배치 경로와 달리 여기는 embed가 트랜잭션 안이라
        // 호출이 둘이 되면 트랜잭션 유지 시간도 두 배가 된다(관리자 단건 수정이라 빈도는 낮다)
        float[] questionVector = textAssembler.variant() == FaqEmbeddingTextVariant.QUESTION_ONLY
                ? vector
                : embeddingClient.embed(textAssembler.assembleQuestion(faq));
        String model = embeddingProperties.model();

        // 있으면 갱신, 없으면 INSERT
        faqEmbeddingRepository.findById(faqId).ifPresentOrElse(
                existing -> existing.refresh(vector, questionVector, model, faq.getVersion()),
                () -> faqEmbeddingRepository.save(FaqEmbedding.builder()
                        .faqId(faq.getFaqId())
                        .faq(faq)
                        .embedding(vector)
                        .embeddingQuestion(questionVector)
                        .modelName(model)
                        .faqVersion(faq.getVersion())
                        .syncStatus(FaqEmbedding.SyncStatus.SYNCED)
                        .build()));
        log.info("[FaqEmbeddingSync] upsert faqId={} version={}", faqId, faq.getVersion());
    }

    @Override
    @Transactional
    public void delete(Long faqId) {
        // 삭제는 멱등
        if (!faqEmbeddingRepository.existsById(faqId)) {
            return;
        }
        faqEmbeddingRepository.deleteById(faqId);
        log.info("[FaqEmbeddingSync] delete faqId={}", faqId);
    }
}
