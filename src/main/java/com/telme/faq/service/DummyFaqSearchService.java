package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(FaqSearchService.class)
public class DummyFaqSearchService implements FaqSearchService {

    // 더미 응답용 고정 날짜
    private static final LocalDate SAMPLE_UPDATED_AT = LocalDate.of(2026, 9, 17);

    // V2 시드 데이터 FAQ 사용함
    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        FaqSearchResponse billing = new FaqSearchResponse(1L, "BILLING", "요금제는 언제 변경할 수 있나요?",
                "요금제는 매월 1회, 영업일 기준 변경 신청일로부터 다음날 자정에 적용됩니다.",
                0.91, 1, SAMPLE_UPDATED_AT, null);
        FaqSearchResponse usim = new FaqSearchResponse(2L, "USIM", "유심 재발급은 어떻게 하나요?",
                "가까운 매장을 방문해 신분증을 지참하시면 즉시 재발급이 가능합니다.",
                0.85, 1, SAMPLE_UPDATED_AT, null);

        // FAQ별로 관련 키워드가 실제 포함됐는지 각각 판단 — 관련 없으면 빈 목록 반환
        List<FaqSearchResponse> matched = new ArrayList<>();
        if (request.query().contains("요금제")) {
            matched.add(billing);
        }
        if (request.query().contains("유심")) {
            matched.add(usim);
        }

        List<FaqSearchResponse> limited = matched.stream().limit(request.topK()).toList();

        // topK로 자른 뒤의 최종 순서를 searchRank로 반영
        List<FaqSearchResponse> ranked = new ArrayList<>();
        for (int i = 0; i < limited.size(); i++) {
            FaqSearchResponse r = limited.get(i);
            ranked.add(new FaqSearchResponse(r.faqId(), r.category(), r.question(), r.answer(),
                    r.score(), r.version(), r.updatedAt(), i + 1));
        }
        return ranked;
    }
}
