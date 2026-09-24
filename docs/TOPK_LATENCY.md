# top-k별 정답률·지연시간 측정 (TELME-59)

## 1. 배경

`docs/SEARCH_TUNING.md` 8절에서 임계값 0.72 기준으로 top-k 1/3/5/10의 Recall을 비교해 낸 결론 "top-3 이상은 값이 동일하다" 에서 다루지 않은 두 가지 :

- **응답 지연시간**: ms 단위 측정이 없다
- **임계값을 낮춘 상태에서의 top-k 효과**: "top-k 확대가 의미를 가지려면 임계값 하향이 선행되어야 한다"고 명시하며 범위 밖으로 남겼다

이 문서는 두 가지를 모두 다룬다 — 2–3절은 지연시간, 4절은 임계값을 낮춘 상태에서의 top-k 효과.

## 2. 측정 방법

- 설정: `application.yml` 기본값(`similarity-threshold=0.72`, `embedding-text.variant=Q_A`)
- 평가셋: `scripts/data/eval_questions_130.json` (긍정 80 + 무관 50)
- top-k: 1, 3, 5, 10
- 스크립트: `scripts/measure_search_quality.py` (TELME-59에서 추가한 응답 지연시간 계측 포함 — 요청 전송–응답 수신 구간만 측정, JSON 파싱 등은 제외)

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

### 2.2 코퍼스 검증

측정 전 코퍼스가 실제로 `Q_A`인지 확인했다. `embedding-text.variant` 설정을 바꿔도 **이미 저장된 벡터에는 소급 적용되지 않고, `FAQ_REEMBED_ENABLED=true`로 명시적으로 재임베딩해야만 반영된다**(`SEARCH_TUNING.md` 12.3절) — 확인 없이 측정하면 설정과 실제 벡터가 어긋난 상태로 잴 위험이 있다.

확인은 임계값과 무관한 AUC로 한다. `SEARCH_TUNING.md` 10절의 Q_A 행 AUC(0.8390)와 비교해 같으면 코퍼스가 맞고, 다르면 재임베딩이 필요하다. DB 지문으로 더 빠르게 확인할 수도 있다:
```bash
docker exec telme-postgres psql -U telme -d telme -tAc \
  "select count(*), md5(string_agg(embedding::text, ',' order by faq_id)) from faq_embeddings;"
```
이 문서의 3·4절 수치는 코퍼스가 `Q_A`임을 확인(AUC 0.8390 일치)한 뒤 측정한 값이다.

## 3. 결과 (워밍업 + 코퍼스 검증 후, n=130)

Recall은 결정적(같은 코퍼스·같은 질문이면 항상 같은 값)이라 1회만 측정했다. 지연시간은 실행마다 변동이 있어(2.1절) **k당 3회, 순서를 라운드로 섞어서**(1·3·5·10 → 1·3·5·10 → 1·3·5·10) 측정해 중앙값과 범위를 같이 실었다.

| top-k | Recall@3 | Recall@5 | 평균 지연(중앙값) | 평균 지연 범위(3회) | p95 지연(중앙값) | p95 지연 범위(3회) |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | - | - | 43.2ms | 41.5–53.1ms | 54.7ms | 49.9–86.5ms |
| 3 | 0.375 | - | 45.2ms | 42.0–45.5ms | 69.2ms | 50.3–72.0ms |
| 5 | 0.375 | 0.375 | 46.0ms | 45.1–47.5ms | 77.2ms | 59.9–79.1ms |
| 10 | 0.375 | 0.375 | 45.0ms | 44.4–56.9ms | 61.3ms | 56.3–82.2ms |

### 3.1 정답률

Recall@3·Recall@5 모두 0.375로 top-k 3/5/10에서 **완전히 동일** — `SEARCH_TUNING.md` 8절·확정값 표(0.375)와 정확히 일치. top-4–10위 안에 top-3에 없던 정답이 걸리는 경우가 이 평가셋에는 없다.

### 3.2 지연시간

**k 간 중앙값 차이(43.2–46.0ms, 최대 2.8ms)보다 같은 k 안에서의 회차 간 변동(예: k=1의 41.5–53.1ms, 11.6ms 폭)이 더 크다** — k당 1회씩만 쟀다면 신호로 오해했을 차이가, 3회 반복해보니 순전히 실행 간 잡음이었음이 드러난다. 1,150건 코퍼스 기준으로 top-k를 1→10으로 늘려도 지연시간에 실질적인 차이는 없다는 뜻이다. `LIMIT` 값이 10배 늘어도 DB 조회·응답 조립 비용이 이 규모에서는 무시할 만큼 작다.

## 4. 임계값을 낮춘 상태에서 top-k 효과

`SEARCH_TUNING.md` 8절은 "top-k 확대가 의미를 가지려면 임계값 하향이 선행되어야 한다"고 남겼다. 실제로 임계값을 낮추면 top-k 확대 효과가 커지는지 확인했다.

### 4.1 측정 방법

임계값 0으로 top-10까지 원시 결과를 한 번 수집한 뒤(`--dump-json`), `scripts/analyze_search_grid.py`로 여러 임계값 × top-k 조합을 오프라인 계산했다.

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
| 0.65 | 0.562 | 0.562 | 0.562 | 0.720 |
| 0.68 | 0.500 | 0.500 | 0.500 | 0.780 |
| 0.70 | 0.438 | 0.438 | 0.438 | 0.820 |
| 0.71 | 0.400 | 0.400 | 0.400 | 0.880 |
| 0.72 | 0.375 | 0.375 | 0.375 | 0.940 |

**가설과 반대되는 결과가 나왔다.** 임계값을 0.72에서 0.65까지 낮춰도 top-3/top-5/top-10의 Recall이 모든 구간에서 **완전히 동일**하다.

즉 "임계값을 낮추면 top-k 확대가 의미 있어질 것"이라는 가설은 이 코퍼스·평가셋 기준으로는 **기각**된다. top-k를 5 이상으로 키우는 시도는 임계값과 무관하게 효과가 없다.

## 5. 종합 결론

- **지연시간**: top-k 1–10 구간에서 차이 없음(3절)
- **정답률**: 임계값을 낮춰도 top-k 확대 효과가 없고(4절), top-3/5/10 사이에 전 구간에서 차이 없음
- 두 결과를 합치면 top-k를 키우는 건 "손해는 없지만 얻는 것도 전혀 없는" 결정이다. top-3을 유지한 `SEARCH_TUNING.md`의 선택은 이 재검증으로도 뒤집히지 않는다.
- 이 측정은 **1,150건 규모 코퍼스·130건 평가셋** 기준이다. 코퍼스가 커지면(`SEARCH_TUNING.md` 9절) 경향이 달라질 수 있어 이 결론을 그대로 확대 적용할 수는 없다.
- 이 문서의 모든 수치는 코퍼스가 `Q_A`임을 확인(2.2절)한 뒤 측정한 값이며, `SEARCH_TUNING.md`와 AUC(0.8390)·threshold별 Recall·거부율 전 구간이 정확히 일치함을 확인했다.

## 6. 재현 절차

측정 전에 코퍼스가 `Q_A`인지 먼저 확인한다(2.2절 참고):
```bash
docker exec telme-postgres psql -U telme -d telme -tAc \
  "select count(*), md5(string_agg(embedding::text, ',' order by faq_id)) from faq_embeddings;"
# 다르면 재임베딩
FAQ_REEMBED_ENABLED=true FAQ_EMBEDDING_TEXT_VARIANT=Q_A \
  java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=0
```

```bash
docker compose up -d   # postgres
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew bootJar
FAQ_SEARCH_TEST_API_ENABLED=true java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

# 워밍업 2회 (결과 버림) — 2.1절 참고
for _ in 1 2; do
  python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json --top-k 5 \
    --api-url http://localhost:18090/api/v1/faq/search > /dev/null
done

# 실측 (지연시간, 2–3절) — k당 3회, 라운드로 순서를 섞어 실행 간 변동과 k 간 차이를 분리
for round in 1 2 3; do
  for K in 1 3 5 10; do
    echo "round$round k=$K:"
    python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
      --top-k $K --api-url http://localhost:18090/api/v1/faq/search
  done
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
