# TEL-ME 채팅 답변 품질 LLM Judge

실제 채팅 API가 만든 답변을 근거성, 질문 충족도, 보류 판단의 세 축으로 판정한다. 판정 기준과 지표는 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md), 실행과 점수 계산, 코드가 정하는 판정은 [서비스 답변 평가](SERVICE_EVALUATION.md)에 있다.

TELME-100에서 만든 Judge와 실험(V1–V7)을 TELME-180에서 최신 develop으로 옮겼다. 지금 쓰는 실행 경로의 코드, 테스트, 평가 입력만 옮기고 실험 원시 결과와 사람 판정표는 [TELME-100 실험 기록](https://github.com/TEL-ME/TEL-ME_BE/tree/ae637974770f03fa8620c6b5540877becf1ff0c5/docs/chat-judge)에 남겼다.

## 구성

```text
docs/chat-judge/
  CHAT_PIPELINE_QUALITY_CRITERIA.md  판정 기준과 보고 지표
  SERVICE_EVALUATION.md              실제 파이프라인 평가 실행, 점수 계산, 코드가 정하는 판정
  judge-validation-v1-review-form.md Judge 검증셋 판정자 B용 판정표
scripts/chat_judge/
  judge_chat_flow.py                 주장 추출과 근거성 판정, 출력 검증, 보류 판정 결합
  build_service_quality_set.py       서비스 품질 평가셋 v2 생성
  build_judge_validation_set.py      Judge 검증셋 v1 생성(TELME-100 V7 사람 판정에서 층화 추출)
  run_judge_validation.py            Judge 검증셋을 판정하고 사람 판정과 축별 비교
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
| [`chat_pipeline_quality_v2.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v2.json) | 서비스 답변 품질 측정 | 아래 표 |
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
- 후보: v1의 대체 FAQ 묶음, 첫 실행에서 실제로 검색된 FAQ, 질문과 임베딩으로 가까운 FAQ 6개(`equivalence_candidates.json`)
- 기준: FAQ 답변 본문에 필수 사실이 모두 있는 FAQ만 인정(`equivalence_labels.json`)
- 결과: 질문을 쓸 때 쓴 FAQ는 모두 인정됐다. v1 대체 FAQ 중 필수 사실이 빠진 24개는 빠지고, 427개가 새로 인정됐다(질문 부분당 평균 3.5개).
- 검수 중 필수 사실이 질문을 다 덮지 못한다고 표시된 문항(예: 토요일 영업을 묻는데 평일 시간만 적힘)은 `note`에 남겼다. 사람 검토 때 먼저 볼 대상이다.

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

# 평가셋 다시 만들기
python -X utf8 -m scripts.chat_judge.build_service_quality_set
python -X utf8 -m scripts.chat_judge.build_judge_validation_set --v7-dir <TELME-100 체크아웃>/docs/chat-judge/experiments/V7-bedrock-cross-judge

# 테스트
python -X utf8 -m unittest scripts.chat_judge.test_judge_chat_flow scripts.chat_judge.test_judge_validation scripts.chat_judge.experiments.v6_live_chat_pipeline.test_chat_pipeline_evaluation scripts.chat_judge.experiments.v7_cross_judge.test_v7
```

- `telme-ollama`, `telme-judge-vllm-bounded` 컨테이너와 모델, FAQ가 있는 PostgreSQL이 필요하다. 평가 DB는 `telme_judge_eval`만 쓴다(없으면 `--postgres-container`의 `telme` DB를 복제). 16GB GPU에서는 생성이 끝난 뒤 Ollama를 내리고 vLLM을 올린다.
- 각 실행은 새 `.measure/chat-pipeline-날짜-시간/`에 질문, 캡처, Judge 요청과 응답, `scores.json`, `report.md`를 저장한다.
- 회색 구간 근거 판정은 Bedrock을 부른다. Bedrock 없이 돌릴 때는 `SEARCH_GRAY_ZONE_ENABLED=false`로 끈다.
- Bedrock 재채점(`experiments/v7_cross_judge/run_cross_judge.py`)은 `--allow-bedrock-call`을 줄 때만 모델을 호출한다.
