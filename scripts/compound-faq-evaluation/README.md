# 복합 FAQ와 비교 질문 평가 자료

일반 비교 기준 수정의 최신 결과는 `runs/comparison-evidence-v4.json`의 `summary`와 `final`부터 본다. `final/`의 세 파일은 그 이전 검증 원본이다. `baseline/`은 변경 전 기준이고, `experiments/`는 원인을 분리해 확인한 중간 결과다. 기존 JSON 원문은 유지했다.

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
| `runs/` | `comparison-evidence-v3.json` | 비교 기준 검증 보완 후 14건 재실행과 연결 표현 4건의 모델 출력 |
| `runs/` | `comparison-evidence-v4.json` | 속성 미지정 비교 검증 수정. `summary`는 결과 요약, `baseline`은 수정 전 재현, `diagnosticRuns`는 진단 원시, `final`은 최종 비교 14문항과 HTTP 30회 및 실제 판정 입력과 출력. FAQ와 모델 정보 및 전체 빌드 결과도 포함 |
| `runs/` | `sentence-boundaries.json` | 문장 경계의 변경 전 결과, 이전 24회와 최종 26회 실제 라우팅, 비교 14건 및 실제 채팅 API 4사례. 최종 라우팅은 `routing_after_three_request`에서 확인 |

재실행 결과는 `runs/`에 생성된다. 보관된 원시 결과를 덮어쓰지 않도록 프로브와 `run_live_api.py`의 기본 출력 경로를 이 폴더로 지정했다. 분석과 실행 방법은 [FAQ 복합 질문과 비교 질문 처리](../../docs/COMPOUND_COMPARISON_QUALITY.md)를 참고한다.
