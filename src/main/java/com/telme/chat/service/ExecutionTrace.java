package com.telme.chat.service;

/** Structured execution metadata only; never pass unverified model output to this boundary. */
public interface ExecutionTrace {
    void stage(Long executionId, String stage, Object value);
    void append(Long executionId, String stage, Object value);

    static ExecutionTrace noop() {
        return new ExecutionTrace() {
            public void stage(Long executionId, String stage, Object value) {}
            public void append(Long executionId, String stage, Object value) {}
        };
    }
}
