# 리뷰 반영 후 실측 원시 자료

[FAQ와 매장 복합 상담 문서](../../../docs/COMPOUND_FAQ_STORE.md)의 리뷰 반영 실측 원시 자료다. 모든 실행은 같은 평가 DB와 모델에서 실제 Spring 채팅 API로 했다.

| 파일 | 코드 | 내용 |
| --- | --- | --- |
| `routing-16-review.json.gz` | 리뷰 반영 코드 | FAQ·매장 복합 16문항. 문항별 trace, SSE, DB 상태, 라우팅 모델 원문 호출(`modelCalls`) |
| `single-180-develop.json.gz` | develop `858b4943` | 단일 질문 180건(답변 가능 110, 무관 70)의 검색 trace와 답변 근거 |
| `single-180-pr.json.gz` | 리뷰 전 PR `fe33e30f` | 위와 같음 |
| `single-180-review.json.gz` | 리뷰 반영 코드 | 위와 같음 |

리뷰 반영 코드는 커밋 전에 측정했다. `routing-16-review.json.gz`의 `gitHead`는 측정 당시 HEAD(`fe33e30f`)이고, 실제 코드는 `sourceTreeSha256`(`src/main/java` 전체의 SHA-256)으로 구분한다.

## 실행 조건

- 평가 DB: `telme_135_live` (FAQ 1,152건, 임베딩 1,152건, 매장 152건, Flyway V9)
- 모델: Ollama `exaone3.5:7.8b`, 임베딩 `bge-m3`
- 앱 환경 변수: `SERVER_PORT=18135`, `POSTGRES_PORT=5440`, `POSTGRES_DB=telme_135_live`, `LLM_PROVIDER=ollama`, `OLLAMA_URL=http://localhost:11436`
- 카카오 키는 더미 값이다. 지역명 매장 검색은 실패하므로 매장 답변 품질은 채점하지 않는다.

## 재실행

```bash
# 1. 모델 호출 기록 프록시 (11436 -> Ollama 11434)
python scripts/compound-store-evaluation/capture_proxy.py --log .measure/telme135-review/model-calls.jsonl

# 2. 측정할 코드의 jar를 위 환경 변수로 실행한 뒤
python scripts/compound-store-evaluation/run_routing.py --database telme_135_live \
    --llm-log .measure/telme135-review/model-calls.jsonl --output .measure/telme135-review/routing.json.gz
python scripts/single-faq-search-evaluation/run_chat_eval.py --base-url http://localhost:18135 \
    --database telme_135_live --output .measure/telme135-review/single-180.json

# 3. 단일 질문 결과 비교 (저장소의 .json.gz는 압축을 풀어 넘긴다)
python scripts/single-faq-search-evaluation/run_chat_eval.py --compare <이전.json> <이후.json>
```
