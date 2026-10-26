-- 문맥 해석 프롬프트 버전(multiturn-resolution-v31-reference, 34자)이 30자를 넘어 호출 기록 저장이 실패했다.
-- 버전 이름은 설명형으로 길어지므로 여유 있게 늘린다.
ALTER TABLE llm_generations ALTER COLUMN prompt_version TYPE VARCHAR(100);
