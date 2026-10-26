# 운영

DB 변경, 모델 전환 설정, 서빙 선택, 장애·관측 검증을 모은다. 각 문서의 작성 시점과 확인 범위를 따르며, 검증 기록을 현재 배포 완료 증거로 자동 해석하지 않는다.

- [질문·검색 근거·최종 답변 추적](chat-execution-trace-verification.md)
- [TELME-94: 모델 오류·타임아웃·검색 실패 폴백 검증 및 보완](chat-failure-fallback-verification.md)
- [Flyway 마이그레이션 가이드](flyway.md)
- [답변 경로 LLM 호출의 gpt-oss 전환 현황](gpt-oss-switch.md)
- [EXAONE·임베딩 서빙 비교 (Ollama vs vLLM + TEI)](serving-comparison.md)

## 관련 문서와 도구

- [매장 검색 운영·확장](../02-features/store/store-search-operations.md): 매장 기능에 한정된 인덱스·확장 설치 안내.
- [답변 저장·전달](../01-pipeline/README.md#08-저장과-전달): 정상 처리의 저장·SSE 경계.
- [서빙 비교 도구](../../scripts/serving-comparison/README.md).
- [전체 모델 전환 평가 도구](../../scripts/bedrock-switch-evaluation/).

이미 적용된 Flyway SQL의 문서 경로 주석은 변경하지 않는다. 체크섬과 배포 이력은 문서 네이밍 통일보다 우선한다.
