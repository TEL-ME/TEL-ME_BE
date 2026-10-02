-- Nullable additive column: old executions remain distinguishable from recorded executions.
-- V15/V16 are used by the merged store changes; this pending trace migration follows them.
-- Uses the existing execution ownership and deletion lifecycle; no raw model output is stored.
ALTER TABLE chat_executions ADD COLUMN pipeline_trace jsonb;
ALTER TABLE llm_generations ADD COLUMN request_options jsonb;
