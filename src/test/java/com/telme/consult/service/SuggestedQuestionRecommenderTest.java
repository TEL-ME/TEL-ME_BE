package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.SuggestedQuestionRecommender.BaseFaq;
import com.telme.consult.service.SuggestedQuestionRecommender.Condition;
import com.telme.consult.service.SuggestedQuestionRecommender.FaqRules;
import com.telme.consult.service.SuggestedQuestionRecommender.Kind;
import com.telme.consult.service.SuggestedQuestionRecommender.Link;
import com.telme.consult.service.SuggestedQuestionRecommender.PolicyEntry;
import com.telme.consult.service.SuggestedQuestionRecommender.PolicyLinks;
import com.telme.consult.service.SuggestedQuestionRecommender.RepresentativeQuestion;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SuggestedQuestionRecommenderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 규칙 확인용 작은 연결표. 실제 연결표는 reproducesValidatedRecommendationsForAllSeedFaqs가 본다
    static SuggestedQuestionRecommender sample() {
        return new SuggestedQuestionRecommender(
                new PolicyLinks(
                        Map.of(
                                "B-01", new RepresentativeQuestion("B-0001", "B 질문"),
                                "C-01", new RepresentativeQuestion("C-0001", "C 질문"),
                                "D-01", new RepresentativeQuestion("D-0001", "D 질문")),
                        Map.of(
                                "A-01", entry(link("B-01", Kind.NEXT), link("C-01", Kind.ALTERNATIVE),
                                        link("D-01", Kind.INFO)),
                                "STORE", entry(
                                        new Link("B-01", Kind.NEXT, new Condition("매장", null, null)),
                                        link("C-01", Kind.INFO)),
                                "DEVICE", entry(
                                        new Link("B-01", Kind.ALTERNATIVE, new Condition(null, null, List.of("분실"))),
                                        new Link("C-01", Kind.ALTERNATIVE, new Condition(null, List.of("분실"), null))),
                                "DUP", entry(link("B-01", Kind.NEXT), link("B-01", Kind.INFO), link("C-01", Kind.INFO)),
                                "NONE", entry())),
                new FaqRules(Set.of("TROUBLE-1"), Set.of("BLOCKED-1"), Map.of("LOST-1", "분실", "WET-1", "침수")));
    }

    private static Link link(String to, Kind kind) {
        return new Link(to, kind, null);
    }

    private static PolicyEntry entry(Link... links) {
        return new PolicyEntry("제목", List.of(links));
    }

    private final SuggestedQuestionRecommender sample = sample();

    private List<String> recommend(String slotId, String policyRef, String question) {
        return sample.recommend(new BaseFaq(slotId, policyRef, question));
    }

    @Test
    void returnsAtMostTwoQuestionsInLinkOrder() {
        assertThat(recommend("S-1", "A-01", "질문")).containsExactly("B 질문", "C 질문");
    }

    // 숨겨진 대표 FAQ를 다음 후보로 채울 수 있게 후보는 개수를 자르지 않는다
    @Test
    void candidateSlotIdsKeepEveryLinkInOrder() {
        assertThat(sample.candidateSlotIds(new BaseFaq("S-1", "A-01", "질문")))
                .containsExactly("B-0001", "C-0001", "D-0001");
    }

    @Test
    void eligibilityBlockedFaqSkipsNextStep() {
        assertThat(recommend("BLOCKED-1", "A-01", "질문")).containsExactly("C 질문", "D 질문");
    }

    @Test
    void troubleFaqKeepsOnlyNextStep() {
        assertThat(recommend("TROUBLE-1", "A-01", "질문")).containsExactly("B 질문");
    }

    @Test
    void storeLinkNeedsStoreWordInBaseQuestion() {
        assertThat(recommend("S-1", "STORE", "매장에서 하나요?")).containsExactly("B 질문", "C 질문");
        assertThat(recommend("S-1", "STORE", "서류가 뭐예요?")).containsExactly("C 질문");
    }

    @Test
    void triggerLinksFollowFaqSituation() {
        assertThat(recommend("LOST-1", "DEVICE", "질문")).containsExactly("C 질문");
        assertThat(recommend("WET-1", "DEVICE", "질문")).containsExactly("B 질문");
    }

    // 관리자 등록 FAQ는 원본 메타데이터가 없다. 상황을 모르면 상황별 연결은 어느 쪽도 붙이지 않는다
    @Test
    void faqWithoutMetadataSkipsTriggerLinksButKeepsOthers() {
        assertThat(recommend(null, "DEVICE", "질문")).isEmpty();
        assertThat(recommend(null, "A-01", "질문")).containsExactly("B 질문", "C 질문");
    }

    @Test
    void returnsEmptyWithoutUsablePolicy() {
        assertThat(recommend("S-1", null, "질문")).isEmpty();
        assertThat(recommend("S-1", "UNKNOWN", "질문")).isEmpty();
        assertThat(recommend("S-1", "NONE", "질문")).isEmpty();
    }

    @Test
    void sameQuestionAppearsOnce() {
        assertThat(recommend("S-1", "DUP", "질문")).containsExactly("B 질문", "C 질문");
    }

    @Test
    void rejectsLinkWithoutRepresentativeQuestion() {
        var links = new PolicyLinks(Map.of(), Map.of("A-01", entry(link("B-01", Kind.NEXT))));

        assertThatThrownBy(() -> new SuggestedQuestionRecommender(links, new FaqRules(null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B-01");
    }

    @Test
    void rejectsMalformedLinks() {
        var reps = Map.of("B-01", new RepresentativeQuestion("B-0001", "B 질문"));
        var rules = new FaqRules(null, null, null);
        var twoConditions = new Link("B-01", Kind.NEXT, new Condition("매장", List.of("분실"), null));
        var noKind = new Link("B-01", null, null);

        assertThatThrownBy(() -> new SuggestedQuestionRecommender(
                new PolicyLinks(reps, Map.of("A-01", entry(twoConditions))), rules))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SuggestedQuestionRecommender(
                new PolicyLinks(reps, Map.of("A-01", entry(noKind))), rules))
                .isInstanceOf(IllegalStateException.class);
    }

    // 독립 검증한 4차 연결표의 FAQ별 추천을 코드가 그대로 재현하는지 본다.
    // 기대값은 scripts/build_suggested_question_data.py가 같은 데이터로 만든다
    @Test
    void reproducesValidatedRecommendationsForAllSeedFaqs() throws Exception {
        var recommender = SuggestedQuestionRecommender.load(MAPPER);
        Map<String, Expected> expected;
        try (InputStream in =
                new ClassPathResource("suggested-question/expected-recommendations.json").getInputStream()) {
            expected = MAPPER.readValue(in, new TypeReference<>() {});
        }

        List<String> mismatches = new ArrayList<>();
        expected.forEach((slotId, e) -> {
            List<String> actual = recommender.recommend(new BaseFaq(slotId, e.policyRef(), e.question()));
            if (!actual.equals(e.suggestions())) {
                mismatches.add(slotId + " 기대 " + e.suggestions() + " 실제 " + actual);
            }
        });

        assertThat(expected).hasSize(1150);
        assertThat(mismatches).isEmpty();
    }

    record Expected(String policyRef, String question, List<String> suggestions) {
    }
}
