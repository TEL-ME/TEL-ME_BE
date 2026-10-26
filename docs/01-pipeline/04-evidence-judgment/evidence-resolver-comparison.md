# 근거 검증 두 호출의 모델 비교 (엑사원 vs gpt-oss)

TELME-178. 답변 생성 전에 쓰이는 두 근거 검증 호출을 엑사원과 gpt-oss-120b로 같은 질문에 돌려 비교했다. 채점 모델은 쓰지 않고, 모델이 고른 FAQ와 인용문을 정답 FAQ 및 FAQ 원문과 대조했다. 비교 결과 gpt-oss가 앞서고 출력 한도가 작으면 잘리는 문제가 확인되어, 출력 한도를 올리고 gpt-oss를 켜는 플래그(`CONSULT_EVIDENCE_RESOLVER_BEDROCK`)를 추가했다. 처음에는 기본 꺼짐이었고 TELME-184에서 기본 켬으로 바꾸고 호출마다 따로 끌 수 있게 했다(아래 적용 절).

## 비교한 호출

| 호출 | 클래스 | 호출되는 때 | 하는 일 |
|---|---|---|---|
| 비교 근거 검증 | `LlmComparisonEvidenceResolver` (`comparison-evidence-v4`, 출력 한도 400) | `A와 B 비교해줘`처럼 비교를 물을 때 | 두 대상 각각의 인용 FAQ를 고르고, 인용문이 FAQ 원문과 같은지 확인한 뒤 원문 그대로 답한다 |
| FAQ 후보 근거 검증 | `LlmFaqCandidateEvidenceResolver` (`faq-candidate-evidence-v2`, 출력 한도 250) | 검색 결과가 비었고 토큰 스트리밍 경로가 아닐 때(복합 질문 하위 상담 등) | 후보 FAQ 10개 중 질문 전체에 답할 수 있는 하나를 고르고 답에서 인용문을 복사한다 |

두 호출 모두 항상 켜져 있지만(`@Component`, 끄는 설정 없음) 해당하는 질문에서만 불린다. 둘 다 앱 기본 `LlmClient`(엑사원)를 쓴다.

## 방법

| 항목 | 내용 |
|---|---|
| 모델 | 엑사원 `exaone3.5:7.8b`(Ollama, 1회), gpt-oss-120b(Bedrock Tokyo, 추론 강도 low, 3회) |
| 호출 경로 | 앱의 요청 변환기를 그대로 써서 두 호출의 실제 출력 한도가 적용된다. 측정용 중계기를 거치지 않았다 |
| 출력 한도 | 1차 측정은 기존 한도(비교 400, 후보 250), 2차 측정은 올린 한도(비교 800, 후보 512)다 |
| 검색 | 앱의 실제 검색(로컬 DB의 FAQ 1,152건, bge-m3). 비교는 원문 검색 top-3 후 대상별 검색, 후보는 `searchCandidates` top-10 |
| 비교 질문 | 19개(`comparison-cases.json`): CMP-001~014, 기존 180건의 비교 표현 3개(EVAL-023, 109, 155), 연결 표현 2개. 정답 FAQ(`goldFaqIds`)는 8개에만 있다 |
| 후보 질문 | 107개(`candidate-cases.json`): 기존 180건 채팅 평가(develop `02f0b3d8`)에서 원문 검색이 비었거나 못 찾은 질문. 정답 있음 45, 무관 62 |
| 도구 | `EvidenceResolverClientProbe`, 집계 `score_resolvers.py`, 모델 클라이언트 `ProbeModelClients` |

## 결과

2차 측정(출력 한도를 올린 뒤)이 현재 코드의 결과이고, 1차 측정(기존 한도)은 잘림 확인을 위해 남긴다. 엑사원은 한도를 올려도 126개 질문 모두 선택과 모델 원문 출력이 1차와 완전히 같았다.

### 비교 근거 검증

| | 엑사원 (1회) | gpt-oss 1차 (한도 400) | gpt-oss 2차 (한도 800) |
|---|---|---|---|
| 검증 통과로 답이 만들어짐 | 6/19 (32%) | 24/57 (42%) | 23/57 (40%) |
| 정답 FAQ를 모두 고름 (정답 있는 8개) | 5/8 (62%) | 19/24 (79%) | 19/24 (79%) |
| 모델이 복사한 인용문이 원문과 그대로 같음 | 18/22 (82%) | 60/60 (100%) | 51/52 (98%) |
| 같은 질문의 반복 결과 일치 | - | 18/19 (95%) | 17/19 (89%) |
| 출력 한도로 잘린 호출 | 0 | 0 (최대 371토큰) | 0 (최대 345토큰) |

### FAQ 후보 근거 검증 (모델이 고른 FAQ 기준)

| | 엑사원 (1회) | gpt-oss 1차 (한도 250) | gpt-oss 2차 (한도 512) |
|---|---|---|---|
| 정답이 후보에 있는 질문에서 정답 선택 | 32/41 (78%) | 113/123 (92%) | 116/123 (94%) |
| 같은 경우 다른 FAQ 선택 / 선택 안 함 | 5 / 4 | 2 / 8 | 3 / 4 |
| 정답이 없는 질문에서 FAQ를 잘못 고름 | 16/62 (26%) | 6/186 (3%) | 5/186 (3%) |
| 모델이 복사한 인용문이 원문과 그대로 같음 | 44/58 (76%) | 123/127 (97%) | 128/130 (98%) |
| 같은 질문의 반복 결과 일치 | - | 99/107 (93%) | 100/107 (93%) |
| 출력 한도로 잘린 호출 | 0 | **2** | **0** (최대 355토큰) |

### 비용과 호출

| | 호출 | 입력 / 출력 토큰 | 비용 |
|---|---|---|---|
| gpt-oss 1차 | 410 (실패 0) | 335,002 / 39,505 | 약 $0.076 |
| gpt-oss 2차 | 408 (실패 0) | 332,130 / 39,094 | 약 $0.076 |

단가는 입력 $0.1545, 출력 $0.618 / 100만 토큰(AWS 요금표 Sydney 표기값)이고 실제 청구액은 확인하지 않았다. 엑사원은 로컬이라 비용이 없다. 호출 중앙값 응답 시간은 1차 기준 557ms, 최대 3.6초였다. gpt-oss는 같은 설정을 다시 돌리면 결과가 1~2%p 흔들린다(1차와 2차의 차이는 이 범위다).

## 읽을 때 주의할 점

- **후보 근거 검증의 앱 채택은 두 모델 모두 0건이다.** 모델이 FAQ를 고른 뒤 앱이 하는 핵심 단어 검사(`coversQuestionTerms`)가 자연스러운 단일 질문을 거절하기 때문이다. 이 호출은 원래 복합 질문의 정규화된 문장에서만 쓰이고, 기존 평가에서도 같은 이유로 0건이었다(`gray-zone-upper-band.md`의 후보 판정기 비교). 그래서 위 표는 앱 검사 전, 모델이 고른 FAQ를 기준으로 했다. 실제 복합 질문 입력에서의 결과는 이 비교로 알 수 없다.
- **gpt-oss는 기존 한도에서 잘릴 수 있었다.** 후보 근거 검증의 EVAL-043에서 3회 중 2회, 한도 250에 걸려 JSON이 끝나기 전에 끊겼다. 그래서 한도를 후보 512, 비교 800으로 올렸고 2차 측정에서 잘림이 없었다(최대 355토큰, 345토큰). 한도 안의 여유는 후보가 약 30%, 비교가 약 57%다.
- **표본이 작다.** 비교는 19개, 그중 정답이 있는 것은 8개다. 방향은 분명하지만 점수 차이를 정확하다고 읽으면 안 된다. 엑사원은 1회만 쟀다.
- **정답 FAQ 기준의 한계.** 비교 질문의 정답은 기존 프로브의 고정 근거이고, 그 밖에 질문에 답할 수 있는 FAQ가 더 있을 수 있다. 후보 질문의 정답도 기존 평가셋의 정답 슬롯이다.
- 후보 질문 107개는 이전 develop(엑사원 앞단)에서 검색이 비었던 질문이다. 지금 코드에서 검색이 비는 질문과 다를 수 있다.

## 판단

- 두 호출 모두 gpt-oss가 엑사원보다 낫거나 비슷했다. 걱정했던 인용문 복사는 비교 검증에서 98~100%, 후보 검증에서 97~98%로 엑사원(82%, 76%)보다 정확했고, 정답이 없을 때 FAQ를 잘못 고르는 비율도 26%에서 3%로 낮았다. 같은 입력의 반복 결과도 대체로 같았다(89~95%).
- 출력 한도는 올렸고 엑사원 결과는 변하지 않았다. gpt-oss는 올린 한도에서 잘림이 없었다.
- 켜는 방법은 아래 적용 절에 있다. 남아 있던 복합 질문 입력에서의 확인은 아래 TELME-182 절에서 했고, 후보 근거 검증은 실제 복합 입력에서도 거의 채택되지 않아 TELME-184 절에서 고쳤다.

## 복합 질문 입력 확인 (TELME-182)

후보 근거 검증이 실제로 쓰이는 입력으로 다시 쟀다. 앞단은 TELME-182 결정대로 두었다(라우팅·문맥 판정·조건 뽑기·요금제 분류 gpt-oss, 요청 개수 판정 끔, 회색 구간 판정·FAQ 되묻기 켬, 근거 검증·답변 생성은 엑사원).

| 항목 | 내용 |
|---|---|
| DB | 측정용 `telme-flow-postgres`(포트 5440)의 `telme_judge_eval`. FAQ 1,152건, 질문 벡터 1,152개를 실행 전에 확인했다 |
| 1단계 입력 캡처 | 브랜치 앱(`5635840b`)으로 Judge 평가셋 v2.2 전체(286 대화, 307턴)를 실제 채팅 API로 1회 실행. 실행 기록 `faqCandidateEvidenceInputs`(검증에 넣은 문장, 인용문 재시도 허용)와 `searchResults`의 `FAQ_CANDIDATE` 후보 10개를 묶었다 |
| 입력 | 57개(복합 21, 부분 답 12, 단일 19, 답 없음 4, 흐름 1) |
| 2단계 비교 | 같은 입력을 앱의 후보 근거 검증(핵심 단어 검사, 인용문 재시도 포함)에 넣었다. gpt-oss 3회는 동시에, 엑사원 3회는 다른 실행이 없을 때 하나씩 돌렸다(2·3회는 TELME-184에서 같은 입력·같은 코드 경로로 잰 이전 방식 P0 결과). gpt-oss는 엑사원 대체 없이 Bedrock만 불렀다 |
| 채점 | 하위 질문 단위 라벨(별도 에이전트가 후보 10개 중 답이 되는 FAQ를 고름, 답 있음 37 / 없음 20, TELME-184에서 만들었다) |

처음 잰 결과(`5635840b`의 이 절)는 앱 기본 포트 5432의 `telme-postgres`로 캡처했다. 이 DB는 질문 벡터가 1,152개 중 2개뿐이라 후보 경로에 들어오는 하위 질문과 후보 10개가 운영과 달랐다. 위 DB로 다시 캡처해 이 절과 결과 파일을 모두 바꿨다.

| | 엑사원 1 / 2 / 3 | gpt-oss 1 / 2 / 3 |
|---|---|---|
| **앱에서 채택** | **1 / 1 / 1** | **1 / 1 / 1** |
| 모델이 고른 것(단어 검사 전): 답 있는 부분에 맞는 FAQ | 24 / 25 / 25 | 25 / 26 / 23 |
| 모델이 고른 것: 답 있는 부분에 다른 FAQ | 7 / 6 / 6 | 7 / 7 / 9 |
| 모델이 고른 것: 답 없는 부분에 FAQ | 9 / 10 / 9 | 8 / 8 / 7 |

- **두 모델 모두 앱 채택이 1건(같은 입력)이다.** "데이터 무제한 로밍 되나요?" 한 건만 통과했고, 하위 질문 라벨로는 맞는 FAQ다(턴 정답에는 없어 채점 스크립트의 턴 기준 표에서는 '다른 FAQ'로 나온다). 모델이 FAQ를 골라도 핵심 단어 검사(`coversQuestionTerms`)가 거의 모두 막는다. 이 검사는 질문의 2글자 이상 낱말이 FAQ 문답에 모두 있어야 통과시켜, "정확히", "같이", "궁금해요" 같은 낱말 하나로도 막힌다.
- **단어 검사 전 모델의 선택도 두 모델이 비슷하다.** 맞는 FAQ를 고른 수와 틀리게 고른 수가 흔들림 범위 안이다.
- **틀린 선택은 조각 입력과 대상 차이에서 나온다.** 검증에는 라우팅이 나눈 하위 질문 원문만 넘어가 "그 김에 이심으로 바꾸면 비용은 얼마예요?"(eSIM), "해지하면 바로 못 쓰게 되는 거예요"(부가서비스)처럼 대상이 원문 앞쪽에만 있는 경우가 많다.
- **결론**: 지금 코드에서는 모델과 관계없이 이 단계가 거의 결과를 내지 못해 모델을 바꾸지 않는다. 단어 검사와 조각 입력은 TELME-184에서 고친다.

응답 시간(입력 30개, 다른 측정 없이 한 줄로): 입력당 gpt-oss p50 1.16초 · p95 2.36초다. 엑사원 시간은 배포 환경(vLLM)에서 따로 잰다. 위 비교는 여러 실행을 동시에 돌려 시간에 쓰지 않는다.

재현(캡처 실행기와 평가셋은 TELME-180 브랜치, `docs/02-features/plan/plan-question-model.md` 재현 절과 같은 방식, DB는 5440):

```bash
# 1단계: PLAN_QUESTION_MODEL.md의 캡처 명령에 CONSULT_PLAN_QUESTION_BEDROCK=true를 더하고 평가셋 v2.2 전체로
python -I scripts/candidate-evidence-evaluation/candidate_replay.py extract <캡처>/capture.json replay-inputs.json
# 2단계: gpt-oss 3회는 동시에, 엑사원은 회차마다 따로 (CANDIDATE_REPLAY_MODELS=모델:회차)
POSTGRES_PORT=5440 CANDIDATE_REPLAY_PROBE=true CANDIDATE_REPLAY_INPUT=<절대 경로>/replay-inputs.json CANDIDATE_REPLAY_OUT=<절대 경로>/replay.jsonl \
CANDIDATE_REPLAY_MODELS=gptoss:3 LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=telme-bedrock-eval \
  ./gradlew test --tests 'com.telme.probe.CandidateEvidenceReplayProbe' --rerun-tasks
# 채점 (저장된 결과로 다시 계산, 턴 정답 기준)
python -I scripts/candidate-evidence-evaluation/candidate_replay.py score \
  scripts/candidate-evidence-evaluation/results/replay-inputs.json.gz \
  scripts/candidate-evidence-evaluation/results/replay.jsonl.gz score.md
```

결과 파일: `scripts/candidate-evidence-evaluation/results/`의 `capture.json.gz`(캡처), `replay-inputs.json.gz`(입력 57개와 후보), `replay.jsonl.gz`(모델별 원문 응답과 선택, 엑사원 2·3회는 TELME-184 `dev-replay.jsonl.gz`의 P0 행).

## 후보 근거 검증 수정 (TELME-184)

후보 근거 검증은 실제 복합 질문 입력에서도 거의 채택되지 않았다. TELME-182 결정대로 앞단을 둔 앱(라우팅·문맥 판정·조건 뽑기·요금제 분류 gpt-oss)으로 Judge 평가셋 v2.2를 돌렸을 때 이 단계에 들어온 하위 질문 57개 중 채택은 엑사원, gpt-oss 모두 1건이었다. 원인은 두 가지다.

1. **핵심 단어 검사가 너무 엄격하다.** `coversQuestionTerms`는 질문의 2글자 이상 낱말이 FAQ 문답에 모두 있어야 통과시켜 "정확히", "같이", "궁금해요" 같은 낱말 하나로도 막힌다(TELME-87에서 엉뚱한 FAQ 채택을 막으려고 넣었다).
2. **검증에 넘기는 문장이 조각이다.** 라우팅이 나눈 하위 질문 원문만 넘어가 "그 김에 이심으로 바꾸면 비용은 얼마예요?", "번호이동 각각"처럼 대상이나 조건이 빠진다.

### 비교한 수정안

| 안 | 단어 검사 | 고객 원문 | 고른 FAQ를 다시 확인 |
|---|---|---|---|
| P0 (이전) | 함 | 안 줌 | 안 함 |
| P1 | 안 함 | 안 줌 | 안 함 |
| P2 | 안 함 | 줌 | 안 함 |
| P3 | 안 함 | 줌 | 회색 구간 판정기(v4)에 하위 질문만 |
| P4 | 안 함 | 줌 | 회색 구간 판정기(v4)에 원문과 함께 ("고객 원문: …\n이 중 판단할 질문: …") |
| P5 | 안 함 | 줌 | P4 + 고객 원문으로도 후보 10개를 더 검색해 붙임 |
| P6 | 안 함 | 줌 | 모델이 답할 부분을 대상까지 채워 다시 쓴 질문만 판정기에 줌 |
| **P7 (채택)** | 안 함 | 줌 | **대상 낱말 규칙(`CandidateSubjectRule`) → P4의 판정기** |

인용문 원문 일치 확인(8자 이상)과 인용문 재시도는 모든 안에 그대로 있다. 대상 낱말 규칙은 모델을 부르지 않는다. 상품·서비스 이름 묶음(eSIM, 유심, 부가서비스, 명의변경, 번호이동, 로밍, 개통, 멤버십, 소액결제, 결합)으로 다음을 본다.

- 하위 질문의 대상은 모두 FAQ 문답에 있어야 한다. 하위 질문에 대상이 없으면 그 문장과 바로 앞 문장(복합 질문의 다른 질문이 섞이지 않게 원문 전체는 보지 않는다)의 대상 중 하나라도 FAQ 문답에 있어야 한다.
- FAQ 제목의 eSIM·부가서비스·명의변경·번호이동·로밍·멤버십·소액결제는 질문 쪽에도 있어야 한다.
- 걸리면 판정기를 부르지 않고 고른 FAQ를 버린다.

### 방법

| 항목 | 내용 |
|---|---|
| DB | 측정용 `telme-flow-postgres`(포트 5440)의 `telme_judge_eval`. 질문 벡터 1,152개를 실행 전에 확인했다 |
| 개발용 | TELME-182 브랜치 앱(`5635840b`)으로 v2.2 전체(307턴)를 실제 채팅 API로 캡처해 얻은 하위 질문 57개와 그때의 후보 10개. 별도 에이전트가 후보 중 이 부분에 답이 되는 FAQ를 붙였다(답 있음 37 / 없음 20). 애매한 것은 사람이 확인했다 |
| 블라인드 | 별도 에이전트가 FAQ 원본만 보고 새로 쓴 두 부분짜리 고객 문장 40개(둘 다 답 있음 25, 한쪽만 10, 둘 다 없음 5). 부분마다 FAQ 원본 전체에서 정답 slot_id를 붙였다. 같은 앱으로 캡처해 후보 경로에 들어온 하위 질문 26개(답 있는 부분 14, 그중 후보에 정답 12)를 썼다 |
| 비교 | 같은 입력을 안별로 앱의 후보 근거 검증에 넣었다. gpt-oss 3회는 동시에, 엑사원은 다른 실행이 없을 때 1회씩 돌렸다. 엑사원은 이전 방식(P0)과 채택안(P7)만 3회 쟀다 |
| 대상 낱말 규칙 | 개발용 오답을 보며 낱말을 보강했다. 그래서 개발용 수치는 좋게 나왔을 수 있고 블라인드 수치가 더 믿을 만하다 |

### 결과: 개발용 57개 (회차별)

| 결과 | P0 | P1 | P2 | P3 | P4 | P5 | P6 | **P7** |
|---|---|---|---|---|---|---|---|---|
| 답 있음: 맞는 FAQ로 답함 (37) | 1 / 1 / 1 | 26 / 22 / 24 | 34 / 34 / 34 | 30 / 29 / 31 | 34 / 34 / 34 | 30 / 29 / 31 | 32 / 32 / 32 | **30 / 31 / 30** |
| 답 있음: 다른 FAQ로 답함 | 0 / 0 / 0 | 7 / 10 / 8 | 2 / 2 / 1 | 2 / 2 / 2 | 0 / 1 / 0 | 3 / 6 / 5 | 1 / 1 / 0 | **0 / 0 / 0** |
| 답 없음: FAQ로 답함 (20, 틀림) | 0 / 0 / 0 | 7 / 8 / 7 | 8 / 9 / 8 | 4 / 3 / 3 | 5 / 5 / 6 | 6 / 4 / 7 | 3 / 5 / 5 | **2 / 2 / 2** |

위는 gpt-oss다. 엑사원은 P0 맞는 답 1 / 1 / 1, 틀림 0 / 0 / 0이고 P7 맞는 답 26 / 25 / 26, 틀림 2 / 3 / 2다.

### 결과: 블라인드 26개 (회차별)

| 결과 | P0 | P2 | P4 | P5 | P6 | **P7** |
|---|---|---|---|---|---|---|
| 정답 FAQ로 답함 (14) | 0 / 0 / 0 | 11 / 11 / 11 | 10 / 11 / 11 | 12 / 12 / 11 | 11 / 9 / 10 | **12 / 12 / 10** |
| 엉뚱한 FAQ로 답함 | 0 / 0 / 0 | 3 / 3 / 3 | 2 / 3 / 3 | 2 / 2 / 2 | 2 / 3 / 3 | **1 / 1 / 2** |
| 답 없는 부분에 FAQ로 답함 | 0 / 0 / 0 | 1 / 1 / 1 | 1 / 1 / 0 | 0 / 0 / 1 | 1 / 1 / 0 | **0 / 0 / 0** |

위는 gpt-oss다. 엑사원은 P0 정답 0 / 0 / 0이고 P7 정답 11 / 11 / 12, 틀림 0 / 0 / 0이다(C032에서 엑사원 호출 오류 2회, 그때는 답하지 않았다. 같은 입력을 엑사원으로 5회 더 돌렸을 때 재현되지 않았고 그 시각 Ollama 응답은 모두 200이라 일시 오류로 본다. 측정 프로그램이 이제 오류 코드도 남긴다). P4의 C036 "엉뚱한 FAQ"("요금이 밀려 있는데 번호이동 되나요" → "밀린 요금 있으면 못 옮겨요 → 먼저 다 내셔야 옮길 수 있습니다")는 정답 목록에서 빠졌을 뿐 맞는 답이다.

### 판단

- **P7을 쓴다.** 단어 검사를 빼면(P1) 틀린 답이 많고, 원문만 주면(P2) 답이 없는 부분에 FAQ를 고르는 경우가 많다. 판정기로 다시 판정하면(P3·P4) 줄지만 주제가 같고 대상이 다른 FAQ로 틀린 사실을 답하는 경우가 남는다. 대상 낱말 규칙을 판정기 앞에 둔 P7은 맞는 답을 조금 잃는 대신 그 경우를 막는다.
  - P4에서 막힌 틀린 사실: "그 김에 이심으로 바꾸면 비용은 얼마예요?"에 유심 재발급 비용 "7,700원"(eSIM은 2,750원), "부가서비스 해지하면 이번 달 요금은 빠지나요?"에 회선 해지의 "일할 계산"(부가서비스는 그 달 요금이 그대로 나간다), "부가서비스 해지하면 바로 못 쓰나요?"에 회선 해지 방법, 답이 없는 "eSIM 활성화 시간"에 일반 개통 시간
- **P7에 남는 것.** 금액·규칙이 틀린 답은 개발용 0건, 블라인드 3회 중 1회 1건이다("새로 개통하면 그날 바로 쓰나요"에 명의변경 당일 처리 FAQ). 그 밖에는 상황이 다른 답이다. 금액·규칙이 틀리지는 않지만 고객이 잘못 이해할 수 있다.
  - "분실로 기기변경할 때 남은 할부는?"에 해지 때의 할부 FAQ(3회 모두). 기기변경 FAQ의 "새 할부로 넘기기"가 빠지고 해지를 암시한다
  - "할부금을 아들 통장 말고 내 카드로 바꿀 수 있나?"에 일반 납부 수단 변경 FAQ(3회 모두). 다른 사람 카드로 되는지는 답하지 않는다
  - 블라인드 "택시에 폰을 두고 내렸는데 바로 정지되나요"에 유심 분실 정지 FAQ(3회 모두). 답 내용("분실 신고하면 즉시 정지")은 같다
- **켜는 것이 낫다.** 이전 방식은 이 하위 질문들에 근거 없음으로 답했다(개발용 57개 중 맞는 답 1건). P7은 개발용 약 30건, 블라인드 약 11건을 맞게 답하고, 대가는 위의 상황이 다른 답과 드문 틀린 사실 1건이다.
- **고르는 모델은 gpt-oss로 둔다.** P7에서 gpt-oss는 개발용 맞는 답이 3회 모두 엑사원보다 4~6건 많았고, 블라인드는 맞는 답이 같고 틀린 답이 1~2건 많았다(C011 경계 사례 3회, C012 1회). 표본이 작은 블라인드 차이보다 개발용의 일관된 차이를 보고, 차이가 크지 않으면 서빙을 하나로 줄일 수 있는 gpt-oss를 고른다는 TELME-175 기준을 따랐다. 엑사원으로 되돌리는 설정을 따로 둔다(적용 절).
- **P5, P6은 쓰지 않는다.** P5는 후보가 늘어난 만큼 엉뚱한 FAQ도 들어와 개발용에서 나빠졌다. P6은 틀린 답 하나를 없앴지만 맞는 답이 줄었고 블라인드에서 차이가 없어 코드에서 뺐다.

### 앱에서 켜 본 확인

이 브랜치 앱(develop 앞단, 엑사원 라우팅)을 측정 DB로 띄우고 판정(P4)과 gpt-oss를 켠 상태로 블라인드 문장 중 후보 경로를 탔던 20개를 실제 채팅 API로 보냈다(대상 낱말 규칙을 넣기 전). 후보 근거 검증 입력 12개(실행 기록 `faqCandidateEvidenceInputs`에 고객 원문 포함)에서 근거 검증 호출(`faq-candidate-evidence-v3`, gpt-oss) 12회가 모두 성공했고, 4턴이 FAQ 원문으로 답했다. 그중 2건("번호이동 후 무를 수 있는 기간" → 14일, "밀린 요금 다 내면" → 2시간 이내 재개)은 맞고, 2건은 위의 오답과 같았다. 대상 낱말 규칙의 판단은 재생 비교에서 판정기 앞까지 온 176건이 파이썬으로 계산한 값과 모두 같았다.

### 후속 질문 확인

위 개발용·블라인드 입력은 모두 대화의 첫 턴이었다. 앞 대화에 기대는 후속 질문("그럼 비용은? 택배로도 돼?")은 확인하지 않아 따로 쟀다.

- **고친 것**: 복합 질문의 고객 원문으로 이번 턴에 보낸 문장 대신 문맥 해석을 거친 문장(`resolvedUserQuery`)을 넘긴다. 이전 대화로 복원한 턴이면 "[대상을 확인할 이전 고객 발언] … [현재 후속 질문] …" 형식으로 앞 발언이 함께 들어가고, 복원하지 않은 턴은 이번 턴 문장과 같다. 단일 질문 경로는 원래 고객 원문을 넘기지 않아 바뀌지 않는다.
- **입력**: 별도 에이전트가 FAQ 원본만 보고 쓴 두 턴짜리 대화 30개. 첫 턴에 대상을 말하고 둘째 턴은 대상을 생략한 두 부분 질문이다(둘 다 답 있음 20, 한쪽만 6, 둘 다 없음 4, 부분마다 정답 slot_id). 위와 같은 앞단(TELME-182 결정)에 이 수정과 P7을 더한 앱으로 측정 DB(5440)에서 캡처했고, 둘째 턴의 후보 근거 검증 입력 23개를 썼다.
- **비교**: gpt-oss 3회 동시. 복원된 턴은 같은 입력에 고객 원문만 이번 턴 문장(수정 전)과 복원된 문장(수정 후)으로 바꿔 P7로 돌렸다.

**복원된 턴 (3개 대화, 입력 5개)**

| 결과 (회차별) | 수정 전 (이번 턴 문장) | 수정 후 (복원된 문장) |
|---|---|---|
| 정답 FAQ로 답함 | 1 / 1 / 1 | **2 / 2 / 2** |
| 엉뚱한 FAQ로 답함 | 2 / 2 / 2 | **0 / 0 / 0** |

수정 전에는 대상이 빠진 조각으로 다른 업무의 FAQ를 골랐다. 유심 재발급 뒤의 "매장 가면 그 자리에서 바로 받을 수 있나요"에 신규 개통 30분 FAQ를, 미성년 자녀 개통 뒤의 "부모가 챙겨갈 서류는 뭐예요?"에 일반 가입 서류(신분증 하나) FAQ를 골랐다. 수정 후에는 둘 다 답하지 않았고 명의변경 뒤의 "그거 하는 데 돈 들어요?"에 명의변경 수수료로 맞게 답했다.

**둘째 턴 전체 (입력 23개, 답 있는 부분 15)**

| 결과 (회차별) | P0 (이전) | P7 |
|---|---|---|
| 정답 FAQ로 답함 | 0 / 0 / 0 | 8 / 8 / 8 |
| 엉뚱한 FAQ로 답함 | 0 / 0 / 0 | 1 / 2 / 2 |
| 답 없는 부분에 FAQ로 답함 | 0 / 0 / 0 | 2 / 2 / 2 |

- **문맥 해석이 30개 중 27개를 복원하지 않았다.** 24개는 모델을 부르지 않고 앞단 규칙(`REGEX_GATED`)이 바로 "혼자 완결된 질문"으로 넘겼다("그 기간에도 요금이 나와요?", "그동안 문자는 받을 수 있어요?", "올리면 바로 적용되나요?" 등). 이 경우 이 수정은 아무것도 바꾸지 못하고, 대상이 없는 조각이 그대로 근거 검증에 들어간다.
- **그 조각에서 P7이 틀린 답을 낸다.** 일시정지 뒤의 "중간에 다시 쓰고 싶으면 바로 풀 수 있나요"에 번호이동 14일 되돌리기 FAQ를, eSIM 뒤의 "신청하고 실제로 쓰기까지 얼마나 걸려요?"에 일반 가입(매장 30분) FAQ를 3회 모두 골랐다. 질문과 고객 문장 어디에도 대상 낱말이 없어 대상 낱말 규칙이 막지 못한다. 개통 뒤의 "매장 가서 하면 당일에 바로 되나요"에 명의변경 당일 처리 FAQ(블라인드 C012의 틀린 답과 같은 FAQ)를, 기기변경 뒤의 할부 질문에 해지 때의 할부 FAQ를 고른 회차도 있다.
- **원인은 근거 검증보다 앞의 문맥 해석이다.** 후속 질문을 복원하면 위 표처럼 막힌다. 앞단 규칙이 지시 표현("그 기간", "그동안", "그거", "올리면")을 후보로 보지 않는 문제는 문맥 해석의 별도 과제로 남긴다. 그때까지 후속 질문에서는 P7이 맞는 답 8건과 함께 틀린 답 3~4건을 낸다(이전 방식은 둘 다 0건).

### 응답 시간

입력 30개를 다른 측정 없이 안 하나씩 한 줄로 돌린 gpt-oss 값이다(위 비교는 동시에 돌려 시간에 쓰지 않는다). 엑사원 시간은 배포 환경(vLLM)에서 따로 잰다.

| gpt-oss | p50 | p95 | 최대 |
|---|---|---|---|
| P0, 입력당 | 1.16초 | 2.36초 | 2.6초 |
| P4 고르기 호출 | 0.82초 | 1.54초 | 1.8초 |
| P4 판정기 호출 (26회) | 1.12초 | 2.30초 | 2.8초 |
| **P4, 입력당** | **2.08초** | **3.35초** | 4.1초 |

P7은 P4에 모델을 부르지 않는 낱말 규칙을 더한 것이라 따로 재지 않았다. 규칙에 걸리면 판정기를 부르지 않아 그만큼 짧아진다.

비용은 캡처 2번과 비교·응답 시간 측정을 합쳐 Bedrock 약 4,500회, 약 $1로 추정한다(실제 청구액 미확인). 후속 질문 확인은 캡처에 기록된 gpt-oss 호출 356회와 재생 215회를 더해 약 570회다.

### 재현

캡처 실행기(`ChatJudgeCaptureProbe`, `scripts/chat_judge`)와 평가셋은 TELME-180 브랜치, 앞단 gpt-oss 설정은 TELME-182 브랜치에 있다. 캡처는 TELME-182 브랜치 앱으로 `docs/02-features/plan/plan-question-model.md`(TELME-182)의 캡처 명령에 `CONSULT_PLAN_QUESTION_BEDROCK=true`를 더해 돌렸다. 블라인드 캡처에 넣은 대화 파일은 저장된 캡처에서 되살려 `--fixture`로 준다.

캡처에 넣은 대화와 재생 입력은 저장하지 않는다. 둘 다 캡처에서 아래 명령으로 똑같이 다시 만들어진다(저장했던 파일과 같은지 확인했다).

```bash
# 측정 DB 확인: 1152가 아니면 멈춘다
docker exec telme-flow-postgres psql -U telme -d telme_judge_eval -Atc "select count(embedding_question) from faq_embeddings"
R=scripts/candidate-evidence-evaluation/results/telme184
# 블라인드 캡처에 넣은 대화(40개) 되살리기
python -I scripts/candidate-evidence-evaluation/blind_replay.py fixture $R/blind-capture.json.gz blind-fixture.json
# 입력 꺼내기 (개발용 57개, 블라인드 26개). 새로 캡처했으면 그 capture.json을 준다
python -I scripts/candidate-evidence-evaluation/candidate_replay.py extract $R/dev-capture.json.gz dev-inputs.json
python -I scripts/candidate-evidence-evaluation/blind_replay.py extract $R/blind-capture.json.gz blind-inputs.json
# 비교: gpt-oss는 동시에, 엑사원은 안·회차마다 따로 (P5는 앱 검색을 불러 측정 DB가 필요하다)
POSTGRES_PORT=5440 CANDIDATE_REPLAY_PROBE=true CANDIDATE_REPLAY_INPUT=<절대 경로>/dev-inputs.json CANDIDATE_REPLAY_OUT=<절대 경로>/replay.jsonl \
CANDIDATE_REPLAY_VARIANTS=P0,P1,P2,P3,P4,P5,P7 CANDIDATE_REPLAY_MODELS=gptoss:3 \
LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=telme-bedrock-eval \
  ./gradlew test --tests 'com.telme.probe.CandidateEvidenceReplayProbe' --rerun-tasks
# 채점 (저장된 결과로 다시 계산)
python -I scripts/candidate-evidence-evaluation/score_dev.py $R/dev-labels.json.gz $R/dev-replay.jsonl.gz dev-score.md
python -I scripts/candidate-evidence-evaluation/blind_replay.py score blind-inputs.json $R/blind-replay.jsonl.gz blind-score.md
# 응답 시간: 안·모델 하나씩 CANDIDATE_REPLAY_LIMIT=30, CANDIDATE_REPLAY_MODELS=gptoss:1 등으로 따로
# 후속 질문: 넣은 대화는 blind_replay.py fixture로 followup-capture에서 되살린다. 입력은 고객 원문을 고르는 세 번째 인자로 꺼낸다
python -I scripts/candidate-evidence-evaluation/blind_replay.py extract $R/followup-capture.json.gz followup-inputs-resolved.json resolved
python -I scripts/candidate-evidence-evaluation/blind_replay.py extract $R/followup-capture.json.gz followup-inputs-raw.json raw
# 재생: resolved 입력은 CANDIDATE_REPLAY_VARIANTS=P0,P7, raw 입력은 복원된 턴 5개만 P7 (gptoss:3)
python -I scripts/candidate-evidence-evaluation/blind_replay.py score followup-inputs-resolved.json $R/followup-replay-resolved.jsonl.gz followup-resolved.md
python -I scripts/candidate-evidence-evaluation/blind_replay.py score followup-inputs-raw.json $R/followup-replay-raw.jsonl.gz followup-raw.md
```

| 파일 (`scripts/candidate-evidence-evaluation/`) | 내용 |
|---|---|
| `candidate_replay.py` | 캡처에서 근거 검증 입력과 후보 꺼내기, 턴 정답 기준 채점 |
| `blind_replay.py` | 블라인드 입력 꺼내기(하위 질문을 고객 문장의 부분에 맞춤), 부분 정답 기준 채점, 캡처에서 넣은 대화 되살리기 |
| `score_dev.py` | 개발용 하위 질문 라벨로 안별 채점 |
| `results/telme184/dev-*` | 개발용 캡처(`dev-capture`), 하위 질문 라벨(`dev-labels`), 안별 결과(`dev-replay`, P0~P7, 엑사원은 P0·P7 3회와 P1~P5 1회) |
| `results/telme184/blind-*` | 블라인드 문장 40개와 정답(`blind-questions`), 캡처(`blind-capture`), 안별 결과(`blind-replay`) |
| `results/telme184/latency.jsonl.gz` | 응답 시간 측정(호출별 시간 `callMs`, `judgeMs`) |
| `results/telme184/followup-*` | 후속 질문 대화 30개와 정답(`followup-questions`), 캡처(`followup-capture`), 재생 결과(`followup-replay-resolved` P0·P7, `followup-replay-raw` 복원된 턴 5개의 수정 전) |
| `src/test/java/com/telme/probe/CandidateEvidenceReplayProbe.java` | 같은 입력을 안·모델·회차별로 돌리는 측정 프로그램 |

## 비교 근거 검증 재확인 (TELME-184)

비교 근거 검증의 gpt-oss 기본값을 정하기 전에, #167의 결론을 지금 방식(결정된 앞단, 측정 DB)으로 다시 확인했다.

| 항목 | 내용 |
|---|---|
| 질문 | #167 비교 질문 19개(`comparison-cases.json`, 정답 FAQ가 있는 것 8개) + 별도 에이전트가 FAQ 원본만 보고 새로 쓴 블라인드 비교 질문 30개(양쪽 답 있음 20, 한쪽만 6, 둘 다 없음 4, 양쪽마다 정답 slot_id). 모두 비교 경로를 타는 끝맺음("…비교해줘", "…차이점")으로 썼다 |
| 1단계 입력 캡처 | TELME-182 브랜치 앱(`4e05d8c4`, 라우팅·문맥 판정·요금제 분류 gpt-oss)에 실행 기록 `comparisonEvidenceInputs`(검증에 넣은 문장, 원문 검색 결과. 앱은 이후 본문 대신 FAQ id 순서 `originalFaqIds`만 남기도록 바꿨고, 본문은 같은 상담의 `searchResults`에서 찾는다)를 더해 측정 DB(5440)로 49개를 실제 채팅 API로 보냈다. 비교 경로를 탄 것 45개(#167 17, 블라인드 28) |
| 2단계 비교 | 같은 입력을 앱의 비교 근거 검증에 넣고 대상별 추가 검색은 앱 검색(후보 10개)을 그대로 불렀다. gpt-oss 3회는 동시에, 엑사원 3회는 하나씩 돌렸다. 회색 구간 때문에 다시 고르는 두 번째 시도는 다시 만들지 않고 첫 시도 입력만 썼다 |
| 채점 | 블라인드 양쪽 답 있음은 답에 쓴 FAQ가 양쪽 정답을 모두 덮고 정답 밖의 FAQ가 없으면 맞음(FAQ 하나가 양쪽을 함께 답하기도 한다). 한쪽만·둘 다 없음은 답하지 않아야 맞음. #167은 정답 FAQ가 있는 8개만 채점 |

| 결과 (회차별) | 엑사원 | gpt-oss |
|---|---|---|
| 블라인드 양쪽 답 있음 (19): 맞는 FAQ로 답함 | 1 / 1 / 1 | **5 / 6 / 6** |
| 블라인드 양쪽 답 있음: 다른 FAQ로 답함 | 0 / 0 / 0 | 0 / 0 / 0 |
| 블라인드 한쪽만·둘 다 없음 (9): 답함(틀림) | 0 / 0 / 0 | 0 / 0 / 0 |
| #167 정답 있음 (8): 정답 FAQ로 답함 | 5 / 5 / 5 | 6 / 6 / 6 |
| #167 정답 있음: 다른 FAQ로 답함 | 1 / 1 / 1 | 2 / 2 / 2 |
| #167 정답 있음: 답 안 함 | 2 / 2 / 2 | 0 / 0 / 0 |

- **gpt-oss가 낫다(#167 결론과 같다).** 블라인드에서 맞게 답한 비교가 엑사원 1건, gpt-oss 5~6건이고 두 모델 모두 틀린 답은 없었다. 답이 없는 쪽이 있는 비교는 두 모델 모두 답하지 않았다.
- #167의 "다른 FAQ로 답함"을 직접 보면 gpt-oss의 CMP-011("eSIM 발급은 2,750원, 물리 유심 재발급은 7,700원으로 eSIM이 저렴합니다")과 엑사원의 CMP-001(5G·LTE 요금제 종류, 알뜰 요금제 설명이 덧붙음)은 정답 목록에 없을 뿐 맞는 답이다. gpt-oss의 CMP-010("분실 신고와 일시 정지")은 분실 신고 쪽에 재발급 FAQ가 섞인 경계 사례이고 틀린 사실은 없다.
- 두 모델 모두 답하는 비교가 적다(블라인드 양쪽 답 있음 19개 중 gpt-oss 5~6개). 대상 확인과 인용문 원문 대조가 엄격해서이며 별도 과제로 남긴다.
- 호출 오류는 두 모델 모두 0건이다. 엑사원 시간은 배포 환경에서 따로 잰다.

재현:

```bash
R=scripts/candidate-evidence-evaluation/results/telme184
# 1단계: 캡처 앱에 comparisonEvidenceInputs 기록을 더하고, 저장된 캡처에서 되살린 대화(49개)로 캡처(DB는 5440)
python -I scripts/candidate-evidence-evaluation/blind_replay.py fixture $R/comparison-capture.json.gz comparison-fixture.json
# 비교 경로 입력(45개) 꺼내기. 새로 캡처했으면 그 capture.json을 준다
python -I scripts/candidate-evidence-evaluation/comparison_replay.py extract $R/comparison-capture.json.gz comparison-inputs.json
# 2단계: gpt-oss 3회는 동시에, 엑사원은 회차마다 따로 (COMPARISON_REPLAY_MODELS=모델:회차)
POSTGRES_PORT=5440 COMPARISON_REPLAY_PROBE=true COMPARISON_REPLAY_INPUT=<절대 경로>/comparison-inputs.json COMPARISON_REPLAY_OUT=<절대 경로>/replay.jsonl \
COMPARISON_REPLAY_MODELS=gptoss:3 LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=telme-bedrock-eval \
  ./gradlew test --tests 'com.telme.consult.service.ComparisonEvidenceReplayProbe' --rerun-tasks
# 채점 (저장된 결과로 다시 계산)
python -I scripts/candidate-evidence-evaluation/comparison_replay.py comparison-inputs.json $R/comparison-replay.jsonl.gz score.md
```

결과 파일: `results/telme184/comparison-blind-questions.json.gz`(블라인드 비교 질문 30개와 정답), `comparison-capture.json.gz`(캡처, 넣은 대화 49개와 비교 경로 입력 45개를 여기서 꺼낸다), `comparison-replay.jsonl.gz`(모델별 결과). 측정 프로그램은 `src/test/java/com/telme/consult/service/ComparisonEvidenceReplayProbe.java`(비교 검색기 인터페이스가 패키지 안에서만 보여 이 패키지에 둔다), 채점은 `scripts/candidate-evidence-evaluation/comparison_replay.py`다.

## 적용

- 모델: 비교 근거 검증은 `telme.consult.evidence-resolver.comparison-bedrock`(환경 변수 `CONSULT_COMPARISON_EVIDENCE_BEDROCK`), 후보 근거 검증은 `candidate-bedrock`(`CONSULT_CANDIDATE_EVIDENCE_BEDROCK`)이다. 둘 다 **기본 켜짐**(gpt-oss)이고, 주지 않으면 공통 환경 변수 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`(기본 true)을 따른다. 켜면 gpt-oss(Bedrock)를 먼저 쓰고 호출이 실패하면 엑사원으로 대체한다. 호출마다 따로 false로 두면 엑사원이다. 되묻기 조건 뽑기(`CONSULT_FAQ_CLARIFICATION_BEDROCK`)와 같은 방식이다.
- Bedrock을 쓸 수 없으면(`llm.bedrock.enabled=false` 등) 켜도 엑사원이다.
- 후보 근거 검증의 판정 방식: `telme.consult.evidence-resolver.candidate-judge`(환경 변수 `CONSULT_CANDIDATE_EVIDENCE_JUDGE`). **기본 켜짐**이다. 켜면 단어 검사 대신 고객 원문을 함께 주고, 고른 FAQ를 대상 낱말 규칙과 회색 구간 판정기로 다시 확인한다(위 TELME-184 절의 P7). false면 예전처럼 단어 검사로 막는다. 판정기는 Bedrock이 켜졌을 때만 있어, Bedrock이 꺼져 있으면 켜도 단어 검사로 막고 시작할 때 경고를 남긴다.
- 판정기 호출이 실패하면(Bedrock 오류, 응답을 읽지 못함) 그 후보만 예전 단어 검사로 판단한다. 회색 구간 판정이 실패하면 예전 기준으로 두는 것과 같다. 판정기에는 엑사원 대체가 없어, 이렇게 하지 않으면 Bedrock 장애 중에는 엑사원이 고른 후보까지 모두 버려진다.
- 복합 질문의 고객 원문은 문맥 해석을 거친 문장이다. 이전 대화로 복원한 후속 질문이면 앞 발언이 함께 들어간다(위 "후속 질문 확인"). 문맥 해석이 후속 질문을 복원하지 못하면 대상 없는 조각으로 판정해 틀린 답이 날 수 있다.
- 후보마다 확인 결과를 실행 기록 `faqCandidateVerifications`에 남긴다(FAQ id, 대상 낱말 규칙 `PASSED`/`REJECTED`, 판정 `ANSWERABLE`/`NOT_ANSWERABLE`/`FAILED`/`NOT_CALLED`, 채택 여부). 규칙의 상품 이름 목록이 맞는 후보를 막는지 운영 기록으로 볼 수 있다.
- 두 호출은 `EvidenceResolverConfiguration`에서 만든다. 두 호출만 켜고 끌 뿐 다른 호출(문맥 판정, 라우팅 등)의 모델은 바꾸지 않는다.
- 출력 한도는 설정과 관계없이 올라간 값(비교 800, 후보 512)을 쓴다. 엑사원은 같은 결과였다.
- 대체는 호출이 실패(오류, 시간 초과)했을 때만 일어난다. 응답이 한도에 걸려 끊기면 오류가 아니라서 대체되지 않으므로, 한도를 넘는 새 프롬프트를 쓸 때는 한도를 함께 본다.

## 재현

```bash
# 엑사원 (Ollama 실행 중이어야 함)
RESOLVER_PROBE=true RESOLVER_PROBE_MODELS=exaone RESOLVER_PROBE_OUT=build/resolver-exaone.jsonl \
  ./gradlew test --tests '*EvidenceResolverClientProbe'
# gpt-oss (Bedrock API 키를 AWS_BEARER_TOKEN_BEDROCK로 지정, 유료)
RESOLVER_PROBE=true RESOLVER_PROBE_MODELS=gptoss RESOLVER_PROBE_OUT=build/resolver-gptoss.jsonl \
  ./gradlew test --tests '*EvidenceResolverClientProbe'
# 집계
python3 -I scripts/evidence-resolver-evaluation/score_resolvers.py \
  scripts/evidence-resolver-evaluation/results/resolver-exaone.jsonl.gz \
  scripts/evidence-resolver-evaluation/results/resolver-gptoss.jsonl.gz
```

결과 파일의 1차 측정은 `resolver-*.jsonl.gz`, 올린 한도의 2차 측정은 `resolver-*-limit-raised.jsonl.gz`다. `RESOLVER_PROBE_KINDS=comparison` 또는 `candidate`로 한 호출만 돌릴 수 있고, `RESOLVER_PROBE_LIMIT`으로 질문 수를 줄일 수 있다.

## 파일

| 파일 | 내용 |
|---|---|
| `src/test/java/com/telme/probe/EvidenceResolverClientProbe.java` | 두 호출을 선택한 모델로 직접 만들어 실행하는 측정 프로그램 |
| `src/test/java/com/telme/probe/ProbeModelClients.java` | 엑사원과 gpt-oss를 같은 방식으로 부르고 호출마다 기록하는 클라이언트 |
| `scripts/evidence-resolver-evaluation/comparison-cases.json`, `candidate-cases.json` | 질문 19개, 107개 |
| `scripts/evidence-resolver-evaluation/score_resolvers.py` | 결과 집계 |
| `scripts/evidence-resolver-evaluation/results/*.jsonl.gz` | 두 모델의 원시 결과, 1차와 2차(합계 약 650KB). 모델 원문 출력과 후보 FAQ 본문이 들어 있다 |
| `src/main/java/com/telme/consult/config/EvidenceResolverConfiguration.java`, `EvidenceResolverProperties.java` | 플래그와 gpt-oss 우선, 엑사원 대체 선택 |
