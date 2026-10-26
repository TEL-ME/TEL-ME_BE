# 독립 기능

채팅에서 연결되거나 별도 API로 제공되는 기능이다. 복합 요청의 분해와 조합은 [파이프라인](../01-pipeline/README.md), 전체 장애 대응은 [운영](../04-operations/README.md)에서 설명한다.

## 매장

- [TELME-103 채팅 매장 검색 연결 계약](store/chat-store-search-contract.md)
- [최근접 매장 거리 계산 방식 결정 및 쿼리 실측 (TELME-68)](store/store-nearest-search.md)
- [최근접 매장 검색 운영·확장 가이드 (TELME-68)](store/store-search-operations.md)

위치·조건 확인 → 매장 검색 → 상세 확인 → 길찾기로 이어지는 기능이다. 현재 이 묶음에는 길찾기 전용 명세가 없으므로, 현재 계약은 `StoreController`, `StoreRouteService`, DTO와 테스트를 확인해야 한다. 기존 최근접 검색 실측 문서가 길찾기까지 설명한다고 해석하지 않는다.

- [매장 API 코드](../../src/main/java/com/telme/store/controller/StoreController.java)
- [길찾기 서비스](../../src/main/java/com/telme/store/service/StoreRouteService.java)
- [매장 데이터 검증 도구](../../scripts/check_stores.py)

## 요금제

- [요금제 목록 조건과 추천 대상을 분류 모델로 읽기](plan/plan-conditions-model.md)
- [요금제 질문 분류 모델 비교 (엑사원 vs gpt-oss)](plan/plan-question-model.md)

## 피드백

- [피드백 API](feedback/feedback-api.md)

실행 도구: [요금제 질문 평가](../../scripts/plan-question-evaluation/), [FAQ·매장 복합 평가](../../scripts/compound-store-evaluation/). 실행 전 각 문서의 전제와 [스크립트 안내](../../scripts/README.md)를 확인한다.
