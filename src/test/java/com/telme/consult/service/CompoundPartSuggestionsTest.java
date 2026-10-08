package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.SuggestedQuestionRecommender.FaqRules;
import com.telme.consult.service.SuggestedQuestionRecommender.PolicyLinks;
import com.telme.consult.service.SuggestedQuestionRecommender.StoreQuestions;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.intent.service.UnsupportedCompoundQuestionException.Part;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class CompoundPartSuggestionsTest {
    private static final String NAME_CHANGE_DOCS = "명의변경 시 필요한 서류를 정리해서 알려주세요.";

    private final FaqSearchService searches = mock(FaqSearchService.class);
    private final SuggestedQuestionRecommender data = new SuggestedQuestionRecommender(
            new PolicyLinks(Map.of(), Map.of(), new StoreQuestions("매장", null,
                    Map.of("NAME_CHANGE", "명의변경 가능한 매장을 알려주세요.",
                            "PORTING", "번호이동 가능한 매장을 알려주세요."),
                    "가까운 매장을 알려주세요.")),
            new FaqRules(Set.of("NAME_CHANGE-0065"), null, null, Set.of()));
    private final CompoundPartSuggestions both = new CompoundPartSuggestions(
            new FaqCandidateNoAnswerSuggestions(searches, data), data, new RuleBasedRoutingFallback());

    private static Part faq(String text) {
        return new Part(ConsultRequest.Intent.FAQ, text, Map.of());
    }

    private static Part store(String text, Map<String, String> conditions) {
        return new Part(ConsultRequest.Intent.STORE, text, conditions);
    }

    private static FaqSearchResponse candidate(String slotId, String question, double score) {
        return new FaqSearchResponse(1L, slotId, "NAME_CHANGE", question, "답변", score, 1, null, 1, "Q_A");
    }

    // 나눈 문장은 짧아 검색을 못 넘으므로 그대로 쓰지 않고, 가까운 실제 FAQ 질문과 매장 버튼 문장으로 바꾼다
    @Test
    void faqPartBecomesFaqQuestionAndStorePartBecomesStoreButtonWithLocation() {
        when(searches.searchCandidates(any())).thenReturn(List.of(candidate("NAME_CHANGE-0007", NAME_CHANGE_DOCS, 0.70)));

        var actual = both.suggest(List.of(faq("명의변경 필요 서류"),
                store("강남역 근처 명의변경 가능 매장 위치", Map.of("location", "강남역"))));

        assertThat(actual).containsExactly(NAME_CHANGE_DOCS, "강남역 명의변경 가능한 매장을 알려주세요.");
        verify(searches).searchCandidates(new FaqSearchRequest("명의변경 필요 서류", 10, FaqSearchVector.QA));
    }

    // 라우터가 업무를 주면 그대로 쓰고, 업무 표현이 없으면 기본 매장 문장이다
    @Test
    void storeButtonUsesRouterServiceTypeOrDefault() {
        var storeOnly = new CompoundPartSuggestions(null, data, new RuleBasedRoutingFallback());

        assertThat(storeOnly.suggest(List.of(store("근처 매장", Map.of("serviceType", "PORT_IN")))))
                .containsExactly("번호이동 가능한 매장을 알려주세요.");
        assertThat(storeOnly.suggest(List.of(store("근처 로밍 요금제 관련 매장", Map.of()))))
                .containsExactly("가까운 매장을 알려주세요.");
        assertThat(storeOnly.suggest(List.of(store("신촌역 근처 매장", Map.of("location", "신촌역")))))
                .containsExactly("신촌역 매장을 알려주세요.");
    }

    // 사용자 상황을 모르므로 문제 상황(TROUBLE) FAQ는 건너뛰고 다음 후보를 쓴다
    @Test
    void skipsTroubleFaq() {
        when(searches.searchCandidates(any())).thenReturn(List.of(
                candidate("NAME_CHANGE-0065", "배우자 명의로 바꿨는데 처리가 30분 넘게 걸리고 있습니다", 0.79),
                candidate("NAME_CHANGE-0086", "명의변경 빨리 되나요 오래 걸리나요", 0.77)));

        assertThat(both.suggest(List.of(faq("명의변경 처리 시간"))))
                .containsExactly("명의변경 빨리 되나요 오래 걸리나요");
    }

    // 플래그로 끈 버튼은 만들지 않고, 맞는 FAQ 후보가 없으면 매장 버튼만 남는다
    @Test
    void disabledOrMissingButtonsAreSkipped() {
        var faqOnly = new CompoundPartSuggestions(
                new FaqCandidateNoAnswerSuggestions(searches, data), null, new RuleBasedRoutingFallback());
        when(searches.searchCandidates(any())).thenReturn(List.of(candidate("NAME_CHANGE-0007", NAME_CHANGE_DOCS, 0.55)));

        assertThat(faqOnly.suggest(List.of(faq("명의변경 필요 서류"), store("근처 매장", Map.of())))).isEmpty();
        assertThat(both.suggest(List.of(faq("명의변경 필요 서류"), store("근처 매장", Map.of()))))
                .containsExactly("가까운 매장을 알려주세요.");
        assertThat(both.suggest(List.of())).isEmpty();
    }
}
