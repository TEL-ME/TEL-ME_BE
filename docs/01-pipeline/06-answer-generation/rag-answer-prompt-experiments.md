# 답변 생성 프롬프트 규칙과 비교 실험

## 적용 대상과 구현 규칙

- 이 명세는 `rag-answer-v4.2` 후보가 적용된 생성 경로와 비교 프로브를 다룬다. 관계 제한 지시가 배포 환경에 적용됐는지 또는 효과가 입증됐는지는 별도 확인한다.
- 일반 생성 경로는 `ConsultChatProcessingService` → `FaqSearchAnswerProvider` → `RagSearchResultAnswerGenerator` → `RagAnswerGenerator` → `AnswerPromptTemplates`다. 프로브는 라우팅·검색·DB·SSE를 거치지 않고 고정 FAQ를 `RagAnswerGenerator`에 전달한다. 운영 활성 설정을 프로브 결과로 확인할 수는 없다.

- `AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT`의 `rag-answer-v4.2`는 FAQ 답변(A)만 사실 근거로 사용한다. FAQ 질문(Q)의 전제는 근거가 아니다.
- 규칙 1은 나열된 사실을 임의로 원인·결과·비교 우위·포함 관계로 연결하지 않도록 제한한다. FAQ 답변에 관계가 명시돼 있으면 설명할 수 있다.
- 일반 관계 제한 지시 외 역할·길이·출력 형식·사용자 프롬프트·거절 문구·다른 규칙은 유지한다. 기준 전문은 `src/test/resources/rag/prompt-relations/rag-answer-v3.txt`다.
- `rag-answer-v4`는 일반 관계 제한 문구, `rag-answer-v4.1`은 평가 FAQ에 대응하는 사례 표현도 포함한 버전이다. 현재 후보 `rag-answer-v4.2`는 사례를 제외하고 일반 규칙만 유지한다. 과거 결과의 버전·전문·해시는 그대로 보존하며 새 후보 결과로 표시하지 않는다.
- 프롬프트 지시는 모델의 준수를 보장하지 않는다. 규칙의 구성 검증·모델 원문·Guard 최종 결과·품질 판정은 별개다.

현재 전문에서 v3에 추가된 지시는 다음 두 줄뿐이다. 나머지 전문은 기준과 동일하다.

> FAQ 답변(A)에 각각 나열된 사실을 임의로 원인·결과, 비교 우위, 포함 관계로 연결하지 마십시오.
> 해당 관계가 답변(A)에 명시된 경우에만 설명하십시오.

## 비교 목적과 보존 사례

다음 문항 ID는 고정 재생 입력의 평가 식별자다. 모두 확정 실패라는 뜻은 아니다. 특정 실행의 결과나 최종 사람 라벨은 이 표에 소급 반영하지 않는다.

| 문항 | 검토 기준 |
| --- | --- |
| EVAL-001 | 수령 방법을 중복 발송 원인으로 연결할 FAQ 답변 근거가 있는지 |
| EVAL-040 | 7,700원 근거를 보존하고 ‘추가’의 의미를 문장 전체에서 별도 검토 |
| HALLU-009 | 일반 제안과 근거가 필요한 절차 안내의 경계. 공동 정책 확인 |
| HALLU-010 | 이메일 무료 등 정상 사실을 유지하고 근거 없는 즉시 확인·추천 표현 구분 |
| HALLU-011 | 매장 7,700원·즉시 발급 보존. 온라인 동일 비용 주장은 별도 판정 |
| HALLU-018 | 제한 확인 안내를 일반 문의·신청 안내로 확장했는지 |
| HALLU-017 | 근거 없는 예외를 추가하지 않고 처리 시간·미납 조건 보존 |
| HALLU-020 | 배송비 포함/별도 여부의 근거가 없으면 추측하지 않음 |
| EVAL-027 | 19시 이후 방문이 어렵다는 명시된 정상 정보 보존 |
| EVAL-004 | 가상계좌의 납부용 입금 전용 계좌 정의 보존 |

## 실험 입력과 실행 준비

- 구성 회귀는 `AnswerPromptTemplatesTest`, 프로브의 입력·단계·오류 기록 회귀는 `PromptRelationComparisonProbeTest`로 확인한다. 이 테스트는 실제 모델 품질 검증이 아니다.

```bash
./gradlew test --tests '*AnswerPromptTemplatesTest' --tests '*PromptRelationComparisonProbeTest' --console=plain
```

- JDK 21, Ollama와 설치된 모델 태그를 준비한다. 재생 프로브는 DB·실시간 검색·Bedrock·Judge 없이 실행한다.
- `src/test/resources/rag/prompt-relations/replay-cases.json`의 질문·FAQ 전체 목록·순서·점수·식별자·Q/A를 그대로 사용한다. 프로브는 이 고정 재생 입력을 로드한다. 같은 디렉터리의 `provenance.json`은 추출 원본의 출처 설명·모델·옵션·선택 문항·해시를 보존하는 자료다.
- `TELME_PROMPT_REPLAY`로 재생 파일을, `TELME_PROMPT_OUT`으로 출력 부모 경로를 지정할 수 있다. 변경할 때도 입력 계약과 전후 동일 조건을 유지한다.
- 모델 태그뿐 아니라 전후 digest·옵션·근거·Guard·생성 전 검사 설정을 동일하게 유지한다. 다른 모델을 사용하면 그 모델 안에서 전후를 비교한다.
- `RAG_EVIDENCE_CHECK_ENABLED`는 전후 동일하게 지정한다. 활성화하면 생성 전 검사도 모델을 호출하고 그 결과를 별도 기록한다.

```bash
TELME_PROMPT_COMPARE=true \
TELME_PROMPT_EVAL_IDS=EVAL-001,HALLU-010,HALLU-011,EVAL-027,EVAL-004,HALLU-020 \
OLLAMA_URL=http://localhost:11434 LLM_MODEL=exaone3.5:7.8b \
LLM_CONTEXT_SIZE=8192 RAG_EVIDENCE_CHECK_ENABLED=false \
./gradlew test --tests '*PromptRelationComparisonProbe' --console=plain --rerun-tasks
```

- 전체 재생 문항은 `TELME_PROMPT_EVAL_IDS`를 설정하지 않고 실행한다. 외부 환경에 설정돼 있으면 먼저 `unset TELME_PROMPT_EVAL_IDS`로 해제한다. 미선택·미처리 문항을 성공으로 세지 않는다.
- 옵트인 없이는 모델 프로브가 실행되지 않는다. 실행 전 재생 배열·고유 문항 ID·질문·sources 배열·FAQ Q/A·유한 숫자 점수·선택 문항 ID를 검증한다. 빈 sources 배열은 허용하며 검색 없음으로 구분한다. 설치된 모델 태그의 digest를 찾지 못하면 실행을 중단한다.
- 실행 전후 digest가 다르면 `model_digest_unchanged=false`를 기록한다. 현재 도구는 이 경우 결과를 자동 삭제하거나 실패 처리하지 않으므로, 동일 조건 비교에서 제외하고 별도로 확인해야 한다.
- 프로브 설정은 RAG temperature 0, max tokens 1024, context 8192, read timeout 60초, connect timeout 5초, retry 최대 2회·1초 대기다. 배포 옵션과 자동 동기화되는 도구는 아니므로 실행 메타데이터를 확인한다.
- 기준과 후보의 실행 순서를 문항별로 번갈아 수행한다. 반복 횟수와 워밍업 처리·채택 기준은 실험 계획에서 정한다. 단일 반복을 확정 비교로 해석하지 않는다.

## 산출물·단계 기록

- 매 실행은 `.measure/prompt-relations/pair-{시각}-{랜덤ID}/`에 분리해 기존 결과를 덮어쓰지 않는다.
- `baseline.json`, `candidate.json`: 생성 전 검사·마지막 시도의 완료 원문 또는 부분 원문·Guard 최종문·재시도 이벤트·오류·시간·문항 상태.
- `metadata.json`: `generator_model`, `model_digest`, `model_digest_after`, `model_digest_unchanged`, `code_revision`, `fixed_code_sha256`, `guard_sha256`, `probe_sha256`, `replay_sha256`와 옵션·문항별 상태 집계.
- 각 답변 행: `prompt_version`·`prompt_sha256`, `raw_state`·`raw_answer`·`partial_raw`, `observed_stage`, `precheck_response`·`precheck_error`, `retry_events`, `guard`, `elapsed_ms`. 기준과 후보 프롬프트 해시는 각각의 답변 행에 기록된다.
- `guard.outcome`은 `NOT_RUN` / `BLOCKED` / `KEPT` / `CHANGED`다. `guard.rule_id`는 현재 `UNAVAILABLE`이며 구체적인 규칙 식별자를 제공하지 않는다.
- `comparison.md`: 질문별 단계·원문→최종문·시간·미선택/미처리/검색 없음/거절/오류. 누락 행을 제거하지 않는다.
- Judge는 프로브가 호출하지 않는다. 별도 채점에는 Judge 모델·설정·기준 버전·`code_sha256`·입력 해시·완료/부분 실패 범위를 기록한다.
- 원본 출처 설명·추출 조건·해시는 `provenance.json`에 보존한다. `origin_sha256`은 추출 원본 결과의 해시이며 provenance 파일 자체의 해시가 아니다. 출처 설명을 바꾸면 provenance 파일 해시는 달라져도 원본 해시는 바꾸지 않는다. 과거 실행의 원시문이 없으면 당시 거절 발생 단계를 소급 확정하지 않는다. 재생 파일의 FAQ 스냅샷은 실행에 포함되지만, 원본 전체 생성 결과가 저장소에 포함된다는 의미는 아니다.
- 원문은 평가용 산출물에만 보관한다. 운영 로그·DB·일반 조회 API에 일괄 저장하지 않는다. 공유 전 민감정보와 접근·보존 범위를 확인한다.
- 재시도 시 이전 원문 버퍼는 비우고 마지막 시도의 원문만 남긴다. `retry_events`는 시도 번호와 오류 종류를 기록하지만 모든 시도별 원문을 보존하지는 않는다. 단계가 기록되지 않은 거절을 생성 전 검사·모델·Guard 중 하나로 추정하지 않는다.

## 결과 판정과 제약

| 비교 항목 | 확인 방법 |
| --- | --- |
| 근거 없는 관계·부가 설명 | FAQ 답변과 원시 생성문·최종문을 각각 대조 |
| 정상 정보·조건·예외 유지 | 필수 사실을 문항별로 지정하고 누락·오차단 확인 |
| 잘못된 거절 | 답할 근거가 있는데 검사·모델·Guard 중 어디서 거절됐는지 확인 |
| 적절한 거절 | 질문이 요구하는 관계·사실이 없을 때 추측하지 않았는지 확인 |
| 실행 실패·응답 시간 | 상태·시도·오류와 elapsed_ms의 측정 범위 확인 |

- `GROUNDED`는 사람 확정 근거 판정이 아니다. NO_EVIDENCE 증가만으로 안전성 개선이라고 하지 않는다.
- 거절 문구의 마침표 차이와 혼합 답변은 관찰 결과로 구분한다. `UNCONFIRMED_MIXED_REFUSAL`은 확정 지원 판정이 아니다.
- 같은 원시문을 서로 다른 Guard에 적용한 실험이 아니면 Guard 단독 효과를 분리했다고 표현하지 않는다. Guard·생성 전 검사·모델이 바뀌면 기준과 후보를 모두 같은 새 조건에서 재실행한다.
- 모델·Guard·Judge의 근거 범위를 대조한다. FAQ 질문의 전제를 Judge가 사실 근거로 인정하는 평가 구성은 FAQ 답변만 사용하는 생성 규칙과 다른 기준이다. 이 차이를 프롬프트 효과로 해석하거나 평가 기준을 임의 변경하지 않는다.
- `elapsed_ms`는 생성 전 검사+생성+Guard 범위다. 검색·라우팅·DB·SSE·브라우저 전체 지연과 구분한다.
- 문자열 구성 테스트나 실행 성공은 답변 품질·전체 환각률 개선의 증거가 아니다. 특정 문구 감소와 문항 전체 개선도 구분한다.
- 평가 문항에 직접 대응하는 사례 지시를 프롬프트에 넣고 같은 문항만으로 효과를 평가하면 일반화 판단이 어려워진다. 사례 제거 후에도 새로운 관계 오류·필수 정보 누락·거절을 같은 조건에서 다시 비교해야 하며, 재비교 전까지 새 후보의 품질 효과는 미확인이다.
- Guard가 안전 안내로 교체한 결과를 프롬프트 개선으로 집계하지 않는다. 앞 문장이 제거돼 접속어만 남는 문제도 Guard 후처리의 별도 검토 대상이며 이 프롬프트 후보에서 해결됐다고 보지 않는다.

## 미확정 후보: 정상 정보 유지와 부분 답변

다음 지시는 **제안 정책**이며 `rag-answer-v4.2` 전문에 적용되지 않았다. 관계 왜곡 제한과 별도 독립변수로 비교해야 한다.

> 질문에 필요한 근거 있는 정보는 유지하십시오. 근거에 없는 부가 설명만 제외하고, 그 부가 설명이 없다는 이유로 답변 전체를 거절하지 마십시오.

| 질문 유형 | 합의할 정책 |
| --- | --- |
| 비용과 배송비를 함께 알려주세요 | 비용만 근거가 있을 때 지원 사실과 미확인 사실을 나눠 부분 답변할지 |
| 배송비가 포함인가요, 별도인가요? | 비용·배송 기간만으로 관계를 대신 답하지 않음. 근거 없는 포함/별도 판단은 금지 |
| 무료이고 즉시 확인되나요? | 무료 사실 보존과 미지원 속도 설명의 분리, 일반 추천 허용 범위 |

- 현재 거절 규칙과의 충돌, 필수 정보·최소 답변 범위, 부분 답변/잘못된 거절의 판정 기준을 먼저 합의한다. 평가 편의를 위해 Judge 기준을 바꾸지 않는다.
- 모델이 정상 정보를 생성했는데 생성 전 검사나 Guard가 교체했다면 해당 단계의 별도 보완 후보로 남긴다. 프롬프트로 해결됐다고 주장하지 않는다.
- 합의 이후 관계 제한만 적용한 기준과 정상 정보 유지 후보를 같은 조건으로 비교한다. 결정 전에는 부분 답변 지원을 확정 정책으로 기술하지 않는다. 정책을 확정할 때는 기존 의사결정 문서에 이유·적용 범위·평가 기준과의 관계를 기록한다.
