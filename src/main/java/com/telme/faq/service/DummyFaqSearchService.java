package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DummyFaqSearchService implements FaqSearchService {

	// V2 시드 데이터 FAQ 사용함
    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        return List.of(
                new FaqSearchResponse(1L, "요금제는 언제 변경할 수 있나요?",
                        "요금제는 매월 1회, 영업일 기준 변경 신청일로부터 다음날 자정에 적용됩니다.", 0.91),
                new FaqSearchResponse(2L, "유심 재발급은 어떻게 하나요?",
                        "가까운 매장을 방문해 신분증을 지참하시면 즉시 재발급이 가능합니다.", 0.85)
        );
    }
}
