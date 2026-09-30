# TELME-82 — 답변 품질 재현 및 사람 검토

## 목적

고정한 질문과 검색 근거를 재생하는 평가 도구를 보완하고, 기존 EXAONE 수정 전후 결과와 Bedrock Judge의 `unsupported` 판정을 근거와 대조한다. 평가 프로브·재현성 테스트·중간 결과 문서를 다루며 AnswerGuard 기능 변경은 이 작업에 포함하지 않는다. 사람 최종 판정과 최종 성능 평가 완료를 선언하는 PR이 아니다.

## 지민님 확인 항목과 현재 결론

| 확인 항목 | 이 PR에서 적용한 내용 | 현재 결론 |
| --- | --- | --- |
| 무엇을 비교하는가 | Guard 수정 전후와 EXAONE/Bedrock 생성 모델 비교를 분리 | 두 비교의 독립변수가 다르므로 한 결과표로 섞지 않음 |
| 무엇을 고정하는가 | eval ID·질문·검색 FAQ 전체, temperature, max tokens를 기록하고 replay 입력 해시를 저장 | 검색 근거가 하나라도 누락·변경되면 같은 조건 비교로 인정하지 않음 |
| 비용 | Bedrock 입력·출력 토큰, 리전·티어·단가·단가 출처·기준일과 예상 USD를 저장 | 과거 실행은 단가·출처가 없어 금액 확정 불가. 이후 유료 실행은 가격 메타데이터 없으면 시작하지 않음 |
| 어떤 기준으로 판단하는가 | 규칙 위반, 근거 충실성, 답변 거절 적절성, 사람 판정을 분리 | Judge `unsupported`만으로 환각률이나 모델 우열을 확정하지 않음 |
| 반복성 | `comparison_id`, `repeat_index`, replay SHA-256을 결과에 기록 | 현재 저장 결과는 1회 실행이므로 최종 모델 비교가 아님. 같은 조건 3회가 필요함 |

이 PR이 확정하는 것은 **비교 실행의 입력·비용·식별 정보를 빠뜨리지 않는 계약**이다. 현재 보유한 결과만으로 “Bedrock이 더 좋다”, “Guard 적용 후 환각률이 낮아졌다”는 결론은 내리지 않는다.

### 평가 항목 구현 상태

| 평가 항목 | 현재 상태 | 이번 PR에서 할 수 있는 것 | 아직 필요한 것 |
| --- | --- | --- | --- |
| Sentence Groundedness | 부분 구현 | `GROUNDED`이고 근거가 있는 최종 답변을 문장별 `supported / unsupported / irrelevant`로 분류 | Judge 변동·근거 범위 불일치 보정과 사람 표본 검토 |
| Claim-level Unsupported Claim Rate | 미구현 | 혼합 문장 후보를 CSV에서 사람이 나눠 검토 | 원자 claim 분리, claim ID, 전체 claim 분모와 확정 라벨 |
| Abstention evaluation | 부분 구현 | `NO_EVIDENCE` 후보에서 적절한 보류와 과도한 보류를 사람 검토 | 답변 가능/불가 정답 라벨 기반 precision·recall 집계 |
| Guard Precision / Recall | 미구현 | 동일 원시 출력에 Guard 전후를 적용하는 실험 조건 정의 | TP·FP·FN·TN을 계산할 원시 출력과 사람 정답 |
| Gold Context vs Retrieved Context | 미구현 | Retrieved Context replay와 정답 FAQ 식별자 보존 | 같은 질문을 Gold/Retrieved 각각으로 생성·평가하는 별도 실험 |

`GROUNDED` 상태는 환각 검사를 통과했다는 뜻이 아니다. 현재 생성 파이프라인이 실제 답변을 반환했다는 상태이며, Groundedness는 그 후 Judge와 사람이 별도로 평가한다.

## 실험 조건

| 항목 | 조건 |
| --- | --- |
| 평가 질문 | `eval_questions_130.json` 130건 + `eval_answer_quality_20.json` 20건, 총 150건 |
| 생성 모델 | Ollama `exaone3.5:7.8b` |
| 생성 설정 | temperature 0, max tokens 1,024 |
| 검색 근거 | 저장된 검색 결과를 재생. 수정 전후 150/150 문항에서 eval ID·질문·검색 FAQ 목록이 일치 |
| 검색 정보 | 검색 결과에 `Q_A`와 `QUESTION_ONLY` variant가 기록됨. 이번 평가는 검색 방식 자체를 비교하지 않음 |
| Judge | Bedrock `openai.gpt-oss-120b-1:0`, temperature 0, reasoning effort `low` |
| 비용 | 이번 PR 검증은 로컬 단위 테스트뿐이며 Bedrock을 호출하지 않음. 기존 Bedrock 실행은 토큰만 저장되어 금액 확정 불가 |

실험 원본은 별도 개발 작업 폴더 `TEL-ME_BE-local/.measure/`에 보관한다. 현재 TELME-82 작업 폴더에는 `.measure/`가 없다. 아래 수치는 기존 저장 결과의 집계이며 이번 PR 코드로 새로 생성·채점한 결과가 아니다. 이 문서는 결과 수치와 사람이 확인할 후보를 추적한다. API 키·개인 자격 증명은 저장하지 않는다.

## 비교 설계

### 1. Guard 수정 전후 비교

독립변수는 **Guard 구현** 하나다. 같은 원시 LLM 출력·질문·검색 근거를 기존 Guard와 수정 Guard에 각각 적용해야 Guard 효과로 인정한다. 현재 저장된 전후 결과는 질문과 검색 근거는 같지만 HALLU-017/020의 최종 생성 답변이 다르다. 따라서 현재 수치는 목표 사례의 변화 확인에는 사용할 수 있지만 Guard 단독 성능 비교에는 사용할 수 없다.

### 2. EXAONE과 Bedrock 생성 비교

독립변수는 **RAG 답변 생성 모델** 하나다. Routing·FAQ 검색·검색 순위·프롬프트·AnswerGuard·Judge를 고정하며, Bedrock 실행은 EXAONE 실행의 저장된 `sources`를 replay한다. Routing 등 다른 LLM 호출은 기존 클라이언트를 유지하고 `RAG_ANSWER` 호출만 Bedrock으로 전환한다.

모델 비교로 인정하려면 다음 조건을 모두 충족해야 한다.

1. 150개 eval ID와 질문이 동일하다.
2. 각 문항의 FAQ ID·slot ID·순위·점수·질문·답변이 동일하며 replay 파일 SHA-256이 기록된다.
3. temperature 0, max tokens 1,024와 동일한 프롬프트·Guard 버전을 사용한다.
4. 각 모델을 같은 조건으로 최소 3회 실행하고 `comparison_id`와 `repeat_index`를 기록한다.
5. 같은 Judge 버전으로 후보를 만들고, 불일치·critical 사례 및 통과 표본 10%를 사람이 확인한다.

현재 보유한 EXAONE/Bedrock 결과는 동일 검색 근거를 사용했지만 1회 결과이며 프롬프트·Guard 커밋 해시가 없다. **예비 결과**로만 사용한다.

#### 현재 저장된 모델 비교 예비 결과

두 생성 결과는 150/150 문항에서 eval ID·질문·검색 FAQ가 같다. 85개 `NO_SEARCH` 문항을 포함해 최종 답변 문자열이 같은 문항은 87개이며, 상태가 다른 문항은 12개다.

| 생성 모델 | GROUNDED | NO_EVIDENCE | BLOCKED | NO_SEARCH |
| --- | ---: | ---: | ---: | ---: |
| EXAONE 3.5 7.8B | 52 | 8 | 5 | 85 |
| Bedrock GPT-OSS 120B | 62 | 1 | 2 | 85 |

동일한 Bedrock Judge 저장 결과는 다음과 같다.

| 생성 모델 | Judge 문장 | supported | unsupported | irrelevant | 참고용 unsupported 비율 |
| --- | ---: | ---: | ---: | ---: | ---: |
| EXAONE 3.5 7.8B | 112 | 97 | 6 | 9 | 5.36% |
| Bedrock GPT-OSS 120B | 95 | 93 | 0 | 2 | 0.00% |

이 표만으로 Bedrock이 더 좋다고 결론 내리지 않는다. 생성한 문장 수가 112개와 95개로 다르고, 1회 실행이며, Bedrock 답변을 같은 계열의 Bedrock 모델이 평가해 `selfJudge=true`에 해당한다. 또한 `NO_EVIDENCE` 감소가 답변 가능성 개선인지 근거 없는 답변 증가인지 사람 판정 전에는 구분할 수 없다. 현재 사람 검토 후보에는 Bedrock 보류 사례만 일부 포함되어 있어 전체 95문장에 대한 사람 검증도 끝나지 않았다.

### 3. 이번 비교에서 제외하는 것

이중 벡터, Top-K, threshold, reranker는 이번 PR의 독립변수가 아니다. 검색 방식 비교는 별도 실험에서 Recall@1/3, MRR@3, 무관 질문 거부율과 함께 평가한다. 검색 변경과 생성 모델 변경을 같은 실험에서 동시에 적용하지 않는다.

## 비용 기록 기준

Bedrock 예상 비용은 다음 식으로 계산한다.

```text
estimated_cost_usd
= input_tokens × input_usd_per_million_tokens / 1,000,000
+ output_tokens × output_usd_per_million_tokens / 1,000,000
```

기존 frozen Bedrock 실행은 150문항에 입력 42,250토큰, 출력 4,019토큰을 사용했다. 당시 메타데이터에 리전별 단가·티어·가격 기준일이 없으므로 정확한 USD 금액은 소급 확정하지 않는다. 새 실행은 다음 값을 필수로 받으며, 누락되면 유료 호출 전에 실패한다.

- `AWS_REGION`
- `TELME_PROBE_BEDROCK_TIER`
- `TELME_PROBE_INPUT_USD_PER_MTOK`
- `TELME_PROBE_OUTPUT_USD_PER_MTOK`
- `TELME_PROBE_PRICE_SOURCE`
- `TELME_PROBE_PRICE_AS_OF`

모든 EXAONE·Bedrock 비교 실행은 `TELME_PROBE_COMPARISON_ID`, `TELME_PROBE_CODE_REVISION`과 1 이상의 `TELME_PROBE_REPEAT_INDEX`도 필수다. 동일 비교 ID 아래 반복번호 1·2·3을 각각 실행한다. 프롬프트·Guard·평가셋 파일 SHA-256은 실행기가 자동으로 기록한다.

단가는 실행 당일 [AWS Bedrock 공식 요금표](https://aws.amazon.com/bedrock/pricing/)에서 리전과 추론 티어가 일치하는 값을 사용한다. Ollama의 외부 API 과금은 0 USD로 구분하되, 로컬 GPU·전력·운영 비용은 이번 측정에 포함하지 않는다. Bedrock 예상 비용과 실제 AWS 청구 금액은 환율·세금·티어·계정 조건으로 달라질 수 있다.

## 판정 기준

### 자동 평가

| 항목 | 계산·판정 방식 | 용도 |
| --- | --- | --- |
| Critical 규칙 위반 | 근거 밖 금액·정책, 의미 반전, 근거 없는 업무 절차 건수 | 1건이라도 있으면 품질 통과로 선언하지 않음 |
| Judge unsupported 비율 | `unsupported 문장 / Judge가 채점한 문장` | 실패 후보 탐색용. 사람 확정 환각률로 사용하지 않음 |
| 사람 확인 미지원 주장률 | `UNSUPPORTED 세부 주장 / 사람이 검토한 전체 세부 주장` | claim 분리와 전수/표본 범위가 기록된 경우에만 계산 |
| 적절한 보류 | 근거가 답을 제공하지 못한 문항에서 `NO_EVIDENCE` | 환각 회피 여부 확인 |
| 과도한 보류 | 근거가 답을 제공하는데 `NO_EVIDENCE` | 안전성 개선으로 답변 가능률이 떨어지는지 확인 |
| 답변 가능률 | `실제 답변을 낸 문항 / 검색 근거가 있는 문항` | unsupported 감소와 함께 해석 |
| Guard Precision | `잘못된 원시 주장을 올바르게 차단한 수 / Guard가 차단한 전체 주장` | 정상 주장의 오차단 확인 |
| Guard Recall | `잘못된 원시 주장을 올바르게 차단한 수 / 원시 출력의 전체 잘못된 주장` | Guard를 통과한 환각 확인 |

### 사람 판정 라벨

`human_label`은 다음 중 하나를 사용한다.

- `SUPPORTED`: 표시된 세부 주장이 검색 근거에 있음
- `UNSUPPORTED`: 세부 주장이 검색 근거에 없음
- `PARTIALLY_UNSUPPORTED`: 한 문장에 지원·미지원 주장이 함께 있음
- `APPROPRIATE_ABSTENTION`: 근거가 질문에 답하지 못해 보류가 적절함
- `OVER_REFUSAL`: 근거가 답을 제공하지만 보류함
- `REVIEW_CONTEXT`: 일반 안내·정책 경계로 팀 기준 합의가 필요함

전후 변화 라벨인 `UNSUPPORTED_BEFORE_SUPPORTED_AFTER`, `UNSUPPORTED_BEFORE_CORRECT_ABSTENTION_AFTER`는 비교 요약에만 사용하며, 최종 claim 집계에서는 전·후를 위 기본 라벨로 나눠 센다.

### 최종 비교 통과 조건

비교 입력 검증, 모델별 3회 반복, 가격·지연 기록, 사람 판정이 모두 끝나기 전에는 모델 우열이나 전체 개선을 선언하지 않는다. 최종 채택 시에는 critical 위반 0건을 우선 확인하고, 사람 확인 미지원·과도한 보류·답변 가능률·비용·지연을 함께 제시한다. 팀이 목표 수치를 합의하기 전에는 임의의 평균 점수 하나로 통과선을 만들지 않는다.

150문항 중 65문항은 검색 근거를 받았고 85문항은 검색 결과가 없었다. EXAONE의 파이프라인 상태는 수정 전 `GROUNDED 52 / NO_EVIDENCE 8 / BLOCKED 5 / NO_SEARCH 85`, 수정 후 `GROUNDED 51 / NO_EVIDENCE 9 / BLOCKED 5 / NO_SEARCH 85`였다. 이 상태 변화 역시 특정 가드 수정만의 효과로 단정하지 않는다.

## 수정 전후 Judge 결과

| 실행 | Judge 문장 | supported | unsupported | irrelevant | Judge unsupported 비율 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 수정 전 | 112 | 97 | 5 | 10 | 5/112 = 4.46% |
| 수정 후 | 110 | 95 | 5 | 10 | 5/110 = 4.55% |

이 수치에서는 unsupported 건수가 5건으로 같고 비율은 0.09%p 높아졌다. **전체 환각률이 개선됐다고 할 수 없다.** 수정 전후 unsupported 문항도 달라졌다. 수정 전은 `EVAL-001`, `EVAL-040`, `HALLU-010`, `HALLU-018`, `HALLU-020`; 수정 후는 `EVAL-001`, `EVAL-040`, `HALLU-009`, `HALLU-010`, `HALLU-011`이다. Judge 결과가 달라진 사례와 Guard 수정의 효과는 사람 검토 결과와 구분한다.

수정 전후 150개 답변 가운데 148개는 동일했고 2개는 달랐다. 검색 FAQ 목록은 150개 모두 같았다. 따라서 검색 입력은 고정됐지만 생성 답변이 완전히 고정된 비교는 아니다. 특히 Guard만의 효과를 분리하려면 원시 생성 답변을 저장한 뒤 기존 Guard와 수정 Guard에 각각 넣는 별도 실험이 필요하다.

## 사람 검토 후보

`TELME-82-human-review.csv`에는 11개의 판정 후보와 근거를 기록했다. 기존 로컬 초안은 9건이었지만, 수정 후 Judge 결과에 새로 등장한 `HALLU-009`와 수정 전후 변화를 확인할 `HALLU-017`·`HALLU-020`을 함께 검토해야 해서 후보를 보완했다. `proposed_human_label`은 검토 제안일 뿐 사람 판정이 아니며, `human_label`과 `human_note`는 서희님이 확인한 뒤 채운다. 판정 단위는 Judge가 표시한 문장이다. 혼합 주장은 문장 전체를 무조건 참/거짓으로 처리하지 말고 포함된 세부 주장을 나눠 확인한다.

이번 검토는 Judge의 unsupported 사례, 수정으로 달라진 HALLU-017·HALLU-020, 그리고 답변 불가 처리의 적절성을 확인하는 후보를 포함한다. 따라서 단순히 Judge가 표시한 문장 수만으로 사람 판정 환각률을 계산하지 않는다.

### 서희님 검토용 요약

다음은 AI가 정리한 검토 제안이다. `human_label`과 `human_note`는 11행 모두 비어 있으며, 서희님이 판정한 뒤에만 채운다. 11행은 10개 문항을 뜻한다. HALLU-020의 EXAONE 전후 사례와 Bedrock 보류 사례는 별도 행이다.

| 문항 / 생성기 | 확인할 답변·변화 | 근거에서 확인된 내용 | 제안과 최종 확인 사항 |
| --- | --- | --- | --- |
| EVAL-001 / EXAONE | 이메일·모바일로 설정하면 중복 발송될 수 있다 | BILLING-0105는 수령 방식과 중복 시 고객센터 확인만 안내 | **미지원 제안.** 수령 방식과 중복 발송의 인과관계를 추가했는지 확인 |
| EVAL-040 / EXAONE | 추가 비용은 7,700원 | USIM-0089에 새 유심과 7,700원이 명시됨 | **금액에 대한 Judge 오판.** ‘추가’가 재발급 비용을 뜻하는지 별도 요금을 뜻하는지 확인한 뒤 문장 전체 판정 |
| HALLU-009 / EXAONE | 정확한 비교를 위해 현재 요금제·통신사를 확인하라는 안내 | PLAN-0146에는 가격 비교만 있고 해당 절차는 없음 | **엄격한 Judge 기준상 미지원 제안.** 일반 제안과 절차 안내의 경계를 이유에 기록; 평가 기준을 임의 변경하지 않음 |
| HALLU-010 / EXAONE | 이메일은 무료이며 즉시 확인 가능해 편리하다 | 이메일 무료와 편한 방식 선택 안내는 있음. 즉시 확인 근거는 없음 | **부분 미지원 제안.** 무료 / 즉시 확인 / 편리함을 나누어 검토 |
| HALLU-011 / EXAONE | 매장 재발급 7,700원, 즉시 수령 | USIM-0001/0077/0073에 금액과 즉시 발급이 명시됨 | **표시된 문장은 지원됨 제안.** 별도 문장인 온라인 동일 비용 주장까지 자동 승인하지 않음 |
| HALLU-018 / EXAONE | 추가 문의는 고객센터에서 확인 | BILLING-0074는 미납 관련 제한만 고객센터 확인을 안내 | **문맥 검토 필요.** 제한 확인 안내를 일반 문의·신청 안내로 확장했는지 확인 |
| HALLU-017 / EXAONE | ‘특별한 경우가 아니라면…’ 문장 제거 | NAME_CHANGE-0014/0023/0086에 30분 처리, 미납 시 완납 조건이 있음 | **전: 미지원 / 후: 지원 제안.** 예외 문구는 없으며 30분·미납 안내는 남음. 선택한 Judge 실행은 수정 전 문장을 오류로 세지 않았음 |
| HALLU-020 / EXAONE | ‘총 비용 7,700원’ → 정보 없음 안내 | USIM-0001/0009/0029는 유심 비용·배송 기간만 안내 | **전: 미지원 / 후: 적절한 보류 제안.** 배송비 포함 여부에는 답할 근거가 없음 |
| EVAL-027 / EXAONE | 평일 19시 이후 방문 질문에 정보 없음 안내 | SERVICE-0033은 19시까지 운영, 이후 방문이 어렵다고 명시 | **과도한 보류 제안.** ‘19시 이후 방문이 어렵다’고 답할 수 있는지 확인 |
| EVAL-004 / Bedrock | 가상계좌 정의 질문에 정보 없음 안내 | BILLING-0026에 요금 납부용 입금 전용 계좌라는 정의가 있음 | **과도한 보류 제안.** 정의를 답할 근거가 있음 |
| HALLU-020 / Bedrock | 배송비 질문에 정보 없음 안내 | 재발급 비용·배송 기간만 있고 배송비 포함 여부는 없음 | **적절한 보류 제안.** 원본에는 마침표 없는 거절이 GROUNDED로 기록돼 있으므로 내용 판정과 원본 상태를 구분 |

검토 시 `human_label`에는 최종 결론, `human_note`에는 해당 FAQ 근거와 이유를 적는다. 부분 미지원은 어떤 세부 주장에 근거가 없는지 기록한다. CSV의 `proposed_human_label`은 기존 초안이며 위 표의 유보 사항까지 읽고 판단해야 한다. 빈칸은 미판정이지 정상 판정이 아니다.

현재 후보만으로 전체 오류 수를 확정하지 않는다. Judge가 supported/irrelevant로 분류한 답변과 보류 답변도 별도 검토해야 한다.

## 프로브 변경

- 생성 모델·모델명·temperature·max tokens·검색 재생 여부와 FAQ 근거를 결과 JSON에 기록한다.
- Bedrock 생성은 실행 전 명시적 유료 호출 허용이 없으면 시작하지 않는다.
- Bedrock 실패 시 완료된 부분 결과와 누적 토큰 사용량을 파일에 남기고 중단 상태 및 실패 문항 ID를 기록한다.
- 같은 실행의 TXT·JSON·사용량 메타데이터는 하나의 실행 ID로 묶는다.
- 재생 입력의 eval ID·질문 구성이 평가셋과 다르면 실행을 거부한다.
- 재생 입력의 최상위 배열, 문항별 sources 배열, FAQ question/answer 필드를 확인한다. 누락·손상된 근거를 정상적인 검색 결과 없음으로 처리하지 않는다. 명시적인 빈 sources 배열은 허용한다.

## 로컬 검증

- `AnswerQualityBaselineProbeTest`: 21건 통과
- `AnswerGuardUserEvidenceTest`: 42건 통과
- 합계 63건 통과, 실패·오류·스킵 0건
- 테스트 과정에서 Bedrock API를 호출하지 않음
- 이 브랜치에서 전체 애플리케이션 통합 테스트는 실행하지 않음

위 검증은 TELME-82 브랜치 기준이다. 별도 Guard 개발 브랜치에서 실행한 182개 테스트나 추가 Guard 규칙의 150문항 재적용 결과는 이 PR의 검증 수치에 합산하지 않는다.

부분 실패 테스트는 생성기를 실제로 중단시키는 통합 테스트가 아니라, 중단 상태를 나타내는 부분 결과와 사용량 메타데이터를 실제 파일로 쓰고 다시 읽는 단위 테스트다. 실제 네트워크 실패 시나리오는 유료 API 호출 없이 검증하지 않았다.

## 한계와 다음 단계

- `unsupported / Judge가 채점한 문장 수`는 Judge 판정 비율이지 사람 확정 환각률이나 claim-level groundedness가 아니다.
- 문장 수가 112개에서 110개로 달라져 수정 전후 비율을 단순 개선으로 해석할 수 없다.
- 사람 검토 후보의 최종 라벨은 아직 미확정이다. 후보 CSV의 빈 `human_label`, `human_note`를 사람이 검토해 채운 뒤 결과를 확정한다.
- Bedrock Judge는 동일한 답변을 다시 채점했을 때도 판정이 바뀐 기록이 있으므로 단일 Judge 실행 결과를 확정치로 쓰지 않는다.
- Generator는 FAQ 답변을 사실 근거로 사용하지만 현재 Judge 입력은 FAQ 질문과 답변을 함께 문맥으로 사용한다. 질문에만 있는 전제를 Judge가 근거로 인정할 수 있어 두 근거 범위를 맞춰야 한다.
- Judge는 `GROUNDED` 상태만 채점하므로 `BLOCKED`와 `NO_EVIDENCE`의 과도한 차단·보류는 unsupported 비율에 나타나지 않는다.
- 다음의 더 엄밀한 Guard 단독 비교는 원시 LLM 출력과 고정 검색 근거를 보존하고, 같은 원시 출력에 기존 Guard·수정 Guard를 각각 적용해 비교한다.

## 재현 자료 위치와 식별

- 수정 전/후 Judge 집계: `.measure/judge-pre-fix-20260930.json`, `.measure/judge-post-fix-20260930.json`
- 수정 전 EXAONE: `.measure/paired-exaone/baseline-ollama-20260930-023825-661.json`
- 수정 후 EXAONE: `.measure/postfix2-20260930/baseline-ollama-20260930-112816-072.json` (`postfix-20260930/`은 중간 재실행)
- Bedrock 보류 후보 대조: `.measure/frozen-baseline-20260930/bedrock/baseline-bedrock-20260930-085006-387.json`
- 모델 비교 Judge: `.measure/frozen-baseline-20260930/judge-bedrock-exaone.json`, `.measure/frozen-baseline-20260930/judge-bedrock-bedrock.json`
- Bedrock 토큰 사용량: `.measure/frozen-baseline-20260930/bedrock/baseline-bedrock-20260930-085006-389.meta.json`
- 사람 검토 후보 원본: `.measure/frozen-baseline-20260930/human-review-candidates.csv`

위 경로는 모두 별도 작업 폴더 `TEL-ME_BE-local` 기준이다. 이 PR만 내려받으면 해당 원본이 생기지 않는다. 리뷰 시 정확한 원본 비교가 필요하면 작성자에게 해당 파일을 요청한다. 재실행하면 모델/Judge 변동으로 같은 결과가 보장되지 않으므로 기존 원본은 보존한다.

| 원본 | SHA-256 |
| --- | --- |
| 수정 전 EXAONE | `4955617445f15c4ea544bef2837086554944dac0ea5c8d4278430f979eeb8182` |
| 수정 후 EXAONE | `aca60347cf7c9360c47ca284ac043da4167bb343cd0fa20577741b1f73292f9a` |
| 수정 전 Judge | `68e1baa20a2259923c2b6ec40bb004b614b28a39b585394a9c5c4b1f036b9409` |
| 수정 후 Judge | `e5f210064098f1817d2f5585e2e63cb7dad9deccef84993a7a4b15cc80f73863` |
| Bedrock 보류 후보 원본 | `3329bb48da188aecf7861febdaa0329ed6ae4a5301fc8c3d645f681e3cf5fc1e` |
| EXAONE 모델 비교 Judge | `59e376294428eaed24476a34d465d0dbc395306366daf234a9d197c93ac752a7` |
| Bedrock 모델 비교 Judge | `1e0d5312d86431dd156ec8bee574406d81c865609b172025011102abde5f8c27` |
| Bedrock 토큰 메타데이터 | `27531d059e6422b31b25cc9716d57c50be05974968c74af525131950dc088406` |

과거 실행에는 정확한 실행 커밋과 Guard/프롬프트 해시가 없어 이 파일 해시만으로 코드까지 재현된다고 보장하지 않는다. `.measure/`와 자격 증명은 PR에 포함하지 않는다. 핵심 조건·수치·검토 후보는 이 문서와 동봉 CSV로 공유한다.
