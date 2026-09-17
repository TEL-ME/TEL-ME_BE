package com.telme.faq.service;

public interface FaqEmbeddingSyncService {

    // 지금은 void — 실패해도 예외를 던지고 FAQ 수정까지 롤백하는 방식으로 충분하면 이대로 진행
    // "FAQ 수정은 확정, 임베딩은 나중에 재시도"처럼 배치로 실패 상태를 남겨야 하는 요건이 생기면
    // 반환 타입(성공/실패 상태)을 다시 검토해야 해야함
    void upsert(Long faqId);

    void delete(Long faqId);
}
