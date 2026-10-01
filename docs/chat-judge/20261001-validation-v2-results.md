# 500건 고정 검증셋 LLM Judge 결과

**실행일:** 2026-10-01  
**Judge:** Ollama qwen3:14b  
**목적:** 고정된 답변과 검색 근거를 보고 Judge가 근거성, 답변 충실도, 답변 불가 여부를 기준에 맞게 판정하는지 확인

## 핵심 결과

| 판정 축 | 일치 | 유효 채점 | 일치율 |
| --- | ---: | ---: | ---: |
| 근거성 | 408 | 457 | 89.3% |
| 답변 충실도 | 343 | 359 | 95.5% |
| 답변 불가 여부 | 215 | 457 | 47.0% |

일치율은 유효한 Judge 출력만 대상으로 계산했다. 답변 충실도는 정답 라벨이 있는 400건 중 미채점 사례를 제외했다. 미채점을 정답 또는 오답으로 처리하지 않았다. 따라서 이 수치는 TEL-ME의 실제 답변 품질이나 서비스 환각률이 아니라, 이 검증셋에서 Judge가 기준 라벨과 일치한 비율이다.

**답변 불가 판정은 현재 기준으로 신뢰하기 어렵다.** SHOULD_ABSTAIN 라벨이 있는 유효 채점 98건을 모두 NOT_APPLICABLE로 판정했다. OVER_REFUSAL은 유효 채점 76건 중 72건을 NOT_APPLICABLE, 4건을 APPROPRIATE로 판정했고 정답 일치는 0건이었다.

## 실험 범위와 데이터

이 실험은 500개 고정 문항에 미리 구성된 질문, 답변, 검색 근거를 넣고 Judge만 평가했다. TEL-ME 채팅 API를 호출하거나 EXAONE으로 답변을 새로 생성한 실행은 아니다. 같은 문항의 입력과 정답 라벨을 기준으로 세 판정 축을 점검했다.

검증셋 구성은 다음과 같다.

| 문항 유형 | 수 |
| --- | ---: |
| 지원되는 답변 | 100 |
| 근거 없는 내용을 덧붙인 답변 | 100 |
| 답할 수 있지만 거절한 답변 | 100 |
| 근거가 없어 답하지 않은 경우 | 100 |
| 복합 FAQ 질문의 일부만 답한 경우 | 50 |
| 통신 서비스 범위 밖 질문 | 50 |
| **전체** | **500** |

FAQ 카탈로그 1,150건을 기준으로 구성됐다. 복합 질문 50건은 각각 두 개의 정답 근거 그룹을 갖는다. 대체 FAQ 라벨은 후보 검색과 검토를 통과한 동등 FAQ를 반영하지만, 카탈로그 전체에서 동등 FAQ를 빠짐없이 찾았다는 뜻은 아니다. 검증셋의 라벨과 후보 구성 방식은 데이터 파일의 goldSourceGroupCompleteness 및 goldSourceGroupPolicy에 기록돼 있다.

## 판정 상세

### 근거성

| 정답 라벨 | 전체 라벨 수 | 유효 채점 | 일치 | 유효 채점 기준 일치율 |
| --- | ---: | ---: | ---: | ---: |
| NOT_APPLICABLE | 250 | 226 | 185 | 81.9% |
| SUPPORTED | 150 | 133 | 125 | 94.0% |
| UNSUPPORTED | 100 | 98 | 98 | 100% |
| **전체** | **500** | **457** | **408** | **89.3%** |

UNSUPPORTED 판정은 채점된 항목에서 모두 맞았지만, 미채점 43건이 모두 이 라벨에 속한 것은 아니다. 표의 일치율은 해당 라벨 중 Judge 응답 검증을 통과한 항목만 대상으로 한다.

### 답변 충실도

| 정답 라벨 | 전체 라벨 수 | 유효 채점 | 일치 | 유효 채점 기준 일치율 |
| --- | ---: | ---: | ---: | ---: |
| COMPLETE | 100 | 84 | 84 | 100% |
| MISSED | 200 | 176 | 166 | 94.3% |
| NOT_APPLICABLE | 50 | 50 | 49 | 98.0% |
| PARTIAL | 50 | 49 | 44 | 89.8% |
| **전체** | **400** | **359** | **343** | **95.5%** |

근거성 판정이 유효하지 않은 문항은 전체 문항 상태가 UNSCORED가 되어 충실도 결과가 생성됐더라도 이 집계에서 제외된다.

### 답변 불가 여부

| 정답 라벨 | 전체 라벨 수 | 유효 채점 | 일치 | 유효 채점 기준 일치율 |
| --- | ---: | ---: | ---: | ---: |
| APPROPRIATE | 150 | 150 | 82 | 54.7% |
| NOT_APPLICABLE | 150 | 133 | 133 | 100% |
| OVER_REFUSAL | 100 | 76 | 0 | 0% |
| SHOULD_ABSTAIN | 100 | 98 | 0 | 0% |
| **전체** | **500** | **457** | **215** | **47.0%** |

모델은 답변을 거절해야 하는 사례와 답변할 수 있는데 거절한 사례를 잘 구분하지 못했다. 전체 일치율만 보지 말고 특히 OVER_REFUSAL과 SHOULD_ABSTAIN 라벨의 혼동을 개선해야 한다.

## 미채점과 재시도

최초 실행은 456건 채점, 44건 미채점이었다. 미채점 44건을 동일 모델, 동일 프롬프트 조건으로 재시도했다. 그중 1건은 통과해 최종 집계가 457건 채점, 43건 미채점으로 바뀌었다.

남은 43건은 모두 Judge가 UNSUPPORTED 주장에 근거 ID를 붙인 응답이었다. 내부 결과 검증이 이를 거부해 해당 문항은 UNSCORED로 남겼다. 미채점을 강제로 점수에 포함하지 않았다.

- BILLING-0032, BILLING-0065, BILLING-0052, BILLING-0053
- PLAN-0022, PLAN-0037, PLAN-0060, PLAN-0115, PLAN-0150, PLAN-0135, PLAN-0145
- DEVICE-0025, DEVICE-0034, DEVICE-0063, DEVICE-0119, DEVICE-0116, DEVICE-0117
- SUBSCRIBE-0051, SUBSCRIBE-0081, SUBSCRIBE-0099, SUBSCRIBE-0096, SUBSCRIBE-0123
- PORTING-0071, PORTING-0073, PORTING-0113, PORTING-0097
- TERMINATE-0020, TERMINATE-0071, TERMINATE-0113
- USIM-0016
- NAME_CHANGE-0014, NAME_CHANGE-0017, NAME_CHANGE-0039, NAME_CHANGE-0079
- ROAMING-0006, ROAMING-0013, ROAMING-0031, ROAMING-0039, ROAMING-0038, ROAMING-0058, ROAMING-0078, ROAMING-0069
- COMPOUND-022

## 재현 정보

- 모델: qwen3:14b
- 모델 digest: bdbd181c33f2ed1b31c972991882db3cf4d192569092138a7d29e973cd9debe8
- Ollama: 0.34.0
- 판정: 기본 프롬프트, few-shot 미사용, 구조화 JSON 출력
- 옵션: temperature=0, num_ctx=8192, num_predict=2048
- 요청 수: 근거성 500회, 충실도 및 답변 불가 판정 500회, 총 1,000회
- 누적 모델 호출 시간: 약 40.7분
- 입력 토큰: 593,195
- 출력 토큰: 147,149
- 검증셋 SHA-256: bf8e3c3e4ed48e784122b7184f67d3200ecdceb89cb9273a4a713825843a2ee3
- FAQ 카탈로그 SHA-256: ca078af56df2c67a427246f50fcde165978fe8b35cb01e890dab6fe5b95f7505
- 근거성 기준 SHA-256: a92e08ac5cfd38f4b766e2146c882c0a579a0aefa564b375f48fa349e54bf51d
- 충실도 및 답변 불가 기준 SHA-256: 4b2bd473d1c81aa56306bb6e520ff64ae50e9ad7c8cac3c8d1fd5dd67fb6edcb

실행 명령:

    python -u -X utf8 scripts/run_judge_validation_v2.py --ollama-url http://localhost:11435 --model qwen3:14b --out .measure/chat-judge-validation-v2.json

전체 요청, 구조화 스키마, 원시 응답, 입력 해시, 토큰 수, 지연 시간, 최종 검증 결과는 [압축 원시 결과](20261001-validation-v2-raw.json.gz)에 보존했다. 재실행 때는 기존 결과 파일을 이어 쓰며 이미 채점된 사례를 건너뛴다. 전체 재실험은 새로운 --out 경로를 지정해야 한다. 최초 실행 결과는 로컬의 .measure/chat-judge-validation-v2-initial.json에 별도로 보존돼 있다.

관련 자료는 [검증 실행기](../../scripts/run_judge_validation_v2.py), [Judge 및 출력 검증](../../scripts/judge_chat_flow.py), [검증셋](../../scripts/data/chat_judge_validation_v2.json), [FAQ 카탈로그](../../scripts/data/faq_full_1150.json)에서 확인할 수 있다.

## 해석과 후속 작업

이 결과는 이 고정 검증셋과 현재 프롬프트, 모델 digest에 대한 Judge 일치도다. 실제 서비스에서 답변 생성의 환각률이나 답변 품질을 추정하지 않는다. 미채점이 43건이고 답변 불가 축의 오판이 집중돼 있으므로 전체 평균만으로 Judge를 승인해서는 안 된다.

다음 평가 전에 아래를 처리한다.

1. UNSUPPORTED 주장에 근거 ID가 붙을 수 없도록 출력 형식과 검증 규칙을 정렬하고, 43건의 미채점 원인을 회귀 사례로 추가한다.
2. OVER_REFUSAL, SHOULD_ABSTAIN, APPROPRIATE를 구분하는 판정 기준과 예시를 다시 설계한다.
3. 동일한 500건을 재채점하고 기존 원시 판정과 비교한다. 원본 결과를 덮어쓰지 않는다.
4. 자동 판정이 개선돼도 경계 사례는 사람의 독립 검토와 함께 보고한다.

출력 형식 보완과 500건 재채점 결과는 [후속 검증 문서](20261001-validation-v2-contract-recheck.md)에 기록했다.
