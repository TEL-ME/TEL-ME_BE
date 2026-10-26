# TELME-103 채팅 매장 검색 연결 계약

## 담당 경계

- TELME-103: 명시 지역 우선 분기, GPS 기반 최근접 검색, 위치 되묻기 판단, 공통 응답 변환,
  메시지 저장, 완료 이벤트 및 이력 응답.
- 명시 지역 담당: `NamedLocationStoreSearchPort` 구현. 위치 변환·지역/주변 조회·캐싱만 수행하고,
  채팅 메시지 저장이나 SSE 완료 이벤트는 수행하지 않는다.
- 명시 지역 구현이 없으면 검색 실패 안내로 완료한다. 지정 지역을 무시하고 GPS 검색으로 대체하지 않는다.

## 요청과 검색 기준

- `ChatMessageSendRequest`: `content`, 선택 필드 `latitude`, `longitude`.
- 좌표는 둘 다 없거나 둘 다 있어야 한다. 제공 시 유한한 숫자, 위도 -90~90, 경도 -180~180을 검증한다.
- 확인된 `location`이 있으면 명시 지역 경로를 호출한다. 없고 좌표가 있으면 GPS 경로를 호출한다.
- 실제 GPS가 있으면 지역 되묻기를 생략한다. 위치 권한 허용만으로는 생략하지 않는다.
- 이전 지역 되묻기에 GPS로 답하면 조건 상태 `COORDINATES`로 해결한다. 가상의 지역 문자열이나 GPS 원본을
  상담 조건에 저장하지 않는다. 해당 상태는 확인된 문자열 조건 변환에서 제외한다.
- GPS 검색은 기존 `StoreSearchProperties` 기본값(현재 최대 5곳·10km)을 재사용한다.

## 명시 지역 구현 방법

`NamedLocationStoreSearchPort`를 구현한 Spring bean 하나를 등록한다.

```java
SearchResult search(String location, Set<StoreServiceType.Code> serviceTypes);
```

- `SUCCESS`: 실제 검색 완료. 매장 목록이 비어 있으면 정상 0건이다. `context`는 필수다.
- `LOCATION_NOT_FOUND`: 위치 변환에서 검색 기준 위치를 찾지 못했다. 매장 0건과 구분한다.
- `FAILED`: 검색 장애. 결과 목록은 비어 있어야 한다.
- 카카오 장애의 `GeneralException`, DB의 `DataAccessException`도 공통 검색 경계에서 안내로 변환한다.
  프로그래밍 오류·직렬화 오류·채팅 저장 오류는 검색 실패로 숨기지 않는다.
- 거리 기반 결과는 `ChatStoreConverter.fromNearby`, 지역 결과는 `fromRegion`을 사용한다.
- 명시 지역 경로도 조회 개수와 적용 업무 조건을 제한해야 한다. 공통 경로는 임의의 업무 코드를
  무시해서 필터를 해제하지 않는다.

## 공통 이력 응답

기존 `storeResults` 배열을 유지한다. 각 항목은 다음 필드로 변환한다.

| 필드 | 의미 |
| --- | --- |
| `storeId`, `name`, `address`, `phone` | 매장 기본 정보 |
| `latitude`, `longitude` | 매장 좌표. 사용자 GPS나 검색 출발점이 아니다 |
| `distanceMeters` | 검색 기준점부터의 직선거리. 지역 전체 검색이면 `null` |

`storeSearchContext`를 메시지 단위로 추가한다.

| 필드 | 의미 |
| --- | --- |
| `type` | `CURRENT_LOCATION`, `REGION`, `ADDRESS`, `PLACE` |
| `label` | 결과 제목에 사용할 검색 기준 이름. GPS는 `현재 위치` |
| `radiusMeters` | 실제 적용 반경. 지역 전체 검색은 `null` |

- 사용자 GPS 원본은 검색 명령으로만 전달하고 검색 기준 이력에 저장하지 않는다.
- 이전 메시지는 `storeSearchContext=null`이어도 정상 조회된다.
- 직선거리와 T map 이동거리의 출발점은 다를 수 있다. 프론트는 각 기준을 명확히 표시한다.
- 결과 0건과 실패 안내도 검색 기준이 있으면 함께 저장한다.
- 기본 반경과 상한이 모두 10km라 기본 검색 0건에 반경 확대를 약속하지 않는다.
  다른 지역 또는 적용 중인 업무 조건 변경을 안내한다.

## 완료 이벤트와 프론트 확인

1. 최종 답변·매장 결과·검색 기준과 완료 상태를 DB 트랜잭션에 저장한다.
2. 트랜잭션 완료 후 기존 SSE `complete`를 보낸다. 카드 데이터는 이 이벤트에 추가하지 않는다.
3. 프론트는 메시지 이력을 재조회하고 `storeResults`, `storeSearchContext`를 표시한다.

2026-10-06 원격 브랜치를 fetch하고 코드로 확인했다. 브라우저 실동작 검증은 수행하지 않았다.

| FE 원격 브랜치 | 확인 결과 |
| --- | --- |
| `origin/develop`, `origin/main` | 해당 채팅 구현 없음 |
| `origin/feat/TELME-1-project-mvp-1` | complete 후 메시지 쿼리 갱신 및 매장 결과 표시 구현 |
| `origin/feat/TELME-2-stores-map` | 같은 흐름 구현. 확인 당시 HEAD `5e50fcc` |
| `origin/feat/TELME-3-auth-me-account-link` | 같은 흐름 구현 |
| `origin/test/ai-chat-ui` | 별도 테스트 UI. 운영 채팅 연결의 근거로 사용하지 않음 |

핵심 위치(`feat/TELME-2-stores-map` 기준):

- `src/api/chat.ts:75`: complete 수신 후 연결 종료와 `onComplete` 호출.
- `src/features/chat/chatRunStore.ts:74`: `refreshMessages(sessionId)` 호출.
- `src/features/chat/components/MessageList.tsx:152`: `storeResults` 표시.
- `src/features/chat/components/StoreResults.tsx:46`: 거리 있을 때만 표시.
- `src/api/chat.ts:24`: 현재 요청은 content만 보낸다. GPS 전달은 프론트 후속 수정이 필요하다.
- `src/api/types.ts:40`: `storeSearchContext` 타입과 검색 기준 제목 표시도 후속 수정이 필요하다.

## 리뷰 순서와 확인 사항

1. `NamedLocationStoreSearchPort`, `ChatStoreResponse`, `ChatStoreSearchContextResponse`: 팀 연결 계약.
2. `ChatMessageSendRequest` → `ChatSessionService` → `ChatProcessingCommand`: 좌표 검증과 전달.
3. `ConsultTurnAnalysisAdapter` → `DialogueService`: GPS 제공 시 되묻기와 기존 대기 조건 처리.
4. `ChatStoreAnswerProvider` → `ChatStoreConverter`: 명시 지역 우선·조회·0건·실패 처리.
5. `ChatAnswer` → `ChatExecutionService` → `ChatMessageConverter`: 저장과 이력 복원.
6. `V22__add_chat_store_search_context.sql`: nullable JSONB 열 추가. 배포 전 마이그레이션 순서를 확인한다.

직접 확인 필요: FE 미병합 브랜치에서 GPS 요청·검색 기준 제목·카드 상세 이동을 연결한 후 화면 확인.
명시 지역 구현 등록 후 지역 0건/위치 불명/카카오 실패 시나리오 확인. 실제 외부 API 확인은 사용자가 수행한다.

운영 주의: 구버전 애플리케이션은 새로운 `COORDINATES` 조건 상태를 읽지 못한다.
배포 시 채팅 처리 노드의 버전을 맞추고 롤백 전에 해당 상태를 가진 진행 중 상담을 점검한다.

## 검증 결과

- Java 21 / `./gradlew build --offline`: 통과.
- 1,499 tests, 0 failures, 0 errors, 95 skipped. 실제 실행 1,404건.
- 실제 호출용 probe는 비활성화했다. 외부 서비스는 mock을 사용했고 DB 통합 테스트는 로컬 PostgreSQL에서 수행했다.
- HTTP 좌표 쌍·범위·유한 숫자 검증, GPS 우선순위, 업무 조건, 0건·실패·위치 불명 안내를 확인했다.
- 요청 전달부터 메시지 저장·이력 재조회, 기존 위치 되묻기를 GPS로 해결하는 상태 전환을 확인했다.
- Spring bean 등록 및 사용자 제공 NamedLocationStoreSearchPort가 기본 미연결 어댑터를 대체하는 것을 확인했다.
- 자체 리뷰에서 Spring AnswerProvider 중복 등록을 수정했고, raw 좌표가 파싱 오류 로그로 노출되지 않게 했다.
