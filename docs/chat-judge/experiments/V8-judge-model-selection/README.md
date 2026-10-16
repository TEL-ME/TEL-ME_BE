# V8 채점 모델 선정 검토

답변 생성 모델(엑사원, gpt-oss) 비교에 쓸 채점(LLM Judge) 모델을 고르기 위해, TELME-100의 기준과 평가셋으로 후보 모델을 채점시켜 사람 판정과 맞춰 봤다. **채점 모델은 아직 확정하지 않았다.** 평가 기준과 평가셋을 보강한 뒤 같은 방식으로 다시 확인한다.

## 요약

- 후보는 TELME-100 V7의 세 모델(Qwen3 235B, gpt-oss-120b, Sonnet 4.6)에 Mistral Large 3를 더해 비교했다. DeepSeek V3.2는 연결 확인용 3턴만 돌렸다.
- gpt-oss-120b는 답변 생성 비교 대상이라 채점에서 뺀다. 같은 모델이 자기 답변을 채점하는 구조를 피하기 위해서다.
- Sonnet 4.6은 같은 302건에 약 $8.4(V7 토큰 기준)로 Qwen의 20배 이상이라, 답변을 고치고 다시 채점하는 반복에 쓰기 어렵다.
- Mistral Large 3는 사람 판정과의 일치도가 Qwen과 Sonnet 사이였다. 인용을 고쳐 써서 채점되지 않은 턴이 302건 중 28건(9%)이다.
- Qwen과 Mistral을 함께 쓰면 사람이 근거 없음으로 판정한 54건 중 46건을 둘 중 하나가 잡았다 (Qwen 단독 42건).

## 평가 설정

| 항목 | 내용 |
|---|---|
| 평가 기준·프롬프트 | TELME-100 `judge_chat_flow.py`, `evaluate_chat_pipeline.py` 그대로 (수정 없음) |
| 채점 대상 | V6에서 저장한 채팅 답변 509턴 중 사람이 판정한 302턴. 답변은 모두 엑사원이 만들었다 |
| 정답 | 사람 판정 302건 ([판정표](../V7-bedrock-cross-judge/v7-three-model-full-302-human-review-lyj.md)) |
| 기존 모델 결과 | Qwen, gpt-oss, Sonnet은 V7 원시 결과를 그대로 사용 (다시 채점하지 않음) |
| 실행 리전 | Tokyo (`ap-northeast-1`) |
| 한 턴당 호출 | 5회 (근거성 2, 충실도 1, 보류 판정 2) |

302건은 모델 판정이 갈리거나 미채점인 답변 위주로 뽑은 목록이라 전체 정확도가 아니다. 모든 모델을 같은 302건에서 비교한다.

### 새 모델 호출 방식이 V7과 다른 점

- Mistral, DeepSeek은 JSON 형식을 프롬프트로 요청했다 (`prompt_json`). V7의 Qwen은 일부 단계만 같은 방식이다.
- 모델이 JSON을 코드 블록으로 감싸 보내면 감싼 부분만 벗기고 내용은 그대로 둔다.
- Mistral 미채점 40건 재시도에서만 주장 추출 단계에 "원문 그대로 복사하고 고쳐 쓰지 말 것" 지시를 한 줄 추가했다.
- 합의 판정과 회귀 검사는 쓰지 않았다. 모델별 결과만 사람 판정과 비교한다.

## 결과

사람 판정 302건과의 일치. 채점된 것 기준이며 괄호는 미해결 건수다.

| 축 | Qwen3 235B | Sonnet 4.6 | Mistral Large 3 |
|---|---|---|---|
| 근거성 | 242/290 (83.4%), 12 | 250/282 (88.7%), 20 | 234/277 (84.5%), 25 |
| 충실도 (단일 질문 254건) | 204/254 (80.3%), 1 | 219/254 (86.2%), 1 | 212/254 (83.5%), 1 |
| 보류 판정 | 235/289 (81.3%), 13 | 246/282 (87.2%), 20 | 232/275 (84.4%), 27 |

미해결을 틀린 것으로 보고 302건 전체를 분모로 하면 다음과 같다.

| 축 | Qwen3 235B | Sonnet 4.6 | Mistral Large 3 |
|---|---|---|---|
| 근거성 | 80.1% | 82.8% | 77.5% |
| 보류 판정 | 77.8% | 81.5% | 76.8% |

근거성 오류 방향은 다음과 같다.

| | Qwen3 235B | Sonnet 4.6 | Mistral Large 3 |
|---|---|---|---|
| 사람이 근거 없음인데 근거 있음으로 놓침 | 9 | 14 | 14 |
| 사람이 근거 있음인데 근거 없음으로 경고 | 30 | 11 | 21 |

원본 집계는 [`agreement-302.json`](agreement-302.json), 재계산은 `compare_models.py`로 한다. gpt-oss-120b 수치는 TELME-100 [V7 분석](../V7-bedrock-cross-judge/v7-three-model-human302-analysis.md)을 참고한다.

### 틀린 방향 (사람 판정 → 모델 판정, 건수)

채점된 건 중 사람과 다른 경우만 센다. 충실도는 평가 그룹이 하나인 256건 기준(채점 가능 254건)이다.

**근거성**

| 사람 → 모델 | Qwen | Sonnet 4.6 | Mistral |
|---|---:|---:|---:|
| SUPPORTED → UNSUPPORTED | 30 | 11 | 21 |
| UNSUPPORTED → SUPPORTED | 9 | 14 | 14 |
| SUPPORTED → NOT_APPLICABLE | 7 | 5 | 7 |
| UNSUPPORTED → NOT_APPLICABLE | 1 | 1 | 1 |
| NOT_APPLICABLE → UNSUPPORTED | 1 | 0 | 0 |
| NOT_APPLICABLE → SUPPORTED | 0 | 1 | 0 |

**충실도 (단일 질문)**

| 사람 → 모델 | Qwen | Sonnet 4.6 | Mistral |
|---|---:|---:|---:|
| PARTIAL → COMPLETE | 22 | 12 | 17 |
| PARTIAL → MISSED | 11 | 8 | 9 |
| MISSED → COMPLETE | 7 | 7 | 9 |
| COMPLETE → PARTIAL | 6 | 3 | 4 |
| MISSED → PARTIAL | 2 | 5 | 3 |
| MISSED → NOT_APPLICABLE | 1 | 0 | 0 |
| COMPLETE → MISSED | 1 | 0 | 0 |

**보류 판정**

| 사람 → 모델 | Qwen | Sonnet 4.6 | Mistral |
|---|---:|---:|---:|
| NOT_APPLICABLE → SHOULD_ABSTAIN | 29 | 11 | 20 |
| SHOULD_ABSTAIN → NOT_APPLICABLE | 8 | 14 | 15 |
| NOT_APPLICABLE → APPROPRIATE | 11 | 1 | 3 |
| APPROPRIATE → NOT_APPLICABLE | 0 | 5 | 3 |
| NOT_APPLICABLE → OVER_REFUSAL | 1 | 2 | 1 |
| APPROPRIATE → SHOULD_ABSTAIN | 2 | 0 | 1 |
| SHOULD_ABSTAIN → APPROPRIATE | 1 | 1 | 0 |
| APPROPRIATE → OVER_REFUSAL | 1 | 1 | 0 |
| SHOULD_ABSTAIN → OVER_REFUSAL | 1 | 0 | 0 |
| OVER_REFUSAL → NOT_APPLICABLE | 0 | 1 | 0 |

### Qwen과 Mistral을 함께 썼을 때

| 축 | 둘 다 채점 | 같은 판정 (사람과 일치) | 갈림 | 한쪽이라도 미해결 |
|---|---|---|---|---|
| 근거성 | 268 | 231 (211) | 37 | 34 |
| 보류 판정 | 265 | 215 (199) | 50 | 37 |

사람이 근거 없음으로 판정한 54건 중 Qwen이 42건, Mistral이 36건, 둘 중 하나라도 46건을 잡았고 8건은 둘 다 놓치거나 미해결이었다. 같은 판정이어도 사람과 다른 경우가 있다(근거성 231건 중 20건). 다수 일치가 정답을 보장하지 않는다.

### 미채점 원인 (Mistral)

| 원인 | 본 실행 | 재시도 후 |
|---|---|---|
| 주장 인용이 답변 원문과 다름 | 33 | 24 |
| 근거 인용이 FAQ에 없음 | 2 | 2 |
| 근거 ID 없는 정책 주장 | 2 | 0 |
| JSON 형식 오류 (뒤에 글이 붙거나 구분자 누락) | 3 | 2 |
| 채점되지 않은 턴 | 40 | 28 |

채점 도구는 답변에서 원문 그대로 잘라낸 문장만 받는다. Mistral은 긴 문장을 쪼개 어미를 바꿔 인용해 이 검증에서 거부된다. 원문 인용 지시를 추가해도 40건 중 12건만 복구됐다. Qwen은 12건, Sonnet은 20건이 미해결이었다.

### Mistral 미채점 28건

| ID (turn) | 사유 |
|---|---|
| PIPE-BILLING-0052 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-BILLING-0092 (0) | 보류: 근거 인용이 FAQ에 없음 |
| PIPE-BILLING-0102 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-BILLING-0113 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-BILLING-0129 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-BILLING-0138 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-COMPOUND-002 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-COMPOUND-025 (0) | 충실도: JSON 형식 오류 |
| PIPE-COMPOUND-037 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-DEVICE-0012 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-DEVICE-0070 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-DEVICE-0117 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-NAME_CHANGE-0025 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-NAME_CHANGE-0061 (0) | 충실도: JSON 형식 오류 |
| PIPE-NAME_CHANGE-0075 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-PLAN-0017 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-PLAN-0036 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-PLAN-0121 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-PORTING-0033 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-PORTING-0097 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-ROAMING-0071 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-ROAMING-0074 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-SERVICE-0065 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-SUBSCRIBE-0084 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-SUBSCRIBE-0123 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-TERMINATE-0010 (0) | 보류: 근거 인용이 FAQ에 없음 |
| PIPE-TERMINATE-0034 (0) | 근거성: 주장 인용이 원문과 다름 |
| PIPE-USIM-0030 (0) | 근거성: 주장 인용이 원문과 다름 |

### 인용을 고쳐 쓴 예

- `PIPE-BILLING-0052`
  - 답변 문장: 요금을 납부하지 않으실 경우, 발신 정지 상태가 지속되며 30일 후에는 수신이 정지되고, 그 이후 60일이 경과하면 직권 해지될 수 있습니다.
  - Mistral 인용: 요금을 납부하지 않으실 경우, 발신 정지 상태가 지속됩니다
- `PIPE-BILLING-0102`
  - 답변 문장: 본인 인증을 통해 한도를 월 100만 원까지 즉시 올릴 수 있으니 이 방법을 시도해 보세요.
  - Mistral 인용: 본인 인증을 통해 한도를 월 100만 원까지 즉시 올릴 수 있습니다
- `PIPE-BILLING-0113`
  - 답변 문장: 변경된 요금제는 변경일 다음 날 00:00부터 적용되며, 해당 달의 요금은 일할 계산되어 두 요금제 금액이 함께 표시됩니다.
  - Mistral 인용: 변경된 요금제는 변경일 다음 날 00:00부터 적용됩니다

### DeepSeek V3.2 3턴 시험

| ID | 근거성 | 충실도 | 보류 판정 | 사람 판정 |
|---|---|---|---|---|
| PIPE-BILLING-0020 | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | UNSUPPORTED / COMPLETE / SHOULD_ABSTAIN |
| PIPE-BILLING-0021 | SUPPORTED | COMPLETE | NOT_APPLICABLE | SUPPORTED / COMPLETE / NOT_APPLICABLE |
| PIPE-BILLING-0065 | UNSUPPORTED | COMPLETE | SHOULD_ABSTAIN | UNSUPPORTED / COMPLETE / SHOULD_ABSTAIN |

세 턴 모두 세 축이 사람 판정과 같았다. 표본이 3건이라 우열 판단에는 쓰지 않는다.

## 비용

실행별 호출 수와 토큰은 [`usage-summary.json`](usage-summary.json)에 있다.

| 모델 | 실행 | 호출 | 비용 |
|---|---|---|---|
| Mistral Large 3 | 3턴 시험 2회, 302건, 미채점 재시도 | 1,682 | 약 $1.11 |
| DeepSeek V3.2 | 3턴 시험 | 16 | 약 $0.01 |
| Qwen3 235B (V7 기록) | 302건 | 1,505 | 약 $0.36 |
| Sonnet 4.6 (V7 기록) | 302건 | 1,502 | 약 $8.40 |

호출 실패는 0건이다. 단가는 AWS 요금표의 Tokyo 표기 기준(Mistral 입력 $0.61, 출력 $1.82 / 100만 토큰)이며 Qwen은 Sydney 표기값, Sonnet은 Anthropic 공개 가격이다. 실제 청구액은 AWS 청구 화면에서 확인해야 한다.

## 판단

- 확정하지 않는다. 평가 기준과 평가셋 보강이 필요하다는 의견이 있어, 보강 뒤 같은 방식으로 다시 확인한다.
- 모델 간 일치율 차이는 대부분 2~5%p이고 표본이 302건이라 우열을 단정하기 어렵다.
- 반복 채점에는 Qwen과 Mistral 조합이 비용 면에서 현실적이다. Mistral은 미채점 9%를 사람이 확인하는 규칙이 필요하다.
- 채점 모델은 반복 개선 중간에 바꾸지 않는다. 바꾸면 점수 변화가 개선 때문인지 채점 변화 때문인지 구분되지 않는다.

## 한계

- 채점 대상 답변이 모두 엑사원 답변이다. gpt-oss 답변을 채점할 때 같은 기준이 맞는지는 별도로 확인한다.
- 302건은 선별된 어려운 표본이다.
- Mistral과 DeepSeek은 호출 방식(`prompt_json`)이 Qwen과 일부 다르고, Mistral 재시도에만 인용 지시가 추가됐다.
- 모델 원시 응답 파일(약 2.5MB)은 저장소에 올리지 않았다.

## 재현

```bash
python3 -m venv .venv && .venv/bin/pip install boto3
# 호출 없이 규모만 확인
python3 -m scripts.chat_judge.experiments.v8_judge_model_selection.run_candidate --model mistral
# 유료 호출 (BEDROCK_API_KEY 환경 변수 필요)
.venv/bin/python -m scripts.chat_judge.experiments.v8_judge_model_selection.run_candidate \
  --model mistral --workers 3 --allow-bedrock-call --out <결과파일.json.gz>
# 사람 판정과 비교
python3 -m scripts.chat_judge.experiments.v8_judge_model_selection.compare_models \
  --model qwen=<qwen 원시결과> --model mistral=<결과파일.json.gz>
```

`scripts/chat_judge`의 V6·V7 파일은 TELME-100 브랜치에서 수정 없이 복사한 것이다. 해당 브랜치가 머지되면 같은 내용이라 충돌하지 않는다.

## 건별 판정

<details>
<summary>302건 건별 판정 (사람, Qwen, Sonnet 4.6, Mistral)</summary>

각 칸은 근거성 / 충실도 / 보류 판정이다. 복합 질문의 충실도는 평가 그룹별 판정을 `/`로 이어 적었고(사람은 답변 전체 하나), `-`는 미채점이나 REVIEW다. 약어: SUP=SUPPORTED, UNS=UNSUPPORTED, NA=NOT_APPLICABLE, COM=COMPLETE, PAR=PARTIAL, MIS=MISSED, SHA=SHOULD_ABSTAIN, OVR=OVER_REFUSAL, APP=APPROPRIATE.

| ID (turn) | 사람 | Qwen | Sonnet 4.6 | Mistral |
|---|---|---|---|---|
| PIPE-BILLING-0020 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-BILLING-0021 (0) | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-BILLING-0052 (0) | SUP / COM / NA | - / COM / - | SUP / COM / NA | - / COM / - |
| PIPE-BILLING-0054 (0) | SUP / PAR / NA | UNS / COM / SHA | SUP / PAR / NA | NA / PAR / NA |
| PIPE-BILLING-0065 (0) | UNS / COM / SHA | SUP / COM / OVR | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-BILLING-0069 (0) | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR |
| PIPE-BILLING-0072 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-BILLING-0074 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-BILLING-0087 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-BILLING-0092 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / - |
| PIPE-BILLING-0097 (0) | SUP / COM / NA | SUP / COM / OVR | SUP / COM / NA | SUP / COM / NA |
| PIPE-BILLING-0100 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-BILLING-0102 (0) | SUP / COM / NA | - / COM / - | SUP / COM / NA | - / COM / - |
| PIPE-BILLING-0113 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-BILLING-0115 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-BILLING-0117 (0) | SUP / PAR / OVR | NA / COM / OVR | NA / PAR / OVR | NA / PAR / OVR |
| PIPE-BILLING-0123 (0) | UNS / PAR / SHA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-BILLING-0129 (0) | SUP / PAR / NA | UNS / COM / SHA | SUP / COM / NA | - / COM / - |
| PIPE-BILLING-0135 (0) | UNS / PAR / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-BILLING-0138 (0) | SUP / PAR / NA | UNS / PAR / SHA | UNS / PAR / SHA | - / PAR / - |
| PIPE-BILLING-0146 (0) | UNS / PAR / SHA | SUP / MIS / NA | UNS / PAR / SHA | UNS / MIS / SHA |
| PIPE-BILLING-0148 (0) | SUP / COM / NA | - / COM / - | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-BILLING-0154 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-BILLING-0155 (0) | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-COMPOUND-001 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-002 (0) | UNS / COM / SHA | UNS / PAR/COM / SHA | UNS / COM/COM / SHA | - / MIS/COM / - |
| PIPE-COMPOUND-004 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | UNS / COM/MIS / SHA | UNS / COM/MIS / SHA |
| PIPE-COMPOUND-005 (0) | UNS / PAR / SHA | UNS / PAR/MIS / SHA | UNS / PAR/MIS / SHA | UNS / PAR/MIS / SHA |
| PIPE-COMPOUND-006 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-007 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-008 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-009 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-010 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | UNS / COM/PAR / SHA | UNS / COM/PAR / SHA |
| PIPE-COMPOUND-012 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-013 (0) | SUP / PAR / APP | UNS / COM/MIS / SHA | SUP / COM/MIS / NA | SUP / COM/MIS / NA |
| PIPE-COMPOUND-014 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-015 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-016 (0) | UNS / PAR / SHA | - / COM/MIS / - | SUP / COM/MIS / NA | UNS / COM/MIS / SHA |
| PIPE-COMPOUND-017 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-018 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | SUP / PAR/MIS / NA | UNS / COM/PAR / SHA |
| PIPE-COMPOUND-019 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-020 (0) | UNS / MIS / SHA | UNS / - / SHA | UNS / PAR/MIS / SHA | UNS / COM/MIS / SHA |
| PIPE-COMPOUND-021 (0) | SUP / PAR / APP | SUP / COM/MIS / APP | SUP / PAR/MIS / NA | SUP / COM/MIS / NA |
| PIPE-COMPOUND-022 (0) | SUP / PAR / NA | SUP / COM/MIS / NA | SUP / COM/MIS / NA | SUP / COM/MIS / NA |
| PIPE-COMPOUND-023 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-024 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-025 (0) | SUP / PAR / NA | SUP / COM/MIS / NA | SUP / COM/MIS / NA | SUP / - / NA |
| PIPE-COMPOUND-026 (0) | UNS / COM / SHA | UNS / COM/MIS / SHA | UNS / COM/PAR / SHA | UNS / COM/PAR / SHA |
| PIPE-COMPOUND-027 (0) | UNS / PAR / SHA | UNS / MIS/MIS / SHA | UNS / MIS/PAR / SHA | UNS / MIS/COM / SHA |
| PIPE-COMPOUND-029 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | - / COM/MIS / - | UNS / COM/PAR / SHA |
| PIPE-COMPOUND-030 (0) | SUP / COM / NA | SUP / COM/COM / NA | SUP / COM/COM / NA | UNS / COM/COM / SHA |
| PIPE-COMPOUND-031 (0) | SUP / PAR / APP | UNS / COM/MIS / SHA | SUP / COM/MIS / NA | SUP / COM/MIS / APP |
| PIPE-COMPOUND-032 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-033 (0) | UNS / PAR / SHA | UNS / MIS/PAR / SHA | UNS / PAR/PAR / SHA | UNS / MIS/COM / SHA |
| PIPE-COMPOUND-034 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-035 (0) | SUP / PAR / NA | UNS / COM/MIS / SHA | UNS / COM/PAR / SHA | UNS / COM/MIS / SHA |
| PIPE-COMPOUND-036 (0) | SUP / PAR / NA | UNS / COM/MIS / SHA | SUP / COM/MIS / NA | UNS / COM/MIS / SHA |
| PIPE-COMPOUND-037 (0) | NA / COM / NA | UNS / COM/PAR / SHA | SUP / COM/COM / NA | - / COM/COM / - |
| PIPE-COMPOUND-038 (0) | UNS / PAR / SHA | UNS / MIS/PAR / SHA | UNS / MIS/COM / SHA | UNS / MIS/COM / SHA |
| PIPE-COMPOUND-039 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-040 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-041 (0) | SUP / PAR / NA | UNS / MIS/PAR / SHA | UNS / MIS/PAR / SHA | SUP / PAR/COM / NA |
| PIPE-COMPOUND-042 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-043 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-045 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-047 (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| PIPE-COMPOUND-048 (0) | UNS / PAR / SHA | SUP / COM/MIS / APP | SUP / COM/MIS / NA | SUP / COM/MIS / NA |
| PIPE-COMPOUND-049 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | SUP / COM/MIS / NA | UNS / COM/COM / SHA |
| PIPE-COMPOUND-050 (0) | UNS / PAR / SHA | UNS / COM/MIS / SHA | UNS / COM/MIS / SHA | UNS / COM/PAR / SHA |
| PIPE-DEVICE-0004 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0008 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0012 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-DEVICE-0024 (0) | SUP / COM / NA | - / COM / - | - / COM / - | UNS / COM / SHA |
| PIPE-DEVICE-0033 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / PAR / NA |
| PIPE-DEVICE-0034 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0040 (0) | SUP / COM / NA | - / COM / - | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0048 (0) | UNS / COM / SHA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-DEVICE-0054 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0062 (0) | UNS / PAR / SHA | UNS / COM / SHA | - / COM / - | SUP / COM / NA |
| PIPE-DEVICE-0063 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-DEVICE-0070 (0) | SUP / MIS / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-DEVICE-0072 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-DEVICE-0076 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0080 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-DEVICE-0084 (0) | UNS / COM / SHA | UNS / COM / SHA | SUP / PAR / NA | UNS / COM / SHA |
| PIPE-DEVICE-0089 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0095 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-DEVICE-0098 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-DEVICE-0099 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-DEVICE-0102 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-DEVICE-0105 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-DEVICE-0116 (0) | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-DEVICE-0117 (0) | SUP / COM / NA | UNS / COM / SHA | UNS / COM / SHA | - / COM / - |
| PIPE-DEVICE-0129 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / PAR / APP |
| PIPE-NAME_CHANGE-0015 (0) | SUP / PAR / APP | NA / PAR / APP | NA / PAR / NA | NA / PAR / NA |
| PIPE-NAME_CHANGE-0020 (0) | SUP / MIS / NA | SUP / MIS / NA | - / PAR / - | UNS / PAR / SHA |
| PIPE-NAME_CHANGE-0024 (0) | UNS / MIS / SHA | UNS / COM / SHA | SUP / COM / APP | UNS / COM / SHA |
| PIPE-NAME_CHANGE-0025 (0) | UNS / COM / SHA | UNS / COM / SHA | - / COM / - | - / COM / - |
| PIPE-NAME_CHANGE-0027 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / OVR | SUP / COM / NA |
| PIPE-NAME_CHANGE-0030 (0) | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-NAME_CHANGE-0036 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-NAME_CHANGE-0044 (0) | SUP / COM / NA | UNS / COM / SHA | - / COM / - | UNS / COM / SHA |
| PIPE-NAME_CHANGE-0049 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-NAME_CHANGE-0058 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-NAME_CHANGE-0061 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / - / APP |
| PIPE-NAME_CHANGE-0068 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-NAME_CHANGE-0070 (0) | SUP / COM / NA | NA / COM / NA | SUP / COM / OVR | SUP / COM / NA |
| PIPE-NAME_CHANGE-0075 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | - / COM / - |
| PIPE-NAME_CHANGE-0076 (0) | SUP / COM / NA | SUP / PAR / NA | NA / COM / NA | NA / PAR / NA |
| PIPE-NAME_CHANGE-0083 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-NAME_CHANGE-0088 (0) | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-OUT-001 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-002 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-003 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-004 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-006 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-007 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-008 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-009 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-012 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-013 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-014 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-015 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-016 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-017 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-018 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-019 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-020 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-021 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-022 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-023 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-025 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-026 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-027 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-028 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-029 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-030 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-031 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-032 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-033 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-035 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-036 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-037 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-038 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-039 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-040 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-041 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-042 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-043 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-044 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-045 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-046 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-047 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-048 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-049 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-OUT-050 (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |
| PIPE-PLAN-0001 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PLAN-0003 (0) | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA |
| PIPE-PLAN-0017 (0) | SUP / COM / NA | - / COM / - | SUP / COM / NA | - / COM / - |
| PIPE-PLAN-0022 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0036 (0) | SUP / PAR / NA | UNS / MIS / SHA | SUP / MIS / NA | - / MIS / - |
| PIPE-PLAN-0038 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0047 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PLAN-0050 (0) | NA / MIS / NA | NA / PAR / NA | NA / PAR / NA | NA / PAR / NA |
| PIPE-PLAN-0058 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0060 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0066 (0) | UNS / MIS / SHA | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-PLAN-0069 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PLAN-0072 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-PLAN-0081 (0) | SUP / COM / NA | NA / COM / NA | NA / COM / NA | NA / COM / NA |
| PIPE-PLAN-0092 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0103 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PLAN-0108 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-PLAN-0115 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0121 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / PAR / SHA | - / COM / - |
| PIPE-PLAN-0127 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PLAN-0136 (0) | SUP / PAR / APP | SUP / COM / APP | SUP / PAR / NA | UNS / PAR / SHA |
| PIPE-PLAN-0143 (0) | UNS / PAR / SHA | UNS / PAR / SHA | SUP / PAR / NA | SUP / COM / NA |
| PIPE-PORTING-0001 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0015 (0) | SUP / COM / NA | - / COM / - | UNS / COM / SHA | SUP / COM / NA |
| PIPE-PORTING-0023 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0029 (0) | UNS / MIS / SHA | UNS / MIS / SHA | SUP / MIS / NA | UNS / MIS / SHA |
| PIPE-PORTING-0032 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PORTING-0033 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-PORTING-0044 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PORTING-0048 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-PORTING-0049 (0) | UNS / MIS / SHA | UNS / MIS / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-PORTING-0052 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-PORTING-0053 (0) | SUP / COM / NA | SUP / - / NA | - / COM / - | UNS / COM / SHA |
| PIPE-PORTING-0060 (0) | UNS / MIS / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-PORTING-0065 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0071 (0) | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR |
| PIPE-PORTING-0077 (0) | NA / MIS / APP | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / APP |
| PIPE-PORTING-0078 (0) | SUP / COM / NA | NA / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0081 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0085 (0) | UNS / MIS / SHA | UNS / COM / SHA | UNS / PAR / SHA | UNS / COM / SHA |
| PIPE-PORTING-0087 (0) | UNS / PAR / SHA | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-PORTING-0092 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0097 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | - / COM / - |
| PIPE-PORTING-0107 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-PORTING-0113 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0022 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-ROAMING-0025 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-ROAMING-0027 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-ROAMING-0030 (0) | SUP / COM / NA | - / COM / - | - / COM / - | SUP / COM / NA |
| PIPE-ROAMING-0031 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0032 (0) | NA / MIS / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-ROAMING-0034 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-ROAMING-0046 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0053 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0062 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0064 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-ROAMING-0071 (0) | SUP / COM / NA | UNS / COM / SHA | UNS / COM / SHA | - / COM / - |
| PIPE-ROAMING-0074 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | - / COM / - |
| PIPE-ROAMING-0077 (0) | SUP / COM / NA | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA |
| PIPE-SERVICE-0003 (0) | NA / PAR / NA | NA / MIS / APP | NA / PAR / NA | NA / MIS / APP |
| PIPE-SERVICE-0008 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SERVICE-0013 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-SERVICE-0014 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SERVICE-0016 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SERVICE-0019 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SERVICE-0020 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SERVICE-0026 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SERVICE-0033 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-SERVICE-0038 (0) | NA / MIS / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-SERVICE-0041 (0) | UNS / COM / SHA | - / COM / - | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-SERVICE-0043 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SERVICE-0044 (0) | UNS / PAR / SHA | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-SERVICE-0046 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-SERVICE-0049 (0) | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SERVICE-0051 (0) | UNS / MIS / SHA | UNS / MIS / SHA | UNS / PAR / SHA | UNS / COM / SHA |
| PIPE-SERVICE-0055 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SERVICE-0056 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SERVICE-0063 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / APP |
| PIPE-SERVICE-0065 (0) | SUP / PAR / NA | SUP / MIS / NA | SUP / PAR / NA | - / COM / - |
| PIPE-SUBSCRIBE-0004 (0) | SUP / COM / NA | SUP / COM / APP | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0005 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0012 (0) | SUP / COM / NA | SUP / MIS / NA | SUP / COM / NA | UNS / PAR / SHA |
| PIPE-SUBSCRIBE-0015 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / APP | NA / MIS / NA |
| PIPE-SUBSCRIBE-0019 (0) | UNS / COM / SHA | UNS / - / SHA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0022 (0) | SUP / COM / OVR | SUP / COM / OVR | SUP / COM / NA | SUP / COM / OVR |
| PIPE-SUBSCRIBE-0029 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0036 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0049 (0) | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0051 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-SUBSCRIBE-0056 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0066 (0) | UNS / MIS / SHA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0077 (0) | UNS / PAR / SHA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-SUBSCRIBE-0079 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-SUBSCRIBE-0081 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0084 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | - / COM / - |
| PIPE-SUBSCRIBE-0088 (0) | SUP / COM / NA | UNS / COM / SHA | UNS / COM / SHA | - / COM / - |
| PIPE-SUBSCRIBE-0094 (0) | UNS / PAR / SHA | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-SUBSCRIBE-0099 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-SUBSCRIBE-0101 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-SUBSCRIBE-0105 (0) | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-SUBSCRIBE-0112 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-SUBSCRIBE-0123 (0) | SUP / COM / NA | SUP / COM / NA | - / COM / - | - / COM / - |
| PIPE-SUBSCRIBE-0127 (0) | NA / PAR / NA | NA / MIS / APP | NA / MIS / NA | NA / MIS / NA |
| PIPE-TERMINATE-0004 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-TERMINATE-0010 (0) | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / - |
| PIPE-TERMINATE-0013 (0) | SUP / PAR / NA | - / COM / - | - / COM / - | UNS / COM / SHA |
| PIPE-TERMINATE-0029 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-TERMINATE-0034 (0) | SUP / MIS / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-TERMINATE-0043 (0) | SUP / COM / NA | SUP / COM / - | SUP / COM / NA | SUP / COM / NA |
| PIPE-TERMINATE-0049 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA |
| PIPE-TERMINATE-0053 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / COM / NA |
| PIPE-TERMINATE-0069 (0) | SUP / COM / NA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-TERMINATE-0071 (0) | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR |
| PIPE-TERMINATE-0073 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-TERMINATE-0085 (0) | UNS / PAR / SHA | UNS / COM / SHA | - / COM / - | UNS / COM / SHA |
| PIPE-TERMINATE-0090 (0) | SUP / PAR / NA | SUP / COM / NA | - / COM / - | SUP / COM / NA |
| PIPE-TERMINATE-0092 (0) | UNS / PAR / SHA | NA / MIS / NA | NA / MIS / NA | NA / PAR / NA |
| PIPE-TERMINATE-0095 (0) | SUP / PAR / NA | NA / PAR / NA | UNS / PAR / SHA | NA / PAR / NA |
| PIPE-TERMINATE-0096 (0) | UNS / COM / SHA | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA |
| PIPE-TERMINATE-0104 (0) | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA | SUP / COM / NA |
| PIPE-TERMINATE-0105 (0) | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR | NA / MIS / OVR |
| PIPE-TERMINATE-0113 (0) | SUP / COM / NA | NA / COM / NA | NA / COM / NA | NA / COM / NA |
| PIPE-TERMINATE-0115 (0) | SUP / COM / NA | - / COM / - | SUP / COM / NA | UNS / COM / SHA |
| PIPE-USIM-0007 (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| PIPE-USIM-0012 (0) | SUP / MIS / NA | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / OVR |
| PIPE-USIM-0027 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0030 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | - / COM / - |
| PIPE-USIM-0036 (0) | SUP / PAR / NA | SUP / COM / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-USIM-0041 (0) | SUP / PAR / NA | UNS / PAR / SHA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-USIM-0049 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | UNS / COM / SHA |
| PIPE-USIM-0054 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0060 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-USIM-0067 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0075 (0) | SUP / COM / NA | UNS / COM / SHA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0077 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0083 (0) | SUP / COM / NA | SUP / COM / APP | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0086 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0095 (0) | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA | SUP / COM / NA |
| PIPE-USIM-0097 (0) | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA | SUP / PAR / NA |
| PIPE-USIM-0099 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA |
| PIPE-USIM-0104 (0) | UNS / COM / SHA | UNS / COM / SHA | UNS / COM / SHA | SUP / COM / NA |
| REG-FAQ_COMPOUND (0) | NA / MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP | NA / MIS/MIS / APP |
| REG-MIXED (0) | UNS / PAR / SHA | UNS / PAR / SHA | UNS / PAR / SHA | UNS / PAR / SHA |
| REG-NO_EVIDENCE (0) | UNS / MIS / SHA | SUP / NA / NA | - / - / - | SUP / MIS / NA |
| REG-STORE (0) | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP | NA / MIS / APP |
| REG-UNRELATED (0) | NA / NA / APP | NA / NA / APP | NA / NA / APP | NA / NA / APP |

</details>
