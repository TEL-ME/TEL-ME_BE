package com.telme.faq.service;

import com.telme.faq.converter.AdminFaqConverter;
import com.telme.faq.dto.req.AdminFaqSearchRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.FaqCitationCount;
import com.telme.rag.repository.MessageSourceRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFaqQueryService {

    private final FaqRepository faqRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final AdminFaqConverter converter;

    public AdminFaqListResponse getFaqs(AdminFaqSearchRequest request) {
        Page<Long> faqIds = findFaqIds(request);
        List<Long> ids = faqIds.getContent();
        return converter.toListResponse(faqIds, orderedFaqs(ids), citationCounts(ids));
    }

    public AdminFaqDetailResponse getFaq(long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));
        return converter.toDetail(faq, citationCounts(List.of(faqId)).getOrDefault(faqId, 0L));
    }

    private Page<Long> findFaqIds(AdminFaqSearchRequest request) {
        PageRequest page = PageRequest.of(request.page(), request.size());
        String keyword = request.keywordPattern();
        String category = request.categoryPattern();
        List<Faq.Status> statuses = request.status().toStatuses();

        return switch (request.sort()) {
            case RECENT -> faqRepository.findAdminFaqIds(keyword, category, statuses, page);
            case CITATION_ASC -> faqRepository.findAdminFaqIdsByCitationAsc(keyword, category, statuses, page);
            case CITATION_DESC -> faqRepository.findAdminFaqIdsByCitationDesc(keyword, category, statuses, page);
        };
    }

    // findAllById는 인자 순서를 보장하지 않아 조회한 id 순서대로 다시 맞춘다
    private List<Faq> orderedFaqs(List<Long> faqIds) {
        if (faqIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Faq> byId = faqRepository.findAllById(faqIds).stream()
                .collect(Collectors.toMap(Faq::getFaqId, Function.identity()));
        return faqIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, Long> citationCounts(List<Long> faqIds) {
        if (faqIds.isEmpty()) {
            return Map.of();
        }
        return messageSourceRepository.countByFaqIds(faqIds).stream()
                .collect(Collectors.toMap(FaqCitationCount::faqId, FaqCitationCount::citationCount));
    }
}
