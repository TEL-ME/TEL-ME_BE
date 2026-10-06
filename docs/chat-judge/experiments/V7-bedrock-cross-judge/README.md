# V7 Bedrock 교차 Judge

V7은 V6에서 실제 Spring 채팅 흐름으로 한 번 실행해 저장한 **509개 채팅 턴을 다시 채점**하는 실험이다. 이 가운데 494건은 답변 생성까지 완료됐고 15건은 파이프라인 실행 중 실패했다. V7에서는 채팅 파이프라인을 다시 실행하지 않는다. `v7-capture.json.gz`는 V6의 질문, 검색, 저장된 답변 및 실패 상태를 보존하면서 품질 판정용 질문별 정답 FAQ 묶음만 확장했다.

## 판정 기준

- 사용자 질문과 확정 조건은 적용 상황을 해석하는 데만 쓴다. 요금, 자격, 절차 등 정책 주장을 `SUPPORTED`로 확정하려면 실제 생성에 전달된 FAQ 답변의 근거 ID가 필요하다.
- 복합 질문은 각 하위 질문을 별도로 평가한다. 질문별로 검증된 대체 정답 FAQ를 함께 제공하지만, 다른 하위 질문의 FAQ를 빌려 답변 충족도를 인정하지 않는다. FAQ 질문 문구는 정책 근거가 아니다.
- 최초 계획은 Qwen3 235B, Claude Sonnet 5.5, GPT-6 Luna의 교차 판정이었다. 실제 보존된 세 모델 결과는 Qwen3 235B, GPT-OSS-120B, Claude Sonnet 4.6이다. 축별 결론이 유효하고 같아도 사람 판정과 어긋난 사례가 있으므로, 자동 확정 범위는 [302건 대조 분석](v7-three-model-human302-analysis.md)의 결과를 반영해 정한다. 원시 요청, 응답과 모델별 판단을 유지한다.
- 이 509건은 고정 도전 평가셋이다. 자동 판정을 실제 이용자 전체의 환각률로 일반화하지 않는다. 기존 사람 판정 40건은 회귀 확인용이며 개선 효과 추정에는 사용하지 않는다.

## 파일

- `v7-capture.json.gz`: V6 답변 509건과 V7 질문별 대체 FAQ 입력
- `v7-regression40-capture.json.gz`: 기존 사람이 판정한 40건의 V7 입력
- `v7-blind40-human-review.md`: 이전 79건과 겹치지 않는 신규 40건의 블라인드 판정표
- `v7-blind40-key.json`: 블라인드 질문의 원본 ID와 선정 근거
- `v7-bedrock-judged-raw.json.gz`: 사용자 요청으로 중단한 최초 부분 실행. 보존용이다.
- `v7-qwen-continued-raw.json.gz`: 최초 부분 결과를 이어서 509건까지 실행한 Qwen 결과.
- `v7-qwen-final-raw.json.gz`: 검증 실패 9건을 한 번 재시도한 최종 Qwen 단독 결과.
- [Qwen 실행량과 비용 추정](v7-qwen-run-cost.md): 원시 결과의 토큰 사용량과 공개 요금표 기준 추정.
- `v7-sonnet-4-6-raw.json.gz`: Tokyo Geo 프로필로 Sonnet 4.6을 사용해 같은 V6 답변을 재채점한 원시 결과. 모든 저장된 Sonnet 요청은 사고 모드를 껐다.
- [Sonnet 4.6 교차 검증 결과](v7-sonnet-4-6-comparison.md): Qwen3 235B·GPT-OSS-120B와의 일치율, 기존 사람 판정 40건 비교, 토큰 및 비용 추정 주의.
- [Sonnet 4.6 검토 목록](v7-sonnet-4-6-human-review.md): 모델 간 판정이 다르거나 사람 판정과 다른 302개 답변.
- [302건 사람 판정과 세 모델 분석](v7-three-model-human302-analysis.md): 최신 사람 판정과 세 모델의 축별 일치, 합의 오류, 기존 판정과 달라진 사례.
- `v7-three-model-human302-analysis.json`: 위 분석의 재계산 가능한 집계와 건별 불일치 ID.

## 재현

```powershell
python -m pip install -r scripts/chat_judge/requirements.txt
python -X utf8 -m scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture
python -X utf8 -m scripts.chat_judge.experiments.v7_cross_judge.prepare_blind_review
python -X utf8 -m unittest scripts.chat_judge.experiments.v7_cross_judge.test_v7
```

Bedrock 호출은 자동으로 실행하지 않는다. 실제 모델 호출에는 사용자의 명시적인 요청과 실행기의 `--allow-bedrock-call` 플래그가 모두 필요하다. 재현 전에는 사용 모델 접근 권한, 예상 호출 규모, 입력 및 결과 파일을 확인하고 기존 원시 결과를 보존한다.

Bedrock의 JSON Schema 출력은 구조를 제한하지만 인용 내용의 진위와 의미를 보장하지 않는다. 그래서 근거 ID, 답변 원문 인용, 질문별 결과 개수와 판정 조합은 로컬 코드로 다시 검증한다. [AWS 구조화 출력 문서](https://docs.aws.amazon.com/bedrock/latest/userguide/structured-output.html)

## 2026-10-06 접근 상태와 Qwen 회귀 점검

`telme-bedrock-eval` 프로필의 Tokyo Converse 소규모 호출에서 Qwen3 235B는 성공했다. 2026-10-06에 Sonnet 5.5와 GPT-6 Luna만 다시 사전 호출했지만, 두 모델 모두 `AccessDeniedException: ... is not available for this account`를 반환했다. 이번에도 본 평가 배치는 실행하지 않았다. IAM 읽기 권한 확인과 실제 모델 사용 가능 여부는 별개다. 두 모델의 계정 접근이 열리기 전에는 세 모델 합의율이나 최종 자동 품질 점수를 낼 수 없다. 모델을 임의로 바꾸지 않는다.

AWS 관리자는 [Bedrock 모델 접근 안내](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html)에 따라 Marketplace 권한과 결제 수단, Anthropic 최초 사용 신청, 모델별 계정 접근을 확인해야 한다. 평가용 IAM 사용자에게는 `bedrock:GetUseCaseForModelAccess`와 `bedrock:GetFoundationModelAvailability` 권한이 없어 해당 상태를 직접 조회하지 못했다. 선행 조건을 충족해도 같은 오류가 지속되면 AWS 문서가 안내하는 추가 계정 접근 문의가 필요하다.

Qwen 출력 형식을 시험하는 과정에서 Bedrock 구조화 출력이 일부 품질 판정에 긴 공백을 생성해 `max_tokens`로 끝났고, 일부 근거 충분성 판정에는 인용문 배열이 빠졌다. 구조화 출력만으로 의미와 인용의 유효성을 보장하지 못한다는 실측 사례다. V7 실행기는 Qwen의 품질 판정과 근거 충분성 판정에 간결한 JSON 출력을 요청하고, 로컬에서 필드, 인용문과 근거 ID를 검증한다. JSON 예시는 `COMPLETE`나 `ENOUGH`를 미리 선택하지 않도록 중립적인 자리표시자를 사용한다. 불충분한 근거에서 생략한 빈 인용문 배열만 `[]`로 정규화한다. 원시 응답은 보존한다.

기존 사람이 판정한 40건을 중립적 JSON 예시로 Qwen 재채점한 결과 38건이 세 축 모두 채점됐다. [사람 판정 대조](v7-qwen-regression40-neutral-comparison.md)에서 근거성은 33건 일치, 6건 불일치, 1건 미해결이었다. 특히 `V6H-007`, `V6H-011`, `V6H-029`, `V6H-038`의 위험한 `SUPPORTED` 오판은 Qwen 단독으로 여전히 남았다. 세 모델 합의가 되더라도 기존 사람 판정과 충돌하면 자동 확정하지 않도록 회귀 게이트를 추가했다. 40건은 개선에 사용한 표본이므로 이 수치로 성능 향상을 주장하지 않는다.

Qwen 단독으로 V6의 509개 턴을 판정했다. 답변 생성이 완료된 494건 가운데 Qwen 정상 채점은 487건, 응답 검증 실패는 7건이다. 나머지 15건은 V6 실행 때 AnswerGuard가 근거에 없는 수치가 포함된 답변을 차단했거나 생성 스트림이 중단돼 완료 답변이 저장되지 않았다. 이 15건은 채팅 파이프라인 실패로 집계해야 하며 “실행 전” 사례가 아니다. 처음 검증에 실패한 9건 중 2건은 재시도에서 채점됐고 7건은 계속 실패했다. Sonnet과 Luna는 호출하지 않았다. 따라서 합의 결과는 없으며, 이 수치를 세 모델 교차 검증이나 전체 서비스 환각률로 해석하지 않는다. 사용량과 비용 산정 범위는 [실행 기록](v7-qwen-run-cost.md)에 적었다.

## 2026-10-06 Sonnet 4.6 교차 검증

요청에 따라 `jp.anthropic.claude-sonnet-4-6`으로 고정된 V6 답변을 재채점했다. 채팅 파이프라인은 재실행하지 않았다. Sonnet은 494개 생성 답변 중 486개를 세 축 모두 채점했고, 근거성 8건은 답변에서 그대로 복사한 연속 인용이 아니어서 로컬 검증에서 미채점 처리됐다. 사고 모드 비활성화 요청 필드는 저장된 모든 Sonnet 평가 요청에서 확인했다.

Qwen·GPT-OSS·Sonnet 세 모델이 모두 채점한 경우, 세 모델의 전체 판정이 같은 비율은 근거성 386/470, 질문 답변 충실도 381/481, 답변 보류 판정의 전체 구성요소 236/487이었다. 이 값은 모델 간 일치도이며 정답률이나 환각률이 아니다. 기존 사람 판정 40건과는 별도로 대조했으며, 해당 40건은 보정 회귀 자료라 독립 검증셋으로 보지 않는다. 상세 분석은 [비교 결과](v7-sonnet-4-6-comparison.md), 대조할 답변은 [검토 목록](v7-sonnet-4-6-human-review.md)에 기록했다.

비용 참고치의 범위에 주의한다. 첫 배치 usage에서 약 $14.03을 계산했지만, 직렬 재시도에서 이전 시도의 일부 요청 로그를 덮어썼고 중복 실행이 1분 44초 동안 겹쳐 추가 usage는 보존되지 않았다. 따라서 $14.03은 전체 최종 비용이 아니다. AWS Cost Explorer 조회도 IAM 권한 부족으로 거부되어 실제 청구액은 확인하지 못했다. 토큰 로그와 실행 이력은 Sonnet 원시 파일의 `usageAudit`에 남겼다.
