package com.telme.rag.service;

import com.telme.rag.exception.AnswerGuardException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnswerGuard {

    // 억·만 단위와 원 단위를 함께 잡는다. "30만 원"과 "300,000원"이 같은 값이어야 한다.
    // 공백은 단위 뒤에만 허용한다. "1 원금"처럼 원으로 시작하는 낱말을 금액으로 읽지 않기 위해서다
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:(\\d[\\d,]*)\\s*억\\s*)?(?:(\\d[\\d,]*)\\s*만\\s*)?(\\d[\\d,]*)?원");

    // 범위는 양쪽 값을 모두 검사한다. "2~3 영업일" 근거가 "5영업일"로 바뀌는 경우도 막아야 한다
    private static final Pattern MEASURE = Pattern.compile(
            "(?:(\\d[\\d,]*(?:\\.\\d+)?)\\s*[~～-]\\s*)?"
                    + "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(영업일|Gbps|Mbps|kbps|Kbps|배|%|일|개월|시간|분|GB|회|년)");

    // 운영 시간처럼 시각 자체가 정책인 경우도 숫자 근거로 검사한다. 9:00과 09:00은 같은 값이다
    private static final Pattern CLOCK_TIME = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

    private static final BigInteger[] UNITS = {
            BigInteger.valueOf(100_000_000L), BigInteger.valueOf(10_000L), BigInteger.ONE
    };

    // 답변 불가 문구 뒤에 붙는 내용은 근거 밖 설명이라 잘라낸다. 앞은 서두일 수도 있어 그대로 둔다
    public String trimAfterNoEvidence(String answer) {
        if (answer == null) {
            return "";
        }
        int found = answer.indexOf(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        if (found < 0) {
            return answer;
        }
        return answer.substring(0, found + AnswerPromptTemplates.NO_EVIDENCE_ANSWER.length()).strip();
    }

    // 프롬프트 3번 규칙이 있어도 지어내 문장째 걷어낸다
    private static final Set<String> CHANNELS = Set.of(
            "샵", "이벤트", "홈페이지", "페이지", "사이트", "앱", "메뉴",
            "고객센터", "콜센터", "매장", "대리점", "지점"
    );

    // 실제 생성 결과에서 근거 없이 추가된 결제·부가 정책이다. 근거에 같은 항목이 있을 때만 답변에 쓸 수 있다
    private static final Set<String> POLICY_ATTRIBUTES = Set.of(
            "부가세", "현금영수증", "택배비", "카드", "현금", "문자", "위약금",
            "할부", "재심사", "추가 서류", "관련 절차"
    );

    private static final Map<ComparisonClaim, Pattern> COMPARISON_PATTERNS = comparisonPatterns();

    // 나머지 문장은 근거대로인 경우가 많아 금액과 달리 답변 전체를 막지 않는다
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|\\R+");

    // 고객이 질문에 적은 값은 사실 근거가 아니다. 고객 입력임을 밝히고 근거에서 확인할 수 없다고 할 때만 허용한다
    private static final Pattern USER_VALUE_REFERENCE = Pattern.compile(
            "^(?:(?:네|맞습니다)[,.!]?\\s*)?(?:고객님(?:이|께서)?\\s*)?"
                    + "(?:말씀하신|문의하신|질문하신|입력하신|적어주신|알려주신)");
    private static final Pattern USER_VALUE_UNSUPPORTED = Pattern.compile(
            "(?:안내(?:된|드린)?\\s*(?:정보|내용)(?:에는|에)?\\s*(?:없(?:습니다|어요)?|포함되지\\s*않(?:습니다|아요)?)|"
                    + "근거(?:에는|에서)?\\s*(?:없(?:습니다|어요)?|확인되지\\s*않(?:습니다|아요)?|찾을\\s*수\\s*없(?:습니다|어요)?)|"
                    + "(?:확인|알)(?:할)?\\s*수\\s*없(?:습니다|어요)?|"
                    + "(?:확인|안내|답변)(?:하기|드리기)?\\s*어렵(?:습니다|어요)?|"
                    + "명시되어\\s*있지\\s*않(?:습니다|아요)?|정보가\\s*없(?:습니다|어요)?)"
                    + "[.!?]?$");
    private static final Pattern USER_VALUE_ASSERTION = Pattern.compile(
            "(?:맞(?:습니다|아요|고|다고)|"
                    + "(?:이용|문의|접속|방문|신청)하(?:시면|세요|십시오)|"
                    + "(?:적용|제공|지원|가능|필요)(?:됩니다|합니다|합니다만|하고)|"
                    + "(?:할|받을|될)\\s*수\\s*있(?:습니다|어요|다)|"
                    + "(?:결제|발급|신청|이용|변경)(?:됩니다|합니다|돼요|해요)|"
                    + "(?:걸립니다|소요됩니다))");
    private static final Pattern EXPLICIT_NO_EVIDENCE = Pattern.compile(
            "(?:(?:제공|안내)된\\s*(?:정보|내용)(?:에는|에)?.*"
                    + "(?:없(?:습니다|어요)?|포함되어\\s*있지\\s*않(?:습니다|아요)?|확인되지\\s*않(?:습니다|아요)?)|"
                    + "근거(?:에는|에서)?.*(?:없(?:습니다|어요)?|확인되지\\s*않(?:습니다|아요)?|찾을\\s*수\\s*없(?:습니다|어요)?)|"
                    + "(?:확인|안내|답변)(?:하기|드리기)?\\s*어렵(?:습니다|어요)?)");
    private static final Pattern NEGATIVE_SCOPE = Pattern.compile(
            "(?:(?:발급|제공|지원|이용|적용|조회)(?:되지|할 수 없)|"
                    + "범위\\s*밖|대상이\\s*아니|불가)");
    private static final Pattern UPPER_BOUND = Pattern.compile(
            "(?:까지|이내|이하|최대|최근|범위\\s*밖|이전)");
    private static final Pattern DEPENDENT_EXPLANATION = Pattern.compile(
            "^(?:이는|이것은|그 이유는|이 때문에|그 결과|이로 인해)");
    private static final Pattern NORMALIZED_MEASURE = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)(영업일|Gbps|Mbps|kbps|Kbps|배|%|일|개월|시간|분|GB|회|년)$");

    private static final Pattern TOPIC_WORD = Pattern.compile("[가-힣A-Za-z]{2,}");
    private static final Pattern TOPIC_PARTICLE = Pattern.compile(
            "(?:에서는|으로는|에게서|까지는|부터는|으로|에서|에게|께서|처럼|보다|마다|이라도|라도|"
                    + "은|는|이|가|을|를|의|에|와|과|도|만)$");
    private static final Set<String> GENERIC_TOPIC_WORDS = Set.of(
            "최근", "최대", "이내", "이하", "이전", "범위", "발급", "제공", "지원", "가능",
            "불가", "서비스", "정보", "기록", "신청", "처리", "사용", "이용", "경우", "고객",
            "내용", "안내", "해당", "완료", "일반적", "기준", "기능", "지원하지", "제공하지",
            "않습니다", "않아요", "미지원");

    private static final Pattern ATTRIBUTE_NEGATIVE = Pattern.compile(
            "(?:불가|불가능|할\\s*수\\s*없|(?:지원|제공)하지\\s*않|"
                    + "(?:결제|발급|포함)(?:가|는|이)?\\s*(?:되지\\s*않|안\\s*되|불가)|"
                    + "포함되어\\s*있지\\s*않|없(?:습니다|어요))");
    private static final Pattern ATTRIBUTE_POSITIVE = Pattern.compile(
            "(?:가능|할\\s*수\\s*있|(?:지원|제공)(?:합니다|됩니다|돼요)|"
                    + "(?:결제|발급|포함)(?:가|는|이)?\\s*(?:됩니다|돼요|가능)|"
                    + "포함되어\\s*있)");

    private static final Set<String> CHARGE_TOPICS = Set.of("수수료", "위약금");
    private static final Set<String> GENERIC_CHARGE_WORDS = Set.of(
            "수수료", "위약금", "발생", "부과", "무료", "면제", "없습니다", "없어요", "있습니다", "있어요");
    private static final Map<FeatureAction, Pattern> FEATURE_ACTION_PATTERNS = featureActionPatterns();
    private static final Pattern CHARGE_ABSENT = Pattern.compile(
            "(?:무료|면제(?:됩니다|합니다|돼요)|(?:수수료|위약금).{0,20}(?:없(?:습니다|어요)|"
                    + "(?:발생|부과)(?:하지|되지)\\s*않))");
    private static final Pattern CHARGE_PRESENT = Pattern.compile(
            "(?:(?:수수료|위약금).{0,20}(?:면제(?:가)?\\s*(?:되지|안\\s*되)\\s*않|"
                    + "(?:발생|부과)(?:합니다|됩니다|돼요)|있(?:습니다|어요)))");
    // "수수료가 부과되지 않는 것은 아닙니다"는 이중 부정이므로 비용이 없다는 뜻이 아니다.
    private static final Pattern CHARGE_DOUBLE_NEGATIVE = Pattern.compile(
            "(?:부과|발생)(?:하지|되지)\\s*않는 것은 아닙니다");

    private static final Map<PolicyClaim, Pattern> POLICY_CLAIM_PATTERNS = policyClaimPatterns();

    /**
     * 생성된 답변에 근거 정책을 순서대로 적용한다.
     * 문장 제거가 먼저 실행되어야 남은 답변의 수치만 검증할 수 있으므로 순서를 이곳에서 관리한다.
     */
    public String applyEvidencePolicy(String answer, String context, String userQuery) {
        String filtered = trimAfterNoEvidence(answer);
        filtered = trimUngroundedChannels(filtered, context, userQuery);
        filtered = trimUngroundedComparisons(filtered, context, userQuery);
        filtered = trimUngroundedPolicyAttributes(filtered, context, userQuery);
        filtered = trimUnsupportedPolicyClaims(filtered, context);
        filtered = trimContradictedChargeClaims(filtered, context);
        verifyAmounts(filtered, context, userQuery);
        verifyMeasures(filtered, context, userQuery);
        return filtered;
    }

    public String trimUngroundedComparisons(String answer, String context, String userQuery) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }

        Set<ComparisonClaim> grounded = comparisonsIn(context);
        Set<ComparisonClaim> asked = comparisonsIn(userQuery);
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<ComparisonClaim> invented = comparisonsIn(sentence);
            invented.removeAll(grounded);
            if (isSafeUserReference(sentence)) {
                invented.removeAll(asked);
            }
            if (invented.isEmpty()) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                continue;
            }
            log.warn("[AnswerGuard] 근거에 없는 비교 표현으로 문장 제거: {} | {}", invented, sentence);
        }
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    public String trimUngroundedChannels(String answer, String context, String userQuery) {
        return trimSentences(answer, context, userQuery, CHANNELS, "근거에 없는 안내 창구");
    }

    public String trimUngroundedPolicyAttributes(String answer, String context, String userQuery) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<String> invented = new LinkedHashSet<>();
            for (String attribute : POLICY_ATTRIBUTES) {
                if (!sentence.contains(attribute)
                        || isPolicyAttributeSupported(attribute, sentence, context)) {
                    continue;
                }
                boolean safeNoEvidence = contains(userQuery, attribute)
                        && EXPLICIT_NO_EVIDENCE.matcher(sentence).find()
                        && !USER_VALUE_ASSERTION.matcher(sentence).find();
                if (!safeNoEvidence) {
                    invented.add(attribute);
                }
            }
            if (invented.isEmpty()) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                continue;
            }
            log.warn("[AnswerGuard] 근거에 없는 정책 속성으로 문장 제거: {} | {}", invented, sentence);
        }
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    private boolean isPolicyAttributeSupported(
            String attribute, String answerSentence, String context) {
        if (context == null) {
            return false;
        }

        ClaimPolarity answerPolarity = attributePolarity(answerSentence, attribute);
        boolean mentioned = false;
        for (String evidenceSentence : SENTENCE.split(context)) {
            if (!evidenceSentence.contains(attribute)) {
                continue;
            }
            mentioned = true;
            if ("카드".equals(attribute)
                    && !hasMatchingPolicyAction(
                    answerSentence, evidenceSentence, Set.of(FeatureAction.PAYMENT))) {
                continue;
            }
            ClaimPolarity evidencePolarity = attributePolarity(evidenceSentence, attribute);
            boolean sameKnownPolarity = answerPolarity != ClaimPolarity.UNKNOWN
                    && evidencePolarity != ClaimPolarity.UNKNOWN
                    && answerPolarity == evidencePolarity;
            if ("카드".equals(attribute) ? sameKnownPolarity
                    : answerPolarity == ClaimPolarity.UNKNOWN
                    || evidencePolarity == ClaimPolarity.UNKNOWN
                    || sameKnownPolarity) {
                return true;
            }
        }
        return mentioned && answerPolarity == ClaimPolarity.UNKNOWN;
    }

    private ClaimPolarity attributePolarity(String sentence, String attribute) {
        if (sentence == null) {
            return ClaimPolarity.UNKNOWN;
        }
        int index = sentence.indexOf(attribute);
        if (index < 0) {
            return ClaimPolarity.UNKNOWN;
        }
        int start = Math.max(0, index - 20);
        int end = Math.min(sentence.length(), index + attribute.length() + 50);
        String scope = sentence.substring(start, end);
        if (ATTRIBUTE_NEGATIVE.matcher(scope).find()) {
            return ClaimPolarity.ABSENT;
        }
        if (ATTRIBUTE_POSITIVE.matcher(scope).find()) {
            return ClaimPolarity.PRESENT;
        }
        return ClaimPolarity.UNKNOWN;
    }

    public String trimUnsupportedPolicyClaims(String answer, String context) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        StringBuilder kept = new StringBuilder();
        boolean previousRemoved = false;
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<PolicyClaim> unsupported = policyClaimsIn(sentence);
            unsupported.removeIf(claim -> isPolicyClaimSupported(claim, sentence, context));
            if (unsupported.isEmpty()) {
                if (previousRemoved && DEPENDENT_EXPLANATION.matcher(sentence.strip()).find()) {
                    log.warn("[AnswerGuard] 제거된 정책 단정에 종속된 설명 문장 제거: {}", sentence);
                    continue;
                }
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                previousRemoved = false;
                continue;
            }
            log.warn("[AnswerGuard] 근거에 없는 정책 단정으로 문장 제거: {} | {}", unsupported, sentence);
            previousRemoved = true;
        }
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    public String trimContradictedChargeClaims(String answer, String context) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            boolean contradicted = false;
            for (String topic : CHARGE_TOPICS) {
                if (!sentence.contains(topic) || context == null) {
                    continue;
                }
                ClaimPolarity answerPolarity = chargePolarity(sentence, topic);
                if (answerPolarity == ClaimPolarity.UNKNOWN) {
                    continue;
                }
                for (String evidenceSentence : SENTENCE.split(context)) {
                    if (!evidenceSentence.contains(topic)
                            || !sharesChargeSubject(sentence, evidenceSentence)) {
                        continue;
                    }
                    ClaimPolarity evidencePolarity = chargePolarity(evidenceSentence, topic);
                    if (evidencePolarity != ClaimPolarity.UNKNOWN
                            && answerPolarity != evidencePolarity) {
                        contradicted = true;
                        log.warn("[AnswerGuard] 근거와 반대인 비용 정책으로 문장 제거: {} | {}", topic, sentence);
                        break;
                    }
                }
                if (contradicted) {
                    break;
                }
            }
            if (!contradicted) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
            }
        }
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    private String trimSentences(
            String answer, String context, String userQuery, Set<String> words, String reason) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<String> invented = ungrounded(sentence, context, userQuery, words);
            if (invented.isEmpty()) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                continue;
            }
            log.warn("[AnswerGuard] {}로 문장 제거: {} | {}", reason, invented, sentence);
        }
        // 지어낸 문장만 있던 답변이라 근거 없음으로 돌린다
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    private Set<String> ungrounded(
            String sentence, String context, String userQuery, Set<String> words) {
        Set<String> invented = new LinkedHashSet<>();
        for (String channel : words) {
            if (!sentence.contains(channel) || contains(context, channel)) {
                continue;
            }
            boolean safeUserReference = contains(userQuery, channel)
                    && isSafeUserReference(sentence);
            if (!safeUserReference) {
                invented.add(channel);
            }
        }
        return invented;
    }

    // 근거의 금액을 계산해 없던 금액을 만들어내는 경우가 있음
    public void verifyAmounts(String answer, String context, String userQuery) {
        Set<BigInteger> invented = ungroundedValues(
                answer, context, userQuery, this::amountsIn,
                (value, sentence) -> isSafeUserReference(sentence));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 금액 발견: {}", invented);
            throw new AnswerGuardException("근거에 없는 금액: " + invented);
        }
    }

    public void verifyMeasures(String answer, String context, String userQuery) {
        Set<String> invented = ungroundedValues(
                answer, context, userQuery, this::measuresIn,
                (value, sentence) -> isSafeUserReference(sentence)
                        || isOutsideSupportedUpperBound(value, sentence, context));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 수치 발견: {}", invented);
            throw new AnswerGuardException("근거에 없는 수치: " + invented);
        }
    }

    private <T> Set<T> ungroundedValues(
            String answer,
            String context,
            String userQuery,
            Function<String, Set<T>> extractor,
            BiPredicate<T, String> safeUserValue) {
        Set<T> grounded = extractor.apply(context);
        Set<T> userValues = extractor.apply(userQuery);
        Set<T> invented = new LinkedHashSet<>();

        if (answer == null) {
            return invented;
        }
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<T> sentenceValues = extractor.apply(sentence);
            sentenceValues.removeAll(grounded);
            if (sentenceValues.isEmpty()) {
                continue;
            }
            sentenceValues.removeIf(value -> userValues.contains(value)
                    && safeUserValue.test(value, sentence));
            invented.addAll(sentenceValues);
        }
        return invented;
    }

    private boolean isOutsideSupportedUpperBound(
            String userMeasure, String sentence, String context) {
        if (!NEGATIVE_SCOPE.matcher(sentence).find() || context == null) {
            return false;
        }

        MeasureValue requested = parseMeasure(userMeasure);
        if (requested == null) {
            return false;
        }

        for (String evidenceSentence : SENTENCE.split(context)) {
            if (!UPPER_BOUND.matcher(evidenceSentence).find()) {
                continue;
            }
            if (!sharesPolicyTopic(sentence, evidenceSentence)) {
                continue;
            }
            for (String evidenceMeasure : measuresIn(evidenceSentence)) {
                MeasureValue boundary = parseMeasure(evidenceMeasure);
                if (boundary != null
                        && requested.unit().equals(boundary.unit())
                        && requested.value().compareTo(boundary.value()) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sharesPolicyTopic(String sentence, String evidenceSentence) {
        Set<String> sentenceTopics = topicWords(sentence);
        sentenceTopics.retainAll(topicWords(evidenceSentence));
        return !sentenceTopics.isEmpty();
    }

    private Set<String> topicWords(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text == null) {
            return words;
        }
        Matcher matcher = TOPIC_WORD.matcher(text);
        while (matcher.find()) {
            String word = TOPIC_PARTICLE.matcher(matcher.group()).replaceFirst("");
            if (word.length() >= 2 && !GENERIC_TOPIC_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private boolean sharesChargeSubject(String answerSentence, String evidenceSentence) {
        Set<String> answerTopics = topicWords(answerSentence);
        Set<String> evidenceTopics = topicWords(evidenceSentence);
        answerTopics.removeAll(GENERIC_CHARGE_WORDS);
        evidenceTopics.removeAll(GENERIC_CHARGE_WORDS);
        answerTopics.retainAll(evidenceTopics);
        return !answerTopics.isEmpty();
    }

    private Set<PolicyClaim> policyClaimsIn(String text) {
        Set<PolicyClaim> claims = new LinkedHashSet<>();
        if (text == null) {
            return claims;
        }
        POLICY_CLAIM_PATTERNS.forEach((claim, pattern) -> {
            if (pattern.matcher(text).find()) {
                claims.add(claim);
            }
        });
        return claims;
    }

    private boolean isPolicyClaimSupported(PolicyClaim claim, String sentence, String context) {
        if (context == null) {
            return false;
        }
        Pattern pattern = POLICY_CLAIM_PATTERNS.get(claim);
        for (String evidenceSentence : SENTENCE.split(context)) {
            if (pattern.matcher(evidenceSentence).find()
                    && sharesPolicyTopic(sentence, evidenceSentence)
                    && (claim != PolicyClaim.FEATURE_UNAVAILABLE
                    || hasMatchingPolicyAction(sentence, evidenceSentence, Set.of()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingPolicyAction(
            String answerSentence, String evidenceSentence, Set<FeatureAction> requiredActions) {
        Set<FeatureAction> answerActions = featureActions(answerSentence);
        Set<FeatureAction> evidenceActions = featureActions(evidenceSentence);
        if (!requiredActions.isEmpty()) {
            answerActions.retainAll(requiredActions);
            evidenceActions.retainAll(requiredActions);
        }
        if (answerActions.isEmpty() || evidenceActions.isEmpty()) {
            return false;
        }
        answerActions.retainAll(evidenceActions);
        return !answerActions.isEmpty();
    }

    private Set<FeatureAction> featureActions(String text) {
        Set<FeatureAction> actions = new LinkedHashSet<>();
        if (text == null) {
            return actions;
        }
        FEATURE_ACTION_PATTERNS.forEach((action, pattern) -> {
            if (pattern.matcher(text).find()) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static Map<FeatureAction, Pattern> featureActionPatterns() {
        Map<FeatureAction, Pattern> patterns = new EnumMap<>(FeatureAction.class);
        patterns.put(FeatureAction.CHANGE, Pattern.compile("(?:변경|바꾸)"));
        patterns.put(FeatureAction.TERMINATE, Pattern.compile("(?:해지|취소)"));
        patterns.put(FeatureAction.ISSUE, Pattern.compile("(?:재?발급)"));
        patterns.put(FeatureAction.REGISTER, Pattern.compile("(?:신청|가입|등록|접수)"));
        patterns.put(FeatureAction.PAYMENT, Pattern.compile("(?:결제|납부)"));
        patterns.put(FeatureAction.USE, Pattern.compile("(?:이용|사용)"));
        patterns.put(FeatureAction.LOOKUP, Pattern.compile("(?:조회|확인)"));
        return Map.copyOf(patterns);
    }

    private ClaimPolarity chargePolarity(String text, String topic) {
        if (text == null) {
            return ClaimPolarity.UNKNOWN;
        }
        ClaimPolarity found = ClaimPolarity.UNKNOWN;
        for (String sentence : SENTENCE.split(text)) {
            if (!sentence.contains(topic)) {
                continue;
            }
            String claimScope = chargeClaimScope(sentence, topic);
            ClaimPolarity current = ClaimPolarity.UNKNOWN;
            if (CHARGE_DOUBLE_NEGATIVE.matcher(claimScope).find()
                    || CHARGE_DOUBLE_NEGATIVE.matcher(sentence).find()) {
                current = ClaimPolarity.PRESENT;
            } else if (CHARGE_PRESENT.matcher(claimScope).find()) {
                current = ClaimPolarity.PRESENT;
            } else if (CHARGE_ABSENT.matcher(claimScope).find()) {
                current = ClaimPolarity.ABSENT;
            }
            if (current == ClaimPolarity.UNKNOWN) {
                continue;
            }
            if (found != ClaimPolarity.UNKNOWN && found != current) {
                return ClaimPolarity.UNKNOWN;
            }
            found = current;
        }
        return found;
    }

    private String chargeClaimScope(String sentence, String topic) {
        int start = sentence.indexOf(topic);
        int end = sentence.length();
        for (String other : CHARGE_TOPICS) {
            if (other.equals(topic)) {
                continue;
            }
            int otherStart = sentence.indexOf(other, start + topic.length());
            if (otherStart >= 0) {
                end = Math.min(end, otherStart);
            }
        }
        return sentence.substring(start, end);
    }

    private MeasureValue parseMeasure(String measure) {
        Matcher matcher = NORMALIZED_MEASURE.matcher(measure);
        if (!matcher.matches()) {
            return null;
        }
        return new MeasureValue(new BigDecimal(matcher.group(1)), matcher.group(2));
    }

    private boolean isSafeUserReference(String sentence) {
        return USER_VALUE_REFERENCE.matcher(sentence).find()
                && USER_VALUE_UNSUPPORTED.matcher(sentence).find()
                && !USER_VALUE_ASSERTION.matcher(sentence).find();
    }

    private boolean contains(String text, String value) {
        return text != null && text.contains(value);
    }

    private Set<ComparisonClaim> comparisonsIn(String text) {
        Set<ComparisonClaim> claims = new LinkedHashSet<>();
        if (text == null) {
            return claims;
        }
        COMPARISON_PATTERNS.forEach((claim, pattern) -> {
            if (pattern.matcher(text).find()) {
                claims.add(claim);
            }
        });
        return claims;
    }

    private static Map<ComparisonClaim, Pattern> comparisonPatterns() {
        Map<ComparisonClaim, Pattern> patterns = new EnumMap<>(ComparisonClaim.class);
        patterns.put(ComparisonClaim.SUPERLATIVE, Pattern.compile("(?:제일|가장)"));
        patterns.put(ComparisonClaim.CHEAPER, Pattern.compile("(?:저렴|(?<!비)(?:싸|싼|쌉))"));
        patterns.put(ComparisonClaim.EXPENSIVE, Pattern.compile("(?:비싸|비싼|비쌉)"));
        patterns.put(ComparisonClaim.FASTER, Pattern.compile("(?:빠르|빠른|빠릅|빨라|빨리|시간.{0,4}절약)"));
        patterns.put(ComparisonClaim.CONVENIENT, Pattern.compile("(?:편리|간편|편의|편한|편하)"));
        patterns.put(ComparisonClaim.ADVANTAGE, Pattern.compile("(?:이점|장점)"));
        patterns.put(ComparisonClaim.STABLE, Pattern.compile("안정"));
        patterns.put(ComparisonClaim.SIMPLE, Pattern.compile("(?:간단|수월|복잡하지\\s*않)"));
        patterns.put(ComparisonClaim.BETTER, Pattern.compile("(?:나은|낫(?:다|습|아요|습니다)|더\\s*좋)"));
        patterns.put(ComparisonClaim.ADVANTAGEOUS, Pattern.compile("유리"));
        patterns.put(ComparisonClaim.SUPERIOR, Pattern.compile("우수"));
        return Map.copyOf(patterns);
    }

    private static Map<PolicyClaim, Pattern> policyClaimPatterns() {
        Map<PolicyClaim, Pattern> patterns = new EnumMap<>(PolicyClaim.class);
        patterns.put(PolicyClaim.CALENDAR_DAY_BASIS,
                Pattern.compile("(?:달력(?:상|일|상의)?(?:\\s*일수|\\s*날짜(?:를)?)?\\s*기준|"
                        + "(?:날짜|일수)\\s*경과.{0,20}(?:기준|적용))"));
        patterns.put(PolicyClaim.BUSINESS_DAY_BASIS,
                Pattern.compile("영업일(?:\\s*수)?\\s*기준"));
        patterns.put(PolicyClaim.HARD_DEADLINE,
                Pattern.compile("(?:최소한|반드시|늦어도).{0,40}(?:까지|전에)"));
        patterns.put(PolicyClaim.FEATURE_UNAVAILABLE,
                Pattern.compile("(?:기능|서비스|변경|해지|취소|결제|신청|가입|발급|조회|이용|사용)"
                        + ".{0,30}(?:제공|지원)하지\\s*않"));
        patterns.put(PolicyClaim.ACTION_LOCATION_OR_TIMING,
                Pattern.compile("(?:(?:현지에서|귀국\\s*후).{0,30}(?:변경|해지|취소|결제|신청|가입|발급|조회|이용|사용|진행)|"
                        + "(?:변경|해지|취소|결제|신청|가입|발급|조회|이용|사용|진행).{0,30}(?:현지에서|귀국\\s*후))"));
        patterns.put(PolicyClaim.EXCEPTION_QUALIFIER,
                Pattern.compile("(?:특별한\\s*경우가\\s*아니라면|특별한\\s*경우(?:를|에는)?\\s*제외|"
                        + "예외(?:적인)?\\s*경우가\\s*아니라면|예외(?:적인)?\\s*경우(?:를|에는)?\\s*제외)"));
        // 개별 비용이 안내됐다고 모든 비용을 포함한 총액까지 확인된 것은 아니다.
        patterns.put(PolicyClaim.COMPLETE_COST,
                Pattern.compile("(?:총\\s*비용|전체\\s*비용|총액).{0,20}(?:은|이)?\\s*(?:\\d|별도|포함|추가|없|발생|입니다|이에요|예요)"));
        return Map.copyOf(patterns);
    }

    private Set<String> measuresIn(String text) {
        Set<String> measures = new LinkedHashSet<>();
        if (text == null) {
            return measures;
        }
        Matcher matcher = MEASURE.matcher(text);
        while (matcher.find()) {
            String unit = matcher.group(3);
            if (matcher.group(1) != null) {
                measures.add(normalizeMeasureNumber(matcher.group(1)) + unit);
            }
            measures.add(normalizeMeasureNumber(matcher.group(2)) + unit);
        }
        Matcher clockMatcher = CLOCK_TIME.matcher(text);
        while (clockMatcher.find()) {
            measures.add("%02d:%02d".formatted(
                    Integer.parseInt(clockMatcher.group(1)),
                    Integer.parseInt(clockMatcher.group(2))));
        }
        return measures;
    }

    private String normalizeMeasureNumber(String number) {
        return new BigDecimal(number.replace(",", ""))
                .stripTrailingZeros()
                .toPlainString();
    }

    private Set<BigInteger> amountsIn(String text) {
        Set<BigInteger> amounts = new LinkedHashSet<>();
        if (text == null) {
            return amounts;
        }
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            BigInteger amount = toAmount(matcher);
            if (amount != null) {
                amounts.add(amount);
            }
        }
        return amounts;
    }

    // 숫자가 하나도 없이 "원"만 걸린 경우는 금액이 아님
    private BigInteger toAmount(Matcher matcher) {
        BigInteger total = BigInteger.ZERO;
        boolean found = false;
        for (int group = 1; group <= UNITS.length; group++) {
            String digits = matcher.group(group);
            if (digits == null) {
                continue;
            }
            found = true;
            total = total.add(new BigInteger(digits.replace(",", "")).multiply(UNITS[group - 1]));
        }
        return found ? total : null;
    }

    private record MeasureValue(BigDecimal value, String unit) {}

    private enum ComparisonClaim {
        SUPERLATIVE,
        CHEAPER,
        EXPENSIVE,
        FASTER,
        CONVENIENT,
        ADVANTAGE,
        STABLE,
        SIMPLE,
        BETTER,
        ADVANTAGEOUS,
        SUPERIOR
    }

    private enum PolicyClaim {
        CALENDAR_DAY_BASIS,
        BUSINESS_DAY_BASIS,
        HARD_DEADLINE,
        FEATURE_UNAVAILABLE,
        ACTION_LOCATION_OR_TIMING,
        EXCEPTION_QUALIFIER,
        COMPLETE_COST
    }

    private enum FeatureAction {
        CHANGE,
        TERMINATE,
        ISSUE,
        REGISTER,
        PAYMENT,
        USE,
        LOOKUP
    }

    private enum ClaimPolarity {
        PRESENT,
        ABSENT,
        UNKNOWN
    }
}
