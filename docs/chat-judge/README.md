# TEL-ME 채팅 답변 품질 LLM Judge

실제 채팅 API가 만든 답변을 근거성, 질문 충족도, 보류 판단의 세 축으로 판정한다.

| 문서 | 내용 |
| --- | --- |
| [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md) | 판정 기준, 근거로 보는 것, 코드가 정하는 판정, 보고 지표 |
| [측정 절차](SERVICE_EVALUATION.md) | 답변 캡처, 판정, 결합, 결과 파일, 재판정 |
| [라벨 검토 목록](LABEL_REVIEW.md) | 평가셋 라벨과 판정 기준 중 사람이 정한 것과 정할 것 |
| [판정자 B 판정표](judge-validation-v1-review-form.md) | Judge 검증셋을 두 번째 사람이 판정하는 양식 |

TELME-100에서 만든 Judge를 TELME-180에서 최신 develop으로 옮겼고, 지금 쓰는 경로만 남겼다. TELME-100의 실험(V1–V7), Ollama 판정 경로, 원시 결과는 [TELME-100 기록](https://github.com/TEL-ME/TEL-ME_BE/tree/ae637974770f03fa8620c6b5540877becf1ff0c5/docs/chat-judge)에 있다.

## 판정 구성

- **판정 모델:** Bedrock의 Qwen3 235B A22B 2507, DeepSeek V3.2(오레곤), Mistral Large 3로 따로 판정하고, 하나라도 실패면 실패로 결합한 값을 대표 수치로 쓴다([판정 모델 선정](#판정-모델-선정)). 로컬 vLLM(Qwen3-14B-AWQ)도 같은 프롬프트로 판정할 수 있다.
- **근거성:** 답변에서 사실 주장을 원문 그대로 뽑은 뒤 주장마다 실제로 생성에 전달된 근거로 판정한다. 근거는 FAQ 답변 본문과, 앱이 FAQ 밖 데이터로 답했으면 그 데이터(요금제 표, 회원 계정 데이터, 매장 목록, 추천 규칙)다.
- **충족도:** 질문 부분마다 정답 FAQ 묶음과 필수 사실을 주고 답변이 그것을 담았는지 판정한다.
- **보류:** 답변이 거절인지와 전달된 근거로 답할 수 있었는지를 따로 판정해 합친다.
- **코드 규칙:** 기대 동작과 응답 유형만으로 정해지는 판정(범위 밖 질문, 거절 고정 문장, 매장 결과, 되묻기 재질문 등)은 모델을 부르지 않는다.

## 판정 모델 선정

2026-10-17에 **세 모델(Qwen3 235B, DeepSeek V3.2, Mistral Large 3)을 따로 판정하고 하나라도 실패면 실패로 결합**하기로 했다.

| 후보 | 결과 |
| --- | --- |
| Qwen3 235B A22B 2507 | 채택. 가장 싸고 빠르다(서비스 품질셋 259턴 $0.32, 4분) |
| DeepSeek V3.2 | 채택. 근거성 일치가 가장 높다. 도쿄 리전은 처리량이 막혀 오레곤을 쓴다(259턴 35분 → 3분, $0.78) |
| Mistral Large 3 | 채택. 혼자서는 미확정이 많지만(인용 문장을 원문과 다르게 옮김) 결합하면 잘못 통과시키는 답을 줄인다($0.74, 6분) |
| Qwen3 Next 80B | 제외. 검증셋에서 충족도 일치가 낮았다(66/116) |
| Claude Sonnet | 제외. 비용 |
| gpt-oss-120b | 제외. 채팅 답변 생성에 쓰는 모델이라 자기 답을 채점하지 않게 한다 |

Judge 검증셋 116건(필수 사실 포함, 판정자 A 기준)의 결합 방식 비교([후보 비교](results/20261016-judge-candidates/README.md)):

| 결합 | 최종 판정 일치 | 사람은 실패인데 통과 | 사람은 통과인데 실패 | 미확정 |
| --- | ---: | ---: | ---: | ---: |
| 세 모델, 하나라도 실패면 실패(채택) | 97 | 6 | 6 | 7 |
| Qwen3 235B + DeepSeek V3.2, 하나라도 실패면 실패 | 97 | 10 | 6 | 3 |
| 세 모델, 다수결 | 96 | 14 | 3 | 3 |

일치율은 두 모델 결합과 같지만, Judge가 가장 피해야 할 오류인 "사람은 실패로 본 답을 통과시킴"이 10건에서 6건으로 준다. 대가로 미확정이 늘고(사람 검토로 넘어간다) 판정 1회 비용이 서비스 품질셋 259턴 기준 약 $1.1에서 $1.8로 는다. 단일 모델은 사람이 실패로 본 답을 15–21건 통과시켰다. 같은 모델을 다시 돌려도 최종 판정이 약 5% 바뀌므로 최종 수치는 반복해 범위로 낸다.

## 코드와 데이터

```text
scripts/chat_judge/
  judge_chat_flow.py              근거성 판정(주장 추출, 근거 판정, 출력 검증), 캡처 읽기, 보류 판정 결합
  evaluate_chat_pipeline.py       충족도·보류 프롬프트, 코드 규칙, 턴 판정과 집계, vLLM 판정 실행
  service_data.py                 FAQ 밖 데이터 근거(요금제 표, 회원 데이터, 매장, 추천 규칙), 앱 고정 문구
  bedrock.py                      Bedrock 모델 호출(모델 별칭, 요금, JSON 출력 방식)
  run_chat_pipeline_eval.py       실제 채팅 API로 답변 캡처, 이어서 vLLM 판정
  run_bedrock_judge.py            저장한 캡처를 Bedrock 모델 하나로 판정(바뀐 턴만 다시 판정 가능)
  combine_judges.py               모델별 판정을 판정 뒤에 결합, 묶음별 신뢰구간과 묶음 평균
  run_judge_validation.py         Judge 검증셋 판정과 사람 판정 비교
  relabel_capture.py              저장한 캡처의 평가 라벨만 새 평가셋으로 교체
  backfill_capture_service_data.py  예전 캡처에 실행 단계 기록과 요금제 표를 평가 DB에서 채움
  export_chat_pipeline_review.py  사람 검토용 review.md 생성
  build_service_quality_set.py    서비스 품질셋 생성
  build_service_flow_set.py       흐름 평가셋 생성
  build_judge_validation_set.py   Judge 검증셋 생성
  test_*.py                       테스트
  data/
    chat_pipeline_quality_v2_2.json  서비스 품질셋(빌더 출력)
    chat_pipeline_flows_v2.json      흐름 평가셋(빌더 출력)
    judge_validation_v1.json         Judge 검증셋(빌더 출력)
    quality_sources/                 서비스 품질셋 원본과 라벨 결정
    flow_sources/                    흐름 평가셋 원본과 라벨 결정
    validation_sources/              Judge 검증셋 필수 사실
src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java  채팅 실행 기록 수집(캡처 프로브)
docs/chat-judge/results/                                   측정 결과(날짜별 폴더, 원시 파일은 raw.tar.xz)
```

## 평가셋

| 평가셋 | 용도 | 구성 |
| --- | --- | --- |
| [서비스 품질셋 v2.2](../../scripts/chat_judge/data/chat_pipeline_quality_v2_2.json) | 서비스 답변 품질 | 286개 대화, 307턴(아래) |
| [흐름 평가셋 v2](../../scripts/chat_judge/data/chat_pipeline_flows_v2.json) | 되묻기·매장 찾기·로그인 회원 흐름 | 88개 대화, 93턴(아래) |
| [Judge 검증셋 v1](../../scripts/chat_judge/data/judge_validation_v1.json) | Judge 자체의 정확도 | TELME-100 V7 실제 답변 116건과 사람 판정 |

평가셋 파일은 빌더 출력이고 직접 고치지 않는다. 원본을 고치고 빌더를 다시 돌린다. 이전 버전(서비스 v2·v2.1, 흐름 v1)은 파일을 두지 않고 빌더 옵션으로 다시 만든다(`build(service_labels=False)`는 v2, `build()`는 v2.1, `build_service_flow_set.build(version=1)`은 흐름 v1). 측정 결과의 `raw.tar.xz`에는 그때 쓴 라벨이 캡처 안에 들어 있다.

### 서비스 품질셋 v2.2

| 묶음 | 턴 | 내용 |
| --- | ---: | --- |
| `paraphrased_single` | 120 | 10개 분야에서 FAQ 12개씩 골라 고객 말투로 다시 쓴 질문. 2개는 고객 조건에 따라 답이 갈려 되묻기도 정상(`ANSWER_OR_CLARIFY`) |
| `paraphrased_compound` | 40 | 서로 다른 분야 두 가지를 한 메시지로 묻는 질문. 질문 부분은 고객 문장에서 나눈 부분 질문이다 |
| `paraphrased_partial` | 19 | 한 부분은 FAQ로 답할 수 있고 다른 부분은 FAQ 어디에도 답이 없는 질문(`PARTIAL_ANSWER`) |
| `multiturn` | 40 | 같은 분야 FAQ 두 개로 만든 2턴 대화 20개. 15개는 둘째 턴이 대상을 생략 |
| `no_faq_answer` | 60 | 통신 질문이지만 답이 없는 질문(없는 세부·상품·정책, 다른 회사, 개인 계정). 요금제 표나 회원 정보로 답할 수 있는 것은 그 출처로 라벨했다 |
| `adjacent_out_of_scope` | 19 | 통신과 가깝지만 상담 범위 밖인 질문 |
| `flow_regression` | 9 | TELME-100 v1의 흐름 회귀 대화. 통과율 평균에서는 뺀다 |

### 흐름 평가셋 v2

앱이 조건을 되물으면 평가셋에 적힌 고객 답변을 보내 최종 응답까지 받고 그 응답을 판정한다.

| 묶음 | 대화 | 내용 |
| --- | ---: | --- |
| `store_flow` | 30 | 매장 찾기. 업무와 지역을 되물으면 답한다. 15개는 '현재 위치 사용'(좌표), 15개는 지명. 지명 검색은 카카오 로컬 API 키(`KAKAO_REST_API_KEY`)가 있어야 해서 없으면 빼고 기록한다 |
| `clarification_flow` | 30 | FAQ 답이 고객 조건에 따라 갈리는 질문 20(고객이 모른다고 답하는 5개 포함)과 비회원 요금제 추천 10 |
| `member_data` | 28 | 로그인한 테스트 회원(5G 스탠다드, 24개월 약정, 8개월 차)의 내 요금제·사용량·예상 요금·약정·위약금·추천 질문 20(2턴 대화 5개 포함)과 회원 데이터가 없는 질문 8 |

### 라벨을 만든 방식

질문과 필수 사실은 Judge 프롬프트와 평가 기준을 보지 않은 별도 작성자(에이전트)가 FAQ 본문만 보고 썼고, 확인은 또 다른 검수자가 FAQ 1,150개 전체와 대조해 했다. 사람 검토는 아직이다(흐름 회귀 9턴만 사람이 썼다).

| 단계 | 서비스 품질셋 원본(`quality_sources/`) | 흐름 평가셋 원본(`flow_sources/`) |
| --- | --- | --- |
| 시드 | `answerable_seed.json`, `multiturn_seed.json`, `v22_seed.json`(고정 난수로 뽑은 시드) | - |
| 작성 | `answerable_written.json`, `multiturn_written.json`, `unanswerable_written.json`, `v22_written.json` | `flows_written.json`, `flows_v2_written.json` |
| 검수 | `unanswerable_verified.json`, `equivalence_labels.json`(정답 FAQ 묶음), `v22_verified.json` | `flows_v2_verified.json` |
| 보완과 결정 | `service_data_labels.json`(요금제 표·회원 정보 출처), `compound_parts.json`(복합 질문 부분 질문과 필수 사실 결정), `required_facts_fixes.json`, `plans_snapshot.json`, `flow_regression.json` | `direct_answer_facts.json`(바로 답했을 때 필수 사실), `review_decisions.json` |
| 검수 참고 | `review_candidates.tar.xz`(정답 FAQ 후보, 답 없는 질문 후보. 코드는 읽지 않는다) | - |

결정 하나하나의 이유와 날짜는 [라벨 검토 목록](LABEL_REVIEW.md)에 있다. 정답 FAQ 묶음은 문구만 다르고 같은 내용을 답하는 FAQ를 함께 인정한다(검수자가 FAQ 답변 본문에 필수 사실이 모두 있는 것만 인정했다).

### Judge 검증셋 v1

TELME-100 V7의 사람 판정 302건 중 단일 질문 답변에서 근거 없는 답 39, 과도한 거절 7, 근거는 맞지만 부분·미답 25, 정상 20, 근거 없어 보류 15, 기타 10건을 골랐다. 라벨은 판정자 한 명(A)의 것이다. 두 번째 판정자(B)가 [판정표](judge-validation-v1-review-form.md)를 A와 모델 판정을 보지 않고 채우면 `run_judge_validation.py --review-b`로 A·B 일치도와 Judge 일치율을 낸다. 답이 없는 질문, FAQ 밖 데이터 답변, 되묻기 대화는 들어 있지 않다.

## 실행

저장소 루트에서 실행한다. 자세한 절차는 [측정 절차](SERVICE_EVALUATION.md)에 있다.

```powershell
# 실제 채팅 API로 답변 캡처(서비스 품질셋이 기본, 흐름 평가셋은 --fixture로)
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --capture-only --out .measure/<실행 이름>
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --fixture scripts/chat_judge/data/chat_pipeline_flows_v2.json --capture-only --out .measure/<실행 이름>

# Bedrock 판정(모델마다 따로, 유료 호출이라 --allow-bedrock-call 필요)
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model qwen --allow-bedrock-call --out <결과.json.gz>
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model deepseek-v3.2-usw2 --workers 16 --allow-bedrock-call --out <결과.json.gz>
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model mistral-large-3 --allow-bedrock-call --out <결과.json.gz>

# 판정 입력이 바뀐 턴만 다시 판정(답변·근거·라벨이 같은 턴은 이전 판정 재사용)
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model qwen --prior <이전 결과> --allow-bedrock-call --out <새 결과>

# 결합과 집계(모델 호출 없음). --capture를 주면 지금 코드 규칙으로 다시 집계한다
python -X utf8 -m scripts.chat_judge.combine_judges <qwen 결과> <deepseek 결과> <mistral 결과> --capture <캡처> --out <결합.json>

# 저장한 캡처에 새 라벨 입히기(답변은 그대로)
python -X utf8 -m scripts.chat_judge.relabel_capture <캡처> --fixture scripts/chat_judge/data/chat_pipeline_quality_v2_2.json --out <새 캡처>

# Judge 검증셋
python -X utf8 -m scripts.chat_judge.run_judge_validation --bedrock-model qwen --allow-bedrock-call --out <결과.json.gz>
python -X utf8 -m scripts.chat_judge.run_judge_validation --judged <결과.json.gz> --review-b docs/chat-judge/judge-validation-v1-review-form.md

# 평가셋 다시 만들기
python -X utf8 -m scripts.chat_judge.build_service_quality_set
python -X utf8 -m scripts.chat_judge.build_service_flow_set
python -X utf8 -m scripts.chat_judge.build_judge_validation_set --v7-dir <TELME-100 체크아웃>/docs/chat-judge/experiments/V7-bedrock-cross-judge

# 테스트
python -X utf8 -m unittest discover -s scripts/chat_judge -p "test_*.py" -t .
```

## 측정 결과

| 폴더 | 내용 |
| --- | --- |
| [20261016-v2-baseline](results/20261016-v2-baseline/README.md) | 서비스 품질셋 v2 첫 캡처와 로컬 Qwen3-14B 판정. 이후 재판정의 답변 원본 |
| [20261016-judge-candidates](results/20261016-judge-candidates/README.md) | Judge 검증셋으로 Bedrock 후보 모델 비교 |
| [20261016-v2-judge-ensemble](results/20261016-v2-judge-ensemble/README.md) | v2 답변을 세 모델로 판정하고 결합, DeepSeek 리전 비교 |
| [20261016-v2_1-service-data](results/20261016-v2_1-service-data/README.md) | 요금제 표 근거와 앱 고정 문구 규칙 반영 |
| [20261016-flows-v1](results/20261016-flows-v1/README.md) | 흐름 평가셋 v1 첫 캡처와 판정(드러난 앱 문제) |
| [20261017-label-fixes](results/20261017-label-fixes/README.md) | 라벨 수정과 기준 보완 뒤 재판정. **가장 최근 수치** |
| [20261017-situation-context](results/20261017-situation-context/README.md) | FAQ 질문 문장을 상황 맥락으로 넘기는 시험. 나빠지거나 같아서 되돌렸다 |
| [20261018-answer-generation](results/20261018-answer-generation/README.md) | 답변 생성 모델(엑사원 vs gpt-oss) 채점. 13번 모델 결정 근거, 사람 검토 전 |
