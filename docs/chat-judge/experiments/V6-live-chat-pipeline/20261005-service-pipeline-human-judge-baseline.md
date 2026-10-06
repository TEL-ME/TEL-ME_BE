# V6 사람 판정과 Judge 대조

- 사람 판정: `20261005-service-pipeline-human-review-lyj.md` (SHA-256 `11e18dbd43da09653ebaa5a3a244a0f690ca8de76ac3b10e5339366ff1b42c08`)
- Judge 결과: `20261002-service-pipeline-judged-raw.json.gz`

39건은 오류 및 정상 사례를 섞어 뽑은 검토 표본이다. 아래 일치율은 전체 서비스 품질이나 Judge의 모집단 정확도가 아니다.
미채점과 REVIEW는 불일치율의 분모에서 제외하고 별도로 표시한다.

| 항목 | 일치 | 불일치 | 미해결 |
| --- | ---: | ---: | ---: |
| 근거성 | 17 | 6 | 16 |
| 질문별 충족도: 모든 하위 질문 일치 | 26 | 12 | 1 |
| 실제 RAG 근거 충분성 | 32 | 7 | 0 |
| 최종 답변 불가 판정 | 16 | 7 | 16 |

충족도 하위 질문별 일치: 31/45.
최종 답변 불가 판정은 근거성 결과와 거절 신호를 결합한 값이며, 모델 단독 출력이 아니다.

## 근거성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| H-001 | `UNSUPPORTED` | `NOT_APPLICABLE` | MISMATCH |
| H-002 | `SUPPORTED` | `None` | UNRESOLVED |
| H-006 | `SUPPORTED` | `NOT_APPLICABLE` | MISMATCH |
| H-007 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-008 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-009 | `SUPPORTED` | `None` | UNRESOLVED |
| H-012 | `SUPPORTED` | `None` | UNRESOLVED |
| H-015 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| H-016 | `SUPPORTED` | `None` | UNRESOLVED |
| H-017 | `SUPPORTED` | `NOT_APPLICABLE` | MISMATCH |
| H-018 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| H-021 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-022 | `SUPPORTED` | `None` | UNRESOLVED |
| H-023 | `SUPPORTED` | `None` | UNRESOLVED |
| H-024 | `SUPPORTED` | `REVIEW` | UNRESOLVED |
| H-028 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-030 | `SUPPORTED` | `None` | UNRESOLVED |
| H-031 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-032 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| H-033 | `SUPPORTED` | `None` | UNRESOLVED |
| H-035 | `UNSUPPORTED` | `None` | UNRESOLVED |
| H-037 | `UNSUPPORTED` | `None` | UNRESOLVED |

## 질문 충족도

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| H-001 | `['MISSED']` | `['PARTIAL']` | MISMATCH |
| H-005 | `['MISSED', 'MISSED']` | `['PARTIAL', 'MISSED']` | MISMATCH |
| H-007 | `['COMPLETE', 'MISSED']` | `['PARTIAL', 'PARTIAL']` | MISMATCH |
| H-008 | `['COMPLETE', 'MISSED']` | `['COMPLETE', 'PARTIAL']` | MISMATCH |
| H-012 | `['PARTIAL']` | `['COMPLETE']` | MISMATCH |
| H-015 | `['COMPLETE', 'MISSED']` | `['COMPLETE', 'PARTIAL']` | MISMATCH |
| H-020 | `['COMPLETE', 'MISSED']` | `[]` | UNRESOLVED |
| H-026 | `['MISSED', 'MISSED']` | `['PARTIAL', 'PARTIAL']` | MISMATCH |
| H-028 | `['PARTIAL']` | `['COMPLETE']` | MISMATCH |
| H-029 | `['COMPLETE']` | `['PARTIAL']` | MISMATCH |
| H-031 | `['PARTIAL', 'COMPLETE']` | `['COMPLETE', 'COMPLETE']` | MISMATCH |
| H-037 | `['PARTIAL']` | `['COMPLETE']` | MISMATCH |
| H-039 | `['MISSED']` | `['PARTIAL']` | MISMATCH |

## 근거 충분성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| H-001 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-007 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-008 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-015 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-020 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-021 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| H-031 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |

## 답변 불가

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| H-001 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| H-002 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-007 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-008 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-009 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-012 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-015 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| H-016 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-018 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| H-021 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-022 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-023 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-024 | `NOT_APPLICABLE` | `REVIEW` | UNRESOLVED |
| H-026 | `OVER_REFUSAL` | `APPROPRIATE` | MISMATCH |
| H-028 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-029 | `NOT_APPLICABLE` | `OVER_REFUSAL` | MISMATCH |
| H-030 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-031 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-032 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| H-033 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| H-035 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-037 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| H-039 | `OVER_REFUSAL` | `NOT_APPLICABLE` | MISMATCH |
