package com.telme.intent.service;

import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.dto.res.LlmRoutingPayload.SubQueryPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// LLM 장애 시 시스템이 멈추는 걸 막는 용도입니다.
// method = RULE로 표기되어 나중에 정확도 분석 때 구분할 수 있는 비상 대체 클래스입니다.
@Component
public class RuleBasedRoutingFallback {

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

    private Map<String, String> extractConditions(String text) {
        Map<String, String> conditions = new HashMap<>();
        for (ServiceTypeRule rule : SERVICE_TYPE_RULES) {
            if (rule.pattern().matcher(text).find()) {
                conditions.put("serviceType", rule.code());
                break;
            }
        }
        return conditions;
    }
}
