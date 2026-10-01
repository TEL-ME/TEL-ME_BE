# TELME-104 되묻기 후속 입력·정정·상담 전환 검증

## 범위와 기준

TELME-98의 요금제 변경 상담과 기존 매장 후속 입력 경로를 재사용해 문맥 연결에서 확인한 오류만 보완한다. 신규 상담 유형, 멀티턴 구조, 복합 질문 지원, DB 상태·테이블, 모델·프롬프트·검색·Guard 규칙을 추가하거나 변경하지 않는다. 선택한 사례의 검증이며 전체 환각률이나 프론트 검증 결과가 아니다.

브랜치는 `fix/TELME-104-followup-context`이며, 최신 TELME-98 PR #87 HEAD `ada4bb0e0d07dbe1437cc39e9a7aa229a4c7b6d3`에서 TELME-104 변경만 분리했다. PR 비교 기준은 `feat/TELME-98-plan-change-clarification`이다. 기존 TELME-98 코드·문서·마이그레이션은 104 diff에 포함하지 않는다. 98의 요금제 상담 기능을 재사용하므로 기능상 선행 의존 관계는 유지한다. 98 병합 후에는 develop으로 비교 기준을 변경하고 최신 기준에서 CI를 다시 확인해야 한다.

확인 기준 develop은 `2a1397fa2e568b2ada264ae491ecb6b37f404617`다. #74·#80·#82·#83·#84는 병합됐고 #85·#87은 develop과 충돌이 남아 있다. 이 선행 충돌은 104의 비교 기준인 #87 HEAD와의 충돌과 구분한다. 104에서 선행 브랜치를 수정하거나 develop을 병합하지 않았다. 마이그레이션은 #87의 V17 피드백·V19 추적 파일을 그대로 사용하며 이번 변경에서 새 버전이나 SQL 수정을 추가하지 않는다. V18/V19 배포 순서와 운영 환경은 선행 작업의 별도 확인 사항이다.

CI의 pull_request 대상에 정확한 98 브랜치 이름을 추가해 이 별도 PR에서도 기존 build·마이그레이션 검사·테스트 게시가 실행되게 한다. develop/main 대상과 push 조건, 권한, 테스트 동작은 유지한다. 선행 PR CI 성공을 104 CI 성공으로 표현하지 않는다.

## 실제 활성 경로와 저장 위치

`ChatSessionController.sendMessage → ChatSessionService → ChatProcessingCommand/ChatProcessingDispatcher → ConsultChatProcessingService → FollowupContextService → QueryRoutingAnalysisProvider → QueryRoutingFollowupAnalysisProvider → QueryRoutingService.analyzeFollowUp → FollowupConditionConverter/FollowupSelectionValidator → ConsultTurnPreparationService/ConsultService → DialogueService → FaqSearchAnswerProvider/RagAnswerGenerator → 기존 AnswerGuard → 최종 저장 → SSE`를 확인했다.

| 단계 | 기존 구조·검증 |
| --- | --- |
| 접근·문맥 | `FollowupContextService`가 세션 소유자·OPEN 상태·해당 세션의 완료된 사용자 QUESTION을 확인하고 세션 잠금 안에서 후보를 조회 |
| 대기 질문 | `PendingClarificationFinder.findBefore`가 같은 세션의 이전 질문만 반환. 완료·취소 상담은 후보에서 제외. 후보를 최근 하나로 임의 축소하지 않음 |
| 모델·fallback | `QueryRoutingService.analyzeFollowUp`의 모델 후속 JSON과 `RuleBasedRoutingFallback`의 검증 가능한 발화를 대조 |
| 조건 검증 | 허용 키·값, 실제 발화의 근거, 현재 상담 ID와 대기 질문 연결을 확인. 모델의 키·값만으로 저장하지 않음 |
| 조건·상태 | `consult_requests`, `consult_conditions`에 상담별 값·상태·asked_message_id·answered_message_id 보존. `JdbcConsultStateStore`의 세션·버전·상태 검사와 트랜잭션 재사용 |
| 재개·취소 | 현재 상담 ID로 재개. 새 질문은 기존 대기 상담 취소 후 초기 라우팅. 새 상담에는 옛 조건을 복사하지 않음 |
| 답변 | 최종 검증 답변은 `chat_messages`에 저장하고 대화 기록 API로 조회. `executionId`, `outputMessageId`, `consultRequestId`로 연결 |
| 전달 | 생성 답변은 기존 `start → token → complete`. 직접 안내·되묻기는 기존 `complete` 메타데이터만 전송하며 내용은 기록 조회로 확인 |

활성 상담 처리의 기본 설정은 true다. 실제 모델 실험은 근거 적합성의 추가 모델 검사만 `CONSULT_LLM_ENABLED=false`로 두었다. 운영 배포 환경변수와 화면의 활성 경로는 확인하지 않았다.

## TELME-98 중복 범위

아래 상태는 TELME-104 추가 전의 TELME-98 준비본을 기준으로 한다. 이전 실제 모델 21개 대화와 추가 자동 테스트 2건은 서로 다른 실행이며 합산하지 않는다.

| 요청 범위 | TELME-98 검증 수준 | TELME-104 보강 |
| --- | --- | --- |
| A 짧은 응답 | 단독 네/아니요는 부분 검증. bare month·감사 동반 응답·새 요청 혼합 미검증 | 질문한 키만 갱신, 지난달이요, 네 고마워요, 대기 없는 응답, 혼합 새 요청 |
| B 두 조건 | 두 조건을 모두 보존하는 Chat 통합 추가 검증 완료. 추가 사례의 실제 모델 미검증 | 기존 테스트 재사용, 쉼표 변형과 실제 모델 검증 |
| C 정정·재사용 | 원문 조건 재사용, 원문보다 후속 정정 우선, 다른 키 정정 자동 검증 완료 | 쉼표 정정·미확인 정정과 다른 조건 동시 입력, 실제 모델 검증 |
| D 애매·모순 | 모름·인지/인지와 반복 종료 부분 검증 | 상반된 두 확정문, “것 같아요”, 변경 이력 불확실성을 임의 값으로 만들지 않음 |
| E 가정·타인 | 타인·가정만 있는 응답 부분 검증 | 본인과 친구가 한 발화에 섞인 경우의 본인 조건 보존 |
| F 모름·거절·보류 | 기존 정책과 자동·실제 모델 검증 완료 | 재사용. 미확인 한 키 + 명확한 다른 키 동시 처리 추가 |
| G 전환 | 새 주제의 취소·조건 분리 부분 검증 | 동의+새 질문, 취소 뒤 다음 네, 현재 질문 설명 요청 |
| H 경계 | 다른 세션, 소유권, 완료·취소, 버전 경합은 기존 DB 테스트로 검증 | 실제 활성 Chat의 복수 대기 후보 오류 처리, 완료 후 네/아니요 추가. 기존 접근 테스트 재사용 |

TELME-98의 상담 정책 테스트 47건과 Chat 통합 기존 30건을 그대로 재사용한다. 요금제의 서버 기준 안내 구성·Guard·저장 정책을 TELME-104에서 재구현하지 않는다.

## 기대 동작과 조건 정책

상담 목적은 개인별 요금제 변경 가능 여부이며 원문은 “제가 지금 요금제를 바꿀 수 있나요?”다. `J=joinedThisMonth`, `C=changedThisMonth`, `예/아니요=FILLED`로 표기한다. 일반 기준은 정책 FAQ의 가입월 제한과 월별 변경 횟수다. 이 두 기준만으로 전체 자격 충족을 확정하지 않는 TELME-98 정책을 유지한다.

| 기존 조건·대기 | 사용자 후속 발화 | 기대 분류 | 갱신·유지 | 상태·다음 동작 |
| --- | --- | --- | --- | --- |
| J 대기 | 네 / 네, 고마워요 | CONDITION_RESPONSE | J=예만 저장. C 추정 없음 | DONE, 확인된 가입월 제한 안내 |
| J=아니요, C 대기 | 아니요 | CONDITION_RESPONSE | C=아니요, J 유지 | DONE, 두 기준에 한정한 안내 |
| J 대기 | 지난달이요 | CONDITION_RESPONSE | J=아니요 | WAITING_CONDITION, C만 질문 |
| 대기 없음·완료 상담 | 네 / 아니요 | 초기 라우팅 | 옛 상담 조건 변경 없음 | 옛 DONE 유지. 기존 범위 밖 안내 가능 |
| J 대기 | 네, 유심 비용도 알려주세요 | NEW_QUESTION | 앞의 네를 J에 저장하지 않음 | 옛 상담 CANCELLED, 기존 단일 질문 정책에 따라 새 FAQ/복합 요청 안내 |
| J 대기 | 지난달에 가입했고, 이번 달에는 아직 요금제를 안 바꿨어요 | CONDITION_RESPONSE | J·C 모두 아니요 | DONE, 추가 질문 없음 |
| J=아니요, C 대기 | 아니, 이번 달에 가입했어요 | CONDITION_RESPONSE·정정 | J=예로 갱신, C 값 없음 유지 | DONE, 다른 조건 질문 없이 확인된 제한 안내 |
| J 대기 | 이번 달 가입했어요. 아니, 지난달이에요 | CONDITION_RESPONSE·정정 | 명시적 최신 J=아니요 | C만 질문. 원문의 옛 값으로 덮어쓰지 않음 |
| J 대기 | 이번 달에 가입했어요. 지난달에 가입했어요 | 해석 불가 DEFERRED | 어느 값도 저장하지 않음 | 기존 질문 유지. 반복 실패 기준 적용 |
| J/C 대기 | 가입한 것 같아요 / 바꾼 것 같기도 하고 아닌 것 같기도 해요 | 해석 불가 DEFERRED | 값 추정 없음 | 질문 메시지 추가 없이 대기 |
| J 대기 | 이번 달인지 지난달인지 모르겠어요 | CONDITION_RESPONSE·미확인 | J=DECLINED, 값 없음 | DONE, 개인 판단 대신 조건별 기준 |
| J 대기 | 이번 달에 가입했다면 / 친구는 이번 달에 가입했어요 | 해석 불가 DEFERRED | 본인 J를 채우지 않음 | 기존 질문 유지·반복 기준 적용 |
| J 대기 | 저는 지난달에 가입했고 친구는 이번 달이에요 | CONDITION_RESPONSE | 본인 J=아니요만 저장 | C만 질문 |
| J/C 대기 | 잘 모르겠어요 / 알려주고 싶지 않아요 | CONDITION_RESPONSE | 현재 키 DECLINED, 값 없음 | 가능한 기준 안내로 DONE. 같은 질문 반복 없음 |
| J/C 대기 | 나중에요 | DEFERRED·명시적 보류 | 조건·질문 유지, 추정 없음 | WAITING_CONDITION, 출력 없음 |
| J=아니요, C 대기 | 가입한 달은 모르겠어요. 이번 달에는 아직 안 바꿨어요 | CONDITION_RESPONSE·미확인 정정 | 옛 J 값 제거·DECLINED, C=아니요 | DONE, 미확인 개인 판단 없음 |
| J/C 대기 | 유심 재발급 비용 알려주세요 | NEW_QUESTION | 옛 키에 저장하지 않음 | CANCELLED 후 새 상담. 다음 네도 옛 질문으로 소비하지 않음 |
| J 대기 | 가입한 달이 무슨 뜻이에요? | DEFERRED·설명 요청 보존 | 조건·질문 변경 없음 | 기존 대기 유지. 설명 답변 생성은 미지원 |
| 서로 다른 세션·다른 소유자 | 임의 후속 값 | 기존 오류/별도 세션 처리 | 해당 세션·소유 상담만 접근 | 잘못된 대상의 상담·조건 변경 없음 |
| 완료·취소 상담 | 임의 후속 값 | 후보에서 제외/상태 오류 | 닫힌 상담 갱신 없음 | 새 질문 경로 또는 기존 오류 |
| 같은 세션 복수 대기 후보 | 임의 후속 값 | STATE_CONFLICT | 어느 후보에도 저장하지 않음 | 실행 FAILED·기존 오류 안내, 두 상담은 WAITING 유지 |

모름과 거절은 의미가 다르지만 기존 저장 enum인 `DECLINED`/값 없음으로 표현한다. 새 상태를 도입하지 않는다. 명확한 정정은 최신 값으로 교체하고 명확한 미확인 정정은 옛 값도 제거한다. 불확실한 충돌만으로 기존 확정 값을 바꾸지 않으며, 현재 대기 조건은 계속 대기한다. 기존 가입월 아니요와 “이번 달에 가입한 것 같아요”가 충돌하는 추가 활성 Chat 통합에서도 옛 값·현재 질문이 유지되고 뒤의 명확한 변경 이력으로 완료되는 것을 확인한다. 명확한 정정·새 질문·명시적 보류는 반복 실패가 아니다.

같은 대기 조건의 해석 불가 응답 **2회**면 값을 만들지 않고 기준 안내로 종료하는 TELME-98 정책을 유지한다. 대기 질문 메시지를 추가 생성하지 않는다. 이번 설명 요청은 새 주제나 실패 횟수로 소비하지 않는다. 설명 생성 기능은 별도 정책 확인·후속 작업이 필요하다.

## 재현한 오류와 수정

추가 단위 검증 45건 중 수정 전 11건이 실패했다. 짧은 month·감사 응답 3건, 불확실한 긍정 1건, 모순 2건, 미확인 정정+다른 조건 누락, 설명 요청의 새 상담 오분류, 복수 후보의 안전한 거절 누락, 매장 위치·업무/거절의 모델 추정 저장을 확인했다. 복수 후보의 최초 재현은 단위 수준의 안전한 선택 검사 누락이며, 수정 전 실사용 DB가 잘못 갱신됐다고 단정하지 않는다.

| 수정 파일 | 원인과 최소 보완 |
| --- | --- |
| `PlanChangeConditions.java` | bare month·감사 동반 동의 미인식, 쉼표 정정 손실, 불확실·모순 발화 임의 선택. 단일 대기 키의 짧은 답만 허용하고 명시적 정정과 불명확한 모순을 구분. 설명 요청 보존 |
| `RuleBasedRoutingFallback.java` | 명확한 조건이 먼저 반환되면 같은 발화의 미확인 키 누락. 검증된 FILLED·DECLINED를 함께 전달. 설명 요청은 기존 DEFERRED 사용 |
| `QueryRoutingService.java` | 검증 결과가 빈 경우 모델 오분류가 남음. 요금제 후속 결과를 실제 발화 검증값과 항상 대조. 매장 location·serviceType·DECLINED도 사용자 발화 근거 없으면 제거 |
| `QueryRoutingFollowupAnalysisProvider.java` | 복수 후보에서 라우터가 첫 상담을 선택할 수 있음. 단일 후보·같은 consultRequestId인지 확인하고 불명확한 대상은 기존 STATE_CONFLICT 사용 |

라우팅 담당과 연결되는 변경은 최초 intent 분류나 복합 질문 정책이 아니라 후속 결과를 저장하기 전의 유효성 검증이다. 기존 모델 JSON·조건 enum·DTO·상태 계약을 유지한다. `FollowupConditionConverter`, `FollowupSelectionValidator`, `ConsultService`, `JdbcConsultStateStore`의 선행 검증과 저장을 재사용했다. 사용자에게 없는 매장 조건을 막는 회귀를 포함하며 매장 정상 위치 입력은 유지했다.

새 자동 테스트는 57건(추출·조건 상태 31, 라우팅 2, 후속 provider 1, 활성 Chat 통합 23)이다. 기존 통합 테스트 파일에 추가했으며 동일 흐름의 두 번째 구현은 없다.

### 추가 검토에서 발견한 주체·미확인 정정 오류

기존 테스트 이후 활성 Chat·실제 DB·대체 모델로 다음 3개 반례를 재현했다. 확정값 추출은 타인·가정을 제외했지만 미확인 키 추출은 주체와 최신 정정 순서를 확인하지 않았다. 그 결과 `DECLINED`가 확정값보다 우선하고 상담이 조기 종료됐다.

| 후속 발화 | 수정 전 재현 | 수정 후 기대·검증 |
| --- | --- | --- |
| 친구는 가입한 달을 모르겠어요 | 본인 가입월 DECLINED, DONE | 본인 값 저장 없음, 기존 가입월 질문 유지 |
| 저는 지난달에 가입했고 친구는 이번 달 변경 이력을 모르겠어요 | 본인 변경 이력 DECLINED, DONE | 본인 가입월 아니요만 저장, 본인 변경 이력 질문 |
| 가입한 달은 모르겠어요. 아니, 지난달에 가입했어요 | 최신 가입월 정정 무시, DECLINED, DONE | 가입월 아니요 저장, 변경 이력 질문 |

`PlanChangeConditions`의 확정값과 미확인 키를 동일한 절 순서·주체 검증으로 해석하고 `RuleBasedRoutingFallback`에서 그 결과를 사용한다. 타인·가정의 미확인은 본인 상태로 저장하지 않는다. 명확한 최신 값은 같은 키의 앞선 미확인을 해제하고, 반대 순서의 명확한 미확인 정정은 옛 값을 제거한다. 반복 종료 횟수·거절·보류·Guard·생성 프롬프트는 변경하지 않았다. 추가 단위 10건과 활성 Chat 통합 5건으로 위 반례, 타인/가정 정보, 짧은 정정 및 최종 답변 재개를 검증했다.

## 검증 결과

### 자동 테스트

최신 98 HEAD `ada4bb0`에서 104만 분리한 브랜치에서 전용 PostgreSQL/PostGIS/pgvector, `TELME_DB_TESTS=true`, JDK 21로 전체 `./gradlew build`를 다시 실행했다. DB 옵트인 테스트를 포함해 **총 1,458건, 통과 1,456건, 실패·오류 0건, 스킵 2건**이다. 명시적 모델 평가용 `RoutingEvaluationProbe`, `AnswerQualityBaselineProbe`만 스킵했다. 결과와 전체 실행 소스 지문은 공유 자료의 `separated_branch_validation`에 기록했다. 이전 작업본에서 추가 보완 후 선택 회귀 211건과 전체 1,458건도 통과했으며 실행별 결과를 합산하지 않는다. 과거 전체 실행 1,443건과 선택 실행 230건은 추가 보완 이전 결과다.

| 테스트 | 이번 통과 | 성격 |
| --- | ---: | --- |
| PlanChangeFollowupContextTest | 31 | 새 단위 사례 |
| QueryRoutingServiceFollowUpTest | 19 | 모델 JSON·규칙 검증 |
| QueryRoutingFollowupAnalysisProviderTest | 5 | 대상 연결 |
| PlanChangeChatIntegrationTest | 53 | 활성 처리기 + 실제 DB/조회, 모델·검색 대체 |
| PlanChangeClarificationPolicyTest | 47 | TELME-98 재사용 |
| FollowupConditionConverterTest | 5 | 조건 변환 재사용 |
| FollowupSelectionValidatorTest | 6 | 대상·질문·키 검증 재사용 |
| FollowupContextDatabaseTest | 29 | 소유권·메시지·세션 등 기반 DB 회귀 포함 |
| PendingClarificationFinderDatabaseTest | 27 | 세션·대기 후보 등 기반 DB 회귀 포함 |
| LocalConsultDatabaseTest | 24 | 저장·상태·버전·동시성 |
| AnswerGuardUserEvidenceTest | 67 | Guard 유지 |
| AnswerGuardTest | 38 | Guard 유지 |
| RagAnswerGeneratorTest | 26 | 생성 유지 |
| ConsultGuardedAnswerDeliveryIntegrationTest | 35 | 최종 전송 유지 |
| ConsultChatPersistenceIntegrationTest | 21 | 저장·조회 유지 |

위 일부 DB 클래스의 집계에는 상속된 공통 회귀가 포함된다. 표는 전체 집계 일부이며 별도 합산하지 않는다. 잘못된 세션·소유자·질문, 닫힌 상담·오래된 버전의 쓰기 거절은 기존 DB 테스트로 재확인했다. 복수 후보는 활성 처리기 통합에서도 양쪽 조건이 바뀌지 않는 것을 확인했다. 이 경우 실행 실패와 오류 메시지는 기존 계약대로 기록된다.

재현 시 Spring datasource와 직접 JDBC DB 테스트의 `POSTGRES_HOST/PORT/DB/USER/PASSWORD`가 같은 전용 시험 DB를 가리켜야 한다. 실제 모델 실험 DB와 자동 테스트 DB는 분리한다. 최초 추가 테스트의 mock 설정, 실제 스키마와 다른 fixture 컬럼, 메타데이터 전송을 token으로 가정한 테스트 입력을 고쳐 재실행했다. 이들은 테스트 설정 문제이며 제품 DB 장애나 기능 변경으로 보고하지 않는다.

### 실제 로컬 모델

추가 보완 이전 EXAONE `exaone3.5:7.8b`로 **21개 합성 대화·60개 입력 턴**을 실제 HTTP/SSE·검색·후속 모델·생성 모델·시험 DB 경로에서 실행했다. 아래 21개 대화와 전후 6개 비교는 당시 소스 지문의 과거 결과이며 이번 추가 보완 후 전체 재실행 결과가 아니다. 모델 digest는 `c7c4e3d1ca22fe9225f18b35eb719f67e2ca96a42e7fd17294a45b83ba8fbf03`다. 생성은 temperature 0/num_ctx 8192/num_predict 1024, 후속 라우팅은 0/8192/300이다. 생성 프롬프트 `rag-answer-v3-plan-v2`와 후속 system prompt 해시를 전후 대조했다. 유료 모델·Judge 호출은 없다.

21개 대화 모두 사전 정의한 조건·상담 상태·질문 수와 일치했다. 중간에 값 추정이 없어야 하는 7개 입력도 저장·질문 변화가 없었다. 저장된 출력 **53건**의 DB·기록 API·trace 최종 내용이 일치했고, 생성 답변 **22건**은 SSE 최종 token 1개가 같은 문자열이었다. 나머지 **31건**은 기존 직접 안내·되묻기의 `complete` 메타데이터만 보내는 경로로 outputMessageId와 기록 내용을 대조했다. 이 31건에서 답변 문자열을 token으로 전송했다고 주장하지 않는다.

수정 전 6개 대화를 같은 질문·응답·초기 검색 FAQ·모델·옵션·프롬프트로 실행해 비교했다. 최종 분기가 수정된 경우 생성 유무·조건 입력·최종 검색 시점은 달라질 수 있으므로 Guard 단독 효과 비교가 아니다.

| 사례 | 수정 전 | 수정 후 |
| --- | --- | --- |
| SHORT-MONTH | 지난달이요 누락, 이어지는 아니요가 가입월에 소비되고 변경 이력 질문 잔류 | 가입월 아니요 → 변경 이력 아니요 → DONE |
| THANKS | 네, 고마워요 이후 값 없음·대기 | 가입월 예, 제한 안내 DONE |
| CONTRADICTION | 상반된 가입월에서 지난달 선택 | 값 없이 대기, 뒤의 모름은 일반 기준 안내 DONE |
| UNCERTAIN | “것 같아요”에서 가입월 예 추정·즉시 종료 | 값 없이 대기, 모름 후 기준 안내 DONE |
| EXPLAIN | 설명 요청을 새 상담으로 전환, 이후 짧은 응답 연결 손실 | 원래 질문 유지, 지난달이요·아니요로 동일 상담 DONE |
| UNKNOWN-CORRECTION | 미확인 정정 누락·원래 가입월 아니요 유지 | 가입월 옛 값 제거, 변경 이력 아니요도 보존, 기준 안내 DONE |

실제 모델의 후속 원시 출력 37건과 검증 이후 DB 값·상태를 공유 자료에 함께 남겼다. 예를 들어 모델이 모순 가입월에 `FILLED/예`를 제시해도 최종 저장은 값 없는 대기다. 런타임 조회 API는 최종 `FollowUpRouteResponse.method/disposition`을 제공하지 않으므로 이 API에서 RULE/LLM 최종 분류를 직접 관찰했다고 표현하지 않는다. 최종 method/fallback 계약은 별도 단위 테스트에서 확인한다.

### 추가 보완 후 실제 모델 재검증

브랜치 분리 이전 추가 보완 작업본에서 반례 3개를 동일한 로컬 EXAONE digest, 생성·후속 설정과 기존 시험 FAQ로 **3개 대화·10개 입력 턴** 재실행했다. 위 표의 3개 후속 처리 모두 기대 상태와 일치했다. 이후 본인 가입월·변경 이력 아니요를 확인하고 같은 상담에서 최종 답변까지 DONE으로 종료했다. 필요한 조건 질문은 각 2개이며 타인 발화에서 새 질문이나 답변을 추가하지 않았다. 이번 분리 후 104의 4개 기능 변경 파일은 해당 실험 소스와 동일하지만 선행 마이그레이션 기준은 V19로 달라졌으므로 분리 브랜치의 실제 모델 재실행으로 표현하지 않는다. 분리 후 전체 DB 회귀는 위 새 실행으로 확인했다.

저장 출력 9건에서 DB·기록 API·trace 최종 내용이 일치했다. 최종 생성 답변 3건은 `start → token → complete`로 한 번 전송되고, 직접 되묻기 6건은 기존 complete 메타데이터/조회 계약을 유지했다. 타인의 모름 입력 1건은 출력 없이 complete만 전달했다. 원시 모델 요청은 최초 라우팅 0.1/8192/500, 후속 0/8192/300, 생성 0/8192/1024임을 확인했다. 실제 브라우저·유료 Judge는 실행하지 않았다.

수정 전 추가 반례는 모델·검색 대체 통합 테스트로 재현했고, 이번 실제 모델 실행은 수정 후 검증이다. 실제 모델의 동일 조건 전후 비교 실험으로 표현하지 않는다. 최종 안내는 FAQ의 가입월·월 1회 두 기준에 한정된 가능 여부이며 전체 요금제 변경 가능성을 확정한 결과가 아니다.

## 재현 자료와 남은 확인

[공유 검증 자료](evidence/telme104-followup-context-20261001.json)에 기대 사례, 후속 모델 원시 JSON, 실행·상담 연결, 최종 조건·답변, 전후 FAQ와 소스 지문을 포함했다. 근거·정책은 선행 [TELME-98 검증](TELME-98-CLARIFICATION-FIXES.md)과 [근거 자료](evidence/telme98-clarification-fixes-20261001.json)를 재사용한다.

전체 합성 실행 원본은 별도 `local-experiments/2026-10-01-followup-context/`의 cases.json, before/, after/, db-messages.json, audit.py에 보존한다. 모델 연결과 저장·조회까지 통과한 로컬 검증용 자료이며 운영 로그에 원문을 추가하지 않았다. PR만 내려받으면 전체 원본은 생기지 않으므로 원본 SSE·프록시 요청 비교가 필요한 리뷰에는 작성자에게 해당 실행을 요청한다. 공유 JSON으로 핵심 검증은 확인할 수 있다.

- 설명 요청을 조건 입력이나 새 상담으로 소비하지 않지만 설명 문장을 생성하지는 않는다. 용재님과 설명 응답 지원 여부를 별도 결정해야 한다.
- 혼합 동의·새 요청은 기존 단일 상담 전환 정책을 유지한다. 동시 처리 지원은 #75와 별도 합의하며 이번에는 추가하지 않는다.
- 복수 후보가 실제로 필요한 복합 상담에서 명시적 선택을 지원할지는 #75와 연결해 후속 확인한다. 현재 단일 처리기는 STATE_CONFLICT로 안전하게 거절한다.
- 소유권·다른 세션·완료/취소·복수 후보는 DB/대체 모델 통합 테스트로 검증했고 실제 모델의 별도 경계 대화 실험은 하지 않았다.
- 실제 브라우저·새로고침·배포 환경은 미검증이다. 프론트에서는 출력 없는 complete의 로딩 종료, 기존 질문 유지, metadata output ID로 기록 조회, 최종 답변 한 번 표시, 전환 뒤 옛 질문 미사용, 새로고침 뒤 동일 이력·상태를 확인해야 한다.
- CI는 `develop`·`main`과 이번 선행 비교 기준인 `feat/TELME-98-plan-change-clarification` 대상 pull_request에서 실행된다. 별도 PR의 최신 HEAD CI를 확인하며, 이후 98 병합·기준 전환 후의 CI는 새 실행으로 구분한다.
- 반복 횟수·상태 정책은 변경하지 않았다. 설명 요청 처리·복수 후보 안전 거절을 팀 정책으로 확정하면 decisions.md 반영 여부를 검토한다.
