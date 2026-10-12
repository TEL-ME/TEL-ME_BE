package com.telme.consult.service;

import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.QueryRoutingAnalysisProvider.CompoundQuestionSuggestions;
import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.intent.service.UnsupportedCompoundQuestionException.Part;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FAQ와 매장 찾기가 섞인 질문에 하나씩 보낼 버튼을 만든다. LLM을 쓰지 않는다.
 *
 * <p>라우터가 나눈 하위 질문 문장은 짧아 그대로 보내면 검색을 통과하지 못하므로 버튼으로 쓰지 않는다.
 * FAQ 쪽은 그 문장과 가까운 실제 FAQ 질문, 매장 쪽은 매장 버튼 문장(지역·업무 포함)으로 만든다.
 * 설계·측정: docs/FOLLOWUP_RECOMMENDATION.md 11.6절
 */
public final class CompoundPartSuggestions implements CompoundQuestionSuggestions {
    private static final String LOCATION_KEY = "location";
    private static final String SERVICE_TYPE_KEY = "serviceType";
    // 지역은 있고 업무가 없을 때. 기본 문장("가까운 매장을 알려주세요.")에 지역을 붙이면 어색하다
    private static final String LOCATION_STORE_QUESTION = "%s 매장을 알려주세요.";
    // 매장 업무 코드 → 매장 버튼 문장을 고를 FAQ 카테고리(10절 표)
    private static final Map<String, String> CATEGORY_BY_SERVICE_TYPE = Map.of(
            "USIM_REISSUE", "USIM",
            "NEW_LINE", "SUBSCRIBE",
            "PORT_IN", "PORTING",
            "NAME_CHANGE", "NAME_CHANGE");

    private final FaqCandidateNoAnswerSuggestions faqCandidates;
    private final SuggestedQuestionRecommender storeQuestions;
    private final RuleBasedRoutingFallback serviceTypes;

    /** faqCandidates, storeQuestions는 각 버튼을 끄면 null이다. */
    public CompoundPartSuggestions(
            FaqCandidateNoAnswerSuggestions faqCandidates,
            SuggestedQuestionRecommender storeQuestions,
            RuleBasedRoutingFallback serviceTypes) {
        this.faqCandidates = faqCandidates;
        this.storeQuestions = storeQuestions;
        this.serviceTypes = Objects.requireNonNull(serviceTypes);
    }

    @Override
    public List<String> suggest(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        // 질문 안에서의 순서를 지키기 위해 하위 질문 순서대로 FAQ·매장 버튼을 하나씩만 만든다
        boolean faqDone = faqCandidates == null;
        boolean storeDone = storeQuestions == null;
        for (Part part : parts) {
            if (!faqDone && part.intent() == ConsultRequest.Intent.FAQ) {
                faqDone = true;
                faqCandidates.firstCandidate(part.queryText(), true).ifPresent(out::add);
            } else if (!storeDone && part.intent() == ConsultRequest.Intent.STORE) {
                storeDone = true;
                String store = storeQuestion(part);
                if (store != null) {
                    out.add(store);
                }
            }
        }
        return out.stream().distinct().toList();
    }

    // 업무는 라우터 조건, 없으면 하위 질문 문장의 업무 규칙으로 정한다. 지역을 말했으면 다시 묻지 않게 앞에 붙인다
    private String storeQuestion(Part part) {
        String serviceType = part.conditions().get(SERVICE_TYPE_KEY);
        if (serviceType == null) {
            serviceType = serviceTypes.serviceTypeOf(part.queryText());
        }
        String category = serviceType == null ? null : CATEGORY_BY_SERVICE_TYPE.get(serviceType);
        String question = storeQuestions.storeQuestionFor(category);
        String location = part.conditions().get(LOCATION_KEY);
        if (question == null || location == null || location.isBlank()) {
            return question;
        }
        return category == null
                ? LOCATION_STORE_QUESTION.formatted(location.strip()) : location.strip() + " " + question;
    }
}
