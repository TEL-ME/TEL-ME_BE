package com.telme.intent.service;

import static com.telme.intent.dto.res.FollowUpRouteResponse.LOCATION_KEY;
import static com.telme.intent.dto.res.FollowUpRouteResponse.SERVICE_TYPE_KEY;

import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.LlmFollowUpPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ConditionPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ResponseType;
import com.telme.intent.dto.res.LlmFollowUpPayload.Status;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.dto.res.LlmRoutingPayload.SubQueryPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// LLM 장애 시 시스템이 멈추는 걸 막는 용도입니다.
// method = RULE로 표기되어 나중에 정확도 분석 때 구분할 수 있는 비상 대체 클래스입니다.
@Component
public class RuleBasedRoutingFallback {

    // "재고 없어?"처럼 정상 질의의 부정어를 거절로 오인하지 않도록 contains 대신 패턴으로 좁게 잡는다
    private static final Pattern DECLINE_PATTERN = Pattern.compile(
        "안\\s*(알려|가르쳐|할래|할게|밝힐)"
        + "|(알려주기|말하기|밝히기)\\s*싫"
        + "|거절"
        + "|(필요|상관)\\s*없"
        + "|(위치|지역|장소)[가은는]?\\s*(안|못)"
        + "|모르겠"
        + "|됐어|건너뛰|생략|스킵|패스"
    );

    // 오탐을 막기 위해 접미사가 분명한 토큰만 지역명으로 인정한다
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
        "[가-힣A-Za-z0-9]{1,14}?(?:역|동|읍|면|사거리|공항|터미널|대학교)"
        + "|[가-힣]{2,14}?(?:구|시|로|길)"
    );

    // 접미사 없이 지역명만 답하는 경우("신촌", "판교")를 받기 위한 길이 상한
    private static final int BARE_LOCATION_MAX_LENGTH = 20;

    // "네", "음"처럼 호응만 하는 답변이 지역으로 잡히면 되묻기를 유지하지 못한다.
    // "네거리"를 거르지 않도록 답변 전체가 호응일 때만 막는다
    private static final Pattern ACK_ONLY_PATTERN = Pattern.compile(
        "^(네|넵|예|응|음|어|아|그래|알겠|오케이|ok|okay)[요오은는\\s.!~]*$",
        Pattern.CASE_INSENSITIVE
    );

    // "잠깐만요", "나중에요"처럼 답을 미루는 표현도 지역이 아니다
    private static final Pattern DEFERRAL_PATTERN = Pattern.compile(
        "잠(깐|시)|이따|나중|기다|생각\\s*(좀|해)|고민|보류|글쎄|몰라|모르"
    );

    private static final Pattern NEW_QUESTION_PATTERN = Pattern.compile(
        "[?？]|알려\\s*(줘|주세요)|설명해|추천해|찾아\\s*(줘|주세요)"
        + "|어떻게|뭐야|무엇|되나요|돼요|인가요"
    );

    private static final Pattern STORE_LOOKUP_PATTERN = Pattern.compile(
        "가까운|근처|주변|찾|어디|위치|주소|영업시간|주차|몇\\s*시|연락처|알려|있나요|있어|보여"
    );
    private static final Pattern STORE_ENTITY_PATTERN = Pattern.compile(
        "매장|대리점|지점|직영점|[가-힣A-Za-z0-9]+역점"
    );
    private static final Pattern SEPARATE_REQUESTS_PATTERN = Pattern.compile(
        "알려\\s*주고|설명\\s*해\\s*주고|추천\\s*해\\s*주고|그리고|또\\s+매장|뿐만 아니라"
    );
    private static final Pattern CANCEL_SERVICE_PATTERN = Pattern.compile("(?<!정)해지");
    private static final Pattern STORE_VISIT_POLICY_PATTERN = Pattern.compile(
        "매장\\s*방문.*(가능|방법|절차)|매장에서.*(처리|신청|예약)"
    );
    private static final Pattern STORE_RESULT_REQUEST_PATTERN = Pattern.compile("찾|어디|위치|주소|곳");

    private static final List<String> FAQ_KEYWORDS = List.of(
        "요금제", "할인", "약정", "위약금", "로밍", "유심", "eSIM",
        "기기변경", "번호이동", "부가서비스", "결합", "데이터",
        "요금", "개통", "해외", "명의", "가입", "혜택", "재발급", "분실", "택배",
        "통신", "휴대폰", "핸드폰", "폰", "단말", "할부", "통화", "청구", "납부", "인터넷", "번호", "보험",
        "5G", "LTE", "USIM", "이심", "유플러스", "U+"
    );

    private record ServiceTypeRule(String code, Pattern pattern) {}

    private record ServiceTypeOption(String label, String code) {}

    // 업무를 되물을 때 버튼으로 내보내는 문구. 화면에 이 순서로 그려진다
    private static final List<ServiceTypeOption> SERVICE_TYPE_OPTIONS = List.of(
        new ServiceTypeOption("유심 재발급", "USIM_REISSUE"),
        new ServiceTypeOption("번호이동", "PORT_IN"),
        new ServiceTypeOption("신규 개통", "NEW_LINE"),
        new ServiceTypeOption("명의변경", "NAME_CHANGE")
    );

    /** 업무 되묻기 버튼 문구. */
    public static List<String> serviceTypeOptions() {
        return SERVICE_TYPE_OPTIONS.stream().map(ServiceTypeOption::label).toList();
    }

    public static String serviceTypeLabel(String code) {
        return SERVICE_TYPE_OPTIONS.stream()
                .filter(option -> option.code().equals(code))
                .map(ServiceTypeOption::label)
                .findFirst()
                .orElse(null);
    }

    private static final List<ServiceTypeRule> SERVICE_TYPE_RULES = List.of(
        new ServiceTypeRule("USIM_REISSUE", Pattern.compile("유심.*변경|유심.*교체|유심.*재발급|eSIM|이심")),
        new ServiceTypeRule("NAME_CHANGE",  Pattern.compile("명의.*변경")),
        new ServiceTypeRule("PORT_IN",      Pattern.compile("번호.*이동|통신사.*변경")),
        new ServiceTypeRule("NEW_LINE",     Pattern.compile("신규.*개통|새.*번호"))
    );

    public LlmRoutingPayload classify(String text) {
        if (text == null || text.isBlank()) {
            return new LlmRoutingPayload(
                Intent.UNKNOWN, BigDecimal.valueOf(0.50), text != null ? text : "",
                Collections.emptyMap(), Collections.emptyList()
            );
        }

        boolean hasStore = STORE_ENTITY_PATTERN.matcher(text).find()
            && STORE_LOOKUP_PATTERN.matcher(text).find()
            && (!STORE_VISIT_POLICY_PATTERN.matcher(text).find()
                || STORE_RESULT_REQUEST_PATTERN.matcher(text).find());
        boolean hasFaq = FAQ_KEYWORDS.stream().anyMatch(text::contains)
            || CANCEL_SERVICE_PATTERN.matcher(text).find()
            || (!hasStore && STORE_ENTITY_PATTERN.matcher(text).find()
                && (text.contains("방문") || text.contains("처리") || text.contains("가능")));
        Map<String, String> conditions = extractConditions(text);

        if (hasStore && hasFaq && SEPARATE_REQUESTS_PATTERN.matcher(text).find()) {
            return new LlmRoutingPayload(
                Intent.BOTH, BigDecimal.valueOf(0.70), text, conditions,
                List.of(
                    new SubQueryPayload((short) 1, ConsultRequest.Intent.FAQ, text, Collections.emptyMap()),
                    new SubQueryPayload((short) 2, ConsultRequest.Intent.STORE, text, conditions)
                )
            );
        }

        if (hasStore) {
            return new LlmRoutingPayload(
                Intent.STORE, BigDecimal.valueOf(0.80), text, conditions,
                List.of(new SubQueryPayload((short) 1, ConsultRequest.Intent.STORE, text, conditions))
            );
        }

        if (hasFaq) {
            return new LlmRoutingPayload(
                Intent.FAQ, BigDecimal.valueOf(0.80), text, conditions,
                List.of(new SubQueryPayload((short) 1, ConsultRequest.Intent.FAQ, text, Collections.emptyMap()))
            );
        }

        return new LlmRoutingPayload(
            Intent.UNKNOWN, BigDecimal.valueOf(0.50), text,
            Collections.emptyMap(), Collections.emptyList()
        );
    }

    // LLM이 조건을 하나도 뽑지 못했을 때의 대체 경로로도 쓰인다
    public LlmFollowUpPayload classifyFollowUp(String text, Set<String> pendingKeys) {
        if (text == null || text.isBlank()) {
            return new LlmFollowUpPayload(ResponseType.DEFERRED, Collections.emptyList());
        }

        String reply = text.trim();

        if (DECLINE_PATTERN.matcher(reply).find()) {
            Set<String> declinedKeys = (pendingKeys == null || pendingKeys.isEmpty())
                ? Set.of(LOCATION_KEY)
                : pendingKeys;
            return new LlmFollowUpPayload(
                ResponseType.CONDITION_RESPONSE,
                declinedKeys.stream()
                    .map(key -> new ConditionPayload(key, Status.DECLINED, null))
                    .toList());
        }

        if (ACK_ONLY_PATTERN.matcher(reply).find() || DEFERRAL_PATTERN.matcher(reply).find()) {
            return new LlmFollowUpPayload(ResponseType.DEFERRED, Collections.emptyList());
        }

        if (NEW_QUESTION_PATTERN.matcher(reply).find()) {
            return new LlmFollowUpPayload(ResponseType.NEW_QUESTION, Collections.emptyList());
        }

        List<ConditionPayload> conditions = new ArrayList<>();

        String location = extractLocation(reply);
        if (location != null) {
            conditions.add(new ConditionPayload(LOCATION_KEY, Status.FILLED, location));
        }

        String serviceType = extractConditions(reply).get(SERVICE_TYPE_KEY);
        if (serviceType != null) {
            conditions.add(new ConditionPayload(SERVICE_TYPE_KEY, Status.FILLED, serviceType));
        }

        ResponseType responseType = conditions.isEmpty()
            ? ResponseType.DEFERRED
            : ResponseType.CONDITION_RESPONSE;
        return new LlmFollowUpPayload(responseType, conditions);
    }

    private String extractLocation(String reply) {
        Matcher matcher = LOCATION_PATTERN.matcher(reply);
        if (matcher.find()) {
            return matcher.group();
        }
        // 길면 지역이 아닌 다른 발화로 보고 되묻기를 유지한다
        if (reply.length() > BARE_LOCATION_MAX_LENGTH || reply.contains("?")) {
            return null;
        }
        // 기준점 표현이 있으면 그 앞의 실제 지명만 받는다("판교 근처요" -> 판교, "현재 위치에서 찾아줘" -> 없음)
        if (RelativeLocation.mentions(reply)) {
            return RelativeLocation.placeBefore(reply);
        }
        // 짧다고 모두 지역으로 보면 "네", "잠깐만요"까지 FILLED가 되어 되묻기가 끊긴다
        if (ACK_ONLY_PATTERN.matcher(reply).find() || DEFERRAL_PATTERN.matcher(reply).find()) {
            return null;
        }
        return reply;
    }

    private Map<String, String> extractConditions(String text) {
        Map<String, String> conditions = new HashMap<>();
        for (ServiceTypeRule rule : SERVICE_TYPE_RULES) {
            if (rule.pattern().matcher(text).find()) {
                conditions.put(SERVICE_TYPE_KEY, rule.code());
                break;
            }
        }
        return conditions;
    }

    // 문장에 업무 표현이 하나만 있으면 그 업무 코드, 없거나 여럿이면 null.
    // "번호이동 말고 신규 개통"처럼 여럿이면 어느 쪽인지 규칙으로 알 수 없어 임의로 고르지 않는다(필터 없이 검색)
    public String serviceTypeOf(String text) {
        if (text == null) {
            return null;
        }
        List<String> codes = SERVICE_TYPE_RULES.stream()
                .filter(rule -> rule.pattern().matcher(text).find())
                .map(ServiceTypeRule::code)
                .toList();
        return codes.size() == 1 ? codes.getFirst() : null;
    }

    // LLM이 준 업무가 문장의 유일한 업무 표현과 같을 때만 인정한다.
    // "번호이동 말고 신규 개통"처럼 업무 표현이 여럿이면 LLM이 어떤 값을 줘도 필터 없이 검색한다
    boolean matchesServiceType(String code, String text) {
        return code != null && code.equals(serviceTypeOf(text));
    }

    /** 버튼 문구를 그대로 누른 답이면 그 업무 코드, 아니면 null. 직접 쓴 문장은 모델이 읽는다. */
    public String serviceTypeOfOption(String reply) {
        if (reply == null) {
            return null;
        }
        String trimmed = reply.strip();
        return SERVICE_TYPE_OPTIONS.stream()
                .filter(option -> option.label().equals(trimmed))
                .map(ServiceTypeOption::code)
                .findFirst()
                .orElse(null);
    }

    boolean hasServiceTypeMention(String text) {
        return SERVICE_TYPE_RULES.stream().anyMatch(rule -> rule.pattern().matcher(text).find());
    }
}
