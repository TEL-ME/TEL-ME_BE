package com.telme.faq.service;

public interface FaqEmbeddingSyncService {

    void upsert(Long faqId);

    void delete(Long faqId);
}
