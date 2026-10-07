package com.telme.consult.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 정상 답변 아래 추천 질문을 정책 연결표로 고른다. LLM을 쓰지 않는다.
 *
 * <p>기준 FAQ의 정책에서 이어지는 정책을 앞에서부터 보고, 그 정책의 대표 질문(실제 FAQ 질문)을 최대 2개 돌려준다.
 * 연결표와 규칙 데이터는 scripts/build_suggested_question_data.py가 만든다. 설계·검증: docs/FOLLOWUP_RECOMMENDATION.md 8절
 */
public final class SuggestedQuestionRecommender {
    static final int MAX_QUESTIONS = 2;
    static final String POLICY_LINKS_PATH = "suggested-question/policy-links.json";
    static final String FAQ_RULES_PATH = "suggested-question/faq-rules.json";

    private final Map<String, List<Link>> linksByPolicy;
    private final Map<String, RepresentativeQuestion> representativeByPolicy;
    private final Map<String, String> questionBySlotId;
    private final FaqRules rules;

    SuggestedQuestionRecommender(PolicyLinks policyLinks, FaqRules rules) {
        Objects.requireNonNull(policyLinks, "policyLinks");
        this.rules = Objects.requireNonNull(rules, "rules");
        Map<String, RepresentativeQuestion> representatives = Map.copyOf(policyLinks.representativeQuestions());
        Map<String, String> questions = new HashMap<>();
        representatives.forEach((policy, rep) -> {
            if (rep == null || rep.slotId() == null || rep.question() == null) {
                throw new IllegalStateException(policy + ": 대표 질문에 slotId, question이 필요합니다");
            }
            questions.put(rep.slotId(), rep.question());
        });
        Map<String, List<Link>> links = new HashMap<>();
        policyLinks.policies().forEach((policy, entry) -> {
            for (Link link : entry.links()) {
                link.validate(policy);
                if (!representatives.containsKey(link.to())) {
                    throw new IllegalStateException(policy + " → " + link.to() + ": 대표 질문이 없습니다");
                }
            }
            links.put(policy, List.copyOf(entry.links()));
        });
        this.representativeByPolicy = representatives;
        this.questionBySlotId = Map.copyOf(questions);
        this.linksByPolicy = Map.copyOf(links);
    }

    public static SuggestedQuestionRecommender load(ObjectMapper mapper) {
        return new SuggestedQuestionRecommender(
                read(mapper, POLICY_LINKS_PATH, PolicyLinks.class), read(mapper, FAQ_RULES_PATH, FaqRules.class));
    }

    private static <T> T read(ObjectMapper mapper, String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("추천 질문 데이터를 읽을 수 없습니다: " + path, e);
        }
    }

    /** 연결표 JSON의 대표 질문 문장으로 최대 2개. 검증한 기대 추천과 비교할 때 쓴다. */
    public List<String> recommend(BaseFaq faq) {
        return candidateSlotIds(faq).stream()
                .limit(MAX_QUESTIONS)
                .map(questionBySlotId::get)
                .toList();
    }

    /**
     * 규칙을 통과한 대표 FAQ의 slotId를 연결표 순서대로 모두 돌려준다. 정책 ID가 없거나 연결표에 없는 정책이면 빈 목록이다.
     * 대표 FAQ가 숨겨졌을 때 다음 후보로 채울 수 있게 개수를 자르지 않는다.
     */
    public List<String> candidateSlotIds(BaseFaq faq) {
        Objects.requireNonNull(faq, "faq");
        List<Link> links = faq.policyRef() == null ? null : linksByPolicy.get(faq.policyRef());
        if (links == null) {
            return List.of();
        }
        // 관리자 등록 FAQ처럼 원본 메타데이터가 없으면 아래 세 값이 기본값이 된다(상황 조건 연결은 건너뜀)
        boolean blocked = rules.isEligibilityBlocked(faq.slotId());
        boolean trouble = rules.isTrouble(faq.slotId());
        String trigger = rules.trigger(faq.slotId());

        Set<String> out = new LinkedHashSet<>();
        for (Link link : links) {
            if (link.condition() != null && !link.condition().matches(faq.question(), trigger)) {
                continue;
            }
            // 자격 조건 때문에 할 수 없다고 안내한 FAQ에 같은 업무의 다음 단계를 권하지 않는다
            if (blocked && link.kind() == Kind.NEXT) {
                continue;
            }
            // 문제 상황 질문에는 대안·관련 정보가 상황에 어긋나는 경우가 많았다(검증 약 68%)
            if (trouble && link.kind() != Kind.NEXT) {
                continue;
            }
            out.add(representativeByPolicy.get(link.to()).slotId());
        }
        return List.copyOf(out);
    }

    /** 추천 기준 FAQ. LLM에 넘긴 검색 결과 중 1순위다. slotId는 원본 JSON에 없는 FAQ면 null이다. */
    public record BaseFaq(String slotId, String policyRef, String question) {
    }

    // NEXT 같은 업무의 다음 단계, ALTERNATIVE 대안, INFO 관련 정보
    enum Kind { NEXT, ALTERNATIVE, INFO }

    record Link(String to, Kind kind, Condition condition) {
        void validate(String from) {
            if (to == null || kind == null) {
                throw new IllegalStateException(from + ": 연결에 to, kind가 필요합니다");
            }
            if (condition != null) {
                condition.validate(from);
            }
        }
    }

    record Condition(String questionContains, List<String> triggerIn, List<String> triggerNotIn) {
        void validate(String from) {
            long count = Stream.of(questionContains, triggerIn, triggerNotIn)
                    .filter(Objects::nonNull)
                    .count();
            if (count != 1) {
                throw new IllegalStateException(from + ": 연결 조건은 하나만 지정해야 합니다");
            }
        }

        boolean matches(String question, String trigger) {
            if (questionContains != null) {
                return question != null && question.contains(questionContains);
            }
            // 상황을 모르면 상황별로 나눈 연결은 어느 쪽도 붙이지 않는다
            if (trigger == null) {
                return false;
            }
            return triggerIn != null ? triggerIn.contains(trigger) : !triggerNotIn.contains(trigger);
        }
    }

    record PolicyEntry(String title, List<Link> links) {
        PolicyEntry {
            links = links == null ? List.of() : links;
        }
    }

    record RepresentativeQuestion(String slotId, String question) {
    }

    record PolicyLinks(
            Map<String, RepresentativeQuestion> representativeQuestions, Map<String, PolicyEntry> policies) {
        PolicyLinks {
            representativeQuestions = Objects.requireNonNull(representativeQuestions, "representativeQuestions");
            policies = Objects.requireNonNull(policies, "policies");
        }
    }

    record FaqRules(Set<String> troubleSlotIds, Set<String> eligibilityBlockedSlotIds, Map<String, String> triggers) {
        FaqRules {
            troubleSlotIds = troubleSlotIds == null ? Set.of() : Set.copyOf(troubleSlotIds);
            eligibilityBlockedSlotIds =
                    eligibilityBlockedSlotIds == null ? Set.of() : Set.copyOf(eligibilityBlockedSlotIds);
            triggers = triggers == null ? Map.of() : Map.copyOf(triggers);
        }

        boolean isTrouble(String slotId) {
            return slotId != null && troubleSlotIds.contains(slotId);
        }

        boolean isEligibilityBlocked(String slotId) {
            return slotId != null && eligibilityBlockedSlotIds.contains(slotId);
        }

        String trigger(String slotId) {
            return slotId == null ? null : triggers.get(slotId);
        }
    }
}
