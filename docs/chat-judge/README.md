# TEL-ME 채팅 LLM Judge

## Folder layout

```text
docs/chat-judge/
  README.md                         experiment index and workflow
  SERVICE_EVALUATION.md             live pipeline scoring rules
  CHAT_PIPELINE_QUALITY_CRITERIA.md evaluation criteria
  experiments/V1-pilot-fewshot/       initial service pilot and few-shot comparison
  experiments/V2-fixed-36-case-validation/  36 fixed Judge boundary cases
  experiments/V3-fixed-500-case-validation/ 500-case validation and contract recheck
  experiments/V4-split-judge-telme100/ split scoring and TELME-100 rerun
  experiments/V5-grounding-two-stage/ 35-case grounding retry and human review
  experiments/V6-live-chat-pipeline/  actual Spring pipeline quality evaluation

scripts/chat_judge/
  *.py                              judge runners, utilities, and tests
  data/                             judge fixtures and evaluation inputs
```


현재 서비스 답변 평가는 [실행 방법과 점수 기준](SERVICE_EVALUATION.md), [509개 실제 질문 결과](experiments/V6-live-chat-pipeline/20261002-service-pipeline-results.md)를 먼저 참고한다. 아래의 초기 파일럿과 합성 답변 검증 결과는 과거 기록이다.

## 초기 파일럿

2026-10-01 기준 `develop` 커밋 `d54964386d5de6db363fb34d205099cd66953ad8`에서 실행했다. 이 평가는 개선 후보를 찾기 위한 8개 시나리오, 9개 질문의 파일럿이다. 전체 환각률이나 품질 개선 수치로 해석하지 않는다.

## 실행 경로와 원시 데이터

별도 워크트리에서 실제 Spring `MockMvc` 채팅 API를 호출했다. 게스트 세션 생성, 메시지 전송, 비동기 실행 완료, 대화 이력 조회, 답변 근거 조회를 순서대로 거쳤다. 테스트 프로필에서만 `ConsultChatProcessingService`와 RAG 연동을 켰고, 기존 개발 DB와 공개 API, Flyway 마이그레이션은 변경하지 않았다. 평가 DB `telme_judge_eval`은 로컬 개발 DB의 독립 복제본이며 FAQ와 임베딩 각각 1,152건을 포함한다.

- 고정 질문과 기대 동작: [`scripts/chat_judge/data/chat_judge_pilot.json`](../../scripts/chat_judge/data/chat_judge_pilot.json)
- 생성 단계 원시 기록: [`20261001-capture.json`](experiments/V1-pilot-fewshot/20261001-capture.json)
- Judge 원시 요청, 원시 응답, 검증 결과: [`20261001-judged.json`](experiments/V1-pilot-fewshot/20261001-judged.json)
- 답변 불가 기준을 다시 적용한 결과: [`20261001-reconciled.json`](experiments/V1-pilot-fewshot/20261001-reconciled.json)
- 별도 few-shot 예시: [`chat_judge_fewshot.json`](../../scripts/chat_judge/data/chat_judge_fewshot.json)
- 같은 답변의 few-shot 원시 채점: [`20261001-fewshot-judged.json`](experiments/V1-pilot-fewshot/20261001-fewshot-judged.json)
- 사람 판정과 비교 결과: [`20261001-manual-labels.json`](experiments/V1-pilot-fewshot/20261001-manual-labels.json), [`20261001-fewshot-comparison.json`](experiments/V1-pilot-fewshot/20261001-fewshot-comparison.json)
- 수집 코드: [`ChatJudgeCaptureProbe.java`](../../src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java)
- 채점 코드와 검증 테스트: [`judge_chat_flow.py`](../../scripts/chat_judge/judge_chat_flow.py), [`test_judge_chat_flow.py`](../../scripts/chat_judge/test_judge_chat_flow.py)

생성 기록에는 질문, 라우팅 결과, 상담 요청과 확정 조건, 검색 질의와 FAQ 본문 및 점수, EXAONE에 전달된 프롬프트, 생성 호출 기록, 실행 상태, 저장된 답변과 근거가 들어 있다. 코드 해시, 평가 질문 해시, 모델 이름과 digest, Ollama 버전, 응답 시간도 남겼다. Judge 기록은 두 평가 기준의 프롬프트, JSON Schema, 요청 옵션, Ollama 원시 응답, 토큰 수, 지연시간과 최종 판정을 보존한다. 원시 채팅 데이터는 이 파일럿의 고정 질문만 사용했다.
이번 캡처에는 EXAONE 요청 15회와 Qwen 판정 요청 18회의 원시 입력이 있다.

생성 모델은 `exaone3.5:7.8b`, Judge는 별도 Ollama 인스턴스에 저장된 `qwen3:14b`다. 같은 GPU에서 두 모델을 동시에 평가하지 않도록 답변 생성 후 Qwen을 순차 호출했다. Judge의 근거 판정 입력에는 정답 라벨을 넣지 않고 실제 검색 FAQ와 확정 조건만 넣었다. 답변 충실도 판정에는 기대 사실과 실제 검색 FAQ를 넣었다. 둘 중 하나라도 호출에 실패하거나 JSON 형식 또는 결과 검증에 실패하면 해당 질문은 `UNSCORED`로 기록하며 Judge 기반 집계에서 제외한다.

현재 평가 DB의 `FaqSearchResponse.slotId`는 모두 비어 있었다. 검색 FAQ의 질문과 답변이 [`faq_full_1150.json`](../../scripts/data/faq_full_1150.json)의 원문과 **둘 다 완전히 일치할 때만** 원본 `slot_id`를 보충했다. 불일치하는 FAQ는 `faqId`로 남는다. 원본 데이터의 해시는 Judge 결과에 기록되어 있다. `BILLING-0067`, `BILLING-0078`, `BILLING-0022`는 같은 기대 사실을 뒷받침하는 대체 정답으로 평가 질문에 포함했다.

## FAQ 원문과 수동 대조

| 시나리오 | 실제 결과와 원문 대조 | Qwen 판정 점검 |
| --- | --- | --- |
| 일반 FAQ | 가상계좌 정의를 `BILLING-0026`에 맞게 답했다. | 근거 있음, 완전 답변 판정 적절 |
| 조건 누락 위험 | 월 30만 원과 본인 인증 후 월 100만 원을 모두 답했다. `BILLING-0067` 등 실제 검색 원문에 있다. | 근거 있음, 완전 답변 판정 적절 |
| 근거 부족 | 질문은 택배비 별도 부과 여부인데, 답변의 `7,700원으로 포함`은 택배비 포함으로 읽힐 수 있다. 검색 FAQ에는 재발급 비용 7,700원만 있다. | 근거 없는 주장과 답변 불가 필요 판정 적절 |
| 사실과 근거 부족 혼합 | 재발급 비용 7,700원과 매장 즉시 발급은 근거가 있다. 배송비 별도 부과 가능성과 주문 시 확인 가능하다는 문구는 검색 FAQ에 없다. | 근거 없음 판정 적절. 다만 답변 불가 판정을 `NOT_APPLICABLE`로 둔 것은 오판이며 근거 없는 배송비 단정을 피해야 한다. |
| 무관 질문 | 날씨 질문을 `UNKNOWN`으로 분류하고 통신 서비스 질문을 요청했다. | 범위 밖 질문에 대한 거절 판정 적절 |
| 이전 대화 후속 질문 | 월 1회 변경 답변 후 `가입한 달에도 돼요?`를 요금제 변경으로 해석했다. 후속 검색에서 `BILLING-0078`, `BILLING-0022`를 찾아 가입 당월 불가, 다음 달 가능을 답했다. | 두 턴 모두 근거 있음, 완전 답변 판정 적절 |
| FAQ 복합 질문 | 요금제 변경과 가상계좌를 한 상담 요청과 한 검색 질의로 처리했고 검색 결과가 0건이었다. 각각의 정답 FAQ는 DB에 있다. | 검색 실패와 미답변 판정 적절. 답변 불가 문구의 `abstention=NOT_APPLICABLE`은 세부 오판이다. |
| 매장 질문 | `STORE` 분류와 강남역 조건 추출은 됐으나 매장 조회가 연결되지 않아 이용 불가 안내를 저장했다. | 매장 미연결 판정 적절. 이용 불가 안내의 `abstention=NOT_APPLICABLE`은 세부 오판이다. |

9개 질문 모두 형식 검증을 통과해 채점됐다. 자동 집계는 근거 없는 주장 2건, 미답변 3건, 검색 실패 1건, 매장 미연결 1건이다. Qwen의 `abstention` 세부 판정 오판 3건은 위 표에 남겼다. 자동 집계 숫자는 서로 중복될 수 있으며 사람의 원문 대조 결과를 대신하지 않는다.

## 답변 불가 판정 보완

Qwen의 판정만으로 답변 불가 유형을 확정하면 위 3건처럼 모순이 생긴다. 현재 도구는 다음 순서로 최종 판정을 계산하고, Qwen이 반환한 `abstention` 원본은 그대로 보존한다.

1. 근거 판정에서 사실 주장 하나라도 `UNSUPPORTED`이면 `SHOULD_ABSTAIN`이다. 일부 사실이 맞더라도 근거 없는 내용을 단정하면 적용한다.
2. 사실 주장이 없고 저장된 답변의 `answerBasis`가 `OUT_OF_SCOPE`이면 기대 동작을 함께 본다. 실제로 범위 밖 질문이면 `APPROPRIATE`, 답해야 할 질문을 범위 밖으로 보낸 경우에는 `OVER_REFUSAL`이다. 라우팅 오류도 별도로 남긴다.
3. 사실 주장이 없고 `answerBasis`가 `NO_EVIDENCE`이면 근거 부족 안내로 보고 `APPROPRIATE`다. 실제 검색 결과가 있고 Qwen이 `OVER_REFUSAL`이라고 판정한 경우에는 그 판정을 유지하되 사람의 검토가 필요하다고 표시한다. 검토 전에는 과도한 거절 건수에 넣지 않는다.
4. 나머지 경우에는 Qwen의 판정을 사용한다. 생성 단계의 답변 유형과 근거 판정이 서로 맞지 않으면 검토 필요 상태로 남긴다.

`FAQ_COMPOUND`는 DB에 정답 FAQ가 있어도 실제 검색 결과가 비어 있었다. 따라서 생성 단계에서 답변을 보류한 판단은 적절하지만, 검색 실패와 미답변은 그대로 기록한다. `STORE`도 현재 매장 조회가 연결되지 않은 상태에서 안내로 마쳤으므로 답변 보류는 적절하고, 매장 미연결은 별도 문제로 남는다. 매장 답변에서 사실을 지어낸다면 근거 없는 주장으로 집계한다.

[`20261001-reconciled.json`](experiments/V1-pilot-fewshot/20261001-reconciled.json)은 Qwen을 다시 호출하지 않고 보존된 결과에 이 규칙을 적용한 파일이다. 원본 답변과 원본 Judge 응답은 바뀌지 않았다. 최종 `SHOULD_ABSTAIN`은 1건에서 2건으로 바뀌었고 Qwen 원본과 최종 판정이 다른 질문은 3건이다. 각 질문의 `abstentionDecision`에서 원본 값, 최종 값, 적용 규칙, 검토 필요 여부를 확인할 수 있다. 재분류 코드 해시와 원본 Judge 결과 해시도 기록했다.

`RETRIEVAL_MISS`는 실행이 완료됐지만 기대 FAQ가 실제 검색 결과에 없을 때만 붙인다. 답변 생성 중 실행이 실패하면 `PROCESSING_FAILURE`와 `GENERATION_FAILURE`로 구분하며, 검색 결과가 비어 있었다는 이유만으로 검색 실패로 중복 집계하지 않는다. 이번 9개 질문에는 실행 자체가 실패한 사례가 없었다.

이 파일럿에서 우선 손볼 곳은 배송비처럼 근거에 없는 조건을 답변에서 제거하는 것과 FAQ 복합 질문을 하위 질문별로 검색하는 것이다. 매장 검색 연결은 별도 기능 작업이다. 같은 질문만 반복해 프롬프트를 맞추는 방식은 과적합 위험이 있으므로, 다음 비교는 별도의 고정 검증 질문으로 진행한다.

이 파일럿 캡처 당시에는 답변에 저장된 근거를 중심으로 평가했다. 평가 도구는 이제 답변 메시지에 실제 저장된 근거를 판정 입력으로 사용하고, 각 검색의 질의도 함께 보존한다. 복합 질문은 fixture의 `goldSourceGroups`를 기준으로 모든 하위 질문 그룹을 확인한다. 다만 기존 20261001 캡처에는 그룹 라벨이 없으므로, 당시 복합 질문 결과를 하위 질문별 검색 성공으로 해석하지 않는다.

초기 검증은 실제 채팅 API 수집 테스트 1회, Python 평가 도구 단위 테스트 8개, 전체 Gradle 테스트로 했다. 전체 Gradle 테스트는 총 1,139개 중 1,044개 통과, 95개 조건부 제외, 실패 0개였다. 답변 불가 기준 보완은 보존된 9개 결과의 재분류와 Python 단위 테스트로 확인했다.

## Few-shot 프롬프트 비교

Judge의 기존 기준 문구와 JSON Schema는 유지하고, 별도 합성 예시를 실제 질문 앞에 사용자 입력과 평가자 응답 한 쌍씩 추가했다. 근거 판정에는 3개, 답변 충실도 및 답변 불가 판정에는 5개 예시를 사용한다. 정상 답변, 일부 누락, 근거 없는 단정, 검색 근거가 없는 경우의 안내, 서비스 범위 밖 질문을 포함한다. 예시는 이 파일럿의 9개 질문을 복사하지 않았고, 근거 판정 예시에도 정답 라벨을 넣지 않았다. 평가 질문과 예시 질문의 중복, 결과 JSON 형식과 근거 ID를 실행 전에 검사한다. 모델 호출에 사용된 예시와 프롬프트 전체는 few-shot 원시 채점 파일의 각 `request.messages`에 보존한다.

기존 채점과 few-shot 채점은 **동일한 EXAONE 답변 9개**, 동일한 Qwen 모델 digest, 동일한 FAQ 원본을 사용했다. 아래 수치는 FAQ 원문과 답변을 대조해 기록한 [`수동 판정`](experiments/V1-pilot-fewshot/20261001-manual-labels.json)과 일치한 질문 수다. `최종 답변 불가`는 Qwen 원본 판정에 저장된 답변 유형과 근거 판정 규칙을 적용한 결과다.

| 판정 축 | 기존 프롬프트 | Few-shot |
| --- | ---: | ---: |
| 근거 판정 | 9/9 | 9/9 |
| 답변 충실도 | 9/9 | 9/9 |
| Qwen 원본 답변 불가 판정 | 6/9 | 6/9 |
| 규칙 보정 후 최종 답변 불가 판정 | 9/9 | 9/9 |
| 전체 Judge 입력 토큰 | 12,099 | 25,734 |

이번 예시는 원본 답변 불가 판정에서 기존 오판 3건을 고치지 못했고, 입력 토큰만 약 2.13배 사용했다. 이 표는 당시의 통합 판정 형식으로 실행한 과거 기록이다. 현행 분리 판정에서 `--fewshot`은 새 형식에 맞춘 근거성 예시만 적용하고, 이전 형식의 충실도 예시는 적용하지 않는다. 따라서 아래 명령을 새로 실행한 결과를 이 표나 과거 비교 스크립트의 결과와 같은 실험으로 취급할 수 없다. 응답 시간은 모델의 적재 및 캐시 상태가 달라 공정한 비용 비교 지표로 사용하지 않았다.

```powershell
python -X utf8 -m scripts.chat_judge.judge_chat_flow docs/chat-judge/experiments/V1-pilot-fewshot/20261001-capture.json --fewshot --out .measure/chat-judge-fewshot-judged.json
```

기본 채점은 `--fewshot`을 생략한다. 다른 예시 파일은 `--fewshot 경로`로 지정한다. 두 결과를 비교할 때는 생성 답변과 FAQ 원본 및 Judge 모델 digest가 같아야 한다.

## 확대된 Judge 독립 검증셋

기존 9건은 실제 채팅 흐름을 관찰하는 파일럿이어서, EXAONE이 내놓은 답변 종류에 따라 Judge 판정별 문항 수가 달라졌다. few-shot의 효과를 따로 보기 위해 [`chat_judge_validation_v1.json`](../../scripts/chat_judge/data/chat_judge_validation_v1.json)에 **36개의 고정 답변 검증 항목**을 구성했다. 고유 질문은 12개이며 같은 질문에 답변과 근거를 바꿔 판정 경계를 시험한다. 이 검증셋은 실제 FAQ 원문 8개를 사용하지만, 답변은 사람이 작성했다. 따라서 채팅 API나 EXAONE의 실제 답변 품질을 측정하는 결과와 섞지 않는다. 기존 파일럿 9건 및 few-shot 예시와 질문 원문이 겹치지 않는다.

| 판정 기준 | 검증 항목 수 |
| --- | --- |
| 근거: 있음 / 없음 / 사실 주장 없음 | 12 / 8 / 16 |
| 충실도: 전체 / 일부 / 미답변 / 적용 불가 | 8 / 8 / 16 / 4 |
| 답변 불가: 해당 없음 / 필요 / 적절 / 과도한 거절 | 12 / 8 / 8 / 8 |

같은 FAQ에 대해 정확한 답변, 일부 누락, 근거 없는 사실 추가, 사실과 반대되는 답변, 근거가 있는데 거절, 검색 근거가 없어 답변을 보류하는 사례를 배치했다. 범위 밖 질문도 4개 넣었다. 특히 **근거가 있는데 거절**과 **검색 근거가 없어 보류**는 문구가 같고 `sources`만 달라서, Judge가 실제 근거를 보고 구별하는지 확인할 수 있다. 각 항목의 기대 라벨은 모델 입력에 포함하지 않으며, 결과에는 입력과 원시 모델 응답을 모두 보존한다. [`judge_validation.py`](../../scripts/chat_judge/experiments/v2_fixed_36_case_validation/judge_validation.py)가 평가 질문 중복과 라벨별 최소 건수를 검사한다.

동일한 36건을 기존 프롬프트와 few-shot으로 순서대로 채점한 원시 기록은 [`20261001-validation-v1-results.json`](experiments/V2-fixed-36-case-validation/20261001-validation-v1-results.json)에 있다. 형식 검증에 실패한 항목은 `UNSCORED`로 남기며 일치 건수로 계산하지 않는다. 실행을 다시 시작해도 완료 또는 미채점으로 끝난 원시 호출을 덮어쓰지 않는다. 재시도 실험은 새 출력 파일에 기록한다.

| 지표 | 기존 | Few-shot |
| --- | ---: | ---: |
| 형식 검증 통과 | 30/36 | 36/36 |
| 전체 36건 중 근거 판정 일치 | 26 | 28 |
| 전체 36건 중 충실도 판정 일치 | 25 | 32 |
| 전체 36건 중 답변 불가 판정 일치 | 16 | 16 |
| **둘 다 채점된 30건 중 근거 판정 일치** | **26** | **24** |
| **둘 다 채점된 30건 중 충실도 판정 일치** | **25** | **27** |
| **둘 다 채점된 30건 중 답변 불가 판정 일치** | **16** | **16** |
| 전체 입력 토큰 | 41,553 | 96,093 |

기존 프롬프트의 미채점 6건은 모두 `SUPPORTED`가 아닌 주장에 근거 ID를 붙여 검증에 실패했다. 그중 4건은 틀린 사실의 답변이고 2건은 거절 답변이다. Few-shot은 이런 형식 오류를 줄였지만, 사실 주장이 없는 답변을 사실 주장으로 오인한 사례가 늘었다. 답변 불가 판정에서는 두 방식 모두 **근거 없는 단정 0/8, 과도한 거절 0/8**로 약했다. 적절한 보류 8건 중 맞힌 것은 각 4건이며, 그 4건은 서비스 범위 밖 질문이다. 실제 검색 근거가 없어서 보류한 4건은 모두 놓쳤다. 따라서 현재 few-shot을 기본값으로 바꾸지 않는다. 36개 항목이 12개 질문의 변형이므로 독립적인 36개 질문으로 해석해서도 안 된다.

```powershell
python -X utf8 -m scripts.chat_judge.experiments.v2_fixed_36_case_validation.judge_validation --out .measure/chat-judge-validation-v1.json
python -X utf8 -m unittest discover -s scripts -p 'test_judge*.py'
```

이 검증셋은 한 FAQ가 근거로 제시된 단일 질문 중심이다. 여러 검색 결과가 충돌하는 상황과 실제 멀티턴은 기존 채팅 캡처 방식으로 별도 평가해야 한다. 36건도 전체 서비스 품질을 대표하는 표본은 아니다.

## 대규모 평가셋 v2

36개 항목은 고유 질문이 12개뿐이라 판정의 재현성을 판단하기에 부족하다. 이를 보완해 [`chat_judge_validation_v2.json`](../../scripts/chat_judge/data/chat_judge_validation_v2.json)에 **고유 질문 500개, 평가 항목 500개**를 고정했다. 평가셋을 구성할 때는 모델 채점을 실행하지 않았고, 이후 실행 결과를 아래 결과 문서에 기록했다. [`build_judge_validation_v2.py`](../../scripts/chat_judge/experiments/v3_fixed_500_case_validation/build_judge_validation_v2.py)로 같은 파일을 다시 만들고, [`test_build_judge_validation_v2.py`](../../scripts/chat_judge/experiments/v3_fixed_500_case_validation/test_build_judge_validation_v2.py)로 라벨, 근거 원문, 중복 및 분포를 확인할 수 있다.

- FAQ 단일 질문 400개: 10개 분야에서 각 40개, 5개 질문 유형에서 각 80개를 골랐다. 각 분야마다 근거가 있는 정상 답변, 근거 밖 수수료나 혜택이 추가된 답변, 근거가 있는데 거절한 답변, 검색 근거가 없어 보류한 답변을 각각 10개씩 넣었다. 질문 유형별로도 각 유형에 네 행동을 20개씩 맞췄다. 원본 FAQ의 초보, 숙련, 시니어 페르소나는 분야마다 13개 또는 14개씩 배치했다.
- FAQ 복합 질문 50개: 서로 다른 분야의 FAQ 두 개를 묻고 첫 번째만 답한다. 두 FAQ 원문을 모두 근거로 제시해, 근거 판정은 `SUPPORTED`, 답변 충실도는 `PARTIAL`인지 검사한다.
- 서비스 범위 밖 질문 50개: 수학, 요리, 스포츠 등 통신과 무관한 고유 질문에 대해 사실을 꾸며내지 않는 안내를 검사한다.

| 기준 | 라벨별 건수 |
| --- | --- |
| 근거 판정 | `SUPPORTED` 150, `UNSUPPORTED` 100, `NOT_APPLICABLE` 250 |
| 답변 충실도 | `COMPLETE` 100, `PARTIAL` 50, `MISSED` 200, `NOT_APPLICABLE` 50, **미라벨 100** |
| 답변 불가 | `NOT_APPLICABLE` 150, `SHOULD_ABSTAIN` 100, `OVER_REFUSAL` 100, `APPROPRIATE` 150 |

근거 밖 정보를 추가한 100건의 충실도는 정답 정보가 포함돼 있어도 잘못된 내용이 섞인 경우라 `COMPLETE`와 `PARTIAL` 중 하나로 기계적으로 고정하지 않았다. 이 축은 `null`로 두고 근거 판정 및 답변 불가 판정에만 사용한다. 기존 파일럿, v1 및 few-shot 예시와 질문 또는 사용한 FAQ ID가 겹치지 않도록 선택했다. 단일 FAQ의 정확한 답변은 원본 문장을 그대로 쓰고, 근거 밖 주장은 분야마다 3가지 문구로 나눠 수수료, 혜택, 기한, 조건을 허구로 추가했다. 답변 보류와 범위 밖 안내도 각각 4가지 문구를 사용했다. 이처럼 **규칙으로 만든 라벨**은 사람의 독립 평가와 다르므로, 모델 채점 전 분야별 표본을 사람이 확인해야 한다.

[`run_judge_validation_v2.py`](../../scripts/chat_judge/experiments/v3_fixed_500_case_validation/run_judge_validation_v2.py)는 기본 프롬프트로 근거성, 답변 충실도, 답변 불가 신호를 각각 호출한다. 입력 해시, 모델 digest, 원시 요청과 응답을 기록하고 중단된 실행을 이어갈 수 있으며 완료된 결과와 형식 오류 결과를 덮어쓰지 않는다. 독립적으로 처음부터 실행하면 500건에 각 3회, 총 1,500회 호출한다. 검증된 이전 판정은 재사용할 수 있다. 이 검증셋은 Judge의 판정 경계를 확인하기 위한 것으로, 실제 EXAONE 답변이나 검색 및 라우팅 성능의 대표 점수로 해석하지 않는다.

```powershell
python -X utf8 -m scripts.chat_judge.experiments.v3_fixed_500_case_validation.build_judge_validation_v2
python -X utf8 -m unittest discover -s scripts -p 'test*judge*.py'
# 첫 기본 Judge 채점에 사용한 명령:
python -X utf8 -m scripts.chat_judge.experiments.v3_fixed_500_case_validation.run_judge_validation_v2 --out .measure/chat-judge-validation-v2.json
```

## 실제 채팅 답변 품질 측정 (현재 실행 경로)

실제 서비스 답변을 측정할 때는 [`run_chat_pipeline_eval.py`](../../scripts/chat_judge/experiments/v6_live_chat_pipeline/run_chat_pipeline_eval.py)를 사용한다. 아래의 과거 Ollama Judge 명령 대신 **vLLM 0.29.0의 Qwen3-14B-AWQ를 동시 요청 8개로 호출**한다. 답변 생성은 기존 EXAONE/Ollama이며 Judge는 vLLM이다.

```powershell
# 실제 API 답변 생성부터 vLLM 채점과 보고서 생성까지
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval

# 모든 분야와 흐름 회귀 사례를 포함한 작은 실행: 58개 대화, 59개 질문
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --limit 58

# 생성 답변을 바꾸지 않고 Judge만 재실행
python -u -X utf8 -m scripts.chat_judge.experiments.v6_live_chat_pipeline.run_chat_pipeline_eval --capture .measure/이전실행/capture.json
```

기본 평가 입력은 [`chat_pipeline_quality_v1.json`](../../scripts/chat_judge/data/chat_pipeline_quality_v1.json)의 **508개 대화, 509개 질문**이다. 기존 500건의 질문을 가져오되 합성 답변과 Judge 정답 라벨은 제거했고, 별도로 실제 흐름 회귀 8개 대화를 넣었다. 이전 검증셋의 `NO_EVIDENCE`는 실험자가 근거를 비운 조건이었다. 실제 카탈로그에는 답이 있는 질문이므로 이번 입력에서는 답변 가능 질문으로 바꿨다. 복합 질문 정답은 하위 질문별 OR 묶음을 유지한다. FAQ 전체 본문을 모두 말하도록 요구하지 않고 질문에 필요한 사실과 조건만 비교한다.

실제 `ChatSessionController`와 `ChatMessageController` 요청을 Spring MockMvc로 보내고, `ConsultChatProcessingService`, PostgreSQL, 검색과 실제 EXAONE 생성 결과를 실행한다. 가짜 LLM 답변이나 미리 작성한 답변은 사용하지 않는다. 공개 HTTP 포트를 띄운 브라우저 테스트는 아니며 최종 SSE 전송 내용 자체는 이 점수에 포함하지 않는다.

Docker가 꺼져 있으면 시작을 시도한다. 평가 DB는 `telme_judge_eval`만 사용한다. DB가 없으면 로컬 `telme`를 읽어 별도 복제하고, 이미 있으면 그대로 사용한다. 개발 DB를 갱신하지 않는다. `telme-postgres`, `telme-ollama`, `telme-judge-vllm-bounded` 컨테이너와 다운로드된 모델을 사용하며, 16GB GPU에서는 생성이 끝난 뒤 Ollama를 내리고 vLLM을 올린다. vLLM 장애를 Ollama Judge로 대체하지 않는다.

판정 컨테이너는 이전과 같은 vLLM 0.29.0 이미지, Qwen3-14B-AWQ 가중치, `dtype=half`, 최대 문맥 4096, 동시 요청 8을 사용한다. Windows에서 자동 메모리 예약 환경의 지연이 관찰됐다. `gpu-memory-utilization=0.80` 시도는 캐시 공간 부족으로 시작하지 못했으며 최종 환경은 `--kv-cache-memory-bytes 2147483648`로 FP16 KV 캐시를 2GiB로 지정했다. 이 옵션을 지정하면 `gpu-memory-utilization`은 캐시 크기를 결정하지 않는다. 최대 길이 4096의 요청 8개를 동시에 전부 담는 용량은 아니며 실제 요청 길이에 따라 vLLM이 대기 또는 선점한다. 가중치, temperature 0, thinking 비활성화, 프롬프트와 스키마는 유지한다. 초기 환경의 부분 결과와 조정 후 환경은 원시 기록의 런타임 메타데이터에서 구분한다.

각 실행은 새 `.measure/chat-pipeline-날짜-시간/`에 질문, 실제 캡처, Gradle 로그, 런타임 설정, 모든 Judge 요청과 응답, 재시도, `scores.json`, `report.md`를 저장한다. 기존 결과를 덮어쓰지 않는다. 세 축 중 하나가 실패해도 다른 축의 유효한 판정은 유지한다. 출력 형식이나 원문 인용 검증 실패는 미채점이고 의미가 애매한 판정은 사람 검토다.

질문 충족도는 실제 답변 문단을, 답변 불가 판정의 근거 인용은 해당 FAQ의 실제 문단을 JSON Schema 선택지로 제한한다. Judge가 FAQ 문장을 답변 인용이라고 복사하거나 문장을 바꿔 쓰는 형식 오류를 막는다. 이 선택지 제한은 인용 출처를 보장하는 장치이며 의미 판단의 정답을 보장하지 않는다. 근거성의 claim 추출은 답변 원문만 입력받고 조건과 부정을 보존한 원문 구간을 검사한 뒤 FAQ와 의미를 비교한다.

**답변 품질 통과율**, **사실 답변의 근거 없는 주장 발생률**, **주장 단위 근거 부족률**, **질문 충족도**, **실행 실패**, **미채점과 사람 검토**를 함께 보고한다. 실패와 미확정 건수를 숨긴 단일 퍼센트로 발표하지 않는다. 정의는 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md)에 있다. 자동 점수는 이 고정 평가 질문에서의 Judge 추정치이며 이용자 전체 분포의 환각률 또는 사람 채점 정확도를 뜻하지 않는다.

```powershell
python -X utf8 -m unittest discover -s scripts -p 'test_chat_pipeline_evaluation.py'
python -X utf8 -m unittest discover -s scripts -p 'test*judge*.py'
```

## 과거 파일럿 재실행 (Ollama Judge 기록 재현용)

1. 로컬 PostgreSQL에 `telme_judge_eval` 독립 DB를 준비하고 같은 버전의 Flyway 스키마, FAQ, 임베딩을 넣는다. 이번 파일럿은 다음 명령으로 로컬 `telme` DB를 한 번 복제했다. 원본 DB에서는 읽기만 수행한다. 이후 실행에서 `POSTGRES_DB=telme_judge_eval`을 반드시 지정한다.

```powershell
docker exec telme-postgres psql -U telme -d postgres -c "CREATE DATABASE telme_judge_eval"
docker exec telme-postgres sh -lc "pg_dump -U telme -d telme | psql -U telme -d telme_judge_eval -v ON_ERROR_STOP=1"
```

2. 답변 생성 Ollama에 `exaone3.5:7.8b`, `bge-m3`를 준비한다. 이번 파일럿은 기존 `test_ollamadata` 볼륨에 저장된 Qwen을 별도 컨테이너에서 사용했다. 해당 볼륨이 없다면 모델을 내려받아야 한다.

```powershell
docker run -d --name telme-judge-ollama --gpus all -p 11435:11434 -v test_ollamadata:/root/.ollama -e OLLAMA_KEEP_ALIVE=1m ollama/ollama:0.34.0
docker exec telme-judge-ollama ollama list
```
3. PowerShell에서 다음을 실행한다.

```powershell
$env:POSTGRES_DB = 'telme_judge_eval'
$env:TELME_CHAT_JUDGE_PROBE = 'true'
$env:TELME_CHAT_JUDGE_OUT = '.measure/chat-judge-capture.json'
$env:LLM_PROVIDER = 'ollama'
.\gradlew test --tests 'com.telme.probe.ChatJudgeCaptureProbe' --rerun-tasks
Remove-Item Env:TELME_CHAT_JUDGE_PROBE
python -X utf8 -m scripts.chat_judge.judge_chat_flow .measure/chat-judge-capture.json --out .measure/chat-judge-judged.json
python -X utf8 -m scripts.chat_judge.judge_chat_flow .measure/chat-judge-capture.json --reconcile .measure/chat-judge-judged.json --out .measure/chat-judge-reconciled.json
python -X utf8 -m scripts.chat_judge.test_judge_chat_flow
```

Judge 실행은 생성 결과를 다시 만들지 않는다. 따라서 같은 생성 기록에 다른 Judge 기준을 적용해 비교할 수 있다. 두 Ollama가 같은 GPU 메모리를 쓸 때는 생성 모델을 내린 뒤 Judge를 실행한다. 새 파일럿을 공유할 때는 `.measure`의 캡처와 채점 결과를 별도 이름으로 보존하고, 실제 FAQ 원문과 수동 대조 결과를 함께 기록한다.

## 복수 정답과 복합 질문의 검색 평가

흐름 평가 fixture의 `goldSourceGroups`는 `[[첫 질문의 대체 FAQ ID들], [둘째 질문의 대체 FAQ ID들]]` 형태다. 바깥 배열은 모두 검색되어야 하는 하위 질문이고, 안쪽 배열은 해당 하위 질문에 대해 인정할 대체 FAQ다. 예를 들어 `[ ["BILLING-0001", "BILLING-0078"], ["BILLING-0026"] ]`은 첫 번째 그룹의 FAQ 중 하나와 두 번째 그룹의 FAQ가 모두 필요하다는 뜻이다.

`judge_chat_flow.py`는 그룹별로 답변 메시지에 저장된 근거를 대조하고, `retrievalCoverage`에 일치 FAQ와 검색 질의를 기록한다. 한 그룹이라도 빠지면 `RETRIEVAL_MISS`다. 기존 fixture의 납작한 `goldSourceSlotIds`는 이전 호환을 위해 단일 OR 그룹으로 읽으므로, 복합 질문에는 `goldSourceGroups`를 명시해야 한다. 새 파일럿 기준은 [`chat_judge_pilot.json`](../../scripts/chat_judge/data/chat_judge_pilot.json)에 반영했다.

500건 Judge 검증셋에 FAQ별 복수 정답 묶음을 보완했다. [`faq_equivalence_labels_v2.json`](../../scripts/data/faq_equivalence_labels_v2.json)은 선택된 500개 기준 FAQ를 같은 분야의 FAQ 1,150건과 비교해 후보를 찾고, EXAONE의 두 차례 판정이 모두 통과한 후보만 대체 정답으로 기록한다.

이번 후보 탐색에서는 BGE-M3 질문 유사도 0.70 이상과 답변 유사도 0.90 이상을 사용하고 기존 180문항 다중 정답 후보도 함께 검토했다. 후보 598쌍을 첫 판정했고 357쌍이 통과했다. 핵심 정보 누락 여부를 더 엄격하게 본 두 번째 판정에서는 그중 246쌍만 남았다. 최종 매핑에는 기준 FAQ 500개 중 181개에 대체 정답 연결 246개가 있으며, 복합 질문의 하위 질문별 정답은 기존처럼 별도 그룹으로 묶인다. 판정 원문은 [`faq_equivalence_adjudication_v2.json`](../../scripts/data/faq_equivalence_adjudication_v2.json), 재검토 기록은 [`faq_equivalence_strict_review_v1.json`](../../scripts/data/faq_equivalence_strict_review_v1.json)에 보존했다.

이 보완으로 이전보다 넓게 후보를 확인했지만, **FAQ 1,150건의 모든 쌍을 사람이 확인한 완전한 동등성 목록은 아니다.** 후보 탐색의 유사도 기준보다 낮은 FAQ는 누락될 수 있고, 최종 라벨도 사람의 독립 검토가 아니라 로컬 EXAONE 두 차례 판정 결과다. 따라서 이번 라벨은 검토 범위와 모델 판정 근거를 추적할 수 있는 확장본이며, 전체 FAQ의 모든 대체 정답이 보장된다고 해석하면 안 된다.

동일한 매핑과 검증셋을 재생성하려면 BGE-M3와 EXAONE Ollama가 실행 중이어야 한다.

```powershell
python -X utf8 scripts/build_faq_equivalence_review_candidates.py --question-threshold 0.70 --answer-threshold 0.90 --out scripts/data/faq_equivalence_review_candidates_v2.json
python -X utf8 scripts/adjudicate_faq_equivalence_candidates.py --input scripts/data/faq_equivalence_review_candidates_v2.json --out scripts/data/faq_equivalence_adjudication_v2.json --policy-mode candidate
python -X utf8 scripts/prepare_faq_equivalence_strict_review.py
python -X utf8 scripts/adjudicate_faq_equivalence_candidates.py --input scripts/data/faq_equivalence_positive_review_v1.json --out scripts/data/faq_equivalence_strict_review_v1.json --policy-mode strict
python -X utf8 scripts/build_faq_equivalence_final_labels.py
python -X utf8 -m scripts.chat_judge.experiments.v3_fixed_500_case_validation.build_judge_validation_v2
```

기존 `20261001-capture.json`은 라벨 구조 도입 전 수집한 원시 기록이라 수정하지 않았다. 새로 채집한 기록에는 pilot의 `goldSourceGroups`가 포함된다. 이전 캡처를 그룹 방식으로 다시 평가하려면 해당 fixture에 하위 질문별 그룹을 보충하고, 새 결과 파일로 저장해야 한다.

## 500건 고정 검증셋 결과

- 결과 해석과 축별 일치율: [500건 LLM Judge 검증 결과](experiments/V3-fixed-500-case-validation/20261001-validation-v2-results.md)
- 전체 요청과 원시 응답: [압축 원시 결과](experiments/V3-fixed-500-case-validation/20261001-validation-v2-raw.json.gz)
- 출력 규칙 보완 후 재채점: [500건 재검증 결과](experiments/V3-fixed-500-case-validation/20261001-validation-v2-contract-recheck.md)
- 재채점 요청과 원시 응답: [압축 원시 결과](experiments/V3-fixed-500-case-validation/20261001-validation-v2-contract-raw.json.gz)
- 근거성, 충실도, 답변 불가 분리 판정: [TELME-100 재검증 결과](experiments/V4-split-judge-telme100/20261001-split-judge-results.md)
- 사람이 확인할 우선 사례: [검토표](experiments/V4-split-judge-telme100/20261001-human-review.md)

### 근거성 Judge 2단계 판정

근거성 판정은 답변 claim 추출과 FAQ 의미 비교를 별도 호출로 수행한다. 첫 호출에는 답변 원문만 전달하고, 각 claim을 답변에서 그대로 복사한 연속 구간으로 받는다. 코드는 인용 구간이 답변에 실제로 있고 답변 순서와 일치하는지 검증한다. 두 번째 호출에는 검증된 claim과 FAQ 답변만 전달해 의미상 근거를 판정한다. claim을 고쳐 기존 판정이나 근거 ID를 보존하는 재시도는 하지 않는다. 원문 검증이나 판정 형식 검증에 실패하면 미채점으로 남긴다.

- 기존 미채점 35건을 vLLM 0.29.0 / Qwen3-14B-AWQ / 동시성 8로 재실행: [결과](experiments/V5-grounding-two-stage/20261002-grounding-two-stage-35-results.md)
- 요청, 응답, 검증 기록: [vLLM 원시 JSON](experiments/V5-grounding-two-stage/20261002-grounding-two-stage-vllm-retry-35-raw.json.gz)
- 독립 40건 사람 검토 방법과 표본 구성: [검토 절차](experiments/V5-grounding-two-stage/20261002-grounding-human-review-method.md)
- 검토자에게 전달할 자료: [블라인드 표본](experiments/V5-grounding-two-stage/20261002-grounding-human-review-blind.json)
- 사람이 직접 판정한 40건과 Qwen Judge 비교: [판정 문서](experiments/V5-grounding-two-stage/20261002-grounding-human-review.md), [교차 검증 결과](experiments/V5-grounding-two-stage/20261005-grounding-human-judge-comparison.md)
- 기존 vLLM 재시도 기록과 미채점 사례 분석: [재시도 측정](experiments/V5-grounding-two-stage/20261002-grounding-retry-vllm.md), [미채점 원인 점검](experiments/V5-grounding-two-stage/20261002-grounding-retry-unscored-audit.md)
