# 답변 생성 채점용 라벨 작성 지침

통신사 TELME 고객센터 챗봇이 받은 질문마다, FAQ 카탈로그 기준으로 "이 질문에 맞는 답이 무엇인가"를 라벨로 만든다.
라벨은 나중에 Judge 모델이 챗봇 답변을 채점할 때 정답 기준으로 쓴다. 챗봇 답변과 검색 결과는 보지 않는다(주지 않는다).

## 쓸 수 있는 자료

- FAQ 카탈로그: `D:\finalproject\TEL-ME_BE_180\scripts\data\faq_full_1150.json` (1,150개, `slot_id`, `question`, `answer`).
  정책 근거는 **answer 본문만**이다. FAQ의 question 문구는 근거가 아니다.
- 카탈로그는 크다. Grep으로 핵심어(요금, 금액, 기간, 서류 이름 등)를 여러 표현으로 찾아 후보를 모은 뒤 answer를 읽고 고른다.
  한 부분에 맞는 FAQ가 여러 개인 경우가 많으니(같은 정책을 다른 말로 쓴 FAQ) 빠짐없이 모은다.
- 판정 기준 참고: `D:\finalproject\TEL-ME_BE_180\docs\chat-judge\CHAT_PIPELINE_QUALITY_CRITERIA.md`

다른 파일(평가셋, 결과 파일, 저장소의 다른 데이터)은 열지 않는다.

## 항목 입력

- `question`: 챗봇 답변 생성기가 받은 질문. 이 질문에 대해 라벨을 만든다.
  복합 질문이 나뉜 경우 고객 원래 발화의 일부만 들어 있을 수 있다(예: "번호이동 준비 서류" — 매장 찾기 부분은 따로 처리됨). 그때는 이 `question`에 해당하는 부분만 라벨로 만든다.
- `conversation`: 앞 턴 대화(고객 발화, 챗봇 응답). 지시어("그거", "그럼 신청은?")를 풀 때만 쓴다. 챗봇 응답 내용이 맞다고 가정하지 않는다.
- `currentCustomerTurn`: 이번 턴 고객 발화(되묻기에 대한 답일 수 있다).
- `resolvedQuestionByApp`: 챗봇이 문맥으로 풀어 쓴 질문. 참고만 하고, 틀렸다고 보이면 대화 기준으로 판단해 note에 적는다.
- `customerConditions`: 고객이 이미 답한 조건(질문 → 답). 답이 이 조건에 따라 갈리면 이 조건에 맞는 사실만 requiredFacts에 넣는다.
- `askedButUnanswered`: 챗봇이 물었지만 고객이 답하지 못한 조건. 이 조건에 따라 답이 갈리면 갈리는 경우를 모두 requiredFacts에 넣는다.

## 출력 (항목마다 한 줄 JSON, JSONL)

```json
{"id": "...", "expectedBehavior": "ANSWER", "answerSources": ["FAQ"],
 "parts": [{"questionPart": "질문 부분(고객 표현에 가깝게, 지시어는 풀어서)", "sourceIds": ["BILLING-0052", "BILLING-0094"]}],
 "requiredFacts": ["미납 요금을 완납하면 2시간 이내에 재개된다"],
 "missingFact": null, "confidence": "high", "note": ""}
```

- `expectedBehavior`
  - `ANSWER`: 카탈로그 answer 본문으로 모든 질문 부분에 답할 수 있다.
  - `PARTIAL_ANSWER`: 일부 부분은 답할 수 있고 일부는 카탈로그에 없다. 없는 부분은 `missingFact`에 쓰고, requiredFacts에 "…은 FAQ에서 확인할 수 없다"를 넣는다. 그 부분은 parts에 넣지 않는다.
  - `ABSTAIN`: 통신 서비스 질문이지만 카탈로그 어디에도 답이 없다(가까운 FAQ가 다른 상황만 다루는 경우 포함). 고객 개인 정보(내 요금, 내 약정)가 있어야 답할 수 있는 질문도 여기다(answerSources `["MEMBER"]`). parts·requiredFacts는 빈 배열.
  - `OUT_OF_SCOPE`: 통신 서비스와 무관하거나 다른 통신사에 관한 질문. parts·requiredFacts는 빈 배열.
  - `ANSWER_OR_CLARIFY`: 고객 조건(가입 기간, 미납 여부 등)에 따라 답이 갈리는데 그 조건을 모른다. 바로 답한다면 갈리는 경우를 모두 안내하는 사실을 requiredFacts에 넣는다.
- `parts`: 질문 부분마다 하나. `sourceIds`는 그 부분에 **answer 본문이 직접 답하는** FAQ 전부. 비슷한 주제지만 다른 상황(다른 대상, 다른 절차)을 다루는 FAQ는 넣지 않는다.
- `requiredFacts`: 질문이 실제로 묻는 사실만, 짧은 평서문으로. 답에 꼭 필요한 조건·예외·금액·기간은 포함하고, 묻지 않은 부가 정보는 뺀다. 같은 부분의 FAQ끼리 사실이 다르면(충돌) confidence를 low로 하고 note에 적는다.
- 계산이 필요한 질문(예: 하루 요금 × 5일)은 카탈로그 사실로 직접 계산할 수 있으면 ANSWER, 카탈로그가 그 계산의 전제(예: 합산 방식)를 말하지 않으면 그 점을 note에 적는다.
- `confidence`: 확신이 없으면 `low`, 이유를 note에. 애매한 것은 억지로 정하지 말고 low로 남긴다(사람이 검토한다).

결과는 지정된 출력 파일에 JSONL로 쓴다. 입력 항목 수와 출력 줄 수가 같아야 한다.
