-- 채팅 다국어 처리

-- 한글이 없는 질문은 한국어로 번역해 content에 두고(상담 처리·문맥·관리자 화면용), 사용자가 입력한 원문은 여기에 둔다.
-- 한국어 질문은 NULL이다. 대화 기록 API는 원문이 있으면 원문을 보여 준다
ALTER TABLE chat_messages ADD COLUMN original_content TEXT;

-- 챗봇 답변의 번역. 같은 답변을 다시 누르면 LLM 없이 이 값을 돌려준다
CREATE TABLE chat_message_translations (
    message_id      BIGINT       NOT NULL REFERENCES chat_messages (message_id) ON DELETE CASCADE,
    target_language VARCHAR(10)  NOT NULL,
    content         TEXT         NOT NULL,
    model           VARCHAR(50)  NOT NULL,
    prompt_version  VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, target_language)
);