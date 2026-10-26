# 채팅 파이프라인

이 목차의 번호는 읽는 순서다. 모든 요청이 FAQ 검색·생성을 차례로 거치는 것은 아니다. 기본 대화는 고정 응답으로 처리될 수 있고, 라우팅 뒤에는 FAQ·매장·요금제 경로로 분기한다. 조건이 부족하면 되묻고 후속 입력을 받아 이어간다.

```text
입력 검사 → 문맥·라우팅 → FAQ 검색 → 근거 판정 → 조건 확인·답변 생성
                    └→ 매장·요금제 처리 ↗
                              ↓
                답변 검사 → 최종 저장·전달
```

위 흐름은 탐색용 요약이다. 분기·반복의 상세 조건과 검증 범위는 개별 문서를 따른다. 과거 실험의 설정을 현재 운영값으로 해석하지 않는다.

## 여러 단계에 걸친 복합 상담

- [FAQ 복합 질문과 비교 질문 처리](compound-comparison-quality.md)
- [FAQ와 매장 복합 상담](compound-faq-store.md)

## 01 입력 검사

- [채팅 입력 제한 상태 조회](01-input-guard/chat-input-guard-status.md)
- [채팅 입력 검사](01-input-guard/chat-input-guard.md)

## 02 문맥과 라우팅

- [기본 대화 및 무러바라 페르소나](02-context-routing/basic-conversation.md)
- [문맥 판정과 이전 발언 연결](02-context-routing/context-resolution.md)
- [멀티턴 문맥과 상담 요약](02-context-routing/multiturn-context-and-summary.md)
- [라우팅 LLM 호출별 EXAONE·gpt-oss 비교](02-context-routing/routing-call-comparison.md)
- [라우팅 평가 기준](02-context-routing/routing-evaluation.md)
- [TELME-132 질문 분해와 검색어 의미 보존](02-context-routing/routing-spacing-and-requests.md)

## 03 FAQ 검색

- [경계 실험용 후보 1차 측정 — 2026-10-01](03-retrieval/boundary-experiment-20261001.md)
- [이중 벡터와 리랭커 비교 (TELME-84)](03-retrieval/dual-vector-vs-reranker.md)
- [평가셋 보강 및 이중 벡터 사전 검증](03-retrieval/eval-set-supplement.md)
- [평가셋 정답 라벨 보완 내역: 원래 정답에 추가한 FAQ 전체 목록](03-retrieval/multigold-label-additions.md)
- [FAQ 다중 정답 라벨링과 리랭커 실험 요약](03-retrieval/rerank-multigold-experiment.md)
- [검색 실패 원인 분류 및 분석 (TELME-64)](03-retrieval/search-failure-analysis.md)
- [검색 품질 측정 & 임계값 캘리브레이션 (1차, TELME-55)](03-retrieval/search-quality-calibration.md)
- [검색 임계값 0.6과 답변 품질 개선 검토](03-retrieval/search-threshold-060-and-answer-quality.md)
- [FAQ 검색 품질 튜닝 보고서](03-retrieval/search-tuning.md)
- [단일 FAQ 검색어 보존과 재검색](03-retrieval/single-faq-search.md)
- [top-k별 정답률·지연시간 측정 (TELME-59)](03-retrieval/topk-latency.md)

## 04 근거 판정

- [근거 검증 두 호출의 모델 비교 (엑사원 vs gpt-oss)](04-evidence-judgment/evidence-resolver-comparison.md)
- [회색 구간 판정 위로 넓히기 측정 — 2026-10-13 (TELME-156), 10-14 구현 전·후 확인, 10-15 비교 질문·판정 안정화 추가 (TELME-160)](04-evidence-judgment/gray-zone-upper-band.md)
- [근거 판정기 모델·서빙 방식별 실측 비교](04-evidence-judgment/judge-serving-comparison.md)

## 05 조건 확인과 되묻기

- [되묻기 조건 뽑기 모델 비교 — 불필요한 되묻기 줄이기 (TELME-176)](05-clarification/clarification-extraction-model.md)
- [되묻기 조건 뽑기](05-clarification/condition-extraction.md)

## 06 답변 생성

- [답변 생성 모델 비교 (엑사원 vs gpt-oss)](06-answer-generation/answer-generation-comparison.md)
- [답변 생성 EXAONE·gpt-oss 3회 반복 평가 (TELME-186)](06-answer-generation/answer-generation-exaone-repeat.md)
- [RAG 답변 품질 측정과 개선 보고서](06-answer-generation/answer-quality.md)
- [후속 질문 추천 설계와 오프라인 검증](06-answer-generation/followup-recommendation.md)
- [답변 생성 프롬프트 규칙과 비교 실험](06-answer-generation/rag-answer-prompt-experiments.md)

## 07 답변 검사

- [TELME-190: 삭제 대상 비교 문장의 근거 판정](07-answer-validation/answer-guard-recovery.md)
- [챗봇 출력 검사](07-answer-validation/chat-output-guard.md)

## 08 저장과 전달

- [TELME-92: Guard 검증·저장 이후 답변 전달](08-delivery/rag-answer-delivery.md)
- [TELME-93: Guard 최종 답변 저장·조회 일치 및 새로고침 유지 검증](08-delivery/rag-answer-persistence-verification.md)

## 관련 실행 도구

기존 실행 진입점·데이터 위치는 유지한다. 명령·환경·측정 조건은 각 보고서와 [스크립트 안내](../../scripts/README.md)에서 확인한다.

| 목적 | 도구·자료 |
| --- | --- |
| 문맥·라우팅 | [문맥 평가](../../scripts/context-resolution-evaluation/), [요청 분해 평가](../../scripts/spacing-routing-evaluation/) |
| FAQ 검색 | [검색 품질 측정](../../scripts/measure_search_quality.py), [검색 평가셋](../../scripts/data/) |
| 근거 판정 | [회색 구간 측정](../../scripts/gray_zone_upper_band_bedrock.py), [후보 근거 평가](../../scripts/candidate-evidence-evaluation/) |
| 되묻기 | [조건 추출 평가](../../scripts/condition-extraction-evaluation/) |
| 생성·복합 상담 | [생성 평가](../../scripts/answer-generation-evaluation/), [복합 FAQ 평가](../../scripts/compound-faq-evaluation/) |
| 전체 답변 품질 | [공통 품질 평가](../03-evaluation/README.md) |
