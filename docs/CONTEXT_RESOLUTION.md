# TELME-139 독립 질문의 문맥 확인 오탐 개선

## 무엇을 바꿨는가

`요금 안 내면 언제 정지되나요?`처럼 현재 문장만으로 이해되는 질문을 이전 발언이 없다는 이유로 막지 않는다. 기본 `REGEX_GATED`에서는 정규식이 모델 호출 후보만 고르고, 실제 문맥 관계는 모델이 판정한다. `LLM_ALL`은 모든 새 질문을 같은 판정기로 처리한다.

| 판정 | 처리 |
| --- | --- |
| `SELF_CONTAINED` | 고객의 현재 원문으로 진행하고 이전 주제는 전달하지 않음 |
| `HISTORY_DEPENDENT` | 검증된 이전 고객 발언을 순서대로 연결. 필요한 최신 정정도 함께 선택 |
| `CLARIFICATION_REQUIRED` | 대상을 결정할 수 없어 확인 안내 제공 |

- 요금, 비용, 기간, 서류, 시간 표현과 지시 표현은 호출 후보이지 차단 근거가 아니다. `그럼`으로 시작하는 발언도 후보로 확인한다.
- 공백을 무시해 호출 후보를 검사하되 저장한 고객 원문과 모델 입력의 원문은 보존한다. 모델은 새 질문 문장을 생성하지 않는다.
- 두 모드가 같은 프롬프트, JSON Schema와 출처 검증을 사용한다. 제공된 고객 발언 ID만 허용하고 중복, 잘못된 역할과 판정에 맞지 않는 출처 조합을 거부한다.
- JSON Schema의 속성을 `relation`, `selectedMessageIds` 순서로 고정한다. 구조를 제한해도 의미 판단의 정확성까지 보장하는 것은 아니다.
- 판정은 `CONTEXT_RESOLUTION`, 프롬프트는 `multiturn-resolution-v18`로 기록한다. 실행 trace에 모드, 관계와 선택한 출처를 남긴다. 잘못된 출력과 호출 실패는 확인 안내로 처리한다.

정규식이 독립 판정을 다시 뒤집거나, 이전 발언이 없으면 모델 호출 전에 막던 경로를 제거했다. FAQ 근거 부족과 신청 조건 누락은 후속 검색 및 FAQ 되묻기에서 처리하며 문맥 불명확과 구분한다. 공개 API, DB 스키마와 기본 모드는 유지한다.

## 평가 입력과 환경

[cases.json](../scripts/context-resolution-evaluation/cases.json)에 기대 관계와 필요한 이전 발언 번호를 실행 전에 고정했다. 기본 입력 60건에 띄어쓰기 변형 20건을 더해 80회 실행한다.

| 기본 평가군 | 문항 수 | 수정 전 일치 | 수정 후 일치 |
| --- | ---: | ---: | ---: |
| 기존 오탐 | 9 | 0 | 9 |
| 새로운 독립 질문 | 15 | 1 | 13 |
| 실제 후속 질문 | 10 | 9 | 9 |
| 대상이 모호한 질문 | 10 | 9 | 7 |
| 주제 전환과 조건 정정 | 10 | 1 | 6 |
| 같은 문장 안의 대상 참조 | 6 | 0 | 5 |
| 합계 | 60 | 20 | 49 |

새로운 51건 중 개발군은 31건, 별도 검증군은 20건이다. 검증군은 마지막 다섯 유형에서 각각 4건을 선정했으며 프롬프트 예시로 사용하지 않았다. 띄어쓰기 변형은 개발군과 검증군에서 각각 10건을 선정했다.

추가 입력은 같은 파일의 `confirmationCases` 20건이다. 개발 단계 이후 고정한 입력이며 프롬프트에 사용하지 않았다. 후보 검사 누락을 발견하고 재검증하는 데 사용했으므로 추가 회귀 검사로 해석한다. 기대값은 구현자가 작성한 라벨이며 독립적인 사람 판정이나 블라인드 평가 결과가 아니다. `independent-10`의 “같나요?”처럼 비교 대상의 명확성이 애매한 라벨은 사람 재확인이 필요하다.

실행 환경은 기준 커밋 `9693186`, 실제 Spring 채팅 API 경로(MockMvc), EXAONE 3.5 7.8B Q4_K_M, bge-m3, FAQ와 벡터 1,152건이 있는 독립 DB `telme_139_live`다. 이전 고객 발언은 완료된 사용자 메시지로 고정하고 현재 질문은 실제 모델과 검색을 거친다. 기존 회귀 17회에서는 앞선 질문도 API로 전송한다.

Ollama 0.34.0, RTX 5070 Ti 16GB에서 문맥 판정은 temperature 0, 컨텍스트 8192, 출력 한도 256을 사용했다. FAQ 되묻기는 기본 설정대로 켰다. `WAITING_CONDITION`은 정상 정책 조건 확인일 수 있으며 문맥 확인 오탐으로 집계하지 않는다. 개발 DB는 평가에 쓰지 않았다.

## 결과와 비용

**기존 오탐 9건과 기존 멀티턴 회귀 17회는 통과했다. 전체 입력의 의미 판정과 출처 선택에는 오판이 남는다.** 관계와 필요한 출처가 모두 맞아야 일치로 집계하며, 잘못된 출력을 확인 안내로 처리한 경우도 정답으로 세지 않는다.

| 검사 | 수정 전 | 수정 후 |
| --- | ---: | ---: |
| 기본 60건 | 20/60 | 49/60 |
| 띄어쓰기 변형 20건 | 11/20 | 16/20 |
| 전체 80회 | 31/80 | 65/80 |
| 개발군 31건 | 13/31 | 26/31 |
| 별도 검증군 20건 | 7/20 | 14/20 |
| 같은 뜻의 공백 변형에서 판정 또는 출처가 다른 쌍 | 5/20 | 2/20 |
| 추가 회귀 입력 | 해당 없음 | 17/20 |

80회에서 독립 질문의 오차단은 38회에서 6회로 줄었지만, 후속 또는 모호한 질문을 독립으로 통과시킨 사례는 수정 후 3회다. 필요한 출처 누락은 5회, 잘못된 출처 선택은 4회이고 일부 사례는 중복 집계된다. 잘못된 모델 출력으로 확인 안내를 반환한 사례는 1회다. 실행 자체의 실패는 없었다.

80회의 문맥 판정 호출은 31회에서 80회로 늘었다. 일반적인 후보 밖 질문은 호출을 추가하지 않는다. 문맥 호출의 중앙값/P95는 334/628ms에서 약 295/511ms였고, 평가 사례 전체 처리의 중앙값/P95는 282/2,122ms에서 1,550/2,891ms였다. 전체 처리 시간에는 평가용 세션 생성, 실행 대기와 결과 조회가 포함된다. 수정 후에는 기존에 차단되던 검색과 답변 생성도 진행하므로 지연 증가를 문맥 모델 호출만의 비용으로 해석하지 않는다.

`clean build --build-cache`는 테스트 2,290개 중 2,181개 통과, 109개 건너뜀, 실패 0이다. 전체 빌드는 FAQ 실측 DB와 분리한 `telme_139_checks`에서 실행했다. 원시 자료와 집계는 [metrics.json](../scripts/context-resolution-evaluation/runs/metrics.json), [comparison.tar.gz](../scripts/context-resolution-evaluation/runs/comparison.tar.gz)에 보관한다.

## 남은 오판과 해석 한계

- 우선 확인할 것은 대상이 모호한 질문을 통과시키는 사례다. `ambiguous-04`, `ambiguous-08`, `ambiguous-10`과 추가 검사의 `fresh-ambiguous-2`, `fresh-ambiguous-3`에서 대상이 없거나 여러 개인데 모델이 독립 또는 문맥 의존으로 판정했다.
- `transition-07`, `transition-09`, `transition-10`은 필요한 업무 또는 최신 정정 발언을 모두 선택하지 못했다. 코드의 ID 검증만으로 의미상 필요한 발언의 누락까지 잡을 수는 없다.
- `independent-08`, `independent-10`, `transition-01`, `same_sentence-06`, `fresh-transition-1`은 독립 질문을 확인 안내로 처리했다. `followup-05`는 이력에서 대상을 찾지 못했다.
- `same_sentence-02-compact`는 출처 없는 문맥 의존 판정을 반환해 출력 검증에서 거부했다. `transition-07-compact`는 원래 질문과 다른 판정을 반환했다. 공백에 따른 호출 경로는 같지만 모델 판정까지 완전히 같지는 않다.
- 후보 표현에 해당하지 않는 생략 발언은 기본 모드에서 놓칠 수 있다. `LLM_ALL`은 후보 검사만 생략하는 옵션이며 모델 오판을 해결하는 옵션은 아니다.
- 이 수치는 문맥 관계와 원문 출처 연결의 검사다. 검색 정답 포함률, 최종 답변 정확도나 환각률이 아니다. 반복과 공백 변형을 독립 고객 질문 수로 세거나 전체 서비스 정확도로 확대 해석하지 않는다.

상세 실패 ID, 기대 관계와 실제 관계, 필요한 출처와 선택된 출처는 `metrics.json`의 `failures`에서 대조할 수 있다. 모델 요청에는 평가 라벨을 전달하지 않는다.

## 재실행

실제 모델 호출은 `ContextResolutionLiveEvaluationTest`가 담당한다. Python은 DB 스냅샷 복사와 저장된 원시 결과 집계만 수행한다. 라이브 검사는 `RUN_TELME139_LIVE=true`일 때만 실행하며 대상 DB 이름이 `telme_139_`로 시작하는지 검사한다.

```powershell
$env:POSTGRES_PORT = '5440'
$env:POSTGRES_DB = 'telme_139_new_eval'
$env:SPRING_FLYWAY_LOCATIONS = 'classpath:db/migration'
$env:RUN_TELME139_LIVE = 'true'
# DB를 먼저 생성하고 Flyway로 스키마만 준비한다.
docker exec telme-flow-postgres createdb -U telme $env:POSTGRES_DB
$env:TELME139_INIT_ONLY = 'true'
./gradlew.bat test --tests '*ContextResolutionLiveEvaluationTest' --rerun-tasks
Remove-Item Env:TELME139_INIT_ONLY
python -X utf8 scripts/context-resolution-evaluation/prepare_database.py --source telme_132_pr120_recheck_20261008 --target $env:POSTGRES_DB
$env:TELME139_RUN = 'new-run'
./gradlew.bat test --tests '*ContextResolutionLiveEvaluationTest' --rerun-tasks
# 추가 20건은 별도 실행 디렉터리에 기록한다.
$env:TELME139_RUN = 'new-confirmation'
$env:TELME139_FIXTURE_KEY = 'confirmationCases'
./gradlew.bat test --tests '*ContextResolutionLiveEvaluationTest' --rerun-tasks
```

스냅샷 원본 DB는 FAQ와 임베딩이 있는 로컬 DB로 바꿀 수 있다. 이미 사용한 실행 이름을 재사용하면 원시 행이 추가되므로 새 이름을 사용한다. `TELME139_SPLIT=development,regression`으로 개발군만 실행할 수 있다. 기본 80회는 해당 변수를 설정하지 않는다.

`summarize.py --baseline <이전 실행 디렉터리> --improved <현재 실행 디렉터리> --confirmation <추가 실행 디렉터리>`로 같은 입력인지 확인하고 집계한다. 압축 자료는 baseline, improved, confirmation, regressions와 개발 자료로 구분하며 입력, 코드와 FAQ 스냅샷의 해시는 압축 안의 `manifest.json`과 `metrics.json`에 기록한다.
