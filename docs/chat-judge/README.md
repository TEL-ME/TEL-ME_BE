# TEL-ME 채팅 답변 품질 LLM Judge

실제 채팅 API가 만든 답변을 근거성, 질문 충족도, 보류 판단의 세 축으로 판정한다. 판정 기준과 지표는 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md), 실행과 점수 계산은 [서비스 답변 평가](SERVICE_EVALUATION.md)에 있다.

TELME-100에서 만든 Judge와 실험(V1–V7)을 TELME-180에서 최신 develop으로 옮겼다. 지금 쓰는 실행 경로의 코드, 테스트, 평가 입력만 옮기고 실험 원시 결과와 사람 판정표는 [TELME-100 실험 기록](https://github.com/TEL-ME/TEL-ME_BE/tree/ae637974770f03fa8620c6b5540877becf1ff0c5/docs/chat-judge)에 남겼다.

## 구성

```text
docs/chat-judge/
  CHAT_PIPELINE_QUALITY_CRITERIA.md  판정 기준과 보고 지표
  SERVICE_EVALUATION.md              실제 파이프라인 평가 실행과 점수 계산
scripts/chat_judge/
  judge_chat_flow.py                 Judge 본체(주장 추출, 근거성, 충족도, 보류 판단, 출력 검증)
  data/                              평가 입력과 few-shot 예시, 테스트용 파일럿 기록(fixtures/)
  experiments/v6_live_chat_pipeline/ 실제 Spring 채팅 API로 답변을 만들고 vLLM Judge로 채점
  experiments/v7_cross_judge/        저장한 답변을 Bedrock 모델로 다시 채점
src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java  채팅 실행 기록 수집
```

## 실행

저장소 루트에서 실행한다.

```powershell
# 실제 API 답변 생성부터 vLLM 채점과 보고서 생성까지
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval

# 생성 답변을 바꾸지 않고 Judge만 다시 실행
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --capture .measure/이전실행/capture.json

# 테스트
python -X utf8 -m unittest scripts.chat_judge.test_judge_chat_flow scripts.chat_judge.experiments.v6_live_chat_pipeline.test_chat_pipeline_evaluation scripts.chat_judge.experiments.v7_cross_judge.test_v7
```

- 기본 입력은 [`chat_pipeline_quality_v1.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v1.json)의 508개 대화, 509개 질문이다. 기대 동작과 정답은 평가에만 쓰고 답변 생성 모델에는 전달하지 않는다.
- `telme-postgres`, `telme-ollama`, `telme-judge-vllm-bounded` 컨테이너와 모델이 필요하다. 평가 DB는 `telme_judge_eval`만 쓴다. 16GB GPU에서는 생성이 끝난 뒤 Ollama를 내리고 vLLM을 올린다.
- 각 실행은 새 `.measure/chat-pipeline-날짜-시간/`에 질문, 캡처, Judge 요청과 응답, `scores.json`, `report.md`를 저장한다.
- Bedrock 재채점(`experiments/v7_cross_judge/run_cross_judge.py`)은 `--allow-bedrock-call`을 줄 때만 모델을 호출한다.

## TELME-180에서 다룰 것

- 평가셋: 질문 500개 중 400개가 FAQ 질문 문구 그대로이고, FAQ에 없는 통신 질문 1건, 되묻기 0건, 필수 사실 6건뿐이다. 사람 판정은 모델끼리 판정이 갈린 답변 위주로 골라 한 사람이 했다.
- 판정 기준: Judge 구현이 `judge_chat_flow.py`와 V6·V7 실행기로 나뉘어 있고, 되묻기 품질을 판정하는 축이 없다.
- 답변: 기존 캡처는 2026-10-02 develop의 EXAONE 파이프라인 답변이다. 지금 파이프라인으로 다시 생성해야 한다.
