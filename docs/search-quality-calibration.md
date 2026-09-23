# 검색 품질 측정 & 임계값 캘리브레이션

`search.similarity-threshold`(`application.yml:60`)는 원래 "무관 질문 유사도 분포 실측 전까지의 잠정값 0.7"이었습니다. 이 문서는 그 실측을 실제로 돌려서 임계값을 확정한 과정과 근거를 남깁니다.

## 1. 측정 방법

`scripts/measure_search_quality.py`(TELME-44)로 `scripts/data/eval_questions_30.json`(SIMILAR/VARIANT 20건 + UNRELATED 10건) 평가셋을 실제 검색 API(`/api/v1/faq/search`, TELME-38)에 돌려 Recall@1/3/5·MRR을 계산합니다. 자세한 사용법은 [scripts/README.md](../scripts/README.md#6-검색-품질-측정) 참고.

랭킹 품질과 임계값 통과 여부가 섞이지 않도록, **baseline/분포 측정은 서버를 `SEARCH_SIMILARITY_THRESHOLD=0`으로 띄운 상태**로 합니다.

```bash
SEARCH_SIMILARITY_THRESHOLD=0 FAQ_SEARCH_TEST_API_ENABLED=true ./gradlew bootRun
python3 scripts/measure_search_quality.py scripts/data/eval_questions_30.json --experiment 기준선
```

## 2. 데이터 규모별 baseline (threshold=0)

`faq_full_300.json` → `faq_full_1150.json` 순서로 적재하며 같은 평가셋을 두 번 측정했습니다 (건수 증가에 따른 Recall 변화를 보기 위해 300건을 먼저 따로 적재).

| | 300건 | 1,150건 |
|---|---|---|
| Recall@1 | 0.650 | 0.450 |
| Recall@3 | 0.900 | 0.650 |
| MRR | 0.750 | 0.542 |
| 정답 hit score 최소 | 0.632 | 0.693 |
| UNRELATED top-1 score 최댓값 | 0.6715 | 0.7156 |

**FAQ가 늘수록 Recall이 뚜렷하게 떨어집니다** — 후보가 많아질수록 서로 비슷한 FAQ끼리 헷갈리는 경우가 늘기 때문입니다. 정답을 못 찾은 질문(EVAL-04/07/08/11/12/18/20)은 `content_hash`로 `faqs` 테이블을 직접 조회해 전부 **적재는 정상, 임베딩 매칭 실패**임을 확인했습니다(적재 누락 아님).

## 3. 겹침(overlap) 문제

1,150건 기준 "정답 hit score 최소(0.693)"가 "UNRELATED top-1 최댓값(0.7156)"보다 **낮습니다** — 두 분포가 겹쳐서, 완벽하게 나누는 단일 임계값이 없습니다. 어떤 값을 골라도 아래 둘 중 하나(또는 둘 다)를 감수해야 합니다:

- 너무 낮게 잡으면: 무관한 질문에도 자신 있게 답변(오탐)
- 너무 높게 잡으면: 진짜 아는 질문인데도 "모르겠습니다"로 과잉 거부

## 4. 임계값 적용 후 재측정 (1,150건 기준)

| | 0 (기준선) | 0.71 | 0.72 |
|---|---|---|---|
| Recall@1 | 0.450 | 0.400 | 0.350 |
| Recall@3 | 0.650 | 0.500 | 0.450 |
| MRR | 0.542 | 0.450 | 0.400 |
| 무관 질문 거부율 | 0.000 | 0.900 | **1.000** |

## 5. 결정: 0.71

`application.yml:60`의 기본값을 0.71로 확정했습니다. 0.72는 무관 질문을 100% 거부하지만 정답 인식률을 더 희생하는 대안으로 검토했으나 채택하지 않았습니다. (0.72가 더 적합하다고 판단되면 이 문서와 `application.yml`을 함께 갱신할 것)
