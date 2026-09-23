package com.telme.intent.service;

import static com.telme.intent.dto.res.FollowUpRouteResponse.LOCATION_KEY;
import static com.telme.intent.dto.res.FollowUpRouteResponse.SERVICE_TYPE_KEY;

import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.LlmFollowUpPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ConditionPayload;
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

    private static final List<String> STORE_KEYWORDS = List.of(
        "매장", "대리점", "지점", "직영점", "가까운", "근처",
        "위치", "어디", "주소", "방문", "영업시간", "찾아줘", "찾아주세요"
    );

    private static final List<String> FAQ_KEYWORDS = List.of(
        "요금제", "할인", "약정", "위약금", "로밍", "유심", "eSIM",
        "기기변경", "번호이동", "해지", "부가서비스", "결합", "데이터",
        "요금", "개통", "해외", "명의", "가입", "혜택"
    );

    private record ServiceTypeRule(String code, Pattern pattern) {}

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

        boolean hasStore = STORE_KEYWORDS.stream().anyMatch(text::contains);
        boolean hasFaq = FAQ_KEYWORDS.stream().anyMatch(text::contains);
        Map<String, String> conditions = extractConditions(text);

        if (hasStore && hasFaq) {
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
                Intent.FAQ, BigDecimal.valueOf(0.80), text, Collections.emptyMap(),
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
            return new LlmFollowUpPayload(Collections.emptyList());
        }

        String reply = text.trim();

        if (DECLINE_PATTERN.matcher(reply).find()) {
            Set<String> declinedKeys = (pendingKeys == null || pendingKeys.isEmpty())
                ? Set.of(LOCATION_KEY)
                : pendingKeys;
            return new LlmFollowUpPayload(declinedKeys.stream()
                .map(key -> new ConditionPayload(key, Status.DECLINED, null))
                .toList());
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

        return new LlmFollowUpPayload(conditions);
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
}
