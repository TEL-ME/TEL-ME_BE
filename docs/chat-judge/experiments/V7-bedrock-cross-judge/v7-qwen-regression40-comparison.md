# V7 qwen과 기존 40건 사람 판정 대조

- 사람 판정: `20261005-heldout40-human-review-lyj.md` (SHA-256 `59ff8d49f1ba2c1aefee1c13a88ed132a457616629c1781a90a5dba1f7ce4699`)
- Judge 결과: `v7-qwen-regression40-raw.json.gz`

기존 40건은 Judge 수정에 사용된 회귀 표본이다. 선정된 검토 표본의 일치 수치다. 전체 서비스 품질이나 Judge의 모집단 정확도가 아니다.
불일치는 사람 라벨과 자동 판정의 차이이며 어느 쪽 오류인지 원문 대조 전에는 확정하지 않는다.
미채점과 REVIEW는 불일치율의 분모에서 제외하고 별도로 표시한다.

| 항목 | 일치 | 불일치 | Judge 미해결 | 사람 보류 |
| --- | ---: | ---: | ---: | ---: |
| 근거성 | 32 | 8 | 0 | 0 |
| 질문별 충족도: 모든 하위 질문 일치 | 33 | 4 | 3 | 0 |
| 실제 RAG 근거 충분성 | 26 | 8 | 6 | 0 |
| 최종 답변 불가 판정 | 29 | 5 | 6 | 0 |

충족도 하위 질문별 일치: 37/41.
최종 답변 불가 판정은 근거성 결과와 거절 신호를 결합한 값이며, 모델 단독 출력이 아니다.

## 근거성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-005 | `SUPPORTED` | `NOT_APPLICABLE` | MISMATCH |
| V6H-007 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-009 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-011 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-013 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-029 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-033 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-038 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |

## 질문 충족도

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-003 | `['COMPLETE']` | `[]` | UNRESOLVED |
| V6H-009 | `['COMPLETE']` | `[]` | UNRESOLVED |
| V6H-011 | `['MISSED']` | `['COMPLETE']` | MISMATCH |
| V6H-022 | `['COMPLETE']` | `[]` | UNRESOLVED |
| V6H-029 | `['PARTIAL']` | `['COMPLETE']` | MISMATCH |
| V6H-030 | `['COMPLETE']` | `['PARTIAL']` | MISMATCH |
| V6H-038 | `['MISSED']` | `['NOT_APPLICABLE']` | MISMATCH |

## 근거 충분성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-003 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-005 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-008 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-009 | `ENOUGH` | `None` | UNRESOLVED |
| V6H-010 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-018 | `ENOUGH` | `None` | UNRESOLVED |
| V6H-020 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-029 | `ENOUGH` | `None` | UNRESOLVED |
| V6H-030 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-031 | `INSUFFICIENT` | `None` | UNRESOLVED |
| V6H-033 | `INSUFFICIENT` | `None` | UNRESOLVED |
| V6H-037 | `ENOUGH` | `None` | UNRESOLVED |
| V6H-038 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| V6H-039 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |

## 답변 불가

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-007 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| V6H-009 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| V6H-011 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| V6H-013 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| V6H-018 | `NOT_APPLICABLE` | `None` | UNRESOLVED |
| V6H-026 | `NOT_APPLICABLE` | `APPROPRIATE` | MISMATCH |
| V6H-029 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| V6H-031 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| V6H-033 | `APPROPRIATE` | `None` | UNRESOLVED |
| V6H-037 | `SHOULD_ABSTAIN` | `None` | UNRESOLVED |
| V6H-038 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
