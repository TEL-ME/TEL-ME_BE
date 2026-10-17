# 답변 생성 EXAONE 3회 반복 평가 (TELME-186)

TELME-178(#167)의 답변 생성 입력 A·B를 EXAONE에 3회씩 다시 호출한 결과다. 운영 코드와 모델 선택·기본 플래그는 바꾸지 않았다.

- 이슈: [TELME-186](https://telme-team.atlassian.net/browse/TELME-186)
- 실행일: 2026-10-18 (Asia/Seoul)
- 실행 환경: TELME-183과 맞춘 네이티브 Ollama 0.34.0. 항목별 대조는 [환경 대조표](../scripts/answer-generation-evaluation/TELME-186-ENVIRONMENT.md)
- **gpt-oss 3회는 이번에 실행하지 않았다.** 실제 Bedrock 호출이 필요해 수동 확인 항목으로 남긴다(아래).

## 고정 조건 (합의한 비교 조건)

과거 실행 당시 상태 전체를 완전히 재현한 것이 아니다.

| 항목 | 값 |
| --- | --- |
| 답변 프롬프트 | `rag-answer-v4.7` 측정 당시 본문(`LG U+`), `AnswerPromptTemplates.java` blob `09f7bc7e…`(커밋 9e633361 시점) |
| 가드 | #167 최종 `AnswerGuard.java` blob `191e71e8…` (d556e53c = develop) |
| 나머지 코드 | develop `35b33f37`. 별도 디렉터리에 추출하고 프롬프트 파일만 위 blob으로 교체해 실행 |
| 입력 | `inputs-a.json` 217건, `inputs-b.json` 145건 (#167과 같은 파일) |
| 모델 | `exaone3.5:7.8b` Q4_K_M, digest `c7c4e3d1…fbf03` |
| 호출 | 스트리밍 `/api/chat`, temperature 0.0, num_predict 1024, num_ctx 8192, 문항 동시성 1, 3회 순차 |

주의: 같은 `rag-answer-v4.7` 라벨이지만 `TELME` 본문인 커밋(e9bb5dd5)이 있다. 라벨이 아니라 blob으로 고정했다.

## 결과

호출 1,086건(A 651 + B 435), 전부 HTTP 200·종료 사유 `stop`, 오류 행 0, 잘림 의심 0.

### A (217입력 × 3회)

| 평가셋 | 건수 | 가드 KEPT / MODIFIED / REPLACED | 근거 없음 | 길이 중앙값 |
| --- | ---: | --- | ---: | ---: |
| eval180 | 324 | 70% / 26% / 4% | 5% | 82자 |
| holdout90 | 270 | 28% / 31% / 41% | 41% | 62자 |
| answer_quality_20 | 57 | 26% / 47% / 26% | 26% | 47자 |
| 전체 | 651 | 49% / 30% / 21% | 22% | 70자 |

### B (145입력 × 3회)

| 질문 종류 | 건수 | KEPT / MODIFIED / REPLACED | 근거 없음 |
| --- | ---: | --- | ---: |
| 복합 FAQ | 114 | 71% / 26% / 3% | 3% |
| FAQ+매장 | 81 | 74% / 26% / 0% | 0% |
| 단일 FAQ | 39 | 54% / 46% / 0% | 0% |
| 되묻기 | 69 | 91% / 4% / 4% | 4% |
| 대화 흐름 | 42 | 100% / 0% / 0% | 0% |
| 멀티턴 | 90 | 63% / 37% / 0% | 0% |
| 전체 | 435 | 74% / 24% / 1% | 1% |

### 반복 일치와 #167 대비

| | A | B |
| --- | ---: | ---: |
| 3회 가드 전 원문이 모두 같은 입력 | 217/217 | 145/145 |
| 3회 최종 답변·근거 여부·가드 처리가 모두 같은 입력 | 217/217 | 145/145 |
| #167 EXAONE 1회와 가드 전 원문 일치 | 211/217 | 143/145 |
| #167과 최종 답변 일치 | 211/217 | 144/145 |

- 3회가 입력마다 완전히 같았다. temperature 0.0의 관측이며 다른 입력·환경에서도 항상 같다는 뜻이 아니다. 같은 서버·같은 로드 상태에서 순차로 돌린 결과다.
- #167 대비 달라진 입력은 A 6개(EVAL-048, EVAL-051, EVAL-073, H-088, H-091, H-127), B 2개(RU-290-t0-r0, CTX-transition-04-t0-r0)이고 가드 처리(KEPT/MODIFIED)는 모두 같았다. 원인(Ollama 0.34.0 대 당시 서버, 하드웨어 등)은 구분하지 못했다.
- #167 문서의 EXAONE 수치(A 48/30/21·근거 없음 22%, B 74/24/1·1%)와 이번 수치(A 49/30/21·22%, B 74/24/1·1%)는 반올림 범위에서 같다.

## 한계

- 하드웨어·OS가 원본(RTX 5070 Ti·Windows Docker)과 다르다(Apple M4, macOS 26.6.2, 통합 메모리 16 GiB, Metal 11.8 GiB).
- 모델 로드 크기가 TELME-183 기록과 +225 MiB 다르다. 원인 미확정.
- 원본 RAM 할당 제한은 기록이 없어 설정하지 않았다. 측정 중 스왑 약 6.6 GiB 사용.
- 가드 결과는 품질 채점이 아니다. 우열 판단은 TELME-180의 품질 채점으로 본다.
- 응답 시간은 관측값이며 성능 비교로 해석하지 않는다.

## 수동 확인 필요

- gpt-oss-120b 3회 반복(Bedrock Tokyo, 추론 강도 low, 출력 한도 1,000)은 실제 API 호출이 필요해 실행하지 않았다. 같은 입력·같은 고정 조건으로 `AnswerGenerationProbe`를 `ANSWER_PROBE_MODELS=gptoss`로 직접 실행해야 한다.

## 보존 자료

`scripts/answer-generation-evaluation/results/TELME-186-20261018/`

| 파일 | 내용 |
| --- | --- |
| `answers-a-exaone.jsonl.gz`, `answers-b-exaone.jsonl.gz` | 회차별 원문·가드 후 답변·가드 처리 (A 651행, B 435행) |
| `ollama-requests.jsonl.gz` | 프록시가 캡처한 실제 `/api/chat` 요청 1,086건과 최종 청크(토큰 수·소요 시간) |
| `obs/` | 서버 기동 로그, `/api/show`, `/api/ps`, 실행 스크립트, gradle 로그 |
| `checksums.sha256` | 위 파일의 SHA-256 (`shasum -a 256 -c checksums.sha256`) |

재집계: `python3 -I scripts/answer-generation-evaluation/score_answers.py <결과.jsonl.gz>`
