package com.telme.faq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqBatchLoadProperties;
import com.telme.faq.dto.req.FaqLoadItem;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// scripts/data/faq_*.json을 읽어 faqs + faq_embeddings에 적재
// 이미 있는 content_hash는 건너뛰고, batchSize건마다 커밋
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqBatchLoader {

    // findByContentHashIn의 IN 절 크기
    private static final int HASH_LOOKUP_CHUNK = 500;

    private final ObjectMapper objectMapper;
    private final FaqRepository faqRepository;
    private final FaqEmbeddingRepository faqEmbeddingRepository;
    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingTextAssembler textAssembler;
    private final EmbeddingProperties embeddingProperties;
    private final FaqBatchLoadProperties properties;
    private final TransactionTemplate transactionTemplate;

    public record LoadResult(int total, int duplicateInFile, int alreadyInDb, int inserted) {
    }

    public LoadResult load(Path jsonPath) {
        return load(jsonPath, properties.batchSize());
    }

    public LoadResult load(Path jsonPath, int batchSize) {
        if (batchSize < 1 || batchSize > FaqBatchLoadProperties.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize는 1~" + FaqBatchLoadProperties.MAX_BATCH_SIZE + ": " + batchSize);
        }
        List<FaqLoadItem> items = read(jsonPath);
        validate(items, jsonPath);

        // 파일 안 중복은 첫 건만 남긴다
        Map<String, FaqLoadItem> byHash = new LinkedHashMap<>();
        for (FaqLoadItem item : items) {
            byHash.putIfAbsent(FaqContentHash.of(item.question(), item.answer()), item);
        }
        int duplicateInFile = items.size() - byHash.size();

        Set<String> existing = findExistingHashes(byHash.keySet());
        List<Map.Entry<String, FaqLoadItem>> pending = byHash.entrySet().stream()
                .filter(e -> !existing.contains(e.getKey()))
                .toList();

        log.info("[FaqBatchLoader] {} - 파일 {}건, 파일 내 중복 {}건, DB에 이미 있음 {}건, 적재 대상 {}건",
                jsonPath, items.size(), duplicateInFile, existing.size(), pending.size());

        int inserted = 0;
        for (int start = 0; start < pending.size(); start += batchSize) {
            List<Map.Entry<String, FaqLoadItem>> chunk = pending.subList(start, Math.min(start + batchSize, pending.size()));
            List<String> texts = chunk.stream()
                    .map(e -> textAssembler.assemble(e.getValue().category(), e.getValue().question(), e.getValue().answer()))
                    .toList();
            // 임베딩은 트랜잭션 밖에서, DB 쓰기만 안에서
            List<float[]> vectors = embeddingClient.embedBatch(texts);

            transactionTemplate.executeWithoutResult(status -> saveChunk(chunk, vectors));
            inserted += chunk.size();
            log.info("[FaqBatchLoader] {}/{}건 커밋", inserted, pending.size());
        }

        return new LoadResult(items.size(), duplicateInFile, existing.size(), inserted);
    }

    private List<FaqLoadItem> read(Path jsonPath) {
        try {
            return objectMapper.readValue(Files.readAllBytes(jsonPath), new TypeReference<List<FaqLoadItem>>() {
            });
        } catch (IOException e) {
            log.warn("[FaqBatchLoader] 파일을 읽을 수 없습니다: {}", jsonPath, e);
            throw new GeneralException(FaqErrorCode.LOAD_FILE_INVALID);
        }
    }

    // DB를 건드리기 전에 파일 전체를 먼저 검사한다
    private void validate(List<FaqLoadItem> items, Path jsonPath) {
        if (items == null || items.isEmpty()) {
            log.warn("[FaqBatchLoader] 비어 있는 파일: {}", jsonPath);
            throw new GeneralException(FaqErrorCode.LOAD_FILE_INVALID);
        }
        for (int i = 0; i < items.size(); i++) {
            FaqLoadItem item = items.get(i);
            if (item == null || isBlank(item.category()) || isBlank(item.question()) || isBlank(item.answer())) {
                log.warn("[FaqBatchLoader] {}번 항목의 category/question/answer가 비어 있습니다: {}", i + 1, item);
                throw new GeneralException(FaqErrorCode.LOAD_FILE_INVALID);
            }
        }
    }

    private Set<String> findExistingHashes(Set<String> hashes) {
        List<String> all = new ArrayList<>(hashes);
        Set<String> existing = new HashSet<>();
        for (int start = 0; start < all.size(); start += HASH_LOOKUP_CHUNK) {
            List<String> part = all.subList(start, Math.min(start + HASH_LOOKUP_CHUNK, all.size()));
            faqRepository.findByContentHashIn(part).forEach(faq -> existing.add(faq.getContentHash()));
        }
        return existing;
    }

    private void saveChunk(List<Map.Entry<String, FaqLoadItem>> chunk, List<float[]> vectors) {
        for (int i = 0; i < chunk.size(); i++) {
            FaqLoadItem item = chunk.get(i).getValue();
            Faq faq = faqRepository.save(Faq.builder()
                    .category(item.category())
                    .question(item.question())
                    .answer(item.answer())
                    .policyRef(item.policyRef())
                    .contentHash(chunk.get(i).getKey())
                    .build());
            faqEmbeddingRepository.save(FaqEmbedding.builder()
                    .faqId(faq.getFaqId())
                    .faq(faq)
                    .embedding(vectors.get(i))
                    .modelName(embeddingProperties.model())
                    .faqVersion(faq.getVersion())
                    .syncStatus(FaqEmbedding.SyncStatus.SYNCED)
                    .build());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
