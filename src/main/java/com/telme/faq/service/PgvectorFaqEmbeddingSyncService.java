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
        String model = embeddingProperties.model();

        // 있으면 갱신, 없으면 INSERT
        faqEmbeddingRepository.findById(faqId).ifPresentOrElse(
                existing -> existing.refresh(vector, model, faq.getVersion()),
                () -> faqEmbeddingRepository.save(FaqEmbedding.builder()
                        .faqId(faq.getFaqId())
                        .faq(faq)
                        .embedding(vector)
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
