# 2026-10-16 서비스 품질셋 v2 첫 측정

최신 develop 파이프라인으로 [서비스 품질셋 v2](../../README.md#평가셋)의 259턴에 답하게 하고, 같은 답변을 로컬 Judge로 판정했다. 같은 Judge로 [Judge 검증셋 v1](../../README.md#평가셋) 116건도 판정해 사람 판정과 비교했다. 점수는 이 고정 평가 질문에서 Judge가 추정한 값이며, 이용자 전체의 품질이나 환각률이 아니다.

## 조건

| 항목 | 값 |
| --- | --- |
| 코드 | develop `0f7d7612`(#164까지) + TELME-180 Judge(`007fb223`과 그 뒤 변경) |
| 답변 생성 | 채팅 API(`ConsultChatProcessingService`), EXAONE 3.5 7.8B(Ollama). 라우팅과 문맥 판정도 EXAONE |
| 검색 | FAQ 1,152건(`telme-flow-postgres`의 `telme` 복제), 회색 구간 근거 판정 끔(`SEARCH_GRAY_ZONE_ENABLED=false`, Bedrock을 부르지 않기 위해) |
| Judge | vLLM 0.29.0, Qwen3-14B-AWQ, 동시 요청 8 |
| 평가셋 | `chat_pipeline_quality_v2.json`(정답 FAQ 검수 반영). 첫 실행은 검수 전 정답으로 채점했고, 같은 답변을 검수 후 정답으로 다시 판정한 결과를 남겼다 |

회색 구간 판정은 운영 기본값이 켜짐이다. 꺼서 검색으로 인정되는 범위가 운영보다 좁을 수 있다.

## 서비스 품질셋 v2

| 지표 | 결과 |
| --- | --- |
| 실행 완료 | 259/259 |
| 답변 품질 통과율 | 45.8% (115/251), 미확정 포함 범위 44.4–47.5% |
| 사실 답변의 근거 없는 답변 비율 | 34.6% (44/127) |
| 답변 가능 질문의 완전 답변율 | 46.2% (85/184) |
| 과도한 거절률 | 3.8% (7/186) |
| 정답 FAQ 검색 적중률 | 50.2% (104/207) |
| 응답 시간 P50 / P95 | 2.1 / 3.4초 |

| 묶음 | 턴 | 품질 통과 | 근거 없는 답 | 검색 적중 |
| --- | ---: | ---: | ---: | ---: |
| 고객 말투 단일 질문 | 120 | 48 | 20 | 63/120 |
| 복합 질문 | 20 | 1 | 3 | 8/40 |
| 멀티턴 | 40 | 17 | 6 | 26/40 |
| FAQ로 답할 수 없는 질문 | 60 | 34 | 13 | - |
| 통신과 가까운 범위 밖 질문 | 10 | 10 | 0 | - |
| 흐름 회귀 | 9 | 5 | 3 | 7/7 |

- FAQ 문구를 그대로 쓴 v1과 달리 고객 말투로 물으면 정답 FAQ를 절반만 찾는다. 답을 찾지 못하면 대부분 "정보를 찾지 못했다"고 보류해 과도한 거절은 적지만 질문은 해결되지 않는다.
- 복합 질문은 20개 중 1개만 통과했다. 하위 질문 40개 중 정답 FAQ를 찾은 것은 8개다.
- FAQ로 답할 수 없는 질문 60개 중 13개에서 근거 없는 사실을 답했다(Judge 판정).
- 불필요한 되묻기로 판정된 턴은 6개다.

## Judge 검증셋 v1과 사람 판정 비교

Qwen3-14B-AWQ와 판정자 A(TELME-100 V7)의 축별 일치다. 검증셋은 Judge가 틀리기 쉬운 답변을 많이 담도록 골라, 일치율을 일반 답변의 정확도로 읽으면 안 된다.

| 축 | 일치 | 주요 오류 |
| --- | --- | --- |
| 근거성 | 83/115 (72%) | 근거 없는 답 38건 중 18건을 근거 있음으로 놓침. 근거 있는 답 50건 중 13건을 근거 없음으로 봄 |
| 충족도 | 80/116 (69%) | 부분 답변 36건 중 24건, 미답 25건 중 9건을 완전 답변으로 봄 |
| 보류 판단 | 80/115 (70%) | 근거 없는 답을 놓친 19건이 그대로 이어짐 |

이 Judge는 근거 없는 답의 절반을 놓치므로, 위 서비스 품질셋의 근거 없는 답변 비율은 실제보다 낮게 잡혔을 가능성이 크다. 판정자 B의 판정이 채워지면 `run_judge_validation.py --review-b`로 A·B 일치도를 확인한 뒤 다시 비교한다.

## 파일

- `capture.json.gz`: 질문, 라우팅, 검색, RAG 입력, 저장 답변(정답 FAQ 검수를 반영한 평가 문항으로 교체)
- `judged-raw.json.gz`, `scores.json`, `category-scores.json`, `report.md`, `review.md`, `manifest.json`: Judge 요청과 응답, 집계, 사람이 읽을 검토 목록
- `judge-validation-qwen14b.json.gz`: Judge 검증셋 판정 원본

```powershell
# 같은 답변을 모델 호출 없이 지금 채점 규칙으로 다시 집계
python -X utf8 -c "from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as p; r=p.rescore(p.load_json('docs/chat-judge/results/20261016-v2-baseline/judged-raw.json.gz'), p.load_json('docs/chat-judge/results/20261016-v2-baseline/capture.json.gz')); print(r['summary']['qualitySuccess'])"
# Judge 검증셋 비교 다시 계산
python -X utf8 -m scripts.chat_judge.run_judge_validation --judged docs/chat-judge/results/20261016-v2-baseline/judge-validation-qwen14b.json.gz
```
