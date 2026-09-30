-- 등록 중복 검사가 조회와 저장으로 나뉘어 있어, 두 관리자가 같은 내용을 동시에 보내면 둘 다 통과한다.
--
-- content_hash를 그대로 쓸 수 없다. 질문과 답변을 구분자 없이 이어 붙여 만들어
-- ("가나", "다라")와 ("가나다", "라")가 같은 값이라, 서로 다른 FAQ를 중복으로 막는다.
-- 이어 붙이고 구분자를 넣는 방법도 그 구분자가 본문에 들어가면 같은 문제가 남아,
-- 질문과 답변을 각각 해시해 두 열로 둔다.
-- 삭제한 FAQ는 같은 내용으로 다시 만들 수 있어야 해서 status로 거른다
CREATE UNIQUE INDEX uk_faqs_content_active
    ON faqs (md5(question), md5(answer)) WHERE status <> 'DELETED';

-- faqs.version은 질문·답변이 바뀔 때만 오르는 내용 버전이고 검색이 e.faq_version과 맞춰 본다.
-- 저장할 때마다 올려야 하는 잠금용 숫자는 겸용할 수 없어 따로 둔다.
-- 기존 행과 잠금을 모르는 적재 경로를 위해 기본값을 준다
ALTER TABLE faqs
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0;
