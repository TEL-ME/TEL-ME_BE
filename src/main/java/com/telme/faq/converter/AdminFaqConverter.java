package com.telme.faq.converter;

import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListItemResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.entity.Faq;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AdminFaqConverter {

    public AdminFaqListResponse toListResponse(Page<Long> faqIds, List<Faq> faqs, Map<Long, Long> citations) {
        // 한 번도 근거로 쓰이지 않은 FAQ는 집계에 안 잡혀 0으로 채운다
        List<AdminFaqListItemResponse> items = faqs.stream()
                .map(faq -> toListItem(faq, citations.getOrDefault(faq.getFaqId(), 0L)))
                .toList();
        return new AdminFaqListResponse(
                items, faqIds.getNumber(), faqIds.getSize(), faqIds.getTotalElements(), faqIds.getTotalPages());
    }

    public AdminFaqListItemResponse toListItem(Faq faq, long citationCount) {
        return new AdminFaqListItemResponse(
                faq.getFaqId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getVersion(),
                faq.getStatus().name(),
                citationCount,
                faq.getUpdatedAt());
    }

    public AdminFaqDetailResponse toDetail(Faq faq, long citationCount) {
        return new AdminFaqDetailResponse(
                faq.getFaqId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getPolicyRef(),
                faq.getVersion(),
                faq.getContentHash(),
                faq.getStatus().name(),
                citationCount,
                faq.getCreatedBy(),
                faq.getUpdatedBy(),
                faq.getCreatedAt(),
                faq.getUpdatedAt());
    }
}
