package com.telme.faq.service;

import com.telme.faq.converter.FaqConverter;
import com.telme.faq.dto.req.FaqListRequest;
import com.telme.faq.dto.res.FaqListResponse;
import com.telme.faq.dto.res.FaqResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
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
public class FaqQueryService {

    // 사용자는 공개 중인 FAQ만 본다. 요청으로 상태를 받지 않는다
    private static final List<Faq.Status> PUBLIC = List.of(Faq.Status.ACTIVE);
    
    private final FaqRepository faqRepository;
    private final FaqConverter converter;
    
    // 관리자 목록 쿼리를 상태만 ACTIVE로 고정해 재사용한다.
    // 자주 묻는 순은 요청마다 message_sources를 세므로, 인용이 크게 쌓여 느려지면 집계를 따로 저장한다
    public FaqListResponse getFaqs(FaqListRequest request) {
        PageRequest page = PageRequest.of(request.page(), request.size());
        String keyword = request.keywordPattern();
        String category = request.categoryPattern();
        
        Page<Long> faqIds = switch (request.sort()) {
        case POPULAR -> faqRepository.findAdminFaqIdsByCitationDesc(keyword, category, PUBLIC, page);
        case RECENT -> faqRepository.findAdminFaqIds(keyword, category, PUBLIC, page);
        };
        return converter.toListResponse(faqIds, orderedFaqs(faqIds.getContent()));
    }
    
    // 숨김·삭제한 FAQ는 검색에서 빠지므로 상세도 없는 FAQ와 같게 404로 응답한다
    public FaqResponse getFaq(long faqId) {
        return faqRepository.findByFaqIdAndStatus(faqId, Faq.Status.ACTIVE)
                .map(converter::toResponse)
                .orElseThrow(() -> new GeneralException(FaqErrorCode.FAQ_NOT_FOUND));
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
}
