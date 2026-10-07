package com.telme.llm.dto.req;

import com.telme.llm.entity.LlmGeneration.Status;
import java.util.List;

//SUCCESS·NO_EVIDENCE는 정상, CANCELLED는 사용자가 답변을 멈춘 것이라 오류 목록에서 뺀다
public enum AdminLlmErrorType {

    TIMEOUT, CONNECTION_FAILED, MODEL_ERROR, ALL;
    
    private static final List<Status> ERROR_STATUSES = List.of(Status.TIMEOUT, Status.CONNECTION_FAILED, Status.MODEL_ERROR);
    
    public List<Status> toStatuses() {
        return this == ALL ? ERROR_STATUSES : List.of(Status.valueOf(name()));
    }
}
