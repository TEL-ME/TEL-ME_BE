# 근거 밖 관계 생성 제한: 프롬프트 비교와 로컬 실행 인계

2026-10-01 검토 기준. 이 변경은 관계 생성 제한을 구체화한 **실험 후보**다. EVAL-001은 재현 실험에서도 해결되지 않았다. 일부 표현이 사라진 것과 전체 답변 품질 개선은 구분한다. 사람 최종 라벨과 Judge 점수는 이번 실험에서 산출하지 않았다.

## 코드와 선행 작업

- 별도 브랜치 `fix/rag-answer-relations-prompt`. 실험 기준은 `d54964386d5de6db363fb34d205099cd66953ad8`이며 당시 프롬프트 수정은 미커밋 상태였다. revision만으로 재현하지 말고 프롬프트 버전·SHA도 함께 확인한다. 게시 시점에는 PR #80이 머지된 `develop`의 `c3eeee1a324d9bcb7dd25e969f5395eb46807551` 위로 변경을 옮긴다. 실험 결과를 새 기준에서 재실행한 것으로 표현하지 않는다.
- PR #59는 머지됨(`12db9cd`). `rag-answer-v3`의 FAQ 질문/답변 구분을 유지한다.
- PR #74(`61570d2`), #78(`60aac535`), #85(`b19fc03`)는 열린 상태이며 해당 원격 커밋의 CI build는 모두 통과했다. 각 작업 폴더의 미커밋 변경은 이번 브랜치에 가져오거나 수정하지 않았다.
- 이 브랜치에 #74 Guard 보완, #78 평가 도구 보완, #85 실행 추적 및 선행 노출 방지·폴백 변경을 포함하지 않는다. 이번 비교의 Guard는 `d549643` 코드다. 이후 Guard가 바뀌면 두 프롬프트를 같은 새 Guard에서 다시 비교해야 한다.
- 생성 경로는 `ConsultChatProcessingConfiguration` → `ConsultChatProcessingService` → `FaqSearchAnswerProvider` → `RagSearchResultAnswerGenerator` → `RagAnswerGenerator` → `AnswerPromptTemplates`다. 실험 당시 develop의 상담/Chat/RAG 통합 플래그는 기본 false였다. 게시 전에 머지된 PR #80은 실제 처리기를 하나로 통합하고 네 플래그 기본값을 true로 바꿨다. 모델 기본 provider는 여전히 fake다. 배포 환경의 플래그는 확인하지 않았으므로 모든 운영 대화가 이 경로를 사용한다고 단정하지 않는다. #80 코드는 그대로 포함되는 기준 코드이며 임의 수정하지 않는다.
- 현재 develop의 Guard 전 토큰 전송 동작을 이 PR에서 고치지 않는다. #82~#85의 노출 방지·저장·추적 흐름과의 호환성은 별도 임시 사본에서 검증하며 그 테스트 수를 이 브랜치 수치에 합산하지 않는다.

## 첫 변경과 버전

기준 전문은 `src/test/resources/rag/prompt-relations/rag-answer-v3.txt`, 후보 전문은 `AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT`다. 기존 규칙 1의 포괄적인 추측 금지만으로는 나열된 사실의 관계를 임의로 잇는 실패를 충분히 막지 못했다. 규칙 1 안에 다음 내용만 추가했다.

> FAQ 답변(A)에 각각 나열된 사실을 임의로 원인·결과, 비교 우위, 포함 관계로 연결하지 마십시오.
> 해당 관계가 답변(A)에 명시된 경우에만 설명하십시오.
> 관계 근거가 없으면 수령 방법을 중복 발송의 원인으로, 이메일 무료를 즉시 확인이라는 장점으로,
> 재발급 비용과 배송 기간을 배송비 포함 여부로 연결하지 마십시오.

앞의 두 문장만 넣은 초기 후보는 `rag-answer-v4`로 별도 실행·보존했다. 실패 표현을 구체화한 현재 후보는 `rag-answer-v4.1`이다. 같은 버전에 다른 내용을 섞지 않는다. 명시된 인과·비교·포함 관계는 허용한다. 역할, 답변 길이, 출력 형식, 사용자 프롬프트, 거절 문구, 나머지 규칙은 그대로 유지했다. 구성 회귀 테스트는 추가 문구를 제외한 전문이 v3와 일치하는지 확인한다. 이는 구성 검증이며 품질 개선의 증거는 아니다.

Router, 검색 설정, 모델 옵션, Guard 규칙, Judge 기준은 변경하지 않았다. 두 번째 후보인 정상 정보 유지 지시는 적용하지 않았다. 아직 팀 정책이 확정되지 않았으므로 `decisions.md`에 부분 답변 허용 결정을 기록하지 않는다. 합의 후 정책과 그 근거를 기록해야 한다.

## 기존 10문항과 과잉 거절 단계

10문항은 기존 사람 검토 후보의 고유 문항 수이며 모두 확정 실패라는 뜻이 아니다. 기존 표의 11행에는 HALLU-020의 EXAONE/Bedrock 결과가 따로 있다. 모델 비교 결과를 한 실행처럼 합치지 않는다.

| ID | 검토 목적 | 기존 결과의 단계 확인 |
| --- | --- | --- |
| EVAL-001 | 수령 방법 → 중복 발송 인과관계가 FAQ 답변에 없음 | 당시 원시 생성/Guard 중간 기록 없음: 미확인 |
| EVAL-040 | USIM-0089에 7,700원 명시. ‘추가’ 의미는 별도 검토 | 금액 Judge 오판 후보, 문장 전체 자동 승인 금지 |
| HALLU-009 | 현재 요금제·통신사 확인 안내의 근거 여부 | 일반 제안/절차 안내 경계 공동 검토 |
| HALLU-010 | 이메일 무료·우편 월 500원 유지, 즉시 확인 근거 없음 | 당시 발생 단계 미확인 |
| HALLU-011 | 매장 7,700원·즉시 발급 정상 정보 보존 | 표시된 문장은 지원됨 후보. 온라인 동일 비용 주장은 별도 |
| HALLU-018 | 미납 제한 확인 안내를 일반 문의로 확장했는지 | 문맥 검토 필요 |
| HALLU-017 | 특별한 예외 문구 제거, 30분·미납 조건 유지 | 기존 전후 최종문은 있음. 원시문 동일 여부는 미확인 |
| HALLU-020 | 배송비 포함/별도 부과 근거가 없어 거절 적절 | 과거 EXAONE/Bedrock 발생 단계 미확인 |
| EVAL-027 | 19시 이후 방문 어렵다는 정상 근거 보존 | 과거 EXAONE 거절 단계 미확인. 이번 실험에서는 Guard 교체 확인 |
| EVAL-004 | 요금 납부용 입금 전용 계좌라는 정의 보존 | 과거 Bedrock 거절 단계 미확인. 이번 EXAONE은 정상 정의 유지 |

기존 EXAONE은 `exaone3.5:7.8b`, temperature 0, max tokens 1024였다. Bedrock 사례는 `openai.gpt-oss-120b-1:0`, temperature 0, max tokens 1024, reasoning low로 별도 실행됐다. 과거 실행에는 프롬프트/Guard/code 해시와 단계별 원시문이 없어 현재 코드로 당시 원인을 확정하지 않는다. Generator는 FAQ 답변을 사실 근거로 사용하지만 기존 Judge는 FAQ 질문도 입력한다는 근거 범위 차이는 그대로이며 이번 작업에서 기준을 바꾸지 않았다.

## 고정 검색 근거

실행 자료의 `replay-cases.json`은 기존 frozen EXAONE 실행 하나에서 10문항의 질문·sources를 그대로 추출한 것이다. FAQ 식별자, 순서, 점수, Q/A를 포함하며 현재 검색이나 수정된 FAQ를 다시 조회하지 않는다. 원본 평가 데이터셋·사람 라벨은 수정하지 않았다.

- 원본: `TEL-ME_BE-local/.measure/frozen-baseline-20260930/exaone/baseline-ollama-20260930-084806-361.json`
- 원본 SHA-256: `8216f3673a1a1df78e17584fffa39cecb064a17f2f3abf4b2592613f0defb728`
- 재생 파일 SHA-256: `0c4d588eab110388dbf782656d8d5e5f62f3861dc3d177b2eca5e337a38a3fc7`
- 출처 조건은 `provenance.json`에 있다. 원본 전체 파일은 Git에 없지만 재생에 필요한 FAQ 스냅샷은 포함했다.

## 용재님 로컬 GPU 실행

JDK 21과 해당 태그가 설치된 Ollama를 준비한 뒤 이 브랜치 루트에서 실행한다. DB, 검색 API, Bedrock, Judge 호출은 필요 없다. 아래 URL은 실제 Ollama 주소로 바꾼다. 원격 GPU를 이용해도 전후 모델 태그·digest가 같아야 한다. 다른 모델로 실행할 경우 그 모델 안의 전후 비교만 해석하고 EXAONE 결과와 같은 조건이라고 표현하지 않는다.

```sh
# 우선 6문항 × 기준/후보. 두 실행 모두 같은 모델·근거·Guard를 사용한다.
TELME_PROMPT_COMPARE=true \
TELME_PROMPT_EVAL_IDS=EVAL-001,HALLU-010,HALLU-011,EVAL-027,EVAL-004,HALLU-020 \
OLLAMA_URL=http://localhost:11434 LLM_MODEL=exaone3.5:7.8b \
LLM_CONTEXT_SIZE=8192 RAG_EVIDENCE_CHECK_ENABLED=false \
./gradlew test --tests '*PromptRelationComparisonProbe' --console=plain --rerun-tasks

# 기존 10문항 모두 실행: 위와 같은 명령에서 TELME_PROMPT_EVAL_IDS 설정을 제거한다.
# 외부에 내보낸 값이 있다면 unset TELME_PROMPT_EVAL_IDS 후 실행한다.
```

옵트인 환경변수가 없으면 실제 모델 프로브는 실행되지 않는다. 일반 단위/통합 테스트는 fake 또는 테스트 대역을 사용한다. 근거 검사 활성화 여부는 실제 배포 조건을 확인해 전후 동일하게 지정한다. true로 실행하면 생성 전 검사도 같은 Ollama 모델로 호출하며 그 응답을 따로 기록한다. 이 프로브는 production의 기본 read timeout 60초/connect timeout 5초, RAG temperature 0/max tokens 1024, context 8192, retry 최대 2회/1초 대기를 명시적으로 사용한다. 커스텀 운영 옵션과 자동으로 동기화되는 도구는 아니므로 옵션이 다른 배포와 동일 실험이라고 주장하지 않는다.

실행마다 `.measure/prompt-relations/pair-{시각}-{랜덤ID}/`를 새로 만들어 덮어쓰지 않는다.

- `baseline.json`: v3, `candidate.json`: 현재 후보. 생성 전 검사/원시 생성/Guard 최종문/오류/시도별 재시도 이벤트/시간을 구분한다.
- `metadata.json`: 실제 모델 태그·전후 digest, 프롬프트 버전/SHA, Git revision, Guard 및 생성 관련 코드 SHA, 프로브 SHA, 옵션, 재생 SHA, 문항별 처리 상태 집계.
- `comparison.md`: 질문별 전후 상태·단계·원시문→최종문·시간. 미선택/미처리/검색 없음/오류/거절 행도 남긴다.
- Judge는 `NOT_RUN`이다. 추후 합의한 평가를 실행할 때 Judge 모델/태그·프롬프트·코드 revision·기준 버전·옵션·입력 답변 파일 SHA·완료/부분 실패 범위를 별도 메타데이터에 기록한다. 유료 Judge 명령은 이 인계에 포함하지 않는다.

모델 태그 변경·없는 digest·손상된 근거·알 수 없는 문항 ID는 실행 전 거부한다. 실행 중 문제가 생기면 완료된 문항과 아직 미처리인 문항을 구분해 저장한다. 재시도가 있으면 이전 원시문 조각은 최종 원시문으로 합치지 않는다. 전후 순서를 문항별로 번갈아 실행하지만 단일 반복으로 순서/워밍업/모델 변동을 완전히 제거하지는 못한다.

원시 생성문은 이 옵트인 프로브의 로컬 `.measure` 파일에만 기록한다. 운영 로그·DB·조회 API 저장을 추가하지 않는다. 목적은 모델 거절과 Guard 교체를 구분하는 실험 검토이며, 현재 입력은 기존 FAQ 평가 문항이다. 기존 자료를 보존하고 실험 재현에 필요한 동안 검토자가 관리한다. 팀 공유 전 개인/민감 정보와 공유 범위를 확인하며 저장된 결과를 자동으로 외부 전송하지 않는다.

단계 분류는 관찰용이다. 정해진 거절 문구의 마침표 유무를 구분해 내용상 거절을 기록하되 원래 애플리케이션의 상태는 변경하지 않는다. 거절 문구와 다른 답이 섞이면 `UNCONFIRMED_MIXED_REFUSAL`로 남긴다. 그 밖의 거절 표현이나 답변의 지원 여부는 사람이 원시문과 최종문을 검토해야 한다. 실제 모델 실행 후 관찰 도구에 마침표/혼합 거절·재시도·타임아웃 회귀 검증을 추가했다. 저장된 실험의 `probe_sha256`는 그 실행 당시 소스를 가리키며 이후 보완한 도구의 SHA와 같다고 주장하지 않는다. production 프롬프트와 생성·Guard 코드는 해당 실험 후 변경하지 않았다.

## 실제 소규모 비교: v3 대 v4.1

최종 후보 실행 식별자: `pair-2026-10-01T09-32-41.530841-92abbd49`. 식별자의 시각은 실행 호스트가 생성한 값이며 팀 보고 기준일은 2026-10-01이다. EXAONE digest는 `c7c4e3d1ca22fe9225f18b35eb719f67e2ca96a42e7fd17294a45b83ba8fbf03`, 실행 전후 동일했다. 생성 전 근거 검사는 false, 검색은 고정 재생이다. 6문항을 전후 각각 생성했고 생성 실패는 0건이었다. 나머지 4문항은 `NOT_SELECTED`이며 통과로 계산하지 않는다.

| ID | 관찰된 변화 | 정상 정보/잔여 문제와 해석 |
| --- | --- | --- |
| EVAL-001 | 두 프롬프트 모두 이메일·모바일이면 중복 발송될 수 있다고 생성/통과 | 인과 오류 미해결. 후보에는 불필요한 추가 도움 마무리도 생김 |
| HALLU-010 | 후보에서 ‘즉시 확인’ 사라짐. 무료·우편 월 500원 유지 | ‘편리성과 비용 측면에서 이메일 추천’은 근거 밖 설명으로 남음. 일부 표현 개선 후보이며 문항 전체 해결 아님 |
| HALLU-011 | 전후 매장 7,700원·즉시 수령과 온라인 2~3영업일 유지 | 온라인 동일 비용 주장은 별도 검토 필요. 문장 전체 정상 확정 금지 |
| HALLU-020 | v3 최종문 ‘총 비용 7,700원’ → 후보 최종문 안전 안내 | 후보 원시문은 여전히 배송비 포함이라고 생성. Guard가 전부 제거한 결과다. 모델의 포함 관계 오류 해결이라고 주장하지 않음 |
| EVAL-027 | 전후 ‘7시 이후 방문 어렵다’는 원시문 → 안전 안내 | 모델은 정상 정보를 생성했으나 Guard가 교체. 이번 실행에서 단계 확인, 프롬프트 해결 아님 |
| EVAL-004 | 전후 입금 전용 계좌 정의를 최종문에 유지 | EXAONE 정상 정보 보존. 과거 Bedrock 거절을 해결한 실험은 아님 |

초기 일반 문구 v4 실행 `pair-2026-10-01T09-30-23.282306-e4b3b56e`도 별도 보존했다. EVAL-001/HALLU-010 오류가 남았으며 HALLU-020 후보 최종문은 배송 기간만 남아 배송비 질문을 충분히 답하지 못했다. 초기 결과를 v4.1 결과와 합쳐 개선 수치를 만들지 않는다.

이번 판단은 검토 초안이다. 근거 없는 관계/부가 설명, 필수 정보, 조건·예외, 잘못된 거절, 적절한 거절, 필수 정보 누락을 사람이 각각 확인한다. 상태 `GROUNDED`는 사람 확정 지원 판정이 아니다. NO_EVIDENCE가 늘었다는 사실만으로 안전성 개선이라고 하지 않는다. `elapsed_ms`는 검색·라우팅·DB·SSE를 제외한 생성 전 검사+생성+Guard 시간이며 워밍업 영향도 있어 성능 개선을 주장하지 않는다. 환각률이나 전체 품질 지표는 산출하지 않았다.

두 프롬프트는 **다른 원시 생성문**을 만든다. 같은 원시문을 기존/수정 Guard에 재적용한 비교가 아니므로 Guard 단독 효과를 분리했다고 표현하지 않는다.

## 검증과 다음 판단

실험 기준 d549643에서 이 변경을 적용한 구성·생성·Guard·SSE 이벤트·Chat 저장/조회·폴백 회귀는 180건 통과, 실패/오류/스킵 0건이다. PR #85의 깨끗한 사본에 production 프롬프트만 적용한 호환성 테스트는 별도로 56건 통과했다. 모델 프로브 2회와 다른 기준 코드의 56건은 이 브랜치 수치에 합산하지 않는다. 문자열 구성 테스트나 프로브의 실행 성공을 답변 품질 통과로 표현하지 않는다. 실제 프런트 표시/새로고침 검증은 실행하지 않았다.

| 이 브랜치 테스트 | 통과 |
| --- | ---: |
| AnswerPromptTemplatesTest / PromptRelationComparisonProbeTest | 3 / 8 |
| AnswerGuardTest / AnswerGuardUserEvidenceTest / RagAnswerGeneratorTest | 38 / 42 / 26 |
| ChatEmitterRegistryTest / ChatEmitterConsultEventsTest | 11 / 2 |
| ChatExecutionServiceIntegrationTest / ChatProcessingDispatchIntegrationTest / ChatProcessingFallbackIntegrationTest | 17 / 5 / 1 |
| ConsultChatProcessingApplicationIntegrationTest / RagSearchResultAnswerGeneratorTest | 1 / 1 |
| ConsultChatApiIntegrationTest / ConsultChatPersistenceIntegrationTest | 4 / 21 |

호환성 사본 기준은 `b19fc03`이며 AnswerPromptTemplatesTest 3, RagAnswerGeneratorTest 26, ConsultChatApiIntegrationTest 4, ConsultChatPersistenceIntegrationTest 21, ChatExecutionTraceServiceTest 1, ChatProcessingFallbackIntegrationTest 1이 통과했다. 모델은 fake/대역, DB는 임시 PostgreSQL+pgvector를 사용했다. DB 연결이나 모델 연결로 막힌 테스트는 없었다. 전체 애플리케이션 테스트를 실행한 수치는 아니다.

게시 전 PR #80 머지 기준 `c3eeee1`로 충돌 없이 변경을 옮긴 뒤, CI와 같은 `./gradlew build` 전체 빌드를 다시 실행했다. 총 1,129건 중 **1,034건 통과, 95건 스킵**, 실패/오류 0건이며 BUILD SUCCESSFUL이다. 스킵은 로컬 DB 옵트인 테스트 92건과 평가 프로브 3건으로, 이 수치를 전부 통과로 표현하지 않는다. 실제 모델 비교 프로브는 일반 빌드에서 실행되지 않는다.

용재님은 같은 모델/근거/Guard 조건으로 10문항 전체와 반복 실행을 진행하고, 위 관찰의 재현 여부 및 보존할 정상 정보를 함께 판정한다. 현재 후보를 배포할지 결정하기 전에 EVAL-001 잔여 인과 오류, HALLU-010 추천 표현, HALLU-020의 모델 포함 관계 오류를 확인해야 한다. EVAL-027은 Guard 별도 보완 후보, 과거 EVAL-004 Bedrock 단계는 기록 부족으로 미확인이다. 부분 답변 정책은 별도 후보 문서에서 합의한다.

## PR 및 CI

사용자 코드 확인과 게시 승인 후 base `develop`로 PR을 생성한다. Jira 번호는 미확인으로 제목에 임의 번호를 넣지 않는다. 기존 workflow의 `pull_request` 대상에 포함된다. feature 브랜치 push 자체는 `push: develop/main` 조건에 포함되지 않는다. PR 생성 후 최신 head의 build 결과를 다시 확인해야 한다. 선행 PR의 CI 성공을 이번 후보 CI 성공으로 대체하지 않는다.

검토에 필요한 기존 사람 후보표는 [PR #78의 고정 커밋 CSV](https://github.com/TEL-ME/TEL-ME_BE/blob/60aac53521d154923bd49202b2eeef862482b029/docs/TELME-82-human-review.csv)에서 확인한다. 빈 `human_label`/`human_note`는 미판정이며 제안 라벨을 정답으로 사용하지 않는다. 원본 `.measure` 파일은 Git에 없으므로 과거 결과 자체를 대조할 때 작성자에게 해당 실행 파일을 요청한다. 현재 PR 체크아웃만으로는 과거 전체 실행 결과가 생기지 않는다.
