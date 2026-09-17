package com.telme.faq.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// TODO: 실제 구현(재임베딩 upsert/delete) 추가 시 이 클래스는 삭제할 것
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
