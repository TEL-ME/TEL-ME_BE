package com.telme.faq.dto.req;

import com.telme.faq.entity.Faq;
import java.util.List;

public enum AdminFaqStatusFilter {
    ACTIVE, HIDDEN, DELETED, ALL;

    private static final List<Faq.Status> EVERY_STATUS = List.of(Faq.Status.values());

    public List<Faq.Status> toStatuses() {
        return this == ALL ? EVERY_STATUS : List.of(Faq.Status.valueOf(name()));
    }
}
