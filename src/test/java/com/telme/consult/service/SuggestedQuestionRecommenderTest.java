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
import com.telme.consult.service.SuggestedQuestionRecommender.StoreQuestions;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                                "NONE", entry()),
                        new StoreQuestions("매장", "매장\\s*없이", Map.of("USIM", "유심 매장 버튼"), "가까운 매장 버튼")),
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

    // 답변이 매장을 언급하면 카테고리별 매장 찾기 문장, 카테고리에 없으면 기본 문장
    @Test
    void storeQuestionFollowsAnswerAndCategory() {
        assertThat(sample.storeQuestion("USIM", "매장에서 바로 재발급됩니다")).isEqualTo("유심 매장 버튼");
        assertThat(sample.storeQuestion("DEVICE", "가까운 매장에 방문해 주세요")).isEqualTo("가까운 매장 버튼");
        assertThat(sample.storeQuestion("USIM", "고객센터로 신청하세요")).isNull();
        assertThat(sample.storeQuestion("USIM", null)).isNull();
        assertThat(sample.storeQuestion("USIM", "매장 없이 신청하세요")).isNull();
    }

    // 기대값 파일은 같은 규칙으로 만들어 규칙 실수를 잡지 못하므로, 실제 FAQ 답변을 직접 적어 확인한다
    @Test
    void realAnswersThatSayNoStoreVisitGetNoStoreQuestion() {
        var recommender = SuggestedQuestionRecommender.load(MAPPER);

        // TERMINATE-0057, TERMINATE-0077, DEVICE-0087
        assertThat(recommender.storeQuestion("TERMINATE",
                "네, 매장 방문 없이 고객센터 전화나 홈페이지로도 신청하실 수 있습니다.")).isNull();
        assertThat(recommender.storeQuestion("TERMINATE",
                "홈페이지나 고객센터로 신청하시면 즉시 해지됩니다. 매장에 가지 않아도 됩니다.")).isNull();
        assertThat(recommender.storeQuestion("DEVICE",
                "파손 수리는 매장이 아니라 제조사 서비스센터에서 진행합니다.")).isNull();

        // 관리자 FAQ에 나올 수 있는 다른 표현
        for (String answer : List.of(
                "매장을 방문하지 않아도 됩니다.",
                "매장에 갈 필요가 없습니다.",
                "매장은 방문할 필요가 없습니다.",
                "매장 방문은 필요하지 않습니다.",
                "매장에 안 가도 됩니다.",
                "매장 방문이 필요 없어요.",
                "매장을 방문하지 않으셔도 됩니다.",
                "매장에 가지 않고도 신청할 수 있어요.",
                "매장에 안 가셔도 돼요.",
                "매장 방문이 필수는 아닙니다.",
                "매장 방문을 하지 않아도 됩니다.",
                "매장에 방문할 필요는 없습니다.",
                "매장에 꼭 갈 필요는 없습니다.")) {
            assertThat(recommender.storeQuestion("TERMINATE", answer)).as(answer).isNull();
        }
        // 매장 방문이 필요하다는 부정("~하지 않으면 안 된다")과 매장과 무관한 부정은 매장 언급을 지우지 않는다
        for (String answer : List.of(
                "매장에 가지 않으면 처리할 수 없습니다.",
                "매장을 방문하지 않으면 개통할 수 없습니다.",
                "매장에 안 가면 발급이 불가능합니다.",
                "매장 방문이 필수입니다.",
                "매장에 꼭 가셔야 합니다.",
                "매장에서 하지 않으면 안 됩니다.",
                "매장에 가시면 바로 받으실 수 있습니다.",
                "매장을 방문하시면 즉시 발급됩니다.",
                "매장에서 확인하지 않으면 교환이 어렵습니다.",
                "매장이 혼잡하지 않으면 30분이면 됩니다.")) {
            assertThat(recommender.storeQuestion("DEVICE", answer)).as(answer).isEqualTo("가까운 매장을 알려주세요.");
        }

        // 매장 방문을 권하거나 선택지로 안내하는 답변에는 붙는다 (USIM-0041, TERMINATE-0036, USIM-0001)
        assertThat(recommender.storeQuestion("USIM", "매장을 방문하시면 즉시 발급받으실 수 있습니다. "
                + "매장 방문이 어려우시면 온라인으로 신청해 택배로 받으실 수 있으며 2~3 영업일이 걸립니다."))
                .isEqualTo("유심 재발급 가능한 매장을 알려주세요.");
        assertThat(recommender.storeQuestion("TERMINATE", "고객센터나 매장에서 신청하시면 됩니다."))
                .isEqualTo("가까운 매장을 알려주세요.");
        assertThat(recommender.storeQuestion("USIM", "매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다."))
                .isEqualTo("유심 재발급 가능한 매장을 알려주세요.");
    }

    @Test
    void noStoreQuestionWithoutStoreData() {
        var recommender = new SuggestedQuestionRecommender(
                new PolicyLinks(Map.of(), Map.of()), new FaqRules(null, null, null));

        assertThat(recommender.storeQuestion("USIM", "매장에서 가능합니다")).isNull();
    }

    @Test
    void rejectsStoreQuestionsWithoutMentionOrDefault() {
        assertThatThrownBy(() -> new StoreQuestions(" ", null, Map.of(), "가까운 매장 버튼"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StoreQuestions("매장", null, Map.of(), null))
                .isInstanceOf(IllegalStateException.class);
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
            // 기대값 파일에는 답변 원문 대신 매장 언급 여부만 있어 언급 여부에 맞는 답변으로 확인한다
            String store = recommender.storeQuestion(e.category(), e.mentionsStore() ? "매장에서 가능합니다" : "답변");
            if (!Objects.equals(store, e.storeQuestion())) {
                mismatches.add(slotId + " 매장 버튼 기대 " + e.storeQuestion() + " 실제 " + store);
            }
        });

        assertThat(expected).hasSize(1150);
        assertThat(mismatches).isEmpty();
    }

    record Expected(
            String policyRef,
            String category,
            String question,
            boolean mentionsStore,
            List<String> suggestions,
            String storeQuestion) {
    }
}
