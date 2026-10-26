package com.telme.chat.repository;

/** 대시보드의 오늘·어제 질문 수. 한 번의 조회로 함께 센다. */
public record QuestionCounts(long today, long yesterday) {
}
