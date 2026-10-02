# 근거 판정 출력 규칙 보완과 500건 재채점

**실행일:** 2026-10-01  
**모델:** Ollama qwen3:14b, digest bdbd181c33f2ed1b31c972991882db3cf4d192569092138a7d29e973cd9debe8  
**비교 기준:** [최초 500건 결과](20261001-validation-v2-results.md)

## 변경 내용

Judge가 UNSUPPORTED 주장에 근거 ID를 붙여 43건이 미채점된 원인을 확인했다. 원시 응답에는 답변에 없는 질문이나 FAQ 내용을 주장으로 추출하거나, faqId가 null이라는 이유로 실제 FAQ 근거를 무시한 사례도 있었다.

- 근거성 판정 입력에서 평가 대상 답변을 assistantAnswer, 검색 근거를 faqSources로 구분하고 선택적인 DB 식별자 faqId는 제외했다. sourceId는 근거 인용 ID로 유지했다.
- 프롬프트에 답변에 실제로 있는 주장만 추출하고, 답변 불가 안내를 사실 주장으로 판정하지 않도록 명시했다. 검색 FAQ는 faqId가 없어도 근거로 취급한다.
- JSON 출력 스키마에서 UNSUPPORTED와 IRRELEVANT 주장의 sourceIds를 빈 배열로 제한했다. 결과 검증기의 기존 금지 규칙도 유지했다.
- 재채점 결과에는 Judge 코드와 두 출력 스키마의 해시를 기록한다. 충실도 요청이 기존 실행과 정확히 같은지 검사한 뒤 그 원시 판정만 재사용할 수 있게 했다.

변경 위치는 [Judge 코드](../../../../scripts/chat_judge/judge_chat_flow.py), [500건 실행기](../../../../scripts/chat_judge/experiments/v3_fixed_500_case_validation/run_judge_validation_v2.py), [회귀 테스트](../../../../scripts/chat_judge/test_judge_chat_flow.py)와 [평가셋 테스트](../../../../scripts/chat_judge/experiments/v3_fixed_500_case_validation/test_build_judge_validation_v2.py)다. TEL-ME 채팅 API나 답변 생성 경로는 변경하지 않았다.

## 재채점 결과

| 항목 | 최초 실행 | 이번 실행 |
| --- | ---: | ---: |
| 전체 문항 | 500 | 500 |
| 유효 채점 | 457 | 500 |
| 미채점 | 43 | 0 |
| 근거성 판정 일치 | 408/457 (89.3%) | 481/500 (96.2%) |
| 충실도 판정 일치 | 343/359 (95.5%) | 382/400 (95.5%) |
| 답변 불가 판정 일치 | 215/457 (47.0%) | 232/500 (46.4%) |

두 실행에서 모두 유효하게 채점된 457건만 비교하면 근거성 일치는 **408건에서 442건**으로 늘었다. 기존 정답 중 11건을 잃었고, 기존 오답 45건을 고쳤다. 최초에 미채점이던 43건은 모두 유효해졌으며 그중 39건은 근거성 라벨과 일치하고 4건은 일치하지 않았다.

이번 근거성 판정 19건은 여전히 라벨과 다르다. 정답 라벨별 일치는 NOT_APPLICABLE 246/250, SUPPORTED 137/150, UNSUPPORTED 98/100이다.

충실도와 답변 불가 판정은 이전의 동일한 Qwen 요청과 응답을 재사용했다. 해당 두 축의 분모와 일치 건수가 바뀐 이유는 기존 미채점 43건도 전체 문항 상태가 유효해져 집계에 포함됐기 때문이다. 답변 불가 판정 자체가 개선된 결과는 아니다.

## 남은 오판과 해석

- USIM-0072와 NAME_CHANGE-0083에서는 근거에 없는 추가 혜택 또는 면제 주장을 SUPPORTED로 잘못 판정했다. 특히 한 응답은 sourceIds가 빈 배열인데도 SUPPORTED를 반환했다.
- 기존에 맞혔던 복합 질문 9건을 새로 UNSUPPORTED로 잘못 판정했다. 대체로 답변하지 않은 두 번째 하위 질문이나 두 번째 FAQ 내용을 답변의 주장으로 추가했다. COMPOUND-001, COMPOUND-005 등이 해당한다.
- 일부 답변 불가 문구를 FAQ에 답이 있다는 이유로 UNSUPPORTED 주장으로 취급한 사례가 남았다.

이번 변경은 **출력 형식 오류로 인한 미채점을 해소**했다. 근거성 판정의 의미상 오판까지 없앤 것은 아니다. 답변 불가 축도 여전히 OVER_REFUSAL 0/100, SHOULD_ABSTAIN 0/100으로 맞히지 못한다. 이 두 축은 별도 개선이 필요하다.

동일한 500건의 실패를 보고 프롬프트를 수정했으므로 이번 수치만으로 새로운 질문에서의 개선을 입증할 수 없다. 독립 질문과 사람 검토 결과로 재확인해야 한다. 43건만 따로 호출한 사전 점검에서는 41건을 맞혔지만 전체 재실행에서 같은 43건 중 39건을 맞혔으므로, 온도 0 설정에서도 판정 변동이 관찰됐다.

## 실행 기록과 검증

기존 결과는 덮어쓰지 않았다. 이번 근거성 판정 500회를 다시 호출했고, 충실도 및 답변 불가 판정 500회는 모델 digest, 기준 프롬프트, 요청 스키마, 입력이 정확히 같은 기존 결과에서 복사했다.

    python -u -X utf8 -m scripts.chat_judge.experiments.v3_fixed_500_case_validation.run_judge_validation_v2 --ollama-url http://localhost:11435 --model qwen3:14b --reuse-adequacy-from .measure/chat-judge-validation-v2.json --out .measure/chat-judge-validation-v2-contract.json

- 전체 결과: [압축 원시 기록](20261001-validation-v2-contract-raw.json.gz)
- 검증셋 SHA-256: bf8e3c3e4ed48e784122b7184f67d3200ecdceb89cb9273a4a713825843a2ee3
- Judge 코드 SHA-256: 71c083f66b6d95b09bba401e0697a5e65703836b9ae2fba6cb63d07fb0017bfc
- 근거성 기준 SHA-256: 908688d559bf1b538f4c3e9323ce21b7c9f386a143b3ae92776a05977ed5e7bb
- 근거성 출력 스키마 SHA-256: 4bab5c8b7045852fd9b806f725b2830fbd0ec9d17c057e9b3ecb0e939bb1bbf2
- 전체 판정 500건의 형식 재검증과 집계 재계산 일치 확인
- Python 단위 테스트 28개 통과

원시 기록에는 요청과 원시 모델 응답, 검증 결과, 토큰 수와 소요 시간이 들어 있다. 이 실험은 고정 답변에 대한 Judge 검증이며 실제 채팅 API에서 답변을 새로 생성한 품질 측정은 아니다.
