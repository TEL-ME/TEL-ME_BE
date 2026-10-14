package com.telme.chat.guard;

/** 원문 번호 대신 저장된 별표 세 글자의 위치만 보존한다. 위치는 UTF-16 문자열 기준이다. */
public record MaskSpan(int start, int end) {
    public MaskSpan {
        if (start < 0 || end <= start || end - start != 3) {
            throw new IllegalArgumentException("마스크 위치가 올바르지 않습니다.");
        }
    }
}
