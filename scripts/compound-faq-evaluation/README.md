# 복합 FAQ와 비교 질문 평가 자료

최종 구현을 확인하려면 `final/`의 세 파일부터 본다. `baseline/`은 변경 전 기준이고, `experiments/`는 원인을 분리해 확인한 중간 결과다. JSON 원문은 파일 위치만 옮겼다.

| 폴더 | 파일 | 내용 |
| --- | --- | --- |
| `final/` | `chat-api-7.json` | 실제 채팅 API 7사례의 답변 저장, 상담 상태, 근거 조회, SSE 대조 |
| `final/` | `comparison-14.json` | 최종 코드로 비교 질문 14건을 다시 평가한 결과 |
| `final/` | `compound-faq-4.json` | 실제 검색을 사용한 독립 FAQ 복합 질문 4사례 |
| `baseline/` | `comparison-policy-14-results.json` | 비교 질문 변경 전 결과 |
| `baseline/` | `compound-faq-live-baseline.json` | 복합 FAQ 검색 보완 전 결과 |
| `experiments/` | `comparison-policy-14-candidates-after.json` | 비교 대상별 검색 후보 확장 결과 |
| `experiments/` | `comparison-policy-14-before-extractive.json` | FAQ 문장만 표시하기 전 생성 답변 |
| `experiments/` | `comparison-first-extractive.json` | 비교 답변에 FAQ 원문 문장을 표시한 첫 실험 결과 |
| `experiments/` | `comparison-policy-forced-evidence-results.json` | 비교 정답 근거를 직접 주입한 진단 결과 |
| `experiments/` | `compound-faq-forced-evidence.json` | 복합 FAQ 정답 근거 주입 초기 결과 |
| `experiments/` | `compound-faq-forced-verified.json` | 복합 FAQ 정답 근거 주입 재검증 결과 |

재실행 결과는 `runs/`에 생성된다. 보관된 원시 결과를 덮어쓰지 않도록 프로브와 `run_live_api.py`의 기본 출력 경로를 이 폴더로 지정했다. 분석과 실행 방법은 [FAQ 복합 질문과 비교 질문 처리](../../docs/COMPOUND_COMPARISON_QUALITY.md)를 참고한다.
