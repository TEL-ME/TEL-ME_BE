# 2026-10-18 답변 생성 모델 채점 (엑사원 vs gpt-oss)

답변 생성(`RagAnswerGenerator`, TELME-182 `GPT_OSS_SWITCH.md`의 13번)을 어느 모델로 할지 이 Judge로 정한다. 엑사원과 gpt-oss-120b가 같은 입력에 만든 답을 세 Judge 모델로 채점했다. 답변은 다른 이슈에서 만들었고 여기서는 채점만 한다. 라벨 쟁점은 1차 사람 검토를 반영했고([LABEL_REVIEW.md](LABEL_REVIEW.md)), Judge 판정이 갈린 답 109개는 사람이 판정했다(아래 "사람 판정").

**결정: 13번 답변 생성은 gpt-oss로 한다.**

## 요약

- **사람 판정 반영 후 B 통과율은 엑사원 67%, gpt-oss 76~77%다**(Judge가 갈렸는데 사람이 안 본 답은 any-fail로 채움). Judge만으로 잰 차이(6~7%p)보다 커졌다. Judge 다수결은 엑사원이 덧붙인 근거 없는 말을 통과시키고 gpt-oss의 짧은 답을 떨어뜨리는 쪽으로 틀렸다.
- **A(정답 근거 고정, 217개)**: 통과율 엑사원 43%, gpt-oss 73~74%(any-fail).
  - 같은 입력에서 gpt-oss만 통과한 것이 61~65개, 엑사원만 통과한 것이 4~6개다.
  - 차이는 대부분 근거에 없는 말이다. 근거 없는 주장이 엑사원 72개, gpt-oss 13~19개다.
  - 답이 없는 질문 95개에서는 엑사원 37%, gpt-oss 82~83%가 통과했다.
- **B(TELME-182에서 정한 앞단을 실제로 통과, 131개)**: 통과율 엑사원 60%, gpt-oss 66~68%(any-fail).
  - 다수결로 합치면 엑사원 79%, gpt-oss 78~79%로 비슷하다.
  - 근거 없는 주장은 엑사원 24개, gpt-oss 9~12개다. 과도한 거절은 엑사원 4개, gpt-oss 10~12개다.
- **B에서 gpt-oss가 진 입력 중 6개는 숫자를 바꿔 쓴 경우다.**
  - `유심 재발급 비용`에 근거(7,700원)와 다른 `7,800원`이라고 써서, 가드가 답을 거절 문장으로 바꿨다. 이 질문에서 21번 중 20번 그랬다.
  - 가드가 막아서 틀린 금액이 나가지는 않지만, 고객은 답을 받지 못한다.
- gpt-oss 세 회차의 통과율 차이는 2%p 이내다. 엑사원은 세 회차 답이 모두 같다.
- **판단**: 13번은 gpt-oss로 정한다. 라벨 검토와 판정 검토를 거칠수록 차이가 커졌다.

## 입력과 답변

| | A | B |
|---|---|---|
| 입력 | 평가셋의 정답 FAQ를 근거로 고정한 217개(eval180 108, answer_quality_20 19, holdout90 90). 앞단과 검색을 거치지 않는다 | TELME-182에서 정한 앞단으로 다시 캡처한 131개. 시나리오 236개, 고객 발화 344개 중 근거가 잡힌 답변 요청이다 |
| 앞단 | 없음 | 1·2·4·5·6·7·10·11번 gpt-oss, 3번 끔, 회색 구간 판정·되묻기 켬. 측정 DB는 `telme-flow-postgres`(5440)의 복제본, 질문 벡터 1,152 |
| 답변 | TELME-186(#173) 결과, 프롬프트 `rag-answer-v4.7` | TELME-182 결과, 프롬프트 `rag-answer-v4.8` |
| 회차 | 엑사원 3회(모두 같음), gpt-oss 3회(Tokyo) | 같음 |

답변 원본은 각 이슈의 `scripts/answer-generation-evaluation/results/`에 있다. 여기의 `capture-A.json.gz`, `capture-B.json.gz`에는 답변, 생성에 넘긴 근거, 라벨, 앞 대화가 모두 들어 있어 이것만으로 다시 채점할 수 있다.

## 방법

| 항목 | 내용 |
|---|---|
| 채점 | `run_bedrock_judge.py`를 그대로 쓴다. 세 축과 코드 규칙이 같다. 근거성은 생성에 실제로 넘긴 FAQ만 근거로 보고, 충족도는 카탈로그 정답 묶음과 필수 사실로 보고, 보류는 거절 여부와 근거로 답할 수 있었는지를 본다. Judge는 Qwen3 235B, DeepSeek V3.2(Oregon), Mistral Large 3 |
| 합치기 | any-fail(하나라도 실패면 실패, 기본)과 다수결(`combine_judges.py` 함수) |
| 중복 | 같은 입력에 같은 답은 한 번만 채점하고 회차로 펼친다. 채점 턴은 A 546개(엑사원 217, gpt-oss 329), B 379개(엑사원 131, gpt-oss 248) |
| 이전 대화 | B는 앞 턴(고객 발화, 캡처 때 앱 응답)을 두 모델에 같게 넘긴다(`judge_answers.py`가 실행기를 감싸 넘김). 되묻기에 답한 턴은 원래 질문과 되묻기를 이전 대화로, 이번 고객 발화를 판정 질문으로 넘긴다(`clarification_dialogue`와 같은 방식) |
| 비용 | Judge $8.34(Qwen $1.44, DeepSeek $3.53, Mistral $3.36), 연결 확인 12턴 $0.10. 호출 13,383건 중 실패 13건. 라벨 1차 검토 뒤 바뀐 47턴 재판정 $0.36(호출 678건, 실패 0) |

### 라벨

A와 B의 질문마다 FAQ 카탈로그 기준 라벨을 새로 만들었다(`scripts/chat_judge/answer_generation/labels/labels-A.jsonl`, `labels-B.jsonl`).

- **작성:** 에이전트 7개가 질문, 앞 대화, 고객 조건만 보고 카탈로그에서 라벨을 썼다(`LABEL_GUIDE.md`). 모델 답변, 검색 근거, 평가셋 정답은 주지 않았다.
- **검증:** 다른 에이전트 7개가 카탈로그 전체와 대조했다(`VERIFY_GUIDE.md`). A는 평가셋 설계자의 정답 FAQ와도 비교했다.
- **검증 결과:** 그대로 둠 274, 고침 58, 판단 불가 16이다. 고친 것은 대부분 빠진 근거 FAQ를 더한 것이고, 기대 동작을 바꾼 항목은 없다.
- **사람 검토(1차):** 판단 불가와 앱 규칙 충돌 18건을 사람이 보고, 같은 문제로 드러난 A 6건과 함께 18개 입력을 고쳤다([LABEL_REVIEW.md](LABEL_REVIEW.md), `labels/human-reviewed.json`). A는 카탈로그가 아니라 **생성에 넘긴 근거**로 답할 수 있는지를 본다. A는 근거를 고정해 "근거에 없으면 지어내지 않는가"를 보는 시험이라, 넘기지 않은 FAQ에 답이 있다고 ANSWER로 두면 맞게 거절한 답이 실패로 잡히기 때문이다. B는 검색까지 거친 실제 경로라 카탈로그 기준이다.

| | ANSWER | ABSTAIN | PARTIAL_ANSWER | ANSWER_OR_CLARIFY |
|---|---|---|---|---|
| A (217) | 107 | 95 | 6 | 9 |
| B (131) | 121 | 5 | 1 | 4 |

A의 holdout90은 설계상 답이 없는 질문이다. 넘긴 근거로 답할 수 있는 것만 ANSWER로 남겼다(예: H-111, 넘긴 근거의 "최대 5회선").

## 결과

통과율은 판정이 확정된 답 중 통과한 비율이다. 미확정은 분모에서 뺀다. gpt-oss는 세 회차의 범위다.

### A (217개)

| | 엑사원 | gpt-oss |
|---|---|---|
| 통과 (any-fail) | 43.2% (86/199) | 73.2~74.2% |
| 통과 (다수결) | 57.5% | 80.8~84.7% |
| 근거 없는 주장 | 72 | 13~19 |
| 과도한 거절 | 16 | 16~17 |
| 미확정 | 18 | 16~19 |

| 묶음 | 입력 | 엑사원 | gpt-oss |
|---|---|---|---|
| 답할 수 있는 질문 | 122 | 48.6% | 65.0~66.0% |
| 답 없음(ABSTAIN) | 95 | 37.2% (근거 없는 주장 44) | 82.1~83.2% (근거 없는 주장 3~4) |
| eval180 | 108 | 50.5% | 70.2~72.2% |
| holdout90 | 90 | 37.1% | 83.3~84.4% |
| answer_quality_20 | 19 | 35.3% | 29.4~35.3% |
| 라벨 검토 대상 제외 | 175 | 48.4% | 78.3~80.5% |

answer_quality_20은 근거가 일부만 답하는 질문이라 두 모델 모두 낮다. 대부분 둘 다 실패한 입력이다(19개 중 9~10개).

### B (131개)

| | 엑사원 | gpt-oss |
|---|---|---|
| 통과 (any-fail) | 60.5% (69/114) | 66.1~67.9% |
| 통과 (다수결) | 78.7% | 77.7~79.2% |
| 근거 없는 주장 | 24 | 9~12 |
| 과도한 거절 | 4 | 10~12 |
| 미확정 | 17 | 22~25 |
| 같은 입력에서 한쪽만 통과 | 11~12 | 19~20 |

| 묶음 | 입력 | 엑사원 | gpt-oss |
|---|---|---|---|
| FAQ+매장 | 27 | 56.0% | 82.6~91.3% |
| 멀티턴 | 25 | 47.8% | 65.0~68.2% |
| 대화 흐름 | 13 | 50.0% | 63.6~70.0% |
| 단일 FAQ | 15 | 54.5% | 60.0% |
| 되묻기 | 20 | 83.3% | 66.7~72.2% |
| 복합 FAQ | 31 | 66.7% | 52.0% |
| 답 없음(ABSTAIN) | 5 | 0% | 0% |

되묻기와 복합 FAQ에서 엑사원만 통과한 입력 10개(gpt-oss 1회차 기준)를 열어 봤다.

| 원인 | 개수 | 예 |
|---|---|---|
| gpt-oss가 `7,800원`이라고 써서 가드가 거절 문장으로 바꿈 | 6 | `유심 재발급 비용` 하위 질문 |
| gpt-oss 답이 짧아 필수 사실이 빠짐 | 2 | 택배 2~3 영업일, 7일 기간권 39,000원 |
| Judge 하나가 맞는 답을 근거 없음으로 봄(any-fail) | 2 | `본인 신분증만 준비하시면 됩니다` |

### 7,800원

- B의 gpt-oss 답 393개 중 가드가 거절 문장으로 바꾼 것은 31개다. 그중 26개가 근거에 없는 숫자를 쓴 경우였고, 거의 모두 `7,800원`이다. 근거 세 개는 모두 `7,700원`이고, 프롬프트에도 7,800은 없다.
- 질문이 `유심 재발급 비용`이면 21번 중 20번 그랬다. 이 질문은 복합 질문에서 잘린 짧은 하위 질문이다. `그럼비용은?`이면 3번 중 3번 그랬다. 같은 근거라도 질문이 길거나 방법을 물으면 `7,700원`으로 맞게 썼다.
- TELME-178의 `ANSWER_GENERATION_COMPARISON.md`(가드 관련 발견)에도 같은 현상이 있다. B에는 이 질문이 7개 입력으로 들어 있어, B에서 gpt-oss의 과도한 거절 대부분이 이 경우다.

## 사람 판정

Judge 세 모델의 판정이 갈렸거나 미확정인 답을 사람이 통과·실패로 판정했다. B의 결론은 이 판정들로 뒤집힐 수 있는 크기였고(any-fail 차이 입력 7~8개, 갈린 답 40~50개), A는 차이가 커서(입력 60개쯤) 표본으로 Judge가 한쪽으로 치우쳤는지만 봤다.

| | 범위 | 판단 대상(갈린 답) |
|---|---|---|
| B | 두 모델의 승패를 가르는 입력에서 Judge가 갈린 답이 있는 입력 52개 전부 | 82 |
| A | 같은 조건의 입력 69개 중 20개 표본(시드 20261018) | 27 |

- 검토 페이지(`build_review_page.py`)에 입력마다 질문, 앞 대화, 라벨, 생성에 넘긴 근거, 두 모델 답, Judge별 판정과 이유를 나란히 놓고 판정했다. 판정은 `human-verdicts.json`(123개, 판단 대상 109개와 Judge가 일치했지만 함께 본 14개)에 있다.
- **기준**: 근거에 없는 말이 한 문장이라도 있으면 실패다("처리 상황에 따라 달라질 수 있습니다", "폰 없이도 쉽게", "다른 요인들로 인해 조정" 등). 묻는 핵심에 답하지 못해도 실패다(신청 방법을 물었는데 적용 시점만 말함, 비용을 물었는데 수수료 없음을 빠뜨림). 핵심이 맞으면 부가 사실(다른 신청 채널, 요금제 이름)이 빠져도 통과다. 고객이 답한 조건에서 바로 나오는 말("남은 할부금이 없으니 추가 절차 없이")은 근거 있는 것으로 본다. 답 없는 질문에 확인할 수 없다고 하지 않고 근처 사실만 말한 답은 실패다.
- **결과**: 실패는 엑사원 19개(B 14, A 5), gpt-oss 2개(B 1, A 1)다. 엑사원 실패는 대부분 근거에 없는 말을 덧붙인 경우다.

**B 통과율(사람 판정 반영)**

| | 엑사원 | gpt-oss |
|---|---|---|
| Judge만(any-fail) | 60.5% | 66.1~67.9% |
| 사람 판정 + 나머지 갈림은 any-fail | **67.2%** (88/131) | **75.6~77.1%** |
| 사람 판정 + 나머지 갈림은 다수결 | 73.4% | 79.2~80.8% |

사람이 안 본 갈린 답이 모델마다 12~17개 남아 있다. 두 모델의 승패가 같은 입력이라 비교에는 거의 영향이 없다.

**Judge 결합과 사람 판정 비교(사람이 본 갈린 답)**

| | B 엑사원 (33) | B gpt-oss (49) | A 엑사원 (13) | A gpt-oss (14) |
|---|---|---|---|---|
| 다수결 일치 | 22 | 43 | 7 | 10 |
| 다수결 통과, 사람 실패 | **5** | 0 | 2 | 0 |
| 다수결 실패, 사람 통과 | 0 | **4** | 2 | 2 |
| 다수결 미확정 | 6 | 2 | 2 | 2 |
| any-fail 실패, 사람 통과 | 5 | 9 | 5 | 8 |
| any-fail 미확정 | 17 | 40 | 4 | 5 |

- B에서 다수결 Judge는 엑사원이 덧붙인 근거 없는 말을 통과시키고(5개), gpt-oss의 짧지만 맞는 답을 떨어뜨렸다(4개). 엑사원에 유리한 쪽으로 틀렸다.
- any-fail은 두 모델 모두에 엄격하다. 미확정은 대부분 Mistral이 주장 문장을 원문 그대로 뽑지 못한 경우다.
- A 표본에서는 한쪽으로 치우치지 않았다. A의 차이(43% 대 73~74%)는 그대로 본다.

## 읽을 때 주의할 점

- **사람 판정은 Judge가 갈린 답에만 했다.** 세 Judge가 같은 판정을 낸 답은 Judge 판정을 그대로 쓴다. 라벨을 또 고치면 그 턴만 다시 판정한다(`--prior`). 남은 2순위 목록은 `labels/human-review.json`에 있다.
- **HALLU-001·002는 하루·한 달 요금만 필수 사실로 둔다.** 답변 프롬프트 4번 규칙이 근거 숫자로 합계를 계산하지 말라고 하기 때문이다. 합계를 쓴 답은 규칙 위반이지만 지금 두 모델의 답은 모두 거절이라 판정 규칙은 따로 두지 않았다.
- **명의변경 대리 서류는 카탈로그 FAQ끼리 다르다.** 위임장을 말하는 FAQ 51개 중 NAME_CHANGE-0001·0067만 인감증명서도 필요하다고 한다. 라벨은 다수 쪽인 "위임장"만 필수 사실로 둔다. FAQ 데이터에서 정리할 일이다.
- **미확정은 대부분 Mistral 쪽이다.** Mistral이 근거성 판정에서 주장 문장을 원문 그대로 뽑지 못한 경우로, A 36개, B 42개다. 답변 모델별 비율은 비슷하다(A 엑사원 16%, gpt-oss 13% / B 17%, 15%).
- **A와 B는 프롬프트 판이 다르다.** A는 통신사 이름이 `LG U+`인 `v4.7`, B는 `TELME`인 `v4.8`이다. A에서 회사 이름이 들어간 답은 엑사원 1개다.
- B의 앞 턴 응답은 캡처 때 앱 기본값(엑사원)이 만든 것이고, 두 모델에 같게 넘겼다.
- 같은 입력을 다시 판정하면 판정의 약 5%가 바뀐다([20261017-label-fixes](../20261017-label-fixes/README.md)). 몇 %p 차이는 그 안에 든다.
- 응답 시간은 비교하지 않는다. gpt-oss 호출은 B에서 중앙값 0.44초였다. 엑사원 시간은 배포 환경(vLLM)에서 잰다.

## 판단

- **A에서는 gpt-oss가 분명히 낫다.** 근거가 질문에 답하지 못할 때 지어내지 않고 거절하는 비율이 크게 높고, 답할 수 있는 질문에서도 앞선다.
- **B에서도 gpt-oss가 낫다.**
  - Judge만으로는 any-fail 6~7%p 차이, 다수결은 같았다. Judge가 갈린 답을 사람이 판정하면 8~10%p 차이다(67.2% 대 75.6~77.1%).
  - 근거 없는 주장은 엑사원의 절반 이하다. 사람 판정에서도 실패는 대부분 엑사원이 덧붙인 근거 없는 말이었다.
  - gpt-oss의 약점은 두 가지다. `7,800원`처럼 근거 숫자를 바꿔 쓰는 실수가 있고, 답이 짧아 필수 사실을 빠뜨리는 경우가 있다. 숫자 실수는 가드가 막아 거절로 끝난다.
- **13번은 gpt-oss로 정한다.** 남은 일은 TELME-182에서 답변 생성용 Bedrock 설정을 넣는 것과, 짧은 `유심 재발급 비용` 질문의 `7,800원` 문제다.

## 재현

```bash
J=scripts/chat_judge/answer_generation
O=docs/chat-judge/results/20261018-answer-generation

# 라벨 입력과 합치기(호출 없음). 라벨 작성·검증은 에이전트가 LABEL_GUIDE.md, VERIFY_GUIDE.md로 했다
python -I -X utf8 $J/prep_label_inputs.py <작업 폴더> <inputs-a.json> <inputs-b182.json> <capture-b182.jsonl>
python -I -X utf8 $J/merge_labels.py <작업 폴더>

# Judge 입력(호출 없음). 답변 파일은 TELME-186(A), TELME-182(B)의 results에 있다
python -I -X utf8 $J/to_capture.py A <inputs-a.json> $J/labels/labels-A.jsonl capture-A.json.gz <answers-a-exaone.jsonl.gz> <answers-a-gptoss.jsonl.gz>
python -I -X utf8 $J/to_capture.py B <inputs-b182.json> $J/labels/labels-B.jsonl capture-B.json.gz <answers-b182-exaone.jsonl.gz> <answers-b182-gptoss.jsonl.gz> --capture <capture-b182.jsonl.gz>

# 채점(유료). 모델마다 따로 돌린다. DeepSeek는 deepseek-v3.2-usw2, --workers 16
python -X utf8 $J/judge_answers.py $O/capture-A.json.gz --model qwen --allow-bedrock-call --workers 8 --out runs/A-qwen.json.gz
# 라벨을 고친 뒤에는 이전 판정을 --prior로 주면 판정 입력이 바뀐 턴만 다시 부른다
python -X utf8 $J/judge_answers.py capture-A-new.json.gz --model qwen --allow-bedrock-call --workers 8 --prior runs/A-qwen.json.gz --out runs2/A-qwen.json.gz

# 집계와 검토 목록(호출 없음). raw.tar.xz를 풀면 raw/에 원시 판정이 나온다. gzip으로 묶어 runs/에 둔다
python -X utf8 $J/aggregate.py A $O/capture-A.json.gz runs/A-qwen.json.gz runs/A-deepseek-v3.2-usw2.json.gz runs/A-mistral-large-3.json.gz --out summary-A.json
python -X utf8 $J/build_review.py <capture-*.json.gz와 runs/가 있는 폴더>

# 검토 페이지와 사람 판정 반영(호출 없음). 페이지는 판정을 artifact 저장소에 남기고, 그것을 human-verdicts.json으로 내보낸다
python -X utf8 $J/build_review_page.py <capture-*.json.gz와 runs/가 있는 폴더> review.html
python -X utf8 $J/apply_human_verdicts.py <capture-*.json.gz와 runs/가 있는 폴더> $O/human-verdicts.json --out $O/human-summary.json
```

Windows의 `tar`는 `D:` 같은 경로를 원격 호스트로 읽으므로 `tar --force-local -xJf`로 푼다.

## 파일

| 파일 | 내용 |
|---|---|
| `scripts/chat_judge/answer_generation/*.py` | 라벨 입력, 라벨 합치기, Judge 입력 변환, 채점 실행, 집계, 검토 목록, 검토 페이지 생성(`build_review_page.py`, `review_page_template.html`), 사람 판정 반영(`apply_human_verdicts.py`) |
| `human-verdicts.json`, `human-summary.json` | 사람 판정 123개와 반영 결과(B 통과율, Judge 결합과 사람 판정 비교) |
| `scripts/chat_judge/answer_generation/LABEL_GUIDE.md`, `VERIFY_GUIDE.md` | 라벨 작성·검증 지침 |
| `scripts/chat_judge/answer_generation/labels/` | 최종 라벨(1차 사람 검토 반영), 라벨 검토 대상(`review.json`), 사람이 검토한 라벨과 판단(`human-reviewed.json`), 남은 검토 목록(`human-review.json`) |
| `LABEL_REVIEW.md` | 라벨 1차 사람 검토와 반영 결과 |
| `capture-A.json.gz`, `capture-B.json.gz` | Judge 입력(답변, 근거, 라벨, 앞 대화). 1차 검토 반영 라벨 기준 |
| `summary-A.json`, `summary-B.json` | 집계. 1차 검토 반영 기준 |
| `raw.tar.xz` | 원시 판정 6개(1차 검토 반영 재판정 포함), 연결 확인 판정, 라벨 작성·검증 원본(약 2.8MB). 검토 전 원시 판정은 커밋 `1bfbf811`의 같은 파일에 있다 |
