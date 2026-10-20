# 서빙별 EXAONE 답변 정확도 (T4, TELME-187)

같은 EXAONE 3.5 7.8B를 Ollama(GGUF Q4_K_M)와 vLLM(AWQ)으로 띄워, Bedrock이 실패해 모든 호출을 EXAONE이 받는 상황에서 서비스 품질셋 v2.2 답변을 캡처하고 Judge로 판정했다. 서빙 비교 전체는 [docs/SERVING_COMPARISON.md](../../../SERVING_COMPARISON.md)에 있다.

## 판단

- **vLLM(AWQ)이 Ollama(GGUF)보다 낫다.** 통과율이 any-fail 24.5% → 30.2%, 다수결 34.0% → 42.9%다.
- 같은 턴끼리 비교하면 차이가 분명하다. any-fail에서 vLLM만 통과 24턴, Ollama만 통과 8턴(부호 검정 p≈0.007), 다수결에서 36턴 대 10턴(p<0.001)이다.
- 근거 없는 주장 비율은 같다(37.9% / 38.5%). 차이는 주로 답 없음 질문의 처리(48% → 63%)와 단일 질문 충족(13% → 18%)에서 나왔다.
- 대체 상황 전체의 답 품질은 낮다(통과율 24~43%). 평상시 경로(gpt-oss)와 비교하는 측정은 아니다.

## 실행 조건

| 항목 | 내용 |
|---|---|
| 날짜 | 2026-10-19 13:27~14:05 (KST) |
| 코드 | `feat/TELME-187-serving-vllm-tei`(`ba4967b8`) + 캡처 프로브 서빙 선택(`TELME_CHAT_JUDGE_LLM_PROVIDER`) |
| 생성 | 서울 g6.xlarge(L4) 스팟 두 대에 각각 Ollama 0.34.0 `exaone3.5:7.8b`(`c7c4e3d1ca22`) + bge-m3 / vLLM 0.31.0 `EXAONE-3.5-7.8B-Instruct-AWQ` + TEI 1.8 bge-m3. 두 캡처를 동시에 돌렸다 |
| 앱 설정 | `LLM_BEDROCK_ENABLED=false`, `SEARCH_GRAY_ZONE_ENABLED=false`, 전환 플래그 켬(`ROUTING_REQUEST_INVENTORY_ENABLED=false`). Bedrock 즉시 장애(S1)와 같은 경로: 모든 LLM 호출이 EXAONE, 회색 구간·후보 판정기는 예전 기준 |
| 캡처 | 이 PC에서 캡처 프로브 실행(`scripts/serving-comparison/capture_remote.py`). 평가 DB는 `telme_judge_eval`(질문 벡터 1,152)을 Ollama는 `telme-flow-postgres`(5440), vLLM은 같은 덤프로 만든 별도 컨테이너(5441)에서 썼다 |
| 평가셋 | 서비스 품질셋 v2.2 전체, 286개 대화 307턴(카카오 키 있음, 매장 대화 포함). Ollama는 1턴이 실행 실패(306턴 판정), vLLM은 307턴 모두 완료 |
| 판정 | Qwen3 235B, DeepSeek V3.2(usw2), Mistral Large 3 각 1회. any-fail(대표)과 다수결 |

## 결과

| | Ollama (GGUF) | vLLM (AWQ) |
|---|---|---|
| 통과율 any-fail | 24.5% (69/282, 95% CI 19.8–29.8) | **30.2%** (83/275, 25.1–35.8) |
| 통과율 다수결 | 34.0% (98/288, 28.8–39.7) | **42.9%** (124/289, 37.3–48.7) |
| 미확정 any-fail / 다수결 | 24 / 18 | 32 / 18 |
| 근거 없는 주장이 있는 답 | 38.5% (40/104) | 37.9% (39/103) |
| 묶음 평균 통과율 any-fail / 다수결 | 28.4% / 35.4% | 33.6% / 44.1% |

묶음별 통과율(any-fail):

| 묶음 | 턴 | Ollama | vLLM |
|---|---:|---|---|
| 범위 밖 질문 | 19 | 16/18 (88.9%) | 18/19 (94.7%) |
| 흐름 회귀 | 9 | 4/8 | 3/7 |
| 멀티턴 | 40 | 6/34 (17.6%) | 7/34 (20.6%) |
| 답 없음 질문 | 60 | 28/58 (48.3%) | 34/54 (63.0%) |
| 복합 질문 | 40 | 1/38 (2.6%) | 2/39 (5.1%) |
| 일부만 답할 수 있는 질문 | 19 | 0/18 | 0/16 |
| 단일 질문 | 119~120 | 14/108 (13.0%) | 19/106 (17.9%) |

같은 턴 짝 비교:

| | 둘 다 통과 | vLLM만 통과 | Ollama만 통과 | 둘 다 실패 | 한쪽 이상 미확정 |
|---|---:|---:|---:|---:|---:|
| any-fail | 55 | 24 | 8 | 177 | 43 |
| 다수결 | 85 | 36 | 10 | 147 | 29 |

## 한계

- 서빙마다 캡처 1회, 판정 모델마다 1회다. 같은 판정을 다시 돌리면 최종 판정의 약 5%가 바뀐다. 짝 비교 차이(24 대 8, 36 대 10)는 그보다 크다.
- 두 서빙의 차이는 양자화(GGUF Q4_K_M / AWQ 4비트)와 실행기(llama.cpp / vLLM)를 함께 바꾼 결과다. 무엇 때문인지는 나누지 않았다.
- Ollama 캡처의 실행 실패 1턴은 판정에서 빠졌다.

## 비용

| 항목 | 비용(대략) |
|---|---|
| Judge Ollama 캡처(Qwen $0.35, DeepSeek $0.87, Mistral $0.84) | $2.06 |
| Judge vLLM 캡처(Qwen $0.36, DeepSeek $0.88, Mistral $0.87) | $2.11 |
| GPU 스팟 2대 약 1시간 | 약 $0.55 |
| 합계 | **약 $4.7** |

## 다시 집계

```powershell
mkdir .measure/t4
tar -xf docs/chat-judge/results/20261019-serving-t4/raw.tar.xz -C .measure/t4
python -X utf8 -m scripts.chat_judge.combine_judges .measure/t4/t4-vllm/qwen.json .measure/t4/t4-vllm/deepseek.json .measure/t4/t4-vllm/mistral.json --capture .measure/t4/t4-vllm/capture.json --out .measure/t4/vllm-combined.json
# 다수결은 --rule majority. Ollama는 t4-ollama 폴더로 같게 한다
```

원시 파일: `raw.tar.xz`(서빙별 `manifest.json`, `capture.json`, 모델별 판정 `qwen.json`·`deepseek.json`·`mistral.json`, 결합 결과 `combined.json`·`combined-majority.json`).
