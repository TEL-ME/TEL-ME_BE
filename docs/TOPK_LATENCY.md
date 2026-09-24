# top-k별 정답률·지연시간 측정 (TELME-59)

## 1. 배경

`docs/SEARCH_TUNING.md` 8절에서 임계값 0.72 기준으로 top-k 1/3/5/10의 Recall을 비교해 낸 결론 "top-3 이상은 값이 동일하다" 에서 다루지 않은 두 가지 :

- **응답 지연시간**: ms 단위 측정이 없다
- **임계값을 낮춘 상태에서의 top-k 효과**: "top-k 확대가 의미를 가지려면 임계값 하향이 선행되어야 한다"고 명시하며 범위 밖으로 남겼다

이 문서는 두 가지를 모두 다룬다 — 2~3절은 지연시간, 4절은 임계값을 낮춘 상태에서의 top-k 효과.

## 2. 측정 방법

- 설정: `application.yml` 기본값 그대로(`similarity-threshold=0.72`, `embedding-text.variant=Q_A`) — 재임베딩 없이 진행
- 평가셋: `scripts/data/eval_questions_130.json` (긍정 80 + 무관 50)
- top-k: 1, 3, 5, 10
- 스크립트: `scripts/measure_search_quality.py` (TELME-59에서 추가한 응답 지연시간 계측 포함 — 요청 전송~응답 수신 구간만 측정, JSON 파싱 등은 제외)

```bash
FAQ_SEARCH_TEST_API_ENABLED=true java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

for K in 1 3 5 10; do
  python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
    --top-k $K --api-url http://localhost:18090/api/v1/faq/search
done
```

### 2.1 워밍업 측정 오류와 수정

최초 순서대로(1→3→5→10) 측정했을 때 top-k가 클수록 응답이 더 빠르게 나오는(73ms→48ms) 결과가 나왔다. top-k와 지연시간이 반비례할 이유가 없어 순서를 뒤집어(10→5→3→1) 재측정하니 경향이 그대로 뒤집혔다(먼저 도는 쪽이 항상 느림) — **JVM JIT·DB 커넥션 풀 워밍업이 안 된 상태에서 첫 번째로 실행된 top-k가 그 워밍업 비용을 떠안는 순서 효과**였음을 확인했다.

이후 모든 측정 전 워밍업 패스 2회(결과 버림)를 넣고 나서야 안정된 값을 얻었다. **이 스크립트로 지연시간을 잴 때는 반드시 워밍업 후 측정할 것.**

## 3. 결과 (워밍업 후, n=130)

| top-k | Recall@3 | Recall@5 | 평균 지연 | p95 지연 |
| --- | --- | --- | --- | --- |
| 1 | - | - | 41.6ms | 53.1ms |
| 3 | 0.388 | - | 41.6ms | 59.6ms |
| 5 | 0.388 | 0.400 | 44.0ms | 62.9ms |
| 10 | 0.388 | 0.400 | 43.0ms | 53.9ms |

### 3.1 정답률

Recall@3(0.388)은 top-k 3/5/10에서 전부 동일 — `SEARCH_TUNING.md` 8절의 결론을 재확인. Recall@5는 top-5부터 0.400으로 소폭 상승(130건 중 1문항 차이).

### 3.2 지연시간

**1,150건 코퍼스 기준으로 top-k를 1→10으로 늘려도 지연시간 차이는 오차 범위 안(41~44ms)** — 유의미한 증가가 없다. `LIMIT` 값이 10배 늘어도 DB 조회·응답 조립 비용이 이 규모에서는 무시할 만큼 작다는 뜻이다.

## 4. 임계값을 낮춘 상태에서 top-k 효과

`SEARCH_TUNING.md` 8절은 "top-k 확대가 의미를 가지려면 임계값 하향이 선행되어야 한다"고 남겼다. 실제로 임계값을 낮추면 top-k 확대 효과가 커지는지 확인했다.

### 4.1 측정 방법

임계값 0으로 top-10까지 원시 결과를 한 번 수집한 뒤(`--dump-json`), `scripts/analyze_search_grid.py`로 여러 임계값 × top-k 조합을 오프라인 계산했다. 재임베딩 없음(설정은 3절과 동일).

```bash
SEARCH_SIMILARITY_THRESHOLD=0 FAQ_SEARCH_TEST_API_ENABLED=true \
  java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
  --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
  --dump-json .measure/raw-1150-Q_A.json

python3 scripts/analyze_search_grid.py .measure/raw-1150-Q_A.json
```

### 4.2 결과

| 임계값 | top-3 Recall@3 | top-5 Recall@5 | top-10 Recall@5 | 거부율(top-k 무관, 공통) |
| --- | --- | --- | --- | --- |
| 0.65 | 0.537 | 0.550 | 0.550 | 0.780 |
| 0.68 | 0.500 | 0.512 | 0.512 | 0.800 |
| 0.70 | 0.463 | 0.475 | 0.475 | 0.840 |
| 0.71 | 0.425 | 0.438 | 0.438 | 0.880 |
| 0.72 | 0.388 | 0.400 | 0.400 | 0.940 |

**가설과 반대되는 결과가 나왔다.** 임계값을 0.72에서 0.65까지 낮춰도 top-3→top-5의 Recall 상승폭은 0.012~0.013으로 거의 일정하다 — 임계값이 낮아진다고 top-k 확대 효과가 커지지 않는다. 그리고 **top-10은 모든 임계값 구간에서 top-5와 완전히 동일한 Recall@5**를 낸다 — 순위 6~10위 안에 top-5에 없던 정답이 걸리는 경우가 이 평가셋(130건)에는 사실상 없다는 뜻이다.

즉 "임계값을 낮추면 top-k 확대가 의미 있어질 것"이라는 가설은 이 코퍼스·평가셋 기준으로는 **기각**된다. top-k를 5 이상으로 키우는 시도는 임계값과 무관하게 효과가 작다.

## 5. 종합 결론

- **지연시간**: top-k 1~10 구간에서 차이 없음(2절)
- **정답률**: 임계값을 낮춰도 top-k 확대 효과는 미미하고 일정함(4절), top-10은 top-5 대비 이득 없음
- 두 결과를 합치면 top-k를 키우는 건 "손해는 없지만 기대만큼 얻는 것도 없는" 결정이다. top-3을 유지한 `SEARCH_TUNING.md`의 선택은 이 재검증으로도 뒤집히지 않는다.
- 이 측정은 **1,150건 규모 코퍼스·130건 평가셋** 기준이다. 코퍼스가 커지면(`SEARCH_TUNING.md` 9절) 경향이 달라질 수 있어 이 결론을 그대로 확대 적용할 수는 없다.

> **참고**: 이번 재측정의 threshold=0.72·top-3 Recall@3 값(0.388)이 `SEARCH_TUNING.md` 확정값 표의 값(0.375)과 1문항(80건 중) 차이가 난다. 거부율은 두 측정에서 완전히 일치(0.940)해 무관 질문 쪽 임베딩은 안정적임을 시사하지만, 긍정 질문 쪽에서 사소한 드리프트가 있었을 가능성이 있다. 두 리포트의 결론(0.72 확정, top-3 유지)에는 영향이 없는 크기지만, 완전한 재현성을 요구하는 후속 작업이 있다면 이 차이의 원인을 먼저 규명할 것.

## 6. 재현 절차

```bash
docker compose up -d   # postgres
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew bootJar
FAQ_SEARCH_TEST_API_ENABLED=true java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

# 워밍업 (결과 버림)
python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json --top-k 5 \
  --api-url http://localhost:18090/api/v1/faq/search > /dev/null

# 실측 (지연시간, 2~3절)
for K in 1 3 5 10; do
  python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
    --top-k $K --api-url http://localhost:18090/api/v1/faq/search
done
```

4절(임계값 × top-k)은 서버를 `SEARCH_SIMILARITY_THRESHOLD=0`으로 다시 띄운 뒤:
```bash
python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
  --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
  --dump-json .measure/raw-1150-Q_A.json

python3 scripts/analyze_search_grid.py .measure/raw-1150-Q_A.json
```
(`.measure/`는 재생성 가능한 원시 결과라 커밋 제외 — `.gitignore` 참고)
