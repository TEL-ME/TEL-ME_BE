# LLM Bedrock gpt-oss-120b 전환 기능별 비교

앱의 LLM 호출을 전부 Bedrock gpt-oss-120b로 바꿨을 때 기능별로 EXAONE 3.5 7.8B와 어떻게 다른지 모은 문서다. 어떤 기능을 바꿀지 정하는 근거로 쓴다. 운영 코드는 바꾸지 않았다.

비교는 전체 EXAONE(모든 LLM 호출 EXAONE) 대 전체 Bedrock(모든 LLM 호출 gpt-oss)이다. 예외는 대화 흐름 22개 하나로, 최신 develop의 회색 구간 판정(#150)이 두 구성 모두 Bedrock이다.

## 측정 방식

- 앱 코드는 Ollama API로만 LLM을 부른다. 평가용 라우터(`scripts/judge-serving-evaluation/judge_router.py --chat bedrock --judge bedrock`)를 띄우고 `OLLAMA_URL`을 라우터로 바꿔 모든 LLM 호출을 Bedrock(도쿄, `ap-northeast-1`)으로 보냈다. 라우터는 스키마 강제 요청을 지시문으로 바꾸고, 출력 한도를 최소 512로, 추론 강도를 low로 보낸다.
- 비교 대상 EXAONE은 같은 코드를 로컬 PC의 Ollama(Q4_K_M) 또는 EC2 vLLM(AWQ)으로 돌렸다.
- 임베딩은 모든 측정에서 로컬 Ollama bge-m3다.
- 라우터가 Bedrock 응답을 다 받은 뒤 넘기므로 답변 스트리밍과 첫 글자 시간은 측정하지 않았다.
- gpt-oss는 같은 입력에도 결과가 흔들려 모든 항목을 같은 커밋으로 3회 이상 쟀다. EXAONE은 회차별 차이가 작아(조건 뽑기 74% / 72%) 1회를 기준으로 했다.

## 기능별 결과

| 기능 | 평가 | 코드 | 횟수 | EXAONE | gpt-oss-120b |
| --- | --- | --- | --- | --- | --- |
| 단일 질문 답변 | 180문항, 답할 수 있는 110 중 근거 있는 답변 | `2a73ba35`(#141), #145 | gpt-oss 4회 | 46, 정답 FAQ를 찾고도 판정기가 막음 2 (B2) | 49 (E2), 라우팅 보정 후 53 / 56 / 56, 막음 0 |
| 문맥 해석 | `context-resolution-evaluation/cases.json` 9개 묶음 213건 | `d86b0400`, #156 | gpt-oss 3회 | 202 | 199 / 202 / 204 → #156 보완 후 204 / 203 / 207 (EXAONE 203) |
| 조건 뽑기 (처음 무엇을 물을지) | `condition-eval/cases.json` 46문항 | `4d428865` | gpt-oss 3회 | 34 (74%) / 33 (72%) | 38 (83%) / 36 (78%) / 38 (83%) |
| 대화 요약 (운영 방식) | `multiturn-evaluation/summary-cases.json` 60사례 | `4d428865` | gpt-oss 3회 | 60/60, 필수 문구 60/60 | 3회 모두 60/60, 필수 문구 60/60 |
| FAQ+매장 복합 라우팅 | `compound-store-evaluation` 16문항 | `4d428865` | gpt-oss 3회 | 15/16 | 16 / 16 / 16 |
| 복합 FAQ·비교 | `compound-faq-evaluation` 7사례 × 3 | `4d428865` | gpt-oss 3회 | 15/21 | 12 / 12 / 12 (21 중) |
| 세션 제목, 상담 종료 요약(TELME-207에서 기능 제거) | 상담 20개 | `aea06404` | gpt-oss 3회 | 20/20, 20/20 | 3회 모두 20/20, 20/20 |
| 대화 흐름 끝까지 | 되묻기·복합·매장·멀티턴 22개 (아래) | `ef23b28a` | gpt-oss 3회 | 16/22 | 13 / 13 / 15 |
| 근거 판정 | #141 | `2a73ba35` | 1회 | 정답을 찾고도 막음 2~4 | 0 (Bedrock 판정으로 결정됨) |

- 복합 FAQ 7사례 중 "요금제 종류 알려주고 강남역 매장 찾아줘"는 평가 기대값이 #135 이전 동작(나눠 달라는 안내)이라 두 모델 모두 실패로 잡힌다. gpt-oss의 추가 실패 3건은 명의변경 서류 질문에 바로 답하지 않고 "대리인으로 방문하실 예정인가요?"라고 되물은 것이다.
- 대화 요약의 텍스트 방식(운영 미사용)은 필수 문구 보존이 EXAONE 28~29/60, gpt-oss 10~14/60이었다.
- 단일 질문의 EXAONE은 전체 EXAONE 구성인 B2(생성·판정 모두 vLLM EXAONE)다. 생성만 EXAONE이고 판정이 Bedrock인 C2도 근거 있는 답변 46, 막음 0이다.

## 대화 흐름 끝까지 (22개)

단일 질문 평가는 되물은 뒤의 답변, 복합 질문의 답 내용, 멀티턴 답변을 보지 않는다. 실제 채팅 API로 대화를 끝까지 진행하고, 마지막 답변을 근거 FAQ 원문과 대조해 판정했다. 스크립트는 [run_flow_scenarios.py](../../../scripts/bedrock-switch-evaluation/run_flow_scenarios.py), 원시 결과는 [results](../../../scripts/bedrock-switch-evaluation/results)다. 최신 develop은 회색 구간 판정(#150)이 켜져 있어 두 구성 모두 그 판정은 Bedrock이다.

| 묶음 | EXAONE 맞음 (1회) | gpt-oss 맞음 (3회) |
| --- | --- | --- |
| 되묻기 완주 (8) | 6 (+부분 1) | 6 / 5 / 7 |
| 복합 FAQ·비교 (5) | 4 (+부분 1) | 3 / 3 / 3 |
| FAQ+매장 복합 (4) | 4 | 2 / 3 / 2 |
| 멀티턴 (5) | 2 (+부분 2) | 2 / 2 / 3 |
| 합계 (22) | 16 | 13 / 13 / 15 |

gpt-oss가 나쁜 곳은 되물은 뒤의 처리이고, 3회 내내 같은 시나리오에서 반복됐다.

- 고객이 "둘 다 직접 매장에 갈 거예요"라고 답해도 "대리인으로 방문하실 예정인가요?"를 다시 묻는다. CO-01, CO-02, ST-03은 3회 모두, CL-07은 2회 이렇게 끝나 명의변경 서류를 안내하지 못했다. EXAONE은 같은 답을 받고 서류를 안내했다.
- CL-01은 "개통한 지 1년 넘었고 남은 할부금은 없어요"라고 답해도 3회 모두 할부금을 다시 물었다. EXAONE은 한 번 묻고 답했다. #154(같은 사실을 두 번 묻는 문제 개선)가 들어간 코드에서도 남는다.
- 조건 답변을 새 질문으로 보고 "어떤 내용에 대한 질문인지 확인이 필요합니다"로 끝나기도 한다(1회차 CO-01·MT-05, 3회차 CO-02).
- 복합 질문의 FAQ 쪽을 놓친다. ST-01 "유심 재발급 비용"을 3회 중 2회 정보 부족으로 답했다. 같은 질문을 단독으로 하면 7,700원을 맞힌다.

조건 뽑기 46문항에서 gpt-oss가 더 정확했던 것은 처음에 무엇을 물을지만 본 결과다. 고객이 답한 뒤의 처리는 이 측정에서 처음 확인했다.

두 모델 공통 문제(모델과 무관):

- 멀티턴 MT-01(인터넷 해지·장비 반납)과 MT-02(번호이동 수수료)는 FAQ에 답이 없다. 처음에는 FAQ에 있는 사실로 정답을 잘못 정해 실패로 셌고, 정답을 근거 없음으로 고쳤다. 두 모델 모두 매번 근거 없음으로 답해 맞았다.
- 나머지 멀티턴은 두 모델 모두 대부분 실패했다(gpt-oss 3회차 MT-04 택배 수령만 맞음). 로밍 데이터 소진 후속 질문은 다른 FAQ(국내 요금제 400kbps, 로밍 20만 원 한도)로 답했다.
- 업무를 말했는데도 매장 업무를 다시 묻는다("번호이동 필요 서류랑 가까운 대리점" → "어떤 업무로 매장을 찾으시나요?").

## 다른 판정 모델

문맥 해석 213건, #156 코드, 3회. 흔들림은 회차마다 판정이 달라진 사례 수다.

| 모델 | 3회 점수 | 항상 틀림 | 흔들림 | 단가 (100만 토큰, 입력/출력) | 도쿄 분당 요청 한도 |
| --- | --- | --- | --- | --- | --- |
| gpt-oss-120b | 204 / 203 / 207 | 4 | 11 | $0.18 / $0.73 | 1,000 |
| Claude Haiku 4.5 (`jp.` 프로필) | 208 / 206 / 208 | 4 | 4 | $1.10 / $5.50 | 50 (교차 리전) |
| gemma-3-12b | 195 / 194 / 195 | 18 | 1 | $0.11 / $0.35 | 1,000 |
| Nova Lite | 측정 불가 | | | $0.072 / $0.29 | 20 |

- 추론 단계가 없는 모델(Haiku, Gemma)은 덜 흔들린다. gpt-oss의 흔들림은 답 전에 추론 토큰을 쓰는 방식과 MoE 구조에서 오는 것으로 본다.
- Haiku는 정확도와 안정성이 가장 좋지만 단가가 6~7배이고 분당 한도가 낮다. Gemma는 정확도가 10점 낮다. Nova Lite는 분당 20회 한도에 걸려 요청이 거절됐다.
- Claude, Gemma, Nova는 JSON을 ```json 코드 블록으로 감싸 응답한다. 이 모델을 쓰려면 클라이언트가 벗겨야 한다.

## 비용

- 180문항 측정 기준 질문당 Bedrock 호출 약 2.7회, 입력 약 5,600토큰, 출력 약 300토큰. gpt-oss-120b 도쿄 단가로 질문당 약 $0.0012(약 1.7원, 1,400원/$ 가정).
- 이 문서의 측정 전체에 든 Bedrock 비용은 10달러 미만으로 추정한다(Cost Explorer 권한이 없어 토큰 기록 × 가격표로 계산).

## 정리

- gpt-oss가 확실히 나은 곳: 근거 판정, 조건 뽑기(첫 질문), 단일 질문 근거 답변(라우팅 보정 조건).
- 차이가 없는 곳: 문맥 해석(#156 보완 후), 대화 요약, 상담 종료 요약(TELME-207에서 기능 제거), 세션 제목.
- gpt-oss가 나쁜 곳: 되물은 뒤의 처리(조건 답변 해석, 같은 조건 재질문), 복합 질문에서 되묻기가 잦음.
- EXAONE을 한 곳이라도 남기면 운영에 EXAONE 서버와 Bedrock을 함께 둬야 한다.

## 한계와 남은 확인

- gpt-oss는 3회, EXAONE은 대부분 1회다. 대화 흐름 22개의 정답 여부는 FAQ 원문과 대조해 사람이 판정했다. CO-01은 정한 조건 답변이 실제 되물은 내용(미납 요금)과 맞지 않았다.
- 답변 내용의 정확도·환각은 별도로 채점하지 않았다. 근거 있는 답변 수와 흐름 22개의 대조가 전부다.
- 스트리밍 첫 글자 시간, 요금제 질문(#151)은 측정하지 않았다.
- 실제 Bedrock 클라이언트로 바꾼 뒤 같은 평가를 다시 돌려 라우터 결과와 같은지 확인해야 한다.

## 재현

1. 라우터: `python scripts/judge-serving-evaluation/judge_router.py --port 11435 --chat bedrock --judge bedrock --profile <AWS 프로필> --log router.jsonl`
2. 앱: `OLLAMA_URL=http://127.0.0.1:11435 LLM_PROVIDER=ollama`로 기동. 최신 develop은 회색 구간 판정이 앱에서 직접 Bedrock을 부르므로 `AWS_PROFILE`, `LLM_BEDROCK_REGION=ap-northeast-1`도 준다.
3. 평가별 실행
   - 180문항: `scripts/single-faq-search-evaluation/run_chat_eval.py`
   - 문맥 해석: `docs/01-pipeline/02-context-routing/context-resolution.md` "평가 재실행"
   - 조건 뽑기: `ConditionExtractionEval` (`LLM_PROVIDER=ollama`)
   - 대화 요약: `ChatSummaryLiveEvaluationTest` (`RUN_TELME121_LIVE=true`)
   - 복합: `scripts/compound-store-evaluation/run_routing.py`, `scripts/compound-faq-evaluation/run_live_api.py --repetitions 3`
   - 대화 흐름: `python scripts/bedrock-switch-evaluation/run_flow_scenarios.py --base-url <앱> --database <앱 DB> --output flows.json`
