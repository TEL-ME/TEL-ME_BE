# V6 별도 40건 사람 판정표

이 40건은 앞서 직접 판정한 39건과 겹치지 않는 고정 평가 질문입니다. 실제 사용자의 무작위 질문 표본은 아닙니다. 이 문서에는 Judge의 판정과 변경 여부를 넣지 않았습니다.

각 사례에서 **실제 저장 답변**을 **답변 생성에 전달된 실제 근거**와 비교해 아래 판정 칸을 채워주세요. 참고 FAQ 기준은 질문 충족도를 확인하는 용도이며, 실제 답변에 전달된 근거로 간주하지 않습니다.

- 근거성: 답변 속 사실 주장이 모두 실제 근거에 있으면 `SUPPORTED`, 하나라도 근거 밖이면 `UNSUPPORTED`, 사실 주장이 없으면 `NOT_APPLICABLE`.
- 질문 충족도: 하위 질문마다 `COMPLETE`, `PARTIAL`, `MISSED`, `NOT_APPLICABLE` 중 하나.
- 답변 불가: 근거 부족 시 적절히 보류했으면 `APPROPRIATE`, 근거 밖 사실을 단정했으면 `SHOULD_ABSTAIN`, 답할 근거가 있는데 거절했으면 `OVER_REFUSAL`, 정상 답변이면 `NOT_APPLICABLE`.
- 실제 근거 충분성: 실제 근거만으로 질문에 답할 수 있으면 `ENOUGH`, 아니면 `INSUFFICIENT`.
- 별도 파이프라인 실패 유형은 답변의 사실성·질문 충족도와 별도로 기록합니다. 질문 의도와 다른 유형으로 잘못 분류해 엉뚱한 확인 질문을 했다면 `MISROUTED_CLARIFICATION`, 다른 파이프라인 오류면 `OTHER`, 해당 오류가 없으면 `NONE`을 적습니다. 애매하면 `NEEDS_HUMAN_REVIEW`로 둡니다.
- 애매하면 억지로 고르지 말고 `NEEDS_HUMAN_REVIEW`와 이유를 메모에 적어주세요. 사람 판정을 다 기록한 뒤 Judge 결과와 대조합니다.

각 `사람 판정 기록`의 빈 줄을 아래처럼 바꿔 적어주세요. 하위 질문이 둘이면 `기준 1`, `기준 2`를 각각 채웁니다.

```text
- 근거성 전체 판정: `SUPPORTED`
- 주장별 판정과 근거 ID: 답변의 어떤 주장을 어떤 FAQ가 뒷받침하는지 간단히 기록
- 질문 충족도:
  - 기준 1: `COMPLETE`
- 답변 불가 판정: `NOT_APPLICABLE`
- 실제 전달 근거 충분성: `ENOUGH`
- 별도 파이프라인 실패 유형: `NONE`
- 메모: 판단 이유나 애매한 부분
```

## 질문 찾아가기

| 번호 | 질문 |
| --- | --- |
| [V6H-001](#v6h-001) | 청구서를 못 받았다는 이유로 정지가 유예되나요? |
| [V6H-002](#v6h-002) | 모바일 알림 청구서로 바꾸면 요금이 붙나요? |
| [V6H-003](#v6h-003) | 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요 |
| [V6H-004](#v6h-004) | 요금제 뭐가 있는지 어떻게 알아요 |
| [V6H-005](#v6h-005) | 데이터 조금 쓰는데 싼 거 있나요 |
| [V6H-006](#v6h-006) | 5G 요금제 이름 좀 알려줘요 |
| [V6H-007](#v6h-007) | 침수된 폰은 교환 대상이 돼요? |
| [V6H-008](#v6h-008) | 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요 |
| [V6H-009](#v6h-009) | 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요? |
| [V6H-010](#v6h-010) | 자녀 회선 온라인 신청 후 당일 개통이 가능한가요? |
| [V6H-011](#v6h-011) | 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요? |
| [V6H-012](#v6h-012) | 외국인인데 회선을 추가하려니 한도라고 해요 |
| [V6H-013](#v6h-013) | 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다 |
| [V6H-014](#v6h-014) | 신청한 지 하루가 넘었는데 아직도 안 됐어요 |
| [V6H-015](#v6h-015) | 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요. |
| [V6H-016](#v6h-016) | 약정 중간에 해지하면 위약금이 얼마나 나오나요? |
| [V6H-017](#v6h-017) | 폰 잃어버려서 해지하는데 할부는 어떻게 해요 |
| [V6H-018](#v6h-018) | 분실한 단말의 회선을 해지하는 절차를 알려주세요. |
| [V6H-019](#v6h-019) | 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요? |
| [V6H-020](#v6h-020) | 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요? |
| [V6H-021](#v6h-021) | 외국 가기 전에 유심 바로 받을 수 있나요 |
| [V6H-022](#v6h-022) | 명의 바꾸는 데 오래 걸려요? |
| [V6H-023](#v6h-023) | 명의 바꾸는 데 돈 들어요 |
| [V6H-024](#v6h-024) | 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요? |
| [V6H-025](#v6h-025) | 로밍 신청하고 얼마나 기다려야 돼요? |
| [V6H-026](#v6h-026) | 로밍 신청은 어디서 해요 |
| [V6H-027](#v6h-027) | 로밍은 어떻게 신청해요? |
| [V6H-028](#v6h-028) | 신고하면 요금을 다 돌려받을 수 있나요? |
| [V6H-029](#v6h-029) | 신고했는데 며칠째 연락이 없어요 |
| [V6H-030](#v6h-030) | 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다 |
| [V6H-031](#v6h-031) | 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요. |
| [V6H-032](#v6h-032) | 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요? |
| [V6H-033](#v6h-033) | 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요. |
| [V6H-034](#v6h-034) | 체스에서 퀸을 잘 활용하는 방법은? |
| [V6H-035](#v6h-035) | 해외여행 여권 유효기간은 얼마나 남아야 해? |
| [V6H-036](#v6h-036) | 반려견 목욕 주기는 어느 정도가 좋아요? |
| [V6H-037](#v6h-037) | 가상계좌가 무엇인가요? |
| [V6H-038](#v6h-038) | 유심 재발급하면 택배비는 따로 내나요? |
| [V6H-039](#v6h-039) | 유심 재발급 비용은 얼마고 배송비도 내야 하나요? |
| [V6H-040](#v6h-040) | 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요? |

---

## V6H-001

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 청구서를 못 받았다는 이유로 정지가 유예되나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 청구서를 못 받았다는 이유로 정지가 유예되나요?
  - FAQ 답변: 정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.
- 동등 정답 FAQ 묶음: `[["BILLING-0087"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 청구서를 못 받으셨더라도 정지는 납부 기한이 지나고 15일 후에 발신 정지, 30일 후에 수신 정지가 적용됩니다. 따라서 청구서 수령 여부와 직접적인 유예 관계는 없습니다. 기한 내에 요금을 납부하시면 정지를 방지할 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `BILLING-0087`
  - FAQ 질문: 청구서를 못 받았다는 이유로 정지가 유예되나요?
  - FAQ 답변: 정지는 납부 기한 기준으로 15일 후 발신 정지, 30일 후 수신 정지가 적용됩니다.
- 근거 ID: `BILLING-0059`
  - FAQ 질문: 청구서 못 받았는데 정지 안 되게 하려면 어떻게 해요
  - FAQ 답변: 청구서를 못 받으셨어도 기한이 지나고 15일이면 전화가 안 걸리니, 기한 안에 내시면 됩니다. 이미 끊겼으면 다 내시면 2시간 안에 됩니다.
- 근거 ID: `BILLING-0115`
  - FAQ 질문: 청구서를 못 받았는데 발신이 정지됐습니다
  - FAQ 답변: 정지는 납부 기한 후 15일 기준입니다. 완납하시면 2시간 이내에 재개됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `1093`, `1070`, `1115`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-002

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 모바일 알림 청구서로 바꾸면 요금이 붙나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 모바일 알림 청구서로 바꾸면 요금이 붙나요?
  - FAQ 답변: 아니요, 모바일과 이메일은 무료입니다. 우편만 월 500원입니다.
- 동등 정답 FAQ 묶음: `[["BILLING-0084"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 모바일 알림 청구서로 변경하실 경우 추가 요금은 발생하지 않습니다. 이메일이나 모바일 알림 모두 무료로 제공됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `BILLING-0084`
  - FAQ 질문: 모바일 알림 청구서로 바꾸면 요금이 붙나요?
  - FAQ 답변: 아니요, 모바일과 이메일은 무료입니다. 우편만 월 500원입니다.
- 근거 ID: `BILLING-0035`
  - FAQ 질문: 청구서 받는 방법을 바꾸려면 어떻게 해야 하나요?
  - FAQ 답변: 이메일, 모바일, 우편 중 원하시는 방법으로 변경 신청하시면 됩니다. 변경은 다음 달 청구분부터 적용되며, 우편은 월 500원의 비용이 있습니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `1090`, `10`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-003

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요

### 참고 FAQ 기준
- 하위 질문 기준 1: 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요
  - FAQ 답변: 똑같이 다음 날 00:00부터 됩니다. 한 달에 한 번입니다.
- 동등 정답 FAQ 묶음: `[["BILLING-0155"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 청구서 발행 전이든 후든 요금제 변경은 다음 날 00:00부터 적용됩니다. 변경 후에는 해당 달의 사용량에 따라 계산됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `BILLING-0155`
  - FAQ 질문: 청구서 나오기 전이랑 후랑 요금제 바꾸는 게 달라요
  - FAQ 답변: 똑같이 다음 날 00:00부터 됩니다. 한 달에 한 번입니다.
- 근거 ID: `BILLING-0127`
  - FAQ 질문: 청구서 보니까 요금제가 이상해요
  - FAQ 답변: 요금제를 바꾸셨으면 다음 날 00:00부터 바뀌고 그 달은 날짜대로 나눠서 계산됩니다.
- 근거 ID: `BILLING-0099`
  - FAQ 질문: 청구서 나온 뒤에 요금제 바꿔도 되나요
  - FAQ 답변: 네, 바꾸면 다음 날 00:00부터 적용됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `1147`, `1125`, `1103`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-004

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 요금제 뭐가 있는지 어떻게 알아요

### 참고 FAQ 기준
- 하위 질문 기준 1: 요금제 뭐가 있는지 어떻게 알아요
  - FAQ 답변: 9가지 있습니다. 15,000원부터 105,000원까지 있어요.
- 동등 정답 FAQ 묶음: `[["PLAN-0060"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-005

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 데이터 조금 쓰는데 싼 거 있나요

### 참고 FAQ 기준
- 하위 질문 기준 1: 데이터 조금 쓰는데 싼 거 있나요
  - FAQ 답변: 알뜰 요금제가 2가지 있습니다. 세이브랑 미니예요.
- 동등 정답 FAQ 묶음: `[["PLAN-0081"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 데이터를 조금씩 사용하시는 경우 알뜰 요금제 중 세이브나 미니 요금제를 고려해 보시는 것이 좋습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `PLAN-0081`
  - FAQ 질문: 데이터 조금 쓰는데 싼 거 있나요
  - FAQ 답변: 알뜰 요금제가 2가지 있습니다. 세이브랑 미니예요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `967`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-006

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 5G 요금제 이름 좀 알려줘요

### 참고 FAQ 기준
- 하위 질문 기준 1: 5G 요금제 이름 좀 알려줘요
  - FAQ 답변: 라이트, 스탠다드, 프리미엄, 언리미티드 4가지입니다.
- 동등 정답 FAQ 묶음: `[["PLAN-0116"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 5G 요금제 이름은 라이트, 스탠다드, 프리미엄, 언리미티드 네 가지입니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `PLAN-0116`
  - FAQ 질문: 5G 요금제 이름 좀 알려줘요
  - FAQ 답변: 라이트, 스탠다드, 프리미엄, 언리미티드 4가지입니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `994`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-007

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 침수된 폰은 교환 대상이 돼요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 침수된 폰은 교환 대상이 돼요?
  - FAQ 답변: 반품·교환은 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 침수 단말은 수리 대상입니다.
- 동등 정답 FAQ 묶음: `[["DEVICE-0008"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 침수된 폰은 교환 대상이 아닙니다. 침수된 단말은 수리 대상으로, 제조사의 서비스센터에서 점검 및 수리를 받으셔야 합니다. 교환은 미개봉 상태이거나 사용 흔적이 전혀 없는 경우에만 가능합니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `DEVICE-0008`
  - FAQ 질문: 침수된 폰은 교환 대상이 돼요?
  - FAQ 답변: 반품·교환은 개통 후 14일 이내에 미개봉이거나 사용 흔적이 없는 상태에서만 1회 가능합니다. 침수 단말은 수리 대상입니다.
- 근거 ID: `DEVICE-0088`
  - FAQ 질문: 새 폰인데 물에 빠져서 교환하려니 안 된대요
  - FAQ 답변: 교환은 미개봉이거나 사용 흔적이 없어야 합니다. 침수 단말은 제조사 서비스센터 수리 대상입니다.
- 근거 ID: `DEVICE-0068`
  - FAQ 질문: 침수된 단말은 14일 이내라도 교환이 불가능한가요?
  - FAQ 답변: 교환은 미개봉 또는 사용 흔적이 없는 상태여야 합니다. 침수 흔적이 있으면 교환 대신 수리로 진행됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `808`, `870`, `854`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-008

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요

### 참고 FAQ 기준
- 하위 질문 기준 1: 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요
  - FAQ 답변: 할부는 다 내거나 새 할부로 넘기면 됩니다. 그리고 개통 6개월은 지나야 바꿀 수 있습니다.
- 동등 정답 FAQ 묶음: `[["DEVICE-0046", "DEVICE-0130"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 할부를 모두 갚거나 새 할부 계약으로 전환해야 기기 변경이 가능합니다. 개통 후 6개월이 경과해야 기기 변경을 할 수 있다는 점도 참고해 주세요.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `DEVICE-0130`
  - FAQ 질문: 기기 바꾸는 거랑 할부 갚는 거랑 뭐가 먼저예요
  - FAQ 답변: 할부는 다 내거나 새 할부로 넘기면 됩니다. 그리고 개통 6개월은 지나야 바꿀 수 있습니다.
- 근거 ID: `DEVICE-0114`
  - FAQ 질문: 배터리 교체랑 기기변경 중 뭐가 나아요?
  - FAQ 답변: 기기변경은 개통 후 6개월이 지나야 하고 기존 할부금을 완납하거나 승계해야 합니다. 조건이 안 되면 수리가 현실적입니다.
- 근거 ID: `DEVICE-0070`
  - FAQ 질문: 개통 6개월이 지나면 할부금 완납 없이도 기기변경이 되나요?
  - FAQ 답변: 기존 할부금을 완납하거나 새 할부에 승계해야 합니다. 둘 중 하나는 필요합니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `902`, `890`, `856`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-009

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?
  - FAQ 답변: 수수료율은 둘 다 연 5.9%로 같지만, 30개월이 기간이 길어 총 수수료가 더 많습니다.
- 동등 정답 FAQ 묶음: `[["DEVICE-0109", "DEVICE-0117"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 액정 파손 후 새 단말을 구매할 때, 24개월과 30개월 할부 모두 연 수수료율은 동일하게 5.9%입니다. 하지만 할부 기간이 더 긴 30개월의 경우 총 수수료가 더 많아집니다. 구체적인 금액 차이는 구매 금액에 따라 달라지므로 정확한 수치는 제공된 정보로는 안내드릴 수 없습니다. 할부 기간 선택 시 총 비용을 고려하시는 것이 좋습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `DEVICE-0117`
  - FAQ 질문: 액정 파손 후 새 단말 구매 시 24개월과 30개월의 총 수수료 차이는 어떤가요?
  - FAQ 답변: 수수료율은 둘 다 연 5.9%로 같지만, 30개월이 기간이 길어 총 수수료가 더 많습니다.
- 근거 ID: `DEVICE-0017`
  - FAQ 질문: 액정 파손 후 새 단말 할부 수수료율을 알려주세요.
  - FAQ 답변: 연 5.9%입니다. 할부 기간은 24개월 또는 30개월입니다.
- 근거 ID: `DEVICE-0097`
  - FAQ 질문: 액정 파손으로 새 폰을 샀는데 30개월 할부가 안 된다고 합니다
  - FAQ 답변: 단말 할부는 24개월 또는 30개월 중 선택할 수 있습니다. 매장에서 조건을 다시 확인해 주세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `891`, `815`, `877`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-010

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 자녀 회선 온라인 신청 후 당일 개통이 가능한가요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 자녀 회선 온라인 신청 후 당일 개통이 가능한가요?
  - FAQ 답변: 온라인 신청은 다음 날 개통됩니다. 당일 개통은 매장에서만 30분 이내에 가능합니다.
- 동등 정답 FAQ 묶음: `[["SUBSCRIBE-0067"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 온라인으로 자녀 회선을 신청하신 경우 당일 개통은 불가능하며, 개통은 신청 다음 날 이루어집니다. 당일 개통을 원하신다면 매장 방문을 고려해 주세요.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SUBSCRIBE-0067`
  - FAQ 질문: 자녀 회선 온라인 신청 후 당일 개통이 가능한가요?
  - FAQ 답변: 온라인 신청은 다음 날 개통됩니다. 당일 개통은 매장에서만 30분 이내에 가능합니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `753`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-011

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요?
  - FAQ 답변: 법정대리인 서류는 만 14세 이상 18세 이하에 필요합니다. 성인이 되면 본인 신분증만으로 가능합니다.
- 동등 정답 FAQ 묶음: `[["SUBSCRIBE-0066"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 네, 만 18세 생일이 지나면 법정대리인 서류 없이 본인 신분증만으로 개통이 가능합니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SUBSCRIBE-0066`
  - FAQ 질문: 만 18세 생일이 지나면 법정대리인 서류 없이 개통할 수 있나요?
  - FAQ 답변: 법정대리인 서류는 만 14세 이상 18세 이하에 필요합니다. 성인이 되면 본인 신분증만으로 가능합니다.
- 근거 ID: `SUBSCRIBE-0062`
  - FAQ 질문: 부모님 동의 없이 고등학생이 개통할 수 있을까요?
  - FAQ 답변: 만 14세 이상 18세 이하는 법정대리인 동의서, 가족관계증명서, 법정대리인 신분증이 있어야 가능합니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `752`, `750`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-012

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 외국인인데 회선을 추가하려니 한도라고 해요

### 참고 FAQ 기준
- 하위 질문 기준 1: 외국인인데 회선을 추가하려니 한도라고 해요
  - FAQ 답변: 성인은 1인당 최대 5회선까지입니다. 이미 5회선이면 추가 개통이 어렵습니다.
- 동등 정답 FAQ 묶음: `[["SUBSCRIBE-0088", "SUBSCRIBE-0096"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 외국인 고객님께서도 성인 기준으로 1인당 최대 5회선까지 개통이 가능합니다. 이미 5회선을 사용 중이시라면 추가 개통이 제한될 수 있습니다. 추가 개통이 필요하시다면 현재 사용 중인 회선 수를 확인해 주시기 바랍니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SUBSCRIBE-0088`
  - FAQ 질문: 외국인인데 회선을 추가하려니 한도라고 해요
  - FAQ 답변: 성인은 1인당 최대 5회선까지입니다. 이미 5회선이면 추가 개통이 어렵습니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `770`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-013

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다

### 참고 FAQ 기준
- 하위 질문 기준 1: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다
  - FAQ 답변: 평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.
- 동등 정답 FAQ 묶음: `[["PORTING-0073", "PORTING-0081"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 신청하신 작업이 평균적으로 2시간 내에 완료되지만, 최대 24시간까지 소요될 수 있습니다. 현재 시간이 점심 이후이므로, 아직 완료되지 않은 경우 정상적인 범위 내에 있습니다. 하지만 24시간이 경과해도 완료되지 않는다면 고객센터로 연락주시기 바랍니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `PORTING-0081`
  - FAQ 질문: 09:00에 신청했는데 점심이 지나도 완료가 안 됩니다
  - FAQ 답변: 평균 2시간이지만 최대 24시간까지 걸릴 수 있습니다. 24시간이 지나도 안 되면 고객센터로 문의해 주세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `593`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-014

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 신청한 지 하루가 넘었는데 아직도 안 됐어요

### 참고 FAQ 기준
- 하위 질문 기준 1: 신청한 지 하루가 넘었는데 아직도 안 됐어요
  - FAQ 답변: 번호이동은 늦어도 24시간 이내에 처리됩니다. 24시간이 지났다면 고객센터에 확인해 주세요.
- 동등 정답 FAQ 묶음: `[["PORTING-0077"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `ROAMING-0062`
  - FAQ 질문: 신청했는데 아직 안 돼요
  - FAQ 답변: 신청하면 1시간 안에 됩니다. 조금만 기다려 주세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `377`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-015

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요.

### 참고 FAQ 기준
- 하위 질문 기준 1: 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요.
  - FAQ 답변: 개통한 지 3개월이 지나야 다른 통신사로 번호이동이 가능합니다. 두 달째라면 아직 신청하실 수 없고, 3개월이 지난 뒤에 신청하셔야 합니다. 명의를 변경하신 경우에는 변경일로부터 1개월이 지나야 합니다.
- 동등 정답 FAQ 묶음: `[["PORTING-0111"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 개통하신 지 두 달째라면 아직 번호 이동 신청이 불가능합니다. 번호 이동은 개통 후 3개월이 경과해야 가능합니다. 따라서 현재로서는 이동하시기 어렵습니다. 3개월이 지나면 신청하실 수 있으니 그 때 다시 고려해 보시는 것을 추천드립니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `PORTING-0111`
  - FAQ 질문: 개통한 지 두 달 됐습니다. 지금 옮기는 게 나은가요.
  - FAQ 답변: 개통한 지 3개월이 지나야 다른 통신사로 번호이동이 가능합니다. 두 달째라면 아직 신청하실 수 없고, 3개월이 지난 뒤에 신청하셔야 합니다. 명의를 변경하신 경우에는 변경일로부터 1개월이 지나야 합니다.
- 근거 ID: `PORTING-0051`
  - FAQ 질문: 개통한 지 3개월 딱 됐는데 오늘 옮길 수 있을까요?
  - FAQ 답변: 개통 후 3개월이 지났으면 가능합니다.
- 근거 ID: `PORTING-0067`
  - FAQ 질문: 개통한 지 두 달 됐는데 옮길 수 있나요
  - FAQ 답변: 3개월이 지나야 옮기실 수 있어, 두 달째라면 아직 어렵습니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `152`, `570`, `139`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-016

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 약정 중간에 해지하면 위약금이 얼마나 나오나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 약정 중간에 해지하면 위약금이 얼마나 나오나요?
  - FAQ 답변: 약정은 24개월이며, 12개월 미만에 해지하면 할인 반환금의 100%, 12개월 이상 24개월 미만이면 50%를 반환합니다.
- 동등 정답 FAQ 묶음: `[["TERMINATE-0010", "TERMINATE-0018", "TERMINATE-0026"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `TERMINATE-0010`
  - FAQ 질문: 약정 중간에 해지하면 위약금이 얼마나 나오나요?
  - FAQ 답변: 약정은 24개월이며, 12개월 미만에 해지하면 할인 반환금의 100%, 12개월 이상 24개월 미만이면 50%를 반환합니다.
- 근거 ID: `TERMINATE-0026`
  - FAQ 질문: 위약금 계산은 어떻게 되는 거예요?
  - FAQ 답변: 24개월 약정 기준으로, 12개월이 되기 전에 해지하면 할인 반환금 100%, 12개월 이상 24개월 미만이면 50%를 내시면 됩니다.
- 근거 ID: `TERMINATE-0102`
  - FAQ 질문: 약정 중간에 해외로 나가게 됐습니다. 지금 해지하는 것과 약정을 채우는 것 중 위약금 차이가 큰가요?
  - FAQ 답변: 약정 기간은 24개월입니다. 12개월이 되기 전에 해지하시면 받으신 요금 할인액의 100%를 반환해야 하고, 12개월 이상 24개월 미만이면 50%를 반환합니다. 같은 해지라도 12개월을 넘겼는지에 따라 반환금이 절반으로 줄어듭니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `245`, `250`, `269`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-017

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 폰 잃어버려서 해지하는데 할부는 어떻게 해요

### 참고 FAQ 기준
- 하위 질문 기준 1: 폰 잃어버려서 해지하는데 할부는 어떻게 해요
  - FAQ 답변: 남은 할부를 한 번에 내시거나, 원하시면 계속 나눠 내실 수 있습니다.
- 동등 정답 FAQ 묶음: `[["TERMINATE-0043"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 폰을 잃어버리신 상황에서 해지를 진행하시더라도, 남은 할부금은 일시에 납부하시거나, 원하시는 경우 해지 후에도 기존 할부 일정을 유지하며 나눠 납부하실 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `TERMINATE-0023`
  - FAQ 질문: 폰 잃어버렸는데 남은 할부는 어떻게 해요
  - FAQ 답변: 한 번에 내시는 게 원칙입니다. 원하시면 해지하고도 나눠 내실 수 있습니다.
- 근거 ID: `TERMINATE-0043`
  - FAQ 질문: 폰 잃어버려서 해지하는데 할부는 어떻게 해요
  - FAQ 답변: 남은 할부를 한 번에 내시거나, 원하시면 계속 나눠 내실 수 있습니다.
- 근거 ID: `TERMINATE-0003`
  - FAQ 질문: 폰을 잃어버려서 해지하면 할부금은 어떻게 돼요?
  - FAQ 답변: 남은 할부금은 일시 납부가 원칙이고, 원하시면 해지 후에도 기존 할부 일정을 유지하실 수 있습니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `634`, `648`, `619`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-018

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 분실한 단말의 회선을 해지하는 절차를 알려주세요.

### 참고 FAQ 기준
- 하위 질문 기준 1: 분실한 단말의 회선을 해지하는 절차를 알려주세요.
  - FAQ 답변: 매장, 고객센터, 홈페이지 중 한 곳에 해지를 신청하면 즉시 처리됩니다. 해지월 요금은 일할 계산됩니다.
- 동등 정답 FAQ 묶음: `[["TERMINATE-0025", "TERMINATE-0033"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 분실한 단말의 회선 해지는 매장, 고객센터, 또는 LG U+ 홈페이지에서 신청 가능합니다. 신청 후 즉시 처리되며, 해지 요금은 사용 기간에 따라 일할 계산됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `TERMINATE-0033`
  - FAQ 질문: 분실한 단말의 회선을 해지하는 절차를 알려주세요.
  - FAQ 답변: 매장, 고객센터, 홈페이지 중 한 곳에 해지를 신청하면 즉시 처리됩니다. 해지월 요금은 일할 계산됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `642`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-019

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요?
  - FAQ 답변: eSIM을 지원하는 단말이라면 매장이나 온라인에서 전환 신청을 하시면 됩니다. 발급 비용은 2,750원입니다.
- 동등 정답 FAQ 묶음: `[["USIM-0027", "USIM-0031", "USIM-0035", "USIM-0039"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 물리 유심을 eSIM으로 바꾸려면 매장이나 온라인을 통해 전환 신청을 진행하시면 됩니다. 발급 비용은 2,750원이 발생합니다. 지원 단말이어야 전환이 가능하니 해당 조건을 확인해 주세요.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0035`
  - FAQ 질문: 물리 유심에서 eSIM으로 바꾸려면 어떻게 하나요?
  - FAQ 답변: eSIM을 지원하는 단말이라면 매장이나 온라인에서 전환 신청을 하시면 됩니다. 발급 비용은 2,750원입니다.
- 근거 ID: `USIM-0099`
  - FAQ 질문: 폰 바꿀 때 물리 유심 그대로 쓰는 거랑 eSIM 새로 받는 거랑 뭐가 달라요?
  - FAQ 답변: eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `281`, `520`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-020

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요?
  - FAQ 답변: 본인 신분증이 필요합니다. 신분증 없이는 발급이 어렵습니다.
- 동등 정답 FAQ 묶음: `[["USIM-0054"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 새 폰으로 변경 시 유심을 받기 위해서는 본인의 신분증이 필수적으로 필요합니다. 신분증 없이는 유심 발급이 어렵습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0054`
  - FAQ 질문: 새 폰으로 바꿀 때 신분증 없이 유심 받을 수 있을까요?
  - FAQ 답변: 본인 신분증이 필요합니다. 신분증 없이는 발급이 어렵습니다.
- 근거 ID: `USIM-0094`
  - FAQ 질문: 새 폰 유심 받으려는데 뭐 가져가야 해요
  - FAQ 답변: 신분증만 가져오시면 됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `487`, `517`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-021

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 외국 가기 전에 유심 바로 받을 수 있나요

### 참고 FAQ 기준
- 하위 질문 기준 1: 외국 가기 전에 유심 바로 받을 수 있나요
  - FAQ 답변: 네, 매장에 가시면 그 자리에서 받으실 수 있습니다.
- 동등 정답 FAQ 묶음: `[["USIM-0065"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 네, 외국으로 떠나시기 전에 매장을 방문하시면 바로 유심을 받으실 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0065`
  - FAQ 질문: 외국 가기 전에 유심 바로 받을 수 있나요
  - FAQ 답변: 네, 매장에 가시면 그 자리에서 받으실 수 있습니다.
- 근거 ID: `USIM-0045`
  - FAQ 질문: 외국 가기 전에 유심 새로 받으려면 어떻게 해요
  - FAQ 답변: 매장에 가시면 바로 받으실 수 있습니다. 7,700원입니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `496`, `480`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-022

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 명의 바꾸는 데 오래 걸려요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 명의 바꾸는 데 오래 걸려요?
  - FAQ 답변: 매장에서 30분 이내에 처리됩니다. 수수료는 없습니다.
- 동등 정답 FAQ 묶음: `[["NAME_CHANGE-0014", "NAME_CHANGE-0086"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 명의 변경 자체는 일반적으로 30분 이내에 처리됩니다. 하지만 미납 요금이 있으면 처리 시간이 더 걸릴 수 있으니, 먼저 미납 여부를 확인해 보시는 것이 좋습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `NAME_CHANGE-0017`
  - FAQ 질문: 명의 바꾸는 데 돈 들어요
  - FAQ 답변: 안 듭니다. 30분이면 됩니다.
- 근거 ID: `NAME_CHANGE-0071`
  - FAQ 질문: 한 시간째 기다리는데 아직이래요
  - FAQ 답변: 명의변경은 보통 30분이면 됩니다. 밀린 요금이 있으면 그것부터 처리하느라 늦어질 수 있으니 매장에 확인해 보세요.
- 근거 ID: `NAME_CHANGE-0065`
  - FAQ 질문: 배우자 명의로 바꿨는데 처리가 30분 넘게 걸리고 있습니다
  - FAQ 답변: 명의변경은 보통 30분 이내에 처리됩니다. 미납 요금이 있으면 지연될 수 있으니 확인해 보세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `398`, `434`, `430`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-023

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 명의 바꾸는 데 돈 들어요

### 참고 FAQ 기준
- 하위 질문 기준 1: 명의 바꾸는 데 돈 들어요
  - FAQ 답변: 안 듭니다. 30분이면 됩니다.
- 동등 정답 FAQ 묶음: `[["NAME_CHANGE-0017"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 명의 변경에는 별도의 비용이 발생하지 않습니다. 약 30분 정도 소요됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `NAME_CHANGE-0017`
  - FAQ 질문: 명의 바꾸는 데 돈 들어요
  - FAQ 답변: 안 듭니다. 30분이면 됩니다.
- 근거 ID: `NAME_CHANGE-0089`
  - FAQ 질문: 명의 바꾸는 게 새로 개통하는 것보다 싸요
  - FAQ 답변: 명의 바꾸는 건 수수료가 없고 30분이면 됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `398`, `446`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-024

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?
  - FAQ 답변: 가능합니다. 다만 할부금이 남아 있어 양수인인 본인이 신용 심사를 통과해야 합니다.
- 동등 정답 FAQ 묶음: `[["NAME_CHANGE-0042"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 네, 가능합니다. 하지만 현재 남아 있는 할부금을 모두 정산하거나, 양수인인 본인이 신용 심사를 통과해야 합니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `NAME_CHANGE-0042`
  - FAQ 질문: 회사에서 받은 폰이 할부 중인데 제 앞으로 가져올 수 있을까요?
  - FAQ 답변: 가능합니다. 다만 할부금이 남아 있어 양수인인 본인이 신용 심사를 통과해야 합니다.
- 근거 ID: `NAME_CHANGE-0018`
  - FAQ 질문: 회사 그만두면서 폰 받으려는데 할부 있으면 안 되나요
  - FAQ 답변: 됩니다. 다만 받는 분이 신용 심사를 통과해야 합니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `415`, `399`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-025

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 로밍 신청하고 얼마나 기다려야 돼요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 로밍 신청하고 얼마나 기다려야 돼요?
  - FAQ 답변: 신청 후 1시간 이내에 적용됩니다.
- 동등 정답 FAQ 묶음: `[["ROAMING-0002"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 로밍 신청 후 약 1시간 이내에 적용됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `ROAMING-0002`
  - FAQ 질문: 로밍 신청하고 얼마나 기다려야 돼요?
  - FAQ 답변: 신청 후 1시간 이내에 적용됩니다.
- 근거 ID: `ROAMING-0007`
  - FAQ 질문: 로밍 신청하면 언제부터 적용되나요?
  - FAQ 답변: 홈페이지나 고객센터에서 신청하시면 1시간 이내에 적용됩니다. 출국 전 신청을 권장드리지만 현지에 도착하신 뒤에도 신청하실 수 있습니다.
- 근거 ID: `ROAMING-0017`
  - FAQ 질문: 로밍 신청 언제 하면 돼요
  - FAQ 답변: 출국 전에 하시는 게 좋습니다. 현지에서도 되고, 신청하면 1시간 안에 됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `339`, `155`, `348`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-026

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 로밍 신청은 어디서 해요

### 참고 FAQ 기준
- 하위 질문 기준 1: 로밍 신청은 어디서 해요
  - FAQ 답변: 홈페이지나 고객센터에서 하시면 됩니다. 신청하고 1시간 안에 적용됩니다.
- 동등 정답 FAQ 묶음: `[["ROAMING-0012", "ROAMING-0032"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 어느 지역의 통신사 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-027

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 로밍은 어떻게 신청해요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 로밍은 어떻게 신청해요?
  - FAQ 답변: 홈페이지나 고객센터에서 신청하시면 됩니다. 출국 전에 신청하시는 걸 권장드리지만, 현지 도착 후에도 신청하실 수 있습니다.
- 동등 정답 FAQ 묶음: `[["ROAMING-0022"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 출국 전에 신청하시는 것이 좋지만, 현지 도착 후에도 신청하실 수 있습니다. 신청 후에는 약 1시간 이내에 로밍 서비스가 적용됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `ROAMING-0022`
  - FAQ 질문: 로밍은 어떻게 신청해요?
  - FAQ 답변: 홈페이지나 고객센터에서 신청하시면 됩니다. 출국 전에 신청하시는 걸 권장드리지만, 현지 도착 후에도 신청하실 수 있습니다.
- 근거 ID: `ROAMING-0032`
  - FAQ 질문: 로밍 신청은 어디서 해요
  - FAQ 답변: 홈페이지나 고객센터에서 하시면 됩니다. 신청하고 1시간 안에 적용됩니다.
- 근거 ID: `ROAMING-0027`
  - FAQ 질문: 고객센터 전화로 로밍을 신청하는 절차를 알려주세요.
  - FAQ 답변: 고객센터에 전화해 로밍 요금제와 이용 기간을 말씀하시면 접수됩니다. 접수 후 1시간 이내에 적용되며, 홈페이지에서 직접 신청하셔도 됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `160`, `163`, `354`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-028

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 신고하면 요금을 다 돌려받을 수 있나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 신고하면 요금을 다 돌려받을 수 있나요?
  - FAQ 답변: 조사 결과 명의도용이 확인되면 해당 요금은 전액 취소됩니다.
- 동등 정답 FAQ 묶음: `[["SERVICE-0034"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 명의도용이 확인되면 해당 요금은 전액 취소됩니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SERVICE-0034`
  - FAQ 질문: 신고하면 요금을 다 돌려받을 수 있나요?
  - FAQ 답변: 조사 결과 명의도용이 확인되면 해당 요금은 전액 취소됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `197`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-029

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 신고했는데 며칠째 연락이 없어요

### 참고 FAQ 기준
- 하위 질문 기준 1: 신고했는데 며칠째 연락이 없어요
  - FAQ 답변: 명의도용 조사는 접수 후 3영업일 이내에 진행됩니다. 영업일 기준이라 주말과 공휴일은 빠지니 그 안이라면 조금 더 기다려 주세요.
- 동등 정답 FAQ 묶음: `[["SERVICE-0044"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 명의도용 조사는 접수 후 일반적으로 3영업일 이내에 진행됩니다. 주말과 공휴일은 포함되지 않으니, 그 기간이 경과했다면 조금 더 기다려 주시기 바랍니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SERVICE-0044`
  - FAQ 질문: 신고했는데 며칠째 연락이 없어요
  - FAQ 답변: 명의도용 조사는 접수 후 3영업일 이내에 진행됩니다. 영업일 기준이라 주말과 공휴일은 빠지니 그 안이라면 조금 더 기다려 주세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `326`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-030

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다

### 참고 FAQ 기준
- 하위 질문 기준 1: 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다
  - FAQ 답변: 조사에서 도용이 확인되어야 요금이 전액 취소됩니다. 접수 후 3영업일 이내에 조사가 진행되니 결과를 확인해 주세요.
- 동등 정답 FAQ 묶음: `[["SERVICE-0024", "SERVICE-0049"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 도용이 확인되기 전까지는 청구서에서 해당 요금이 취소되지 않을 수 있습니다. 접수 후 3영업일 이내에 조사가 진행되므로, 그 결과를 확인해 주시기 바랍니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `SERVICE-0049`
  - FAQ 질문: 명의도용으로 나온 요금이 아직 청구서에 그대로 있습니다
  - FAQ 답변: 조사에서 도용이 확인되어야 요금이 전액 취소됩니다. 접수 후 3영업일 이내에 조사가 진행되니 결과를 확인해 주세요.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `329`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-031

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 청소년이나 시니어 요금제는 아무나 가입할 수 있나요 그리고 기기변경 시 유심 재발급 구비 서류를 알려주세요.

### 참고 FAQ 기준
- 하위 질문 기준 1: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요?
  - FAQ 답변: 청소년 요금제는 만 18세 이하, 시니어 요금제는 만 65세 이상만 가입할 수 있습니다. 가입 시 생년월일 확인이 가능한 신분증이 필요합니다.
- 하위 질문 기준 2: 기기변경 시 유심 재발급 구비 서류를 알려주세요.
  - FAQ 답변: 본인 신분증만 지참하시면 됩니다. 대리인이 방문하면 위임장, 대리인 신분증, 가입자 신분증 사본이 필요합니다.
- 동등 정답 FAQ 묶음: `[["PLAN-0004", "PLAN-0144"], ["USIM-0034", "USIM-0074"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 청소년 요금제와 시니어 요금제는 각각 만 18세 이하와 만 65세 이상만 가입 가능합니다. 가입 시에는 생년월일 확인이 가능한 신분증이 필요합니다. 기기 변경 시 유심 재발급을 위한 구비 서류는 다음과 같습니다: - 신분증 사본 - 기존 통신사 이용 약관 해지 증명서 (기존 통신사에서 발급)

### 답변 생성에 전달된 실제 근거
- 근거 ID: `PLAN-0004`
  - FAQ 질문: 청소년이나 시니어 요금제는 아무나 가입할 수 있나요?
  - FAQ 답변: 청소년 요금제는 만 18세 이하, 시니어 요금제는 만 65세 이상만 가입할 수 있습니다. 가입 시 생년월일 확인이 가능한 신분증이 필요합니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `93`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
  - 기준 2:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-032

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요. 그리고 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요.
  - FAQ 답변: 개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.
- 하위 질문 기준 2: 자녀가 성인이 되어 명의를 이전할 때 부모 쪽 준비물은 무엇인가요?
  - FAQ 답변: 양도인인 부모님의 신분증이 필요하고, 자녀와 함께 매장을 방문하셔야 합니다. 동행이 어려우면 위임장을 자녀에게 맡기시면 됩니다.
- 동등 정답 FAQ 묶음: `[["DEVICE-0002", "DEVICE-0010", "DEVICE-0014", "DEVICE-0018", "DEVICE-0046"], ["NAME_CHANGE-0004", "NAME_CHANGE-0028"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 기기변경 신청은 개통일로부터 6개월이 경과한 후에 가능합니다. 할부금 처리는 기존 할부금을 완납하거나 새 할부 계약으로 승계하는 방식으로 이루어집니다. 자녀 명의 변경과 관련해서는 현재 제공된 정보로는 구체적인 준비물을 안내드리기 어렵습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `DEVICE-0010`
  - FAQ 질문: 기기변경 신청 가능 시점과 할부금 처리 기준을 알려주세요.
  - FAQ 답변: 개통일로부터 6개월이 지나야 기기변경을 신청할 수 있습니다. 기존 할부금은 완납하거나 새 할부에 승계하는 방식으로 처리됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `35`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
  - 기준 2:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-033

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요. 그리고 가족에게 데이터를 나눠주는 절차를 알려주세요.

### 참고 FAQ 기준
- 하위 질문 기준 1: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요.
  - FAQ 답변: 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.
- 하위 질문 기준 2: 가족에게 데이터를 나눠주는 절차를 알려주세요.
  - FAQ 답변: 앱 또는 홈페이지의 데이터 선물 메뉴에서 신청합니다. 월 2회, 회당 최대 2GB까지 가능합니다.
- 동등 정답 FAQ 묶음: `[["USIM-0036", "USIM-0040", "USIM-0080"], ["PLAN-0043"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 해외 체류 중 유심을 분실하셨군요. 먼저 홈페이지에서 분실 신고를 진행하시면 즉시 이용이 정지됩니다. 귀국 후에는 새 유심을 발급받으시면 기존 유심은 자동으로 해지됩니다. 가족에게 데이터를 나눠주는 절차에 대해서는 현재 제공된 정보로는 안내드릴 수 있는 내용이 없습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0040`
  - FAQ 질문: 해외 체류 중에 유심을 분실했을 때 처리 절차를 알려주세요.
  - FAQ 답변: 홈페이지에서 분실 신고를 하시면 즉시 이용 정지됩니다. 귀국 후 새 유심을 재발급받으시면 기존 유심은 자동으로 해지됩니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `477`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
  - 기준 2:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-034

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 체스에서 퀸을 잘 활용하는 방법은?

### 참고 FAQ 기준

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-035

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 해외여행 여권 유효기간은 얼마나 남아야 해?

### 참고 FAQ 기준

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-036

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 반려견 목욕 주기는 어느 정도가 좋아요?

### 참고 FAQ 기준

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-037

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 가상계좌가 무엇인가요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 가상계좌가 뭐예요
  - FAQ 답변: 요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.
- 필수 사실:
  - 가상계좌는 요금 납부를 위해 사용자에게 부여되는 입금 전용 계좌다
- 동등 정답 FAQ 묶음: `[["BILLING-0026"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 가상계좌는 요금을 납부할 때 사용하는 입금 전용 계좌입니다. 주로 계좌이체를 통해 요금을 쉽게 납부할 수 있도록 설계되었습니다. 신용카드나 간편결제 방법 외에도 이 가상계좌를 통해 편리하게 요금을 낼 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `BILLING-0026`
  - FAQ 질문: 가상계좌가 뭐예요
  - FAQ 답변: 요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `1043`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-038

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 유심 재발급하면 택배비는 따로 내나요?

### 참고 FAQ 기준

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 유심 재발급 비용은 7,700원으로 포함되어 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0001`
  - FAQ 질문: 유심 재발급 비용이 얼마예요?
  - FAQ 답변: 7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.
- 근거 ID: `USIM-0029`
  - FAQ 질문: 새 폰에 쓸 유심을 택배로 받는 방법 알려주세요.
  - FAQ 답변: 온라인으로 재발급을 신청하시면 택배로 2~3 영업일 안에 받으실 수 있습니다. 비용은 7,700원입니다.
- 근거 ID: `USIM-0009`
  - FAQ 질문: 기기변경 시 유심 재발급 택배 소요 기간을 알려주세요.
  - FAQ 답변: 온라인 신청 시 택배로 2~3 영업일이 걸립니다. 매장 방문은 즉시 발급이며 비용은 7,700원입니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `273`, `468`, `454`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-039

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 유심 재발급 비용은 얼마고 배송비도 내야 하나요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 유심 재발급 비용이 얼마예요?
  - FAQ 답변: 7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.
- 필수 사실:
  - 유심 재발급 비용은 7,700원이다
  - 배송비 부과 여부는 제공된 FAQ에서 확인할 수 없다
- 동등 정답 FAQ 묶음: `[["USIM-0001", "USIM-0009"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 유심 재발급 비용은 7,700원입니다. 온라인으로 신청하신 경우에는 택배로 배송되며, 배송비는 별도로 발생하지 않습니다. 매장 방문 시에는 즉시 받으실 수 있습니다.

### 답변 생성에 전달된 실제 근거
- 근거 ID: `USIM-0001`
  - FAQ 질문: 유심 재발급 비용이 얼마예요?
  - FAQ 답변: 7,700원입니다. 매장에서 바로 받으실 수 있고, 온라인 신청 시 택배로 2~3 영업일 걸립니다.
- 근거 ID: `USIM-0073`
  - FAQ 질문: 유심이 안 읽혀서 새로 받아야 할 것 같은데 비용이 얼마나 드나요?
  - FAQ 답변: 인식 오류가 계속되면 재발급을 받으시는 게 좋습니다. 매장에서는 즉시 발급되고, 비용은 7,700원입니다.
- 근거 ID: `USIM-0077`
  - FAQ 질문: 유심이 부러졌는데 오늘 급하게 써야 해요
  - FAQ 답변: 매장을 방문하시면 즉시 재발급됩니다. 비용은 7,700원입니다.
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- `273`, `291`, `504`

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
## V6H-040

### 대화 맥락
- 이전 대화 없음

### 현재 질문
> 요금제는 한 달에 몇 번 바꿀 수 있고 가상계좌는 뭐예요?

### 참고 FAQ 기준
- 하위 질문 기준 1: 요금제 바꾸는 거 한 달에 몇 번까지 되나요?
  - FAQ 답변: 요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, 그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.
- 하위 질문 기준 2: 가상계좌가 뭐예요
  - FAQ 답변: 요금 낼 때 쓰는 입금 전용 계좌입니다. 계좌이체, 신용카드, 간편결제로도 낼 수 있습니다.
- 필수 사실:
  - 요금제는 한 달에 한 번 변경할 수 있다
  - 가상계좌는 요금 납부용 입금 전용 계좌다
- 동등 정답 FAQ 묶음: `[["BILLING-0001"], ["BILLING-0026"]]`

### 답변 생성에 전달된 확정 조건
- 없음

### 실제 저장 답변
> 안내드릴 수 있는 정보가 없습니다.

### 답변 생성에 전달된 실제 근거
- 실제 근거 없음
- 근거 출처 기록: `captured_rag_input`

### DB에 저장된 근거 ID
- 없음

### 사람 판정 기록
- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 주장별 판정과 근거 ID:
- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록
  - 기준 1:
  - 기준 2:
- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):
- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):
- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):
- 메모:

---
