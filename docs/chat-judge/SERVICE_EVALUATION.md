# 실제 채팅 답변의 품질 평가

현재 실행 도구는 `scripts/chat_judge/experiments/v6_live_chat_pipeline/run_chat_pipeline_eval.py`다. 실제 Spring 채팅 API가 만든 EXAONE 답변을 저장한 뒤 vLLM Qwen3-14B-AWQ로 병렬 채점한다. 기존의 사람이 작성한 답변 500건은 Judge 자체의 검증 자료이며 서비스 품질 점수로 사용하지 않는다.

## 실행

저장소 루트에서 실행한다.

```powershell
# 508개 대화에 포함된 509개 질문 전체 실행
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval

# 58개 대화, 59개 질문으로 먼저 확인
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --limit 58

# 저장된 실제 답변을 다시 채점할 때는 생성 모델을 호출하지 않는다.
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --capture .measure/기존실행/capture.json
```

`--limit`은 대화 수다. 기본 입력은 [`chat_pipeline_quality_v1.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v1.json)이며 500개의 카탈로그 질문과 8개 흐름 회귀 대화로 구성된다. 기대 동작과 복수 정답은 평가용으로만 사용하며 답변 생성 모델에 전달하지 않는다.

로컬의 `telme-postgres`, `telme-ollama`, `telme-judge-vllm-bounded` 컨테이너와 모델이 필요하다. 평가 DB는 `telme_judge_eval`로 고정하며 개발 DB에 쓰지 않는다. 생성과 채점 모델을 동시에 GPU에 올리지 않는다. Docker가 꺼져 있으면 시작을 시도하며 vLLM 대신 Ollama로 채점하는 대체 경로는 없다.

현재 판정 환경은 vLLM 0.29.0, `Qwen/Qwen3-14B-AWQ`, FP16 연산, 문맥 길이 4096, 동시 작업 8개다. KV 캐시는 2GiB이며 긴 요청은 엔진이 대기 또는 선점할 수 있다. 출력은 temperature 0, thinking 비활성화, 최대 2048토큰이다. 모델과 Docker 이미지 및 실제 실행 인자는 원시 기록에 남긴다. Windows/WSL 환경은 이전 실험의 `buffer_utils.py` 호환 패치를 사용한다. 상세 환경은 [실행 안내](README.md#실제-채팅-답변-품질-측정-현재-실행-경로)를 참고한다.

## 채점 기준

| 항목 | 비교 기준 | 실패로 보는 경우 |
| --- | --- | --- |
| 근거성 | 답변의 사실 주장과 실제 RAG 입력의 FAQ 답변 본문 | 근거에 없는 사실 추가, 조건 제거, 부정이나 금액 및 기간 변경 |
| 질문 충족도와 정확성 | 사용자 질문 및 해당 질문의 정답 FAQ | 틀린 핵심 답변, 필요한 조건 누락, 복합 질문의 일부만 답변 |
| 답변 불가 판단 | 질문 및 실제 RAG 입력의 근거 | 충분한 근거가 있는데 거절하거나 근거 없이 단정 |
| 실행 | 실제 API 처리 및 최종 저장 상태 | 처리 실패, 답변 미저장 |

단어가 달라도 의미와 조건이 같으면 지지된 주장으로 평가한다. 코드의 문자열 검사는 **Judge가 인용한 문구가 실제 답변이나 FAQ에 있는지**만 확인한다. 답변과 FAQ 문장이 똑같아야 한다는 검사는 아니다.

주장 추출에는 답변 원문만 제공한다. 추출한 원문 구간을 검증한 뒤 별도 호출에서 FAQ와 의미를 비교한다. 조건이나 부정을 지운 문장으로 바꾸거나 질문의 내용을 답변 주장으로 넣으면 미채점이다. 애매한 근거 관계는 `REVIEW`이며 억지로 성공이나 환각으로 확정하지 않는다.

질문 충족도에는 정답 FAQ를 제공한다. 이는 검색에 실패해 답변을 보류한 경우도 미답으로 찾기 위해서다. 반면 근거성과 답변 불가 판단에는 정답 라벨을 제공하지 않는다. 복합 질문의 정답 묶음은 `[[첫 질문의 대체 FAQ들], [둘째 질문의 대체 FAQ들]]`이다. 묶음 내부는 OR, 묶음 사이는 AND다.

## 점수 해석

| 지표 | 분자 / 분모 |
| --- | --- |
| 답변 품질 통과율 | 실행 완료, 질문 충족, 근거성, 적절한 보류를 모두 만족한 질문 / 품질이 확정된 질문. 실행 실패는 실패로 포함 |
| 근거 없는 답변 발생률 | 지지되지 않은 사실이 하나 이상 있는 답변 / 근거 판정이 가능한 사실 답변 |
| 주장 단위 근거 부족률 | 지지되지 않은 사실 주장 / 지지 또는 근거 부족으로 확정된 사실 주장 |
| 완전 답변율 | 질문에 필요한 사실을 모두 답한 질문 / 충족도 판정이 가능한 답변 가능 질문 |
| 과도한 거절률 | 충분한 근거가 있는데 거절한 질문 / 보류 판정이 가능한 답변 가능 질문 |
| 검색 적중률 | 정답 묶음 중 하나 이상 찾은 하위 질문 / 정답 묶음이 있는 하위 질문 |

미채점과 사람 검토는 성공으로 넣지 않는다. 품질 통과율은 전체 질문을 기준으로 한 최저 및 최고 범위도 함께 표시한다. 거절 답변을 사실 답변의 환각률 분모에 넣어 수치를 낮추지 않는다. 검색 후보, 실제 RAG 입력, 저장된 근거의 적중률은 각각 계산한다.

이 점수는 고정 질문에서 자동 Judge가 추정한 서비스 답변 품질이다. 실제 이용자 전체의 환각률이나 사람이 확정한 점수로 표현하지 않는다. 최종 저장 답변을 평가하며 최종 SSE 전송 내용의 일치 여부는 이 점수에 포함하지 않는다. 매장 미연결은 별도 흐름 회귀 결과로 구분한다.

## 원시 자료와 검토

각 실행은 새 `.measure/chat-pipeline-날짜-시간/`에 아래 파일을 보존한다.

- `questions.json`, `capture.json`: 질문, 실제 라우팅, 검색 결과, RAG 입력, 생성 프롬프트, 저장 답변과 실행 상태
- `manifest.json`, `capture-gradle.log`: 코드 및 입력 해시, 모델과 실행 환경, 수집 로그
- `judged-raw.json.gz`: Judge의 모든 요청, 응답, 인용, 재시도와 검증 오류
- `scores.json`, `category-scores.json`, `report.md`: 전체 및 유형별 점수
- `review.md`: 실제 답변, 근거와 함께 읽을 수 있는 실행 실패, 미채점, 모호한 판정 및 근거 없는 주장

중단된 채점은 동일한 코드, 입력과 설정일 때만 재개한다. 완료된 오류 판정도 보존하며 재개하면서 조용히 정상 판정으로 바꾸지 않는다.

```powershell
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline .measure/기존실행/capture.json --out .measure/기존실행/judged-raw.json.gz --resume
```

2026-10-02의 실제 실행 결과와 원시 자료는 [509개 질문 결과](experiments/V6-live-chat-pipeline/20261002-service-pipeline-results.md)에 정리했다. 보다 상세한 정책은 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md)을 참고한다.
