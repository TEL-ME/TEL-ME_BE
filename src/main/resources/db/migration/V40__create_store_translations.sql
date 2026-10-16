-- 매장 이름·주소의 번역. 한국어 원문은 stores에 그대로 두고, 번역이 없는 언어는 한국어로 응답한다.
-- locale은 언어 코드(en 등)만 쓴다. en-US와 en-GB를 나눠 관리할 일이 없어서다
CREATE TABLE store_translations (
    store_id   BIGINT       NOT NULL REFERENCES stores (store_id) ON DELETE CASCADE,
    locale     VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    address    TEXT         NOT NULL,
    PRIMARY KEY (store_id, locale)
);