package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqReembedProperties;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// faqs 전체를 현재 faq.embedding-text.variant로 다시 임베딩해 faq_embeddings에 덮어쓴다
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqReembedder {

    private final FaqRepository faqRepository;
    private final FaqEmbeddingRepository faqEmbeddingRepository;
    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingTextAssembler textAssembler;
    private final EmbeddingProperties embeddingProperties;
    private final FaqReembedProperties properties;
    private final TransactionTemplate transactionTemplate;

    public record ReembedResult(int total, int updated, int created, int answerTruncated) {
    }

    public ReembedResult reembedAll() {
        return reembedAll(properties.batchSize());
    }

    public ReembedResult reembedAll(int batchSize) {
        if (batchSize < 1 || batchSize > FaqReembedProperties.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize는 1~" + FaqReembedProperties.MAX_BATCH_SIZE + ": " + batchSize);
        }

        FaqEmbeddingTextVariant variant = textAssembler.variant();
        long total = faqRepository.count();
        log.info("[FaqReembedder] 시작 - {}건, variant={}, batchSize={}", total, variant, batchSize);

        int[] counts = new int[3]; // updated, created, answerTruncated
        int page = 0;
        int processed = 0;
        List<Faq> chunk;
        while (!(chunk = readPage(page++, batchSize)).isEmpty()) {
            List<String> texts = chunk.stream().map(textAssembler::assemble).toList();
            // 임베딩은 트랜잭션 밖에서, DB 쓰기만 안에서
            List<float[]> vectors = embeddingClient.embedBatch(texts);

            List<Faq> current = chunk;
            transactionTemplate.executeWithoutResult(status -> saveChunk(current, vectors, counts));

            counts[2] += (int) chunk.stream().filter(faq -> isAnswerTruncated(variant, faq)).count();
            processed += chunk.size();
            log.info("[FaqReembedder] {}/{}건 커밋", processed, total);
        }

        return new ReembedResult(processed, counts[0], counts[1], counts[2]);
    }

    private List<Faq> readPage(int page, int batchSize) {
        return faqRepository.findAll(PageRequest.of(page, batchSize, Sort.by("faqId"))).getContent();
    }

    private void saveChunk(List<Faq> chunk, List<float[]> vectors, int[] counts) {
        for (int i = 0; i < chunk.size(); i++) {
            Faq faq = chunk.get(i);
            float[] vector = vectors.get(i);
            FaqEmbedding existing = faqEmbeddingRepository.findById(faq.getFaqId()).orElse(null);
            if (existing != null) {
                existing.refresh(vector, embeddingProperties.model(), faq.getVersion());
                counts[0]++;
            } else {
                faqEmbeddingRepository.save(FaqEmbedding.builder()
                        .faqId(faq.getFaqId())
                        .faq(faqRepository.getReferenceById(faq.getFaqId()))
                        .embedding(vector)
                        .modelName(embeddingProperties.model())
                        .faqVersion(faq.getVersion())
                        .syncStatus(FaqEmbedding.SyncStatus.SYNCED)
                        .build());
                counts[1]++;
            }
        }
    }

    // Q_A_HEAD200이 실제로 답변을 잘랐는지
    private boolean isAnswerTruncated(FaqEmbeddingTextVariant variant, Faq faq) {
        return variant == FaqEmbeddingTextVariant.Q_A_HEAD200
                && faq.getAnswer() != null
                && faq.getAnswer().codePointCount(0, faq.getAnswer().length()) > FaqEmbeddingTextVariant.ANSWER_HEAD_LIMIT;
    }
}
