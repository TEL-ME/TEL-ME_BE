# V6 사람 판정과 Judge 대조

- 사람 판정: `20261005-heldout40-human-review-lyj.md` (SHA-256 `59ff8d49f1ba2c1aefee1c13a88ed132a457616629c1781a90a5dba1f7ce4699`)
- Judge 결과: `judged-raw.json.gz`

선정된 검토 표본의 일치 수치다. 전체 서비스 품질이나 Judge의 모집단 정확도가 아니다.
불일치는 사람 라벨과 자동 판정의 차이이며 어느 쪽 오류인지 원문 대조 전에는 확정하지 않는다.
미채점과 REVIEW는 불일치율의 분모에서 제외하고 별도로 표시한다.

| 항목 | 일치 | 불일치 | Judge 미해결 | 사람 보류 |
| --- | ---: | ---: | ---: | ---: |
| 근거성 | 32 | 7 | 1 | 0 |
| 질문별 충족도: 모든 하위 질문 일치 | 34 | 6 | 0 | 0 |
| 실제 RAG 근거 충분성 | 31 | 9 | 0 | 0 |
| 최종 답변 불가 판정 | 32 | 7 | 1 | 0 |

충족도 하위 질문별 일치: 38/44.
최종 답변 불가 판정은 근거성 결과와 거절 신호를 결합한 값이며, 모델 단독 출력이 아니다.

## 근거성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-007 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-009 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-011 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-013 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-029 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |
| V6H-032 | `SUPPORTED` | `UNSUPPORTED` | MISMATCH |
| V6H-033 | `SUPPORTED` | `None` | UNRESOLVED |
| V6H-038 | `UNSUPPORTED` | `SUPPORTED` | MISMATCH |

## 질문 충족도

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-003 | `['COMPLETE']` | `['PARTIAL']` | MISMATCH |
| V6H-011 | `['MISSED']` | `['COMPLETE']` | MISMATCH |
| V6H-029 | `['PARTIAL']` | `['COMPLETE']` | MISMATCH |
| V6H-031 | `['COMPLETE', 'MISSED']` | `['COMPLETE', 'PARTIAL']` | MISMATCH |
| V6H-032 | `['COMPLETE', 'MISSED']` | `['PARTIAL', 'MISSED']` | MISMATCH |
| V6H-038 | `['MISSED']` | `['NOT_APPLICABLE']` | MISMATCH |

## 근거 충분성

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-001 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-005 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-007 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-013 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-015 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-020 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-030 | `ENOUGH` | `INSUFFICIENT` | MISMATCH |
| V6H-038 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |
| V6H-039 | `INSUFFICIENT` | `ENOUGH` | MISMATCH |

## 답변 불가

| ID | 사람 | Judge | 상태 |
| --- | --- | --- | --- |
| V6H-007 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| V6H-009 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| V6H-011 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| V6H-013 | `NOT_APPLICABLE` | `SHOULD_ABSTAIN` | MISMATCH |
| V6H-029 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |
| V6H-032 | `APPROPRIATE` | `SHOULD_ABSTAIN` | MISMATCH |
| V6H-033 | `APPROPRIATE` | `None` | UNRESOLVED |
| V6H-038 | `SHOULD_ABSTAIN` | `NOT_APPLICABLE` | MISMATCH |

## 별도 파이프라인 실패 유형

이 항목은 사람의 원인 분류다. 자동 `ROUTING_MISMATCH`는 기대 의도와 실제 라우팅 의도가 다른지 코드로 비교한 신호이며, 엉뚱한 되묻기까지 판정한 결과는 아니다.

- 사람 판정: `MISROUTED_CLARIFICATION` 1건, `NONE` 39건
- 자동 `ROUTING_MISMATCH`: 3/40건

| ID | 사람 분류 | 자동 의도 불일치 신호 |
| --- | --- | --- |
| V6H-026 | `MISROUTED_CLARIFICATION` | 있음 |
| V6H-035 | `NONE` | 있음 |
| V6H-036 | `NONE` | 있음 |

사람 분류 `MISROUTED_CLARIFICATION`을 자동으로 판정한 건수는 아직 없다. 위 표의 자동 신호는 더 넓은 의도 불일치를 뜻한다.
