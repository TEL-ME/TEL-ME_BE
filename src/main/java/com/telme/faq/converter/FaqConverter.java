package com.telme.faq.converter;

import com.telme.faq.dto.res.FaqListResponse;
import com.telme.faq.dto.res.FaqResponse;
import com.telme.faq.entity.Faq;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class FaqConverter {

    public FaqListResponse toListResponse(Page<Long> faqIds, List<Faq> faqs) {
        return new FaqListResponse(
                faqs.stream().map(this::toResponse).toList(),
                faqIds.getNumber(), faqIds.getSize(), faqIds.getTotalElements(), faqIds.getTotalPages());
    }
    
    public FaqResponse toResponse(Faq faq) {
        return new FaqResponse(faq.getFaqId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer());
    }
}
