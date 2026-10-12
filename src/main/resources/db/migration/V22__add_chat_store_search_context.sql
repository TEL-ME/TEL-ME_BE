ALTER TABLE chat_messages ADD COLUMN store_search_context JSONB;
COMMENT ON COLUMN chat_messages.store_search_context IS
    'Store search origin type, display label and applied radius; excludes raw user GPS coordinates';
