# V7 세 모델 교차 검증 목록 302건 사람 판정

대상: [세 모델 교차 검증 사람 검토 목록](v7-sonnet-4-6-human-review.md)

각 답변을 실제 질문과 저장된 FAQ 답변에 대조해 302건 모두 사람이 판정했습니다. 모델 판정은 비교 단서로만 사용했고, 다수결을 정답으로 간주하지 않았습니다.
리뷰어: 이용재

## 판정 코드

- 근거성: SUPPORTED는 FAQ가 주장을 뒷받침, UNSUPPORTED는 근거 밖 또는 상충 주장, NOT_APPLICABLE은 사실 주장이 없음, REVIEW는 자료만으로 확정하기 어려움.
- 충실도: COMPLETE는 핵심 답변 완료, PARTIAL은 일부 답변 또는 필요한 추가 질문, MISSED는 핵심 미답 또는 오답, NOT_APPLICABLE은 범위 밖 질문.
- 보류: NOT_APPLICABLE은 답변 보류가 아님, SHOULD_ABSTAIN은 근거 없는 주장을 하면 안 됨, OVER_REFUSAL은 관련 근거가 있는데 거절, APPROPRIATE는 근거 부족 또는 범위 밖이라 보류가 적절, REVIEW는 확정 불가.
- 출력 잘림은 위 세 축의 점수와 분리한다. `OUTPUT_TRUNCATED`는 생성 종료나 스트림 기록으로 잘림이 확인된 경우, `OUTPUT_TRUNCATED_SUSPECTED`는 `반면에`처럼 앞 문맥이 누락됐을 정황만 있는 경우에 쓴다. 잘림 의심 사례는 실제 남은 답변만 채점한다.

## 집계
- 총 302건을 답변 단위로 판정했습니다.
- 근거성: SUPPORTED 131, UNSUPPORTED 54, NOT_APPLICABLE 117, REVIEW 0.
- 답변 충실도: COMPLETE 116, PARTIAL 63, MISSED 77, NOT_APPLICABLE 46, REVIEW 0.
- 답변 보류: NOT_APPLICABLE 135, SHOULD_ABSTAIN 54, OVER_REFUSAL 7, APPROPRIATE 106, REVIEW 0.
- 출력 상태: `OUTPUT_TRUNCATED` 0건, `OUTPUT_TRUNCATED_SUSPECTED` 2건 (`PIPE-BILLING-0138`, `PIPE-TERMINATE-0095`).

- 복합 질문은 답변 전체 기준으로 충실도를 정리했습니다. 각 하위 질문의 근거는 원본 사람 검토 목록에서 확인할 수 있습니다.
- APPROPRIATE와 MISSED가 동시에 나올 수 있습니다. 검색 근거가 없어 안전하게 거절했지만 FAQ를 검색하지 못해 사용자 질문은 해결되지 않은 경우입니다.

`PIPE-BILLING-0138`의 V6 캡처에는 최종 답변만 저장되어 있습니다. 해당 건은 실행 `COMPLETED`, RAG 호출 `SUCCESS`, 저장된 답변의 `answerBasis=GROUNDED`였고 실행 로그에서 AnswerGuard 경고는 찾지 못했습니다. 다만 원본 LLM 출력, 종료 사유, Guard의 입력·출력 비교가 없어 모델이 처음부터 `반면에`로 시작했는지 Guard가 앞 문장을 제거했는지는 판별할 수 없습니다. 따라서 잘림은 확정하지 않고 `OUTPUT_TRUNCATED_SUSPECTED`로 별도 표시합니다.

## 302건 개별 판정

## 재확인 반영, 2026-10-06

기존 40건 사람 판정과의 차이, 실제 답변, 전달된 FAQ를 다시 대조해 아래 12건의 사람 판정을 확정했습니다. 모델 다수결은 사용하지 않았습니다. `PIPE-ROAMING-0032`는 잘못된 매장 확인 질문으로 `MISROUTED_CLARIFICATION`에 해당하며, 답변 보류 자체는 아니므로 보류 축을 `NOT_APPLICABLE`로 기록했습니다.

| ID | 재확인 판정, 근거성 / 충실도 / 보류 |
| --- | --- |
| `PIPE-PLAN-0081` | `SUPPORTED / COMPLETE / NOT_APPLICABLE` |
| `PIPE-DEVICE-0008` | `SUPPORTED / COMPLETE / NOT_APPLICABLE` |
| `PIPE-DEVICE-0117` | `SUPPORTED / COMPLETE / NOT_APPLICABLE` |
| `PIPE-SUBSCRIBE-0066` | `UNSUPPORTED / MISSED / SHOULD_ABSTAIN` |
| `PIPE-SUBSCRIBE-0088` | `SUPPORTED / COMPLETE / NOT_APPLICABLE` |
| `PIPE-ROAMING-0032` | `NOT_APPLICABLE / MISSED / NOT_APPLICABLE` |
| `PIPE-ROAMING-0022` | `SUPPORTED / PARTIAL / NOT_APPLICABLE` |
| `PIPE-SERVICE-0044` | `UNSUPPORTED / PARTIAL / SHOULD_ABSTAIN` |
| `PIPE-COMPOUND-010` | `UNSUPPORTED / PARTIAL / SHOULD_ABSTAIN` |
| `PIPE-COMPOUND-013` | `SUPPORTED / PARTIAL / APPROPRIATE` |
| `PIPE-COMPOUND-031` | `SUPPORTED / PARTIAL / APPROPRIATE` |
| `REG-NO_EVIDENCE` | `UNSUPPORTED / MISSED / SHOULD_ABSTAIN` |

| # | 답변 ID (turn) | 근거성 | 충실도 | 보류 | 판정 근거 |
|---:|---|---|---|---|---|
| 1 | PIPE-BILLING-0020 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 2 | PIPE-BILLING-0021 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 3 | PIPE-BILLING-0052 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 4 | PIPE-BILLING-0054 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 5 | PIPE-BILLING-0065 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 6 | PIPE-BILLING-0069 (turn 0) | NOT_APPLICABLE | MISSED | OVER_REFUSAL | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 7 | PIPE-BILLING-0072 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 8 | PIPE-BILLING-0074 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 9 | PIPE-BILLING-0087 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 10 | PIPE-BILLING-0092 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 11 | PIPE-BILLING-0097 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 12 | PIPE-BILLING-0100 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 13 | PIPE-BILLING-0102 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 14 | PIPE-BILLING-0113 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 15 | PIPE-BILLING-0115 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 16 | PIPE-BILLING-0117 (turn 0) | SUPPORTED | PARTIAL | OVER_REFUSAL | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 17 | PIPE-BILLING-0123 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 18 | PIPE-BILLING-0129 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 19 | PIPE-BILLING-0135 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 20 | PIPE-BILLING-0138 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 남은 문장의 가상계좌 매회 입금 설명은 FAQ가 뒷받침함. 답변이 `반면에`로 시작해 앞선 자동이체 설명이 잘렸을 정황이 있으나, 종료 기록이 없어 잘림은 확정하지 않고 `OUTPUT_TRUNCATED_SUSPECTED`로 별도 표시함. |
| 21 | PIPE-BILLING-0146 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 22 | PIPE-BILLING-0148 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 23 | PIPE-BILLING-0154 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 24 | PIPE-BILLING-0155 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 25 | PIPE-COMPOUND-001 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 26 | PIPE-COMPOUND-002 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 27 | PIPE-COMPOUND-004 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 28 | PIPE-COMPOUND-005 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 29 | PIPE-COMPOUND-006 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 30 | PIPE-COMPOUND-007 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 31 | PIPE-COMPOUND-008 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 32 | PIPE-COMPOUND-009 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 33 | PIPE-COMPOUND-010 (turn 0) |UNSUPPORTED|PARTIAL|SHOULD_ABSTAIN| 청소년·시니어 요금제는 답했지만 전달된 FAQ에 없는 유심 재발급 서류를 만들어 답함. |
| 34 | PIPE-COMPOUND-012 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 35 | PIPE-COMPOUND-013 (turn 0) |SUPPORTED|PARTIAL|APPROPRIATE| 기기변경과 할부금은 FAQ로 뒷받침됨. 자녀 명의 이전의 준비물은 근거가 없어 적절히 보류함. |
| 36 | PIPE-COMPOUND-014 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 37 | PIPE-COMPOUND-015 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 38 | PIPE-COMPOUND-016 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 39 | PIPE-COMPOUND-017 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 40 | PIPE-COMPOUND-018 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 41 | PIPE-COMPOUND-019 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 42 | PIPE-COMPOUND-020 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. |
| 43 | PIPE-COMPOUND-021 (turn 0) | SUPPORTED | PARTIAL | APPROPRIATE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 44 | PIPE-COMPOUND-022 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 45 | PIPE-COMPOUND-023 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 46 | PIPE-COMPOUND-024 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 47 | PIPE-COMPOUND-025 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 48 | PIPE-COMPOUND-026 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 49 | PIPE-COMPOUND-027 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 50 | PIPE-COMPOUND-029 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 51 | PIPE-COMPOUND-030 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 52 | PIPE-COMPOUND-031 (turn 0) |SUPPORTED|PARTIAL|APPROPRIATE| 해외 유심 분실 절차는 FAQ로 뒷받침됨. 가족 데이터 공유 절차는 근거가 없어 적절히 보류함. |
| 53 | PIPE-COMPOUND-032 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 54 | PIPE-COMPOUND-033 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 55 | PIPE-COMPOUND-034 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 56 | PIPE-COMPOUND-035 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 57 | PIPE-COMPOUND-036 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 58 | PIPE-COMPOUND-037 (turn 0) | NOT_APPLICABLE | COMPLETE | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심은 답함. |
| 59 | PIPE-COMPOUND-038 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 60 | PIPE-COMPOUND-039 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 61 | PIPE-COMPOUND-040 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 62 | PIPE-COMPOUND-041 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 63 | PIPE-COMPOUND-042 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 64 | PIPE-COMPOUND-043 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 65 | PIPE-COMPOUND-045 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 66 | PIPE-COMPOUND-047 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 67 | PIPE-COMPOUND-048 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 68 | PIPE-COMPOUND-049 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 69 | PIPE-COMPOUND-050 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 70 | PIPE-DEVICE-0004 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 71 | PIPE-DEVICE-0008 (turn 0) |SUPPORTED|COMPLETE|NOT_APPLICABLE| 대체 FAQ가 침수 단말의 서비스센터 수리와 교환 제한을 모두 뒷받침함. |
| 72 | PIPE-DEVICE-0012 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 73 | PIPE-DEVICE-0024 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | FAQ가 개통 후 14일 이내 미사용 상태의 교환을 안내하고, 답변도 이 조건을 명시함. 질문 핵심은 답함. |
| 74 | PIPE-DEVICE-0033 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 75 | PIPE-DEVICE-0034 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 76 | PIPE-DEVICE-0040 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 77 | PIPE-DEVICE-0048 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ의 교환 조건은 미사용 상태인데 답변은 사용 흔적이 거의 없는 상태로 조건을 완화함. 침수 시 수리 안내는 FAQ와 일치하지만, 완화된 교환 조건을 단정하지 말았어야 함. |
| 78 | PIPE-DEVICE-0054 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 79 | PIPE-DEVICE-0062 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거만으로는 이 사용자가 6개월 이내 조건에 해당한다고 확인할 수 없음. |
| 80 | PIPE-DEVICE-0063 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 81 | PIPE-DEVICE-0070 (turn 0) | SUPPORTED | MISSED | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 82 | PIPE-DEVICE-0072 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 83 | PIPE-DEVICE-0076 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 84 | PIPE-DEVICE-0080 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 85 | PIPE-DEVICE-0084 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 86 | PIPE-DEVICE-0089 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 87 | PIPE-DEVICE-0095 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 88 | PIPE-DEVICE-0098 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 89 | PIPE-DEVICE-0099 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 90 | PIPE-DEVICE-0102 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 91 | PIPE-DEVICE-0105 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 92 | PIPE-DEVICE-0116 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 93 | PIPE-DEVICE-0117 (turn 0) |SUPPORTED|COMPLETE|NOT_APPLICABLE| FAQ가 동일한 5.9% 수수료율과 30개월의 총 수수료 증가를 뒷받침함. 정확한 금액은 구매가에 따라 달라진다는 설명은 근거와 모순되지 않음. |
| 94 | PIPE-DEVICE-0129 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. |
| 95 | PIPE-NAME_CHANGE-0015 (turn 0) | SUPPORTED | PARTIAL | APPROPRIATE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 96 | PIPE-NAME_CHANGE-0020 (turn 0) | SUPPORTED | MISSED | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 97 | PIPE-NAME_CHANGE-0024 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 98 | PIPE-NAME_CHANGE-0025 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 99 | PIPE-NAME_CHANGE-0027 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 100 | PIPE-NAME_CHANGE-0030 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 101 | PIPE-NAME_CHANGE-0036 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 102 | PIPE-NAME_CHANGE-0044 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 103 | PIPE-NAME_CHANGE-0049 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 104 | PIPE-NAME_CHANGE-0058 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 105 | PIPE-NAME_CHANGE-0061 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 106 | PIPE-NAME_CHANGE-0068 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 107 | PIPE-NAME_CHANGE-0070 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 108 | PIPE-NAME_CHANGE-0075 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE |  답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 109 | PIPE-NAME_CHANGE-0076 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 110 | PIPE-NAME_CHANGE-0083 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 111 | PIPE-NAME_CHANGE-0088 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 112 | PIPE-OUT-001 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 113 | PIPE-OUT-002 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 114 | PIPE-OUT-003 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 115 | PIPE-OUT-004 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 116 | PIPE-OUT-006 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 117 | PIPE-OUT-007 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 118 | PIPE-OUT-008 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 119 | PIPE-OUT-009 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 120 | PIPE-OUT-012 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 121 | PIPE-OUT-013 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 122 | PIPE-OUT-014 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 123 | PIPE-OUT-015 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 124 | PIPE-OUT-016 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 125 | PIPE-OUT-017 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 126 | PIPE-OUT-018 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 127 | PIPE-OUT-019 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 128 | PIPE-OUT-020 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 129 | PIPE-OUT-021 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 130 | PIPE-OUT-022 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 131 | PIPE-OUT-023 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 132 | PIPE-OUT-025 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 133 | PIPE-OUT-026 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 134 | PIPE-OUT-027 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 135 | PIPE-OUT-028 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 136 | PIPE-OUT-029 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 137 | PIPE-OUT-030 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 138 | PIPE-OUT-031 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 139 | PIPE-OUT-032 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 140 | PIPE-OUT-033 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 141 | PIPE-OUT-035 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 142 | PIPE-OUT-036 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 143 | PIPE-OUT-037 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 144 | PIPE-OUT-038 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 145 | PIPE-OUT-039 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 146 | PIPE-OUT-040 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 147 | PIPE-OUT-041 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 148 | PIPE-OUT-042 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 149 | PIPE-OUT-043 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 150 | PIPE-OUT-044 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 151 | PIPE-OUT-045 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 152 | PIPE-OUT-046 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 153 | PIPE-OUT-047 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 154 | PIPE-OUT-048 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 155 | PIPE-OUT-049 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 156 | PIPE-OUT-050 (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
| 157 | PIPE-PLAN-0001 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 158 | PIPE-PLAN-0003 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 159 | PIPE-PLAN-0017 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 160 | PIPE-PLAN-0022 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 161 | PIPE-PLAN-0036 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 162 | PIPE-PLAN-0038 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 163 | PIPE-PLAN-0047 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 164 | PIPE-PLAN-0050 (turn 0) | NOT_APPLICABLE | MISSED | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 165 | PIPE-PLAN-0058 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 166 | PIPE-PLAN-0060 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 167 | PIPE-PLAN-0066 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 168 | PIPE-PLAN-0069 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 169 | PIPE-PLAN-0072 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 170 | PIPE-PLAN-0081 (turn 0) |SUPPORTED|COMPLETE|NOT_APPLICABLE| FAQ가 저사용 데이터 요금제로 제시한 세이브와 미니를 추천해 핵심 질문에 답함. 구체적인 가격 질문은 아님. |
| 171 | PIPE-PLAN-0092 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 172 | PIPE-PLAN-0103 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 173 | PIPE-PLAN-0108 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 174 | PIPE-PLAN-0115 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 175 | PIPE-PLAN-0121 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 176 | PIPE-PLAN-0127 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 177 | PIPE-PLAN-0136 (turn 0) | SUPPORTED | PARTIAL | APPROPRIATE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 178 | PIPE-PLAN-0143 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 179 | PIPE-PORTING-0001 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 180 | PIPE-PORTING-0015 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 181 | PIPE-PORTING-0023 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 182 | PIPE-PORTING-0029 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 183 | PIPE-PORTING-0032 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 184 | PIPE-PORTING-0033 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 185 | PIPE-PORTING-0044 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 186 | PIPE-PORTING-0048 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 187 | PIPE-PORTING-0049 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 188 | PIPE-PORTING-0052 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 189 | PIPE-PORTING-0053 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 190 | PIPE-PORTING-0060 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 191 | PIPE-PORTING-0065 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 192 | PIPE-PORTING-0071 (turn 0) | NOT_APPLICABLE | MISSED | OVER_REFUSAL | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 193 | PIPE-PORTING-0077 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 답변은 거절했고 검색된 로밍 FAQ는 번호이동 질문에 답하지 않음. |
| 194 | PIPE-PORTING-0078 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 195 | PIPE-PORTING-0081 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 196 | PIPE-PORTING-0085 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 197 | PIPE-PORTING-0087 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 198 | PIPE-PORTING-0092 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 199 | PIPE-PORTING-0097 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 200 | PIPE-PORTING-0107 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 201 | PIPE-PORTING-0113 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 202 | PIPE-ROAMING-0022 (turn 0) |SUPPORTED|PARTIAL|NOT_APPLICABLE| 신청 시기와 적용 시간은 답했지만 홈페이지나 고객센터라는 신청 경로를 빠뜨림. |
| 203 | PIPE-ROAMING-0025 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 204 | PIPE-ROAMING-0027 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 205 | PIPE-ROAMING-0030 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | FAQ가 답변의 문제 해결 절차를 뒷받침함. |
| 206 | PIPE-ROAMING-0031 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 207 | PIPE-ROAMING-0032 (turn 0) |NOT_APPLICABLE|MISSED|NOT_APPLICABLE| 로밍 신청 경로 질문에 매장 위치를 되물은 잘못된 확인 질문임. 답변 보류가 아니므로 보류 축은 NOT_APPLICABLE. |
| 208 | PIPE-ROAMING-0034 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 209 | PIPE-ROAMING-0046 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 210 | PIPE-ROAMING-0053 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 211 | PIPE-ROAMING-0062 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 212 | PIPE-ROAMING-0064 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 213 | PIPE-ROAMING-0071 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 214 | PIPE-ROAMING-0074 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 215 | PIPE-ROAMING-0077 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 216 | PIPE-SERVICE-0003 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 217 | PIPE-SERVICE-0008 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 218 | PIPE-SERVICE-0013 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 219 | PIPE-SERVICE-0014 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 220 | PIPE-SERVICE-0016 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 221 | PIPE-SERVICE-0019 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 222 | PIPE-SERVICE-0020 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 223 | PIPE-SERVICE-0026 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 224 | PIPE-SERVICE-0033 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 225 | PIPE-SERVICE-0038 (turn 0) | NOT_APPLICABLE | MISSED | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 226 | PIPE-SERVICE-0041 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 227 | PIPE-SERVICE-0043 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 228 | PIPE-SERVICE-0044 (turn 0) |UNSUPPORTED|PARTIAL|SHOULD_ABSTAIN| 3영업일 기준은 맞지만 FAQ의 ‘기간 안이면 기다림’을 ‘기간이 지난 뒤 기다림’으로 조건을 뒤집음. |
| 229 | PIPE-SERVICE-0046 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 230 | PIPE-SERVICE-0049 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | FAQ says the charge is cancelled if fraud is confirmed; the investigation is still pending. |
| 231 | PIPE-SERVICE-0051 (turn 0) | UNSUPPORTED | MISSED | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 232 | PIPE-SERVICE-0055 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 233 | PIPE-SERVICE-0056 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 234 | PIPE-SERVICE-0063 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 235 | PIPE-SERVICE-0065 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 236 | PIPE-SUBSCRIBE-0004 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 237 | PIPE-SUBSCRIBE-0005 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 238 | PIPE-SUBSCRIBE-0012 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 239 | PIPE-SUBSCRIBE-0015 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 240 | PIPE-SUBSCRIBE-0019 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 241 | PIPE-SUBSCRIBE-0022 (turn 0) | SUPPORTED | COMPLETE | OVER_REFUSAL | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 242 | PIPE-SUBSCRIBE-0029 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 243 | PIPE-SUBSCRIBE-0036 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 244 | PIPE-SUBSCRIBE-0049 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 245 | PIPE-SUBSCRIBE-0051 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 246 | PIPE-SUBSCRIBE-0056 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 247 | PIPE-SUBSCRIBE-0066 (turn 0) |UNSUPPORTED|MISSED|SHOULD_ABSTAIN| FAQ는 만 14세 이상 18세 이하에 법정대리인 서류가 필요하다고 함. 답변이 18세 생일 이후 면제라고 조건을 뒤집음. |
| 248 | PIPE-SUBSCRIBE-0077 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 249 | PIPE-SUBSCRIBE-0079 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 250 | PIPE-SUBSCRIBE-0081 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 251 | PIPE-SUBSCRIBE-0084 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 252 | PIPE-SUBSCRIBE-0088 (turn 0) |SUPPORTED|COMPLETE|NOT_APPLICABLE| 외국인 회선 한도 질문에 연결된 FAQ가 성인 1인당 최대 5회선이라는 기준을 뒷받침함. |
| 253 | PIPE-SUBSCRIBE-0094 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 254 | PIPE-SUBSCRIBE-0099 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 255 | PIPE-SUBSCRIBE-0101 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 256 | PIPE-SUBSCRIBE-0105 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 257 | PIPE-SUBSCRIBE-0112 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 258 | PIPE-SUBSCRIBE-0123 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 259 | PIPE-SUBSCRIBE-0127 (turn 0) | NOT_APPLICABLE | PARTIAL | NOT_APPLICABLE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 일부만 답했거나 필요한 정보가 빠짐. |
| 260 | PIPE-TERMINATE-0004 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 261 | PIPE-TERMINATE-0010 (turn 0) | NOT_APPLICABLE | MISSED | OVER_REFUSAL | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 262 | PIPE-TERMINATE-0013 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | FAQ는 즉시 해지와 일할 요금 계산을 뒷받침하지만 전체 절차는 설명하지 않음. |
| 263 | PIPE-TERMINATE-0029 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 264 | PIPE-TERMINATE-0034 (turn 0) | SUPPORTED | MISSED | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 265 | PIPE-TERMINATE-0043 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 266 | PIPE-TERMINATE-0049 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 267 | PIPE-TERMINATE-0053 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 268 | PIPE-TERMINATE-0069 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 269 | PIPE-TERMINATE-0071 (turn 0) | NOT_APPLICABLE | MISSED | OVER_REFUSAL | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 270 | PIPE-TERMINATE-0073 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 271 | PIPE-TERMINATE-0085 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 272 | PIPE-TERMINATE-0090 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 273 | PIPE-TERMINATE-0092 (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 274 | PIPE-TERMINATE-0095 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | FAQ는 할부 납부를 기존 일정대로 나눠 낼 수 있다고 뒷받침하지만 일시 납부와의 장단점 비교는 충분하지 않음. 답변이 `반면에`로 시작해 앞선 일시 납부 설명이 잘렸을 정황이 있으나 종료 기록이 없어 확정하지 않고 `OUTPUT_TRUNCATED_SUSPECTED`로 별도 표시함. |
| 275 | PIPE-TERMINATE-0096 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 276 | PIPE-TERMINATE-0104 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 277 | PIPE-TERMINATE-0105 (turn 0) | NOT_APPLICABLE | MISSED | OVER_REFUSAL | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 278 | PIPE-TERMINATE-0113 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 279 | PIPE-TERMINATE-0115 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 280 | PIPE-USIM-0007 (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 281 | PIPE-USIM-0012 (turn 0) | SUPPORTED | MISSED | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 282 | PIPE-USIM-0027 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 283 | PIPE-USIM-0030 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 284 | PIPE-USIM-0036 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 285 | PIPE-USIM-0041 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 286 | PIPE-USIM-0049 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 287 | PIPE-USIM-0054 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 288 | PIPE-USIM-0060 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 289 | PIPE-USIM-0067 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 290 | PIPE-USIM-0075 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 291 | PIPE-USIM-0077 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 292 | PIPE-USIM-0083 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 293 | PIPE-USIM-0086 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 294 | PIPE-USIM-0095 (turn 0) | SUPPORTED | COMPLETE | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 질문 핵심은 답함. |
| 295 | PIPE-USIM-0097 (turn 0) | SUPPORTED | PARTIAL | NOT_APPLICABLE | 답변의 핵심 사실을 FAQ 답변이 뒷받침함. 일부만 답했거나 필요한 정보가 빠짐. |
| 296 | PIPE-USIM-0099 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 297 | PIPE-USIM-0104 (turn 0) | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 질문 핵심은 답함. |
| 298 | REG-FAQ_COMPOUND (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 299 | REG-MIXED (turn 0) | UNSUPPORTED | PARTIAL | SHOULD_ABSTAIN | FAQ 근거에 없는 사실이나 조건·대상 변경을 답변에 단정함. 일부만 답했거나 필요한 정보가 빠짐. |
| 300 | REG-NO_EVIDENCE (turn 0) |UNSUPPORTED|MISSED|SHOULD_ABSTAIN| 질문은 택배비 별도 여부지만 FAQ는 배송 기간과 유심 비용만 제시함. 7,700원만으로 배송비 질문에 답하지 못함. |
| 301 | REG-STORE (turn 0) | NOT_APPLICABLE | MISSED | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 질문 핵심에 답하지 못했거나 잘못 답함. |
| 302 | REG-UNRELATED (turn 0) | NOT_APPLICABLE | NOT_APPLICABLE | APPROPRIATE | 사실 주장이 없는 거절 또는 확인 질문이라 근거성 비적용. 범위 밖 질문이라 충실도 비적용. |
## 참고

- 이 문서는 302건의 사람 판정 기록이며, 모델과 독립된 별도 사람의 골드 라벨입니다.
