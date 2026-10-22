package com.telme.llm.service;

// 이 실행이 사용자가 다시 시도를 눌러 만든 두 번째 시도인지 알려준다
@FunctionalInterface
public interface UserRetryLookup {

    UserRetryLookup NEVER = executionId -> false;

    boolean afterUserRetry(Long executionId);
}
