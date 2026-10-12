package com.telme.chat.safety;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.util.List;

/** 검사 사유만 보관하며 차단한 생성문은 예외·로그에 넣지 않는다. */
public final class ChatOutputBlockedException extends GeneralException {

    private final String policyVersion;
    private final String field;
    private final List<String> ruleIds;

    public ChatOutputBlockedException(String policyVersion, String field, List<String> ruleIds) {
        super(ChatErrorCode.OUTPUT_POLICY_BLOCKED);
        this.policyVersion = policyVersion;
        this.field = field;
        this.ruleIds = List.copyOf(ruleIds);
    }

    public String policyVersion() {
        return policyVersion;
    }

    public String field() {
        return field;
    }

    public List<String> ruleIds() {
        return ruleIds;
    }
}
