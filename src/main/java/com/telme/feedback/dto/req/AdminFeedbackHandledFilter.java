package com.telme.feedback.dto.req;

public enum AdminFeedbackHandledFilter {
    ALL(0),
    HANDLED(1),
    UNHANDLED(2);

    private final int mode;

    AdminFeedbackHandledFilter(int mode) {
        this.mode = mode;
    }

    // 쿼리에 그대로 넘긴다. null이 들어갈 수 있는 불리언으로 넘기면
    // Postgres가 파라미터 타입을 정하지 못해 쿼리가 실패한다
    public int mode() {
        return mode;
    }
}
