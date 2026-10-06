# V6 사람 검토 대조 키

> 사람 판정을 먼저 기록하고 잠근 뒤에 확인하세요. 이 문서에는 원본 caseId, 표본 유형, Judge 결과와 검증 오류가 들어 있습니다.

- 원본: `20261002-service-pipeline-judged-raw.json.gz`
- 원본 SHA-256: `67b66976c4d530fea6fc01e028b22fd3669b9212aa32014bad42004fab4713be`
- 표본: 총 39건, 실행 완료 후 출력 검증 실패 16건, Judge 사람 검토 요청 7건, 정상 채점 표본 16건

## 사람 판정을 잠근 뒤 대조할 사례

### 출력 검증 실패 유형

- `grounding` / `ClaimTextMismatch`: 12건
- `grounding` / `ValueError`: 3건
- `quality` / `ValueError`: 1건

---

## Judge가 사람 검토를 요청한 사례 (7건)

### H-001 / `PIPE-TERMINATE-0092`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-005 / `PIPE-COMPOUND-040`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL", "MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

### H-006 / `PIPE-PLAN-0106`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-017 / `PIPE-TERMINATE-0113`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-024 / `PIPE-BILLING-0148`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `REVIEW`
- 주장 판정: `[{"claim": "요금제 변경은 신청하신 날 다음 날 자정(00:00)부터 적용됩니다.", "verdict": "SUPPORTED", "sourceIds": ["BILLING-0148", "BILLING-0141", "BILLING-0050"]}, {"claim": "청구 기준일은 변경된 요금제 기준으로 그 달 사용량에 따라 일할 계산되어 청구됩니다.", "verdict": "SUPPORTED", "sourceIds": ["BILLING-0148"]}, {"claim": "변경된 요금제의 혜택은 변경 적용일 이후 사용량부터 반영됩니다.", "verdict": "REVIEW", "sourceIds": []}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "REVIEW", "rule": "UNCERTAIN_GROUNDING", "disagreesWithJudge": false, "requiresReview": true}}`

### H-026 / `PIPE-COMPOUND-047`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL", "PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

### H-039 / `PIPE-SUBSCRIBE-0051`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `True`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

## 실행은 완료됐지만 Judge 출력 검증 실패 (16건)

### H-002 / `PIPE-PLAN-0137`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.", "attemptErrors": []}}`

### H-007 / `PIPE-COMPOUND-029`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["PARTIAL", "PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-008 / `PIPE-COMPOUND-035`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE", "PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-009 / `PIPE-PLAN-0017`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-012 / `PIPE-TERMINATE-0104`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-016 / `PIPE-DEVICE-0111`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-020 / `PIPE-COMPOUND-025`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "UNSCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "번호이동 가능 여부를 확인하려면 개통일로부터 3개월이 경과했는지 먼저 확인해 주세요.", "verdict": "SUPPORTED", "sourceIds": ["PORTING-0027"]}, {"claim": "명의변경 경험이 있다면, 그 변경일로부터도 1개월이 경과했는지 함께 확인해 보시는 것이 좋습니다.", "verdict": "SUPPORTED", "sourceIds": ["PORTING-0027"]}]`
- 질문 충족도: `[]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`
- 출력 검증 실패: `{"quality": {"error": "ValueError: Answered subquestion has no answer citation", "attemptErrors": []}}`

### H-021 / `PIPE-USIM-0099`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.", "attemptErrors": []}}`

### H-022 / `PIPE-TERMINATE-0001`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-023 / `PIPE-SUBSCRIBE-0122`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-028 / `PIPE-BILLING-0020`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-030 / `PIPE-BILLING-0102`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-031 / `PIPE-COMPOUND-002`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE", "COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-033 / `PIPE-TERMINATE-0107`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

### H-035 / `PIPE-PLAN-0121`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ValueError: 전체 근거 판정과 개별 주장이 모순됩니다.", "attemptErrors": []}}`

### H-037 / `PIPE-PORTING-0049`
- 실행 기대: `ANSWER`
- Judge 상태: `UNSCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "UNSCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `미채점`
- 주장 판정: `[]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": null}`
- 출력 검증 실패: `{"grounding": {"error": "ClaimTextMismatch: Extracted claim is not an exact ordered span of assistantAnswer.", "attemptErrors": []}}`

## 단일 질문 일부 누락 사례 (2건)

### H-003 / `PIPE-USIM-0012`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "신고 후 즉시 이용이 정지됩니다.", "verdict": "SUPPORTED", "sourceIds": ["USIM-0012", "USIM-0036"]}]`
- 질문 충족도: `["PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-029 / `PIPE-PLAN-0136`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "일반 라인업의 경우 5G 4종, LTE 3종, 알뜰폰 2종으로 구성되어 있습니다.", "verdict": "SUPPORTED", "sourceIds": ["PLAN-0136"]}]`
- 질문 충족도: `["PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "OVER_REFUSAL", "rule": "REFUSAL_ENOUGH", "disagreesWithJudge": false, "requiresReview": false}}`

## 근거가 충분한데 거절한 사례 (2건)

### H-004 / `PIPE-BILLING-0069`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "OVER_REFUSAL", "rule": "REFUSAL_ENOUGH", "disagreesWithJudge": false, "requiresReview": false}}`

### H-027 / `PIPE-USIM-0028`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "OVER_REFUSAL", "rule": "REFUSAL_ENOUGH", "disagreesWithJudge": false, "requiresReview": false}}`

## 범위 밖 질문을 거절한 사례 (2건)

### H-010 / `PIPE-OUT-011`
- 실행 기대: `OUT_OF_SCOPE`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["NOT_APPLICABLE"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

### H-034 / `REG-UNRELATED`
- 실행 기대: `OUT_OF_SCOPE`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["NOT_APPLICABLE"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

## 복합 질문 일부 누락 사례 (2건)

### H-011 / `PIPE-COMPOUND-008`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED", "MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

### H-015 / `PIPE-COMPOUND-036`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `UNSUPPORTED`
- 주장 판정: `[{"claim": "법인 회선을 개인 명의로 전환할 때 필요한 구비 서류는 양도인과 양수인의 신분증입니다.", "verdict": "SUPPORTED", "sourceIds": ["NAME_CHANGE-0010"]}, {"claim": "원칙적으로는 두 분이 함께 방문하시는 것이 좋으나, 한 분이 방문이 어려우시다면 위임장을 준비하셔야 합니다.", "verdict": "SUPPORTED", "sourceIds": ["NAME_CHANGE-0010"]}, {"claim": "교환 가능 여부에 대해서는 현재 제공 중인 서비스 정책에 따라 다를 수 있습니다.", "verdict": "UNSUPPORTED", "sourceIds": []}]`
- 질문 충족도: `["COMPLETE", "PARTIAL"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "SHOULD_ABSTAIN", "rule": "UNSUPPORTED_FACTUAL_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

## 검색 근거가 부족해 보류한 사례 (2건)

### H-013 / `PIPE-NAME_CHANGE-0061`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

### H-014 / `PIPE-NAME_CHANGE-0068`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

## 완전 답변이지만 근거 밖 주장이 있는 사례 (2건)

### H-018 / `PIPE-SUBSCRIBE-0095`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `UNSUPPORTED`
- 주장 판정: `[{"claim": "세컨드폰 신청은 오후 8시 이후에는 처리되지 않고, 다음 날 처리 시간에 진행됩니다.", "verdict": "UNSUPPORTED", "sourceIds": []}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "SHOULD_ABSTAIN", "rule": "UNSUPPORTED_FACTUAL_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-032 / `PIPE-DEVICE-0102`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `UNSUPPORTED`
- 주장 판정: `[{"claim": "개통 후 6개월이 지나지 않아 교체가 어렵습니다.", "verdict": "UNSUPPORTED", "sourceIds": []}, {"claim": "현재는 수리를 받으셔야 합니다.", "verdict": "SUPPORTED", "sourceIds": ["DEVICE-0102"]}, {"claim": "자세한 수리 절차는 가까운 서비스 센터를 방문하시거나 연락주시면 안내해 드리겠습니다.", "verdict": "UNSUPPORTED", "sourceIds": []}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "SHOULD_ABSTAIN", "rule": "UNSUPPORTED_FACTUAL_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

## 이전 대화가 포함된 후속 질문 사례 (1건)

### H-019 / `REG-MULTI_TURN`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "가입한 달에는 요금제 변경이 불가능합니다.", "verdict": "SUPPORTED", "sourceIds": ["BILLING-0078", "BILLING-0022", "BILLING-0106"]}, {"claim": "변경 가능한 시기는 다음 달부터입니다.", "verdict": "SUPPORTED", "sourceIds": ["BILLING-0078", "BILLING-0022", "BILLING-0106"]}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

## 근거가 있고 완전 답변으로 채점된 사례 (2건)

### H-025 / `PIPE-SERVICE-0026`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "부가서비스를 없애려면 신청 절차를 진행하시면 됩니다.", "verdict": "SUPPORTED", "sourceIds": ["SERVICE-0026"]}, {"claim": "변경 신청 후 그 달 말까지는 기존 부가서비스를 계속 이용하실 수 있으며,", "verdict": "SUPPORTED", "sourceIds": ["SERVICE-0026"]}, {"claim": "요금에도 변화가 없습니다.", "verdict": "SUPPORTED", "sourceIds": ["SERVICE-0026"]}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

### H-036 / `PIPE-SERVICE-0036`
- 실행 기대: `ANSWER`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `SUPPORTED`
- 주장 판정: `[{"claim": "가입 당일에 부가서비스를 해지해도 그 달의 전체 요금이 청구됩니다.", "verdict": "SUPPORTED", "sourceIds": ["SERVICE-0036", "SERVICE-0031", "SERVICE-0016"]}, {"claim": "일할 계산이 적용되지 않기 때문입니다.", "verdict": "SUPPORTED", "sourceIds": ["SERVICE-0036", "SERVICE-0031", "SERVICE-0016"]}]`
- 질문 충족도: `["COMPLETE"]`
- 답변 불가 판단: `{"answerIsRefusal": false, "evidenceAnswerability": "ENOUGH", "decision": {"judgeLabel": null, "label": "NOT_APPLICABLE", "rule": "NO_REFUSAL_OR_UNSUPPORTED_CLAIM", "disagreesWithJudge": false, "requiresReview": false}}`

## 매장 조회 사례 (1건)

### H-038 / `REG-STORE`
- 실행 기대: `STORE_LOOKUP`
- Judge 상태: `SCORED`; 검토 요청: `False`
- 축 상태: `{"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"}`
- 근거성 전체: `NOT_APPLICABLE`
- 주장 판정: `[]`
- 질문 충족도: `["MISSED"]`
- 답변 불가 판단: `{"answerIsRefusal": true, "evidenceAnswerability": "INSUFFICIENT", "decision": {"judgeLabel": null, "label": "APPROPRIATE", "rule": "REFUSAL_INSUFFICIENT", "disagreesWithJudge": false, "requiresReview": false}}`

