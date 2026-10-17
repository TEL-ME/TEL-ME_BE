# 근거 검증 두 호출의 모델 비교 (엑사원 vs gpt-oss)

TELME-178. 답변 생성 전에 쓰이는 두 근거 검증 호출을 엑사원과 gpt-oss-120b로 같은 질문에 돌려 비교했다. 채점 모델은 쓰지 않고, 모델이 고른 FAQ와 인용문을 정답 FAQ 및 FAQ 원문과 대조했다. 비교 결과 gpt-oss가 앞서고 출력 한도가 작으면 잘리는 문제가 확인되어, 출력 한도를 올리고 gpt-oss를 켜는 플래그(`CONSULT_EVIDENCE_RESOLVER_BEDROCK`, 기본 꺼짐)를 추가했다.

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

- **후보 근거 검증의 앱 채택은 두 모델 모두 0건이다.** 모델이 FAQ를 고른 뒤 앱이 하는 핵심 단어 검사(`coversQuestionTerms`)가 자연스러운 단일 질문을 거절하기 때문이다. 이 호출은 원래 복합 질문의 정규화된 문장에서만 쓰이고, 기존 평가에서도 같은 이유로 0건이었다(`GRAY_ZONE_UPPER_BAND.md`의 후보 판정기 비교). 그래서 위 표는 앱 검사 전, 모델이 고른 FAQ를 기준으로 했다. 실제 복합 질문 입력에서의 결과는 이 비교로 알 수 없다.
- **gpt-oss는 기존 한도에서 잘릴 수 있었다.** 후보 근거 검증의 EVAL-043에서 3회 중 2회, 한도 250에 걸려 JSON이 끝나기 전에 끊겼다. 그래서 한도를 후보 512, 비교 800으로 올렸고 2차 측정에서 잘림이 없었다(최대 355토큰, 345토큰). 한도 안의 여유는 후보가 약 30%, 비교가 약 57%다.
- **표본이 작다.** 비교는 19개, 그중 정답이 있는 것은 8개다. 방향은 분명하지만 점수 차이를 정확하다고 읽으면 안 된다. 엑사원은 1회만 쟀다.
- **정답 FAQ 기준의 한계.** 비교 질문의 정답은 기존 프로브의 고정 근거이고, 그 밖에 질문에 답할 수 있는 FAQ가 더 있을 수 있다. 후보 질문의 정답도 기존 평가셋의 정답 슬롯이다.
- 후보 질문 107개는 이전 develop(엑사원 앞단)에서 검색이 비었던 질문이다. 지금 코드에서 검색이 비는 질문과 다를 수 있다.

## 판단

- 두 호출 모두 gpt-oss가 엑사원보다 낫거나 비슷했다. 걱정했던 인용문 복사는 비교 검증에서 98~100%, 후보 검증에서 97~98%로 엑사원(82%, 76%)보다 정확했고, 정답이 없을 때 FAQ를 잘못 고르는 비율도 26%에서 3%로 낮았다. 같은 입력의 반복 결과도 대체로 같았다(89~95%).
- 출력 한도는 올렸고 엑사원 결과는 변하지 않았다. gpt-oss는 올린 한도에서 잘림이 없었다.
- 켜는 방법은 아래 적용 절에 있다. 남아 있던 복합 질문 입력에서의 확인은 아래 TELME-184 절에서 했다. 후보 근거 검증은 실제 복합 입력에서도 거의 채택되지 않아 그 절에서 고쳤다.

## 후보 근거 검증 수정 (TELME-184)

후보 근거 검증은 실제 복합 질문 입력에서도 거의 채택되지 않는다. TELME-182 결정대로 앞단을 둔 앱(라우팅·문맥 판정·조건 뽑기·요금제 분류 gpt-oss)으로 Judge 평가셋 v2.2를 돌렸을 때 이 단계에 들어온 하위 질문 57개 중 채택은 엑사원, gpt-oss 모두 1건이었다. 원인은 두 가지다.

1. **핵심 단어 검사가 너무 엄격하다.** `coversQuestionTerms`는 질문의 2글자 이상 낱말이 FAQ 문답에 모두 있어야 통과시켜 "정확히", "같이", "궁금해요" 같은 낱말 하나로도 막힌다(TELME-87에서 엉뚱한 FAQ 채택을 막으려고 넣었다).
2. **검증에 넘기는 문장이 조각이다.** 라우팅이 나눈 하위 질문 원문만 넘어가 "그 김에 이심으로 바꾸면 비용은 얼마예요?", "번호이동 각각"처럼 대상이나 조건이 빠진다.

### 비교한 수정안

| 안 | 단어 검사 | 고객 원문 | 고른 FAQ를 다시 판정 |
|---|---|---|---|
| P0 (지금) | 함 | 안 줌 | 안 함 |
| P1 | 안 함 | 안 줌 | 안 함 |
| P2 | 안 함 | 줌 | 안 함 |
| P3 | 안 함 | 줌 | 회색 구간 판정기(v4)에 하위 질문만 |
| **P4 (채택)** | 안 함 | 줌 | **회색 구간 판정기(v4)에 원문과 함께** ("고객 원문: …\n이 중 판단할 질문: …") |
| P5 | 안 함 | 줌 | P4 + 고객 원문으로도 후보 10개를 더 검색해 붙임 |
| P6 | 안 함 | 줌 | 모델이 답할 부분을 대상까지 채워 다시 쓴 질문만 판정기에 줌 |

인용문 원문 일치 확인(8자 이상)과 인용문 재시도는 모든 안에 그대로 있다.

### 방법

| 항목 | 내용 |
|---|---|
| DB | 측정용 `telme-flow-postgres`(포트 5440)의 `telme_judge_eval`. 질문 벡터 1,152개를 실행 전에 확인했다 |
| 개발용 | TELME-182 브랜치 앱(`5635840b`)으로 v2.2 전체(307턴)를 실제 채팅 API로 캡처해 얻은 하위 질문 57개와 그때의 후보 10개. 별도 에이전트가 후보 중 이 부분에 답이 되는 FAQ를 붙였다(답 있음 37 / 없음 20). 애매한 것은 사람이 확인했다 |
| 블라인드 | 별도 에이전트가 FAQ 원본만 보고 새로 쓴 두 부분짜리 고객 문장 40개(둘 다 답 있음 25, 한쪽만 10, 둘 다 없음 5). 부분마다 FAQ 원본 전체에서 정답 slot_id를 붙였다. 같은 앱으로 캡처해 후보 경로에 들어온 하위 질문 26개(답 있는 부분 14, 그중 후보에 정답 12)를 썼다 |
| 비교 | 같은 입력을 안별로 앱의 후보 근거 검증에 넣었다. gpt-oss 3회는 동시에, 엑사원 1회는 다른 실행이 없을 때 하나씩 돌렸다 |

### 결과: 개발용 57개 (gpt-oss 3회 / 엑사원 1회)

| 결과 | P0 | P1 | P2 | P3 | **P4** | P5 | P6 |
|---|---|---|---|---|---|---|---|
| 답 있음: 맞는 FAQ로 답함 (37) | 1 | 26 / 22 / 24 | 34 / 34 / 34 | 30 / 29 / 31 | **34 / 34 / 34** | 30 / 29 / 31 | 32 / 32 / 32 |
| 답 있음: 다른 FAQ로 답함 | 0 | 7 / 10 / 8 | 2 / 2 / 1 | 2 / 2 / 2 | **0 / 1 / 0** | 3 / 6 / 5 | 1 / 1 / 0 |
| 답 없음: FAQ로 답함 (20, 틀림) | 0 | 7 / 8 / 7 | 8 / 9 / 8 | 4 / 3 / 3 | **5 / 5 / 6** | 6 / 4 / 7 | 3 / 5 / 5 |
| 엑사원: 맞는 FAQ / 틀림 | 1 / 0 | 24 / 16 | 29 / 11 | 27 / 5 | 26 / 4 | 20 / 16 | - |

### 결과: 블라인드 26개 (gpt-oss 3회 / 엑사원 1회)

| 결과 | P0 | P2 | **P4** | P5 | P6 |
|---|---|---|---|---|---|
| 정답 FAQ로 답함 (14) | 0 | 11 / 11 / 11 | **10 / 11 / 11** | 12 / 12 / 11 | 11 / 9 / 10 |
| 엉뚱한 FAQ로 답함 | 0 | 3 / 3 / 3 | 2 / 3 / 3 | 2 / 2 / 2 | 2 / 3 / 3 |
| 답 없는 부분에 FAQ로 답함 | 0 | 1 / 1 / 1 | 1 / 1 / 0 | 0 / 0 / 1 | 1 / 1 / 0 |
| 엑사원: 정답 / 틀림 | 0 / 0 | 11 / 5 | 10 / 2 | 11 / 3 | - |

블라인드에서 반복해 틀린 것을 직접 보면 C036("요금이 밀려 있는데 번호이동 되나요" → "밀린 요금 있으면 못 옮겨요 → 먼저 다 내셔야 옮길 수 있습니다")은 정답 목록에서 빠졌을 뿐 맞는 답이고, C011(폰 분실 정지에 유심 분실 정지 FAQ)은 답의 내용이 같은 경계 사례다. 실제로 틀린 것은 C012("새로 개통하면 그날 바로 쓰나요"에 명의변경 당일 처리 FAQ)와 C040(답이 없는 eSIM 활성화 시간에 일반 개통 시간 FAQ)이다.

### 판단

- **P4를 쓴다.** 단어 검사를 빼면(P1) 틀린 답이 많고, 원문만 주면(P2) 답이 없는 부분에 FAQ를 고르는 경우가 많다. 판정기로 다시 판정하면 이 경우가 절반 가까이 준다. 판정기에 원문을 함께 준 P4가 하위 질문만 준 P3보다 맞는 답이 많다.
- **고르는 모델은 gpt-oss가 낫다.** P4에서 엑사원은 맞는 답이 26건으로 gpt-oss(34)보다 적고 답할 수 있는 것을 11건 놓쳤다.
- **P5, P6은 쓰지 않는다.** P5는 후보가 늘어난 만큼 엉뚱한 FAQ도 들어와 개발용에서 나빠졌다. P6은 틀린 답 하나를 없앴지만 맞는 답이 줄었고 블라인드에서 차이가 없어 코드에서 뺐다.
- **남는 위험: 대상이 다른 FAQ로 틀린 사실을 답한다.** 개발용 P4 gpt-oss에서 57개 중 2~3건이 틀린 사실이었다.
  - "그 김에 이심으로 바꾸면 비용은 얼마예요?"에 유심 재발급 비용 FAQ로 "7,700원"(eSIM은 2,750원), 3회 모두
  - "부가서비스 해지하면 이번 달 요금은 빠지나요?"에 회선 해지 FAQ로 "일할 계산"(부가서비스는 그 달 요금이 그대로 나간다), 3회 중 2회
  - "부가서비스 해지하면 바로 못 쓰나요?"에 회선 해지 방법, 3회 중 3회(P6에서는 0회)
  - 그 밖에 일부만 관련 있는 FAQ로 답한 경계 사례(정지·해지 비교에 분실 일시정지, 할부금 납부 카드 변경에 일반 납부 수단 변경)
  - 모두 주제는 같고 대상(eSIM과 유심, 부가서비스와 회선)이 다른 경우로, 회색 구간 판정기에도 알려진 약점이다(`GRAY_ZONE_UPPER_BAND.md` 8.2절).
- 그래서 **설정 뒤에 두고 기본은 끈다**(`CONSULT_CANDIDATE_EVIDENCE_JUDGE`). 지금(P0)은 이 질문들에 근거 없음으로 답해 틀리지는 않는다. 켜면 개발용 기준 맞는 답이 1건에서 34건으로 늘고 틀린 사실이 2~3건 생기는 맞바꿈이라 켤지는 팀이 정한다.

### 응답 시간

입력 30개를 다른 측정 없이 안·모델 하나씩 한 줄로 돌렸다(위 비교는 동시에 돌려 시간에 쓰지 않는다).

| | p50 | p95 | 최대 |
|---|---|---|---|
| P0 엑사원, 입력당 | 0.51초 | 0.82초 | 1.0초 |
| P0 gpt-oss, 입력당 | 1.16초 | 2.36초 | 2.6초 |
| P4 gpt-oss 고르기 호출 | 0.82초 | 1.54초 | 1.8초 |
| P4 gpt-oss 판정기 호출 (26회) | 1.12초 | 2.30초 | 2.8초 |
| **P4 gpt-oss, 입력당** | **2.08초** | **3.35초** | 4.1초 |

켜면 이 단계를 타는 하위 질문(검색이 비었을 때의 복합 하위 상담)마다 지금(P0 엑사원)보다 p50 기준 약 1.6초, p95 기준 약 2.5초가 늘어난다.

비용은 캡처 2번과 비교·응답 시간 측정을 합쳐 Bedrock 약 4,000회, 약 $1로 추정한다(실제 청구액 미확인).

### 재현

캡처 실행기(`ChatJudgeCaptureProbe`, `scripts/chat_judge`)와 평가셋은 TELME-180 브랜치, 앞단 gpt-oss 설정은 TELME-182 브랜치에 있다. 캡처는 TELME-182 브랜치 앱으로 `docs/PLAN_QUESTION_MODEL.md`(TELME-182)의 캡처 명령에 `CONSULT_PLAN_QUESTION_BEDROCK=true`를 더해 돌렸다. 블라인드 문장은 `blind-fixture.json.gz`를 `--fixture`로 준다.

```bash
# 측정 DB 확인: 1152가 아니면 멈춘다
docker exec telme-flow-postgres psql -U telme -d telme_judge_eval -Atc "select count(embedding_question) from faq_embeddings"
R=scripts/candidate-evidence-evaluation/results/telme184
# 입력 꺼내기 (개발용, 블라인드)
python -I scripts/candidate-evidence-evaluation/candidate_replay.py extract <개발용 캡처>/capture.json dev-inputs.json
python -I scripts/candidate-evidence-evaluation/blind_replay.py extract <블라인드 캡처>/capture.json blind-inputs.json
# 비교: gpt-oss는 동시에, 엑사원은 안마다 따로 (P5는 앱 검색을 불러 측정 DB가 필요하다)
POSTGRES_PORT=5440 CANDIDATE_REPLAY_PROBE=true CANDIDATE_REPLAY_INPUT=<절대 경로>/dev-inputs.json CANDIDATE_REPLAY_OUT=<절대 경로>/replay.jsonl \
CANDIDATE_REPLAY_VARIANTS=P0,P1,P2,P3,P4,P5 CANDIDATE_REPLAY_MODELS=gptoss:3 \
LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=telme-bedrock-eval \
  ./gradlew test --tests 'com.telme.probe.CandidateEvidenceReplayProbe' --rerun-tasks
# 채점 (저장된 결과로 다시 계산)
python -I scripts/candidate-evidence-evaluation/score_dev.py $R/dev-labels.json $R/dev-replay.jsonl.gz dev-score.md
python -I scripts/candidate-evidence-evaluation/blind_replay.py score $R/blind-inputs.json.gz $R/blind-replay.jsonl.gz blind-score.md
# 응답 시간: 안·모델 하나씩 CANDIDATE_REPLAY_LIMIT=30, CANDIDATE_REPLAY_MODELS=gptoss:1 등으로 따로
```

| 파일 (`scripts/candidate-evidence-evaluation/`) | 내용 |
|---|---|
| `candidate_replay.py` | 캡처에서 근거 검증 입력과 후보 꺼내기, 턴 정답 기준 채점 |
| `blind_replay.py` | 블라인드 입력 꺼내기(하위 질문을 고객 문장의 부분에 맞춤), 부분 정답 기준 채점 |
| `score_dev.py` | 개발용 하위 질문 라벨로 안별 채점 |
| `results/telme184/dev-*` | 개발용 캡처, 입력 57개, 라벨, 안별 결과(P6 포함) |
| `results/telme184/blind-*` | 블라인드 문장 40개와 정답, 캡처 입력, 캡처, 입력 26개, 안별 결과 |
| `results/telme184/latency.jsonl.gz` | 응답 시간 측정(호출별 시간 `callMs`, `judgeMs`) |
| `src/test/java/com/telme/probe/CandidateEvidenceReplayProbe.java` | 같은 입력을 안·모델·회차별로 돌리는 측정 프로그램 |

## 적용

- 플래그: `telme.consult.evidence-resolver.bedrock`(환경 변수 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`). **기본 꺼짐**이라 켜지 않으면 지금처럼 엑사원이다. 켜면 gpt-oss(Bedrock)를 먼저 쓰고 호출이 실패하면 엑사원으로 대체한다. 되묻기 조건 뽑기(`CONSULT_FAQ_CLARIFICATION_BEDROCK`)와 같은 방식이다.
- Bedrock을 쓸 수 없으면(`llm.bedrock.enabled=false` 등) 플래그를 켜도 엑사원이다.
- 후보 근거 검증의 판정 방식: `telme.consult.evidence-resolver.candidate-judge`(환경 변수 `CONSULT_CANDIDATE_EVIDENCE_JUDGE`). **기본 꺼짐**이라 켜지 않으면 지금처럼 단어 검사로 막는다. 켜면 단어 검사 대신 고객 원문을 함께 주고 고른 FAQ를 회색 구간 판정기로 다시 판정한다(위 TELME-184 절의 P4). 판정기는 Bedrock이 켜졌을 때만 있어 Bedrock이 꺼져 있으면 켜도 단어 검사로 막는다. 켤 때는 고르는 모델도 gpt-oss로 두려고 `CONSULT_EVIDENCE_RESOLVER_BEDROCK=true`를 함께 켠다.
- 두 호출은 `EvidenceResolverConfiguration`에서 만든다. 두 호출만 켜고 끌 뿐 다른 호출(문맥 판정, 라우팅 등)의 모델은 바꾸지 않는다.
- 출력 한도는 플래그와 관계없이 올라간 값(비교 800, 후보 512)을 쓴다. 엑사원은 같은 결과였다.
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
