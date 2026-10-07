# 복합 FAQ와 비교 질문 평가 자료

이번 PR의 검증 결과와 재현 방법은 [평가 문서](../../docs/COMPOUND_COMPARISON_QUALITY.md)에 정리했다. 리뷰할 때는 최신 비교 결과와 채팅 API 결과를 먼저 확인한다. 모델 호출 입력과 원문 출력, 수정 전 재현 및 중간 진단은 `runs/comparison-evidence-v4.json.gz`에 보관했다.

| 자료 | 내용 |
| --- | --- |
| `final/comparison-14-latest.json` | v4 최종 코드로 실행한 비교 질문 14건의 검색 후보, 선택 근거와 답변. 모델 호출 로그는 압축 원시에 보관 |
| `final/chat-api-7.json` | 별도로 확인한 실제 채팅 API 7사례의 저장, 상담 상태, 근거 조회 및 SSE 대조 |
| `final/compound-faq-4.json` | 실제 검색을 사용한 독립 FAQ 복합 질문 4사례 |
| `baseline/comparison-policy-14-results.json` | 비교 질문 변경 전 기준 결과 |
| `baseline/compound-faq-live-baseline.json` | 복합 FAQ 검색 보완 전 결과 |
| `experiments/comparison-policy-forced-evidence-results.json` | 비교 질문에 정답 근거를 직접 주입해 검색과 답변 생성을 분리한 진단 |
| `experiments/compound-faq-forced-verified.json` | 복합 FAQ에 정답 근거를 주입해 재검증한 4사례 |
| `runs/comparison-evidence-v4.json.gz` | 최신 실험의 수정 전 재현, 중간 진단, 최종 14문항과 HTTP 30회 및 모델 입력과 원문 출력 |
| `runs/comparison-evidence-v3.json.gz` | 이전 기준의 비교 질문 14건과 연결 표현 4건 검증 기록 |
| `runs/sentence-boundaries.json.gz` | 문장 경계 수정 전후 라우팅, 비교 질문 및 실제 API 검증 기록 |

압축 자료는 UTF-8 JSON을 gzip으로 압축했다. Python에서는 `json.load(gzip.open(path, "rt", encoding="utf-8"))`로 읽을 수 있다. v4 압축 원문을 풀었을 때의 SHA-256은 `ff74d0f0a1dc98c454a1e91af32298e1688d2de96be82ce0d1aa73131bb17dfc`다.

재실행 결과는 `runs/`에 별도 파일로 생성된다. 프로브와 `run_live_api.py`의 기본 출력 경로는 보관된 실험 결과를 덮어쓰지 않도록 설정했다.
