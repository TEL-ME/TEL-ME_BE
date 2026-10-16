# TEL-ME 채팅 답변 품질 LLM Judge

실제 채팅 API가 만든 답변을 근거성, 질문 충족도, 보류 판단의 세 축으로 판정한다. 판정 기준과 지표는 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md), 실행과 점수 계산, 코드가 정하는 판정은 [서비스 답변 평가](SERVICE_EVALUATION.md)에 있다.

TELME-100에서 만든 Judge와 실험(V1–V7)을 TELME-180에서 최신 develop으로 옮겼다. 지금 쓰는 실행 경로의 코드, 테스트, 평가 입력만 옮기고 실험 원시 결과와 사람 판정표는 [TELME-100 실험 기록](https://github.com/TEL-ME/TEL-ME_BE/tree/ae637974770f03fa8620c6b5540877becf1ff0c5/docs/chat-judge)에 남겼다.

## 구성

```text
docs/chat-judge/
  CHAT_PIPELINE_QUALITY_CRITERIA.md  판정 기준과 보고 지표
  SERVICE_EVALUATION.md              실제 파이프라인 평가 실행, 점수 계산, 코드가 정하는 판정
  judge-validation-v1-review-form.md Judge 검증셋 판정자 B용 판정표
  LABEL_REVIEW.md                    평가셋 라벨·판정 기준 중 사람이 정할 항목
scripts/chat_judge/
  judge_chat_flow.py                 주장 추출과 근거성 판정, 출력 검증, 보류 판정 결합
  build_service_quality_set.py       서비스 품질 평가셋 v2.1 생성(v2는 서비스 데이터 라벨 없이 같은 빌더로 재현)
  relabel_capture.py                 저장한 캡처의 평가 라벨을 새 평가셋 버전으로 교체
  build_service_flow_set.py          되묻기·매장·로그인 회원 흐름 평가셋 v1 생성
  build_judge_validation_set.py      Judge 검증셋 v1 생성(TELME-100 V7 사람 판정에서 층화 추출)
  run_judge_validation.py            Judge 검증셋을 판정하고 사람 판정과 축별 비교(vLLM 또는 Bedrock)
  run_bedrock_judge.py               저장한 캡처를 Bedrock 모델 하나로 판정(Qwen3 235B, DeepSeek V3.2, Mistral Large 3)
  combine_judges.py                  모델별 판정을 판정 뒤에 결합(하나라도 실패면 실패, 다수결)
  service_data.py                    요금제 표 근거, 앱 고정 안내·대체 문구(코드 규칙)
  backfill_capture_service_data.py   예전 캡처에 실행 단계 기록과 요금제 표를 평가 DB에서 채움
  data/                              평가셋, 평가셋 원본(quality_v2_sources/), 테스트용 파일럿 기록(fixtures/)
  experiments/v6_live_chat_pipeline/ 실제 Spring 채팅 API로 답변을 만들고 vLLM Judge로 채점(충족도·보류 프롬프트)
  experiments/v7_cross_judge/        저장한 답변을 Bedrock 모델로 다시 채점(V6와 같은 프롬프트와 규칙)
src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java  채팅 실행 기록 수집
```

판정 경로는 하나다. 근거성은 `judge_chat_flow.py`의 주장 추출과 근거 판정, 충족도와 보류는 `evaluate_chat_pipeline.py`의 프롬프트를 쓰고, Bedrock 재채점도 같은 프롬프트와 코드 규칙을 쓴다. `judge_chat_flow.py`에 남은 Ollama용 충족도·보류 프롬프트는 V1 파일럿 재현용이다.

## 평가셋

| 평가셋 | 용도 | 구성 |
| --- | --- | --- |
| [`chat_pipeline_quality_v1.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v1.json) | 이전 기준(TELME-100) | 카탈로그 질문 500(400개가 FAQ 질문 문구 그대로), 흐름 회귀 9턴 |
| [`chat_pipeline_quality_v2.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v2.json) | 서비스 답변 품질 측정(고정) | 아래 표 |
| [`chat_pipeline_quality_v2_1.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v2_1.json) | 서비스 답변 품질 측정 | v2와 같은 질문에 서비스 데이터 라벨을 더했다(아래) |
| [`chat_pipeline_flows_v1.json`](../../scripts/chat_judge/data/chat_pipeline_flows_v1.json) | 되묻기·매장·로그인 회원 흐름 | 매장 찾기 12, 되묻기 12, 로그인 회원 12개 대화(아래) |
| [`judge_validation_v1.json`](../../scripts/chat_judge/data/judge_validation_v1.json) | Judge 자체의 정확도 측정 | TELME-100 V7 실제 답변 116건과 사람 판정 |

서비스 품질셋 v2는 238개 대화, 259턴이다. 질문과 필수 사실은 Judge 프롬프트와 평가 기준을 보지 않은 별도 작성자(에이전트)가 썼고, 원본은 [`quality_v2_sources/`](../../scripts/chat_judge/data/quality_v2_sources)에 있다. 사람 검토 전이다.

| 묶음 | 턴 | 만든 방법 |
| --- | ---: | --- |
| `paraphrased_single` | 120 | 10개 분야에서 FAQ 12개씩 골라 고객 말투로 다시 쓴 질문과 필수 사실. 2개는 고객 조건에 따라 답이 갈려 되묻기도 정상(`ANSWER_OR_CLARIFY`) |
| `paraphrased_compound` | 20 | 위 질문 중 서로 다른 분야 두 개를 한 문장으로 묻는 질문 |
| `multiturn` | 40 | 같은 분야 FAQ 두 개로 만든 2턴 대화 20개. 15개는 둘째 턴이 대상을 생략 |
| `no_faq_answer` | 60 | 통신 질문이지만 FAQ 답변 본문으로 답이 확정되지 않는 질문(없는 세부·상품·정책, 다른 회사, 개인 계정). 다른 검수자가 임베딩으로 찾은 가까운 FAQ 5개와 FAQ 전체 검색으로 답이 없음을 확인했다 |
| `adjacent_out_of_scope` | 10 | 통신과 가깝지만 상담 범위 밖인 질문(앱 사용법, 기기 설정 등) |
| `flow_regression` | 9 | v1의 흐름 회귀 대화 그대로 |

카탈로그에는 문구만 다르고 같은 내용을 답하는 FAQ가 많다. 그래서 답변 가능 질문의 질문 부분 200개마다 인정할 FAQ를 별도 검수자가 다시 정했다.
- 후보: v1의 대체 FAQ 묶음, 첫 실행에서 실제로 검색된 FAQ, 질문과 임베딩으로 가까운 FAQ 6개(`equivalence_candidates.json`). 답이 없는 질문의 검수 후보(`unanswerable_candidates.json`)와 함께 [`review_candidates.tar.xz`](../../scripts/chat_judge/data/quality_v2_sources/review_candidates.tar.xz)로 묶었다. 코드는 읽지 않는 검수 자료다
- 기준: FAQ 답변 본문에 필수 사실이 모두 있는 FAQ만 인정(`equivalence_labels.json`)
- 결과: 질문을 쓸 때 쓴 FAQ는 모두 인정됐다. v1 대체 FAQ 중 필수 사실이 빠진 24개는 빠지고, 427개가 새로 인정됐다(질문 부분당 평균 3.5개).
- 검수 중 필수 사실이 질문을 다 덮지 못한다고 표시된 문항(예: 토요일 영업을 묻는데 평일 시간만 적힘)은 `note`에 남겼다. 사람 검토 때 먼저 볼 대상이다.

v2.1은 v2에 [`service_data_labels.json`](../../scripts/chat_judge/data/quality_v2_sources/service_data_labels.json)을 더한 라벨이다. v2는 FAQ 답변 본문만 보고 답의 유무를 정했지만 채팅은 요금제 표와 회원 정보로도 답한다.
- 턴마다 답의 출처(`answerSources`: `FAQ`, `PLAN_TABLE`, `MEMBER`, `STORE`)를 적었다.
- 요금제 목록·최저가처럼 요금제 표로도 답이 정해지는 질문 부분 10개는 요금제 표(`plans_snapshot.json`)도 인정 근거로 더했다.
- FAQ에는 답이 없지만 요금제 표로 답이 정해지는 질문 5개를 답변 질문으로 바꿨다. 시니어 요금제 가격(U018), 판매하지 않는 요금제(U023 5G 프리미엄 플러스, U024 LTE 슬림, U025 군인 전용, U030 키즈워치 전용)는 "판매 중인 요금제에 없다"가 정답이다.
- 내 요금·사용량·위약금 질문 3개(U049–U051)는 비로그인 실행에서는 로그인 안내가 정답(보류)이고, 로그인 실행에서는 회원 데이터로 답해야 한다(`memberExpectedBehavior`). 로그인 실행은 아직 없다.
- 복합 질문 20개의 질문 부분 이름은 v2에서 원래 단일 질문 문장이었다(예: 고객은 평일 마감을 묻는데 질문 부분은 토·일요일 영업). 고객 문장에서 나눈 부분 질문으로 바꿨다([`compound_parts.json`](../../scripts/chat_judge/data/quality_v2_sources/compound_parts.json), 별도 작성자).
- 정답 FAQ 검수 메모 37건은 턴의 `reviewNotes`에 있다. 복합 질문의 필수 사실 중 부분 질문이 묻지 않는 사실 9곳은 뺐고(검토 목록 D2), 단일 질문 보완 제안 3곳은 정할 때까지 적용하지 않았다([라벨 검토 목록](LABEL_REVIEW.md)).
- 이전 캡처의 답변을 새 라벨로 채점할 때는 `relabel_capture.py`로 라벨만 바꾼다.

흐름 평가셋 v1은 앱이 조건을 되묻는 대화를 고객 답변까지 이어서 본다. 고객 조건에 따라 답이 갈리는 되묻기 8개는 앱이 되묻지 않고 바로 답했을 때의 필수 사실도 둔다([`direct_answer_facts.json`](../../scripts/chat_judge/data/flow_v1_sources/direct_answer_facts.json), 별도 작성자). 시나리오는 Judge 프롬프트와 평가 기준을 보지 않은 별도 작성자(에이전트)가 썼다([`flow_v1_sources/`](../../scripts/chat_judge/data/flow_v1_sources)).
- `store_flow` 12: 매장 찾기. 앱이 업무와 지역을 되물으면 답한다. 8개는 '현재 위치 사용'(좌표), 4개는 지명으로 답한다. 지명 검색은 카카오 로컬 API가 필요해 키가 없으면 실행기가 빼고 `manifest.json`의 `skippedCases`에 남긴다. 질문에 업무를 말했어도 앱이 업무를 되물을 수 있어, 시나리오에 업무 답변이 없으면 기대 업무로 만든 답변을 넣고 `auto`로 표시했다.
- `clarification_flow` 12: FAQ 답이 고객 조건에 따라 갈리는 질문 8(고객이 모른다고 답하는 2개 포함)과 비회원 요금제 추천 4.
- `member_data` 12: 로그인한 테스트 회원의 내 요금제·사용량·예상 요금·약정·위약금·사용량 기반 추천 9(2턴 대화 2개 포함)과 회원 데이터가 없는 질문 3. 프로브가 평가 DB에 테스트 회원(`judge-member@telme.test`, 5G 스탠다드, 24개월 약정, 8개월 전 가입)이 없을 때만 만들고, 대화 직전 계정 데이터를 `memberData`로 남긴다.

Judge 검증셋 v1은 TELME-100 V7의 사람 판정 302건 중 단일 질문 답변에서 근거 없는 답 39, 과도한 거절 7, 근거는 맞지만 부분·미답 25, 정상 20, 근거 없어 보류 15, 기타 10건을 골랐다. 지금 라벨은 판정자 한 명(A)의 것이다. 두 번째 판정자(B)가 [판정표](judge-validation-v1-review-form.md)를 A와 모델 판정을 보지 않고 채운 뒤 `run_judge_validation.py --review-b`로 A·B 일치도와 Judge 일치율을 낸다.

## 실행

저장소 루트에서 실행한다.

```powershell
# 실제 API 답변 생성부터 vLLM 채점과 보고서 생성까지(v2)
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --fixture scripts/chat_judge/data/chat_pipeline_quality_v2.json

# 생성 답변을 바꾸지 않고 Judge만 다시 실행
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --capture .measure/이전실행/capture.json

# Judge 검증셋 판정과 사람 판정 비교(vLLM이 떠 있어야 한다)
python -X utf8 -m scripts.chat_judge.run_judge_validation --out .measure/judge-validation.json.gz
python -X utf8 -m scripts.chat_judge.run_judge_validation --judged .measure/judge-validation.json.gz --review-b docs/chat-judge/judge-validation-v1-review-form.md

# 근거가 바뀐 턴만 다시 판정(답변·근거가 같은 턴은 이전 판정 재사용, 코드 규칙은 지금 기준)
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model qwen --prior <이전 결과> [--prior-capture <이전 결과의 캡처>] --allow-bedrock-call --out <새 결과>

# 예전 캡처에 실행 단계 기록·요금제 표를 채우고 새 라벨을 입힘(답변은 그대로)
python -X utf8 -m scripts.chat_judge.backfill_capture_service_data <캡처> --out <보강 캡처>
python -X utf8 -m scripts.chat_judge.relabel_capture <보강 캡처> --fixture scripts/chat_judge/data/chat_pipeline_quality_v2_1.json --out <새 라벨 캡처>

# 저장한 판정에 지금 코드 규칙을 다시 적용해 결합(모델 호출 없음)
python -X utf8 -m scripts.chat_judge.combine_judges <결과...> --capture <캡처>

# 평가셋 다시 만들기
python -X utf8 -m scripts.chat_judge.build_service_quality_set
python -X utf8 -m scripts.chat_judge.build_judge_validation_set --v7-dir <TELME-100 체크아웃>/docs/chat-judge/experiments/V7-bedrock-cross-judge

# 테스트
python -X utf8 -m unittest scripts.chat_judge.test_judge_chat_flow scripts.chat_judge.test_judge_validation scripts.chat_judge.test_combine_judges scripts.chat_judge.test_service_data scripts.chat_judge.experiments.v6_live_chat_pipeline.test_chat_pipeline_evaluation scripts.chat_judge.experiments.v7_cross_judge.test_v7
```

- `telme-ollama`, `telme-judge-vllm-bounded` 컨테이너와 모델, FAQ가 있는 PostgreSQL이 필요하다. 평가 DB는 `telme_judge_eval`만 쓴다(없으면 `--postgres-container`의 `telme` DB를 복제). 16GB GPU에서는 생성이 끝난 뒤 Ollama를 내리고 vLLM을 올린다.
- 각 실행은 새 `.measure/chat-pipeline-날짜-시간/`에 질문, 캡처, Judge 요청과 응답, `scores.json`, `report.md`를 저장한다.
- 회색 구간 근거 판정은 Bedrock을 부른다. Bedrock 없이 돌릴 때는 `SEARCH_GRAY_ZONE_ENABLED=false`로 끈다.
- Bedrock 재채점(`experiments/v7_cross_judge/run_cross_judge.py`)은 `--allow-bedrock-call`을 줄 때만 모델을 호출한다.
