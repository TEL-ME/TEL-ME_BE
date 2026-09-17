package com.telme.faq.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DummyFaqEmbeddingSyncService implements FaqEmbeddingSyncService {

	
	//호출되었다는 것만 로그로 확인
    @Override
    public void upsert(Long faqId) {
        log.info("[Dummy] upsert faqId={}", faqId);
    }

    @Override
    public void delete(Long faqId) {
        log.info("[Dummy] delete faqId={}", faqId);
    }
}
