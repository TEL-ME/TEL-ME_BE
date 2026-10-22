package com.telme.llm.service;

@FunctionalInterface
public interface UserRetryLookup {

    UserRetryLookup NEVER = executionId -> false;

    boolean afterUserRetry(Long executionId);
}
