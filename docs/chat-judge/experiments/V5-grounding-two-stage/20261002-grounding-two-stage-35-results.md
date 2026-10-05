# 근거성 Judge 2단계 검증: 기존 미채점 35건

## 실행 환경

- 실행일: 2026-10-02
- 백엔드: vLLM OpenAI 호환 API, `vllm/vllm-openai:v0.29.0`
- 모델: `Qwen/Qwen3-14B-AWQ`, AWQ 4비트
- GPU: RTX 5070 Ti, VRAM 16 GB
- 설정: 동시 요청 8개, `max-model-len=4096`, `temperature=0`, `top_p=0.95`, `top_k=20`, 최대 출력 2048 토큰, thinking 비활성화
- WSL2 UVA 우회: `D:/finalproject/test/vllm_patch/buffer_utils.py` 마운트
- 재현 명령: `python -m scripts.chat_judge.experiments.v5_grounding_two_stage.run_grounding_two_stage_regression --url http://localhost:8001 --workers 8`

## 대상과 방식

대상은 [기존 vLLM 재시도 기록](20261002-grounding-retry-vllm-raw.json.gz)의 `remainingErrors` 35건이다. 원본 500건 결과와 FAQ 입력은 수정하지 않았다.

첫 단계는 답변 원문만 보고 사실 주장을 원문 그대로 추출한다. 코드는 각 문장이 답변 안에 실제로 있고 순서도 맞는지 검사한다. 두 번째 단계는 추출된 문장과 FAQ 답변만 보고 의미상 근거를 판정한다. 출력 스키마는 추출된 문장만 선택할 수 있게 제한했고, 판정기가 문장을 바꾸거나 빼면 미채점으로 둔다.

## 결과

| 항목 | 결과 |
|---|---:|
| 대상 | 35건 |
| 판정 및 코드 검증 통과 | 34건 |
| 미채점 | 1건 |
| 검증된 claim | 56개 |
| claim 판정 | SUPPORTED 55개, UNSUPPORTED 1개 |
| 전체 판정 | SUPPORTED 31건, NOT_APPLICABLE 2건, UNSUPPORTED 1건 |
| claim 요청 누적 처리 시간 | 115.6초 |

`USIM-0011`은 미채점이다. 답변에는 “2,750원이 **며**”라고 쓰였는데, 추출 모델이 “2,750원입**니다**”로 바꿔 출력했다. 코드가 원문에 없는 문장을 거부해 점수화하지 않았다. 이 건은 정확한 인용 추출의 한계를 보여준다.

`NAME_CHANGE-0038`에서는 조건을 보존한 원문 문장만 추출되어 SUPPORTED로 판정됐다. `SERVICE-0056`의 한 claim은 UNSUPPORTED로 판정됐다. 해당 판정이 실제 오류인지 판단하려면 사람이 원문 FAQ와 대조해야 한다.

## 해석과 기록

34건은 모델의 의미 판정이 맞았다는 증명이 아니다. 이번 결과는 파싱, 출력 계약, 원문 인용 검사를 통과한 비율이다. 별도 40건 사람 검토와 Judge 비교를 완료했다: [사람 판정](20261002-grounding-human-review.md), [비교 결과](20261005-grounding-human-judge-comparison.md). 이는 층화 표본의 결과로 500건 전체 정확도를 뜻하지 않는다.

이전 실행 파일은 혼동 방지를 위해 보존했다. `20261002-grounding-two-stage-ollama-retry-35-raw.json.gz`는 잘못 Ollama로 실행한 결과이며 최종 근거로 사용하지 않는다. 첫 vLLM 실행 및 스키마 보정 중간 결과도 별도 `*-initial-35-raw.json.gz` 파일로 남겨뒀다. 최종 결과는 [vLLM 원시 기록](20261002-grounding-two-stage-vllm-retry-35-raw.json.gz)이다.
