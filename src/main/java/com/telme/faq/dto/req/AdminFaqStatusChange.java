package com.telme.faq.dto.req;

import com.telme.faq.entity.Faq;

// 삭제는 DELETE가 맡아 여기서는 받지 않는다. 같은 일을 두 경로로 두면 한쪽에만 조건이 붙는다
public enum AdminFaqStatusChange {
    ACTIVE, HIDDEN;

    public Faq.Status toStatus() {
        return Faq.Status.valueOf(name());
    }
}
