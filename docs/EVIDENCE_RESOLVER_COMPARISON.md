# 근거 검증 두 호출의 모델 비교 (엑사원 vs gpt-oss)

TELME-178. 답변 생성 전에 쓰이는 두 근거 검증 호출을 엑사원과 gpt-oss-120b로 같은 질문에 돌려 비교했다. 채점 모델은 쓰지 않고, 모델이 고른 FAQ와 인용문을 정답 FAQ 및 FAQ 원문과 대조했다. 앱의 모델 설정은 바꾸지 않았다.

## 비교한 호출

| 호출 | 클래스 | 호출되는 때 | 하는 일 |
|---|---|---|---|
| 비교 근거 검증 | `LlmComparisonEvidenceResolver` (`comparison-evidence-v4`, 출력 한도 400) | `A와 B 비교해줘`처럼 비교를 물을 때 | 두 대상 각각의 인용 FAQ를 고르고, 인용문이 FAQ 원문과 같은지 확인한 뒤 원문 그대로 답한다 |
| FAQ 후보 근거 검증 | `LlmFaqCandidateEvidenceResolver` (`faq-candidate-evidence-v2`, 출력 한도 250) | 검색 결과가 비었고 토큰 스트리밍 경로가 아닐 때(복합 질문 하위 상담 등) | 후보 FAQ 10개 중 질문 전체에 답할 수 있는 하나를 고르고 답에서 인용문을 복사한다 |

두 호출 모두 항상 켜져 있지만(`@Component`, 끄는 설정 없음) 해당하는 질문에서만 불린다. 둘 다 앱 기본 `LlmClient`(엑사원)를 쓴다.

## 방법

| 항목 | 내용 |
|---|---|
| 모델 | 엑사원 `exaone3.5:7.8b`(Ollama, 1회), gpt-oss-120b(Bedrock Tokyo, 추론 강도 low, 3회) |
| 호출 경로 | 앱의 요청 변환기를 그대로 써서 두 호출의 실제 출력 한도(400, 250)가 적용된다. 측정용 중계기를 거치지 않았다 |
| 검색 | 앱의 실제 검색(로컬 DB의 FAQ 1,152건, bge-m3). 비교는 원문 검색 top-3 후 대상별 검색, 후보는 `searchCandidates` top-10 |
| 비교 질문 | 19개(`comparison-cases.json`): CMP-001~014, 기존 180건의 비교 표현 3개(EVAL-023, 109, 155), 연결 표현 2개. 정답 FAQ(`goldFaqIds`)는 8개에만 있다 |
| 후보 질문 | 107개(`candidate-cases.json`): 기존 180건 채팅 평가(develop `02f0b3d8`)에서 원문 검색이 비었거나 못 찾은 질문. 정답 있음 45, 무관 62 |
| 도구 | `EvidenceResolverClientProbe`, 집계 `score_resolvers.py`, 모델 클라이언트 `ProbeModelClients` |

## 결과

### 비교 근거 검증

| | 엑사원 (1회) | gpt-oss (3회) |
|---|---|---|
| 검증 통과로 답이 만들어짐 | 6/19 (32%) | 24/57 (42%) |
| 정답 FAQ를 모두 고름 (정답 있는 8개) | 5/8 (62%) | 19/24 (79%) |
| 모델이 복사한 인용문이 원문과 그대로 같음 | 18/22 (82%) | 60/60 (100%) |
| 같은 질문의 반복 결과 일치 | - | 18/19 (95%) |
| 출력 한도로 잘린 호출 | 0 | 0 (한도 400, 최대 371토큰 사용) |

### FAQ 후보 근거 검증 (모델이 고른 FAQ 기준)

| | 엑사원 (1회) | gpt-oss (3회) |
|---|---|---|
| 정답이 후보에 있는 질문에서 정답 선택 | 32/41 (78%) | 113/123 (92%) |
| 같은 경우 다른 FAQ 선택 / 선택 안 함 | 5 / 4 | 2 / 8 |
| 정답이 없는 질문에서 FAQ를 잘못 고름 | 16/62 (26%) | 6/186 (3%) |
| 모델이 복사한 인용문이 원문과 그대로 같음 | 44/58 (76%) | 123/127 (97%) |
| 같은 질문의 반복 결과 일치 | - | 99/107 (93%) |
| 출력 한도로 잘린 호출 | 0 | 2 (한도 250) |

### 비용과 호출

gpt-oss 호출 410건(실패 0), 입력 335,002 토큰, 출력 39,505 토큰이고, 단가(입력 $0.1545, 출력 $0.618 / 100만 토큰) 기준 약 $0.076이다. 호출 중앙값 응답 시간은 557ms, 최대 3.6초였다. 엑사원은 로컬이라 비용이 없다. 단가는 AWS 요금표의 Sydney 표기값이며 실제 청구액은 확인하지 않았다.

## 읽을 때 주의할 점

- **후보 근거 검증의 앱 채택은 두 모델 모두 0건이다.** 모델이 FAQ를 고른 뒤 앱이 하는 핵심 단어 검사(`coversQuestionTerms`)가 자연스러운 단일 질문을 거절하기 때문이다. 이 호출은 원래 복합 질문의 정규화된 문장에서만 쓰이고, 기존 평가에서도 같은 이유로 0건이었다(`GRAY_ZONE_UPPER_BAND.md`의 후보 판정기 비교). 그래서 위 표는 앱 검사 전, 모델이 고른 FAQ를 기준으로 했다. 실제 복합 질문 입력에서의 결과는 이 비교로 알 수 없다.
- **gpt-oss가 출력 한도에서 잘린 사례가 있다.** 후보 근거 검증의 EVAL-043에서 3회 중 2회, 한도 250에 걸려 JSON이 끝나기 전에 끊겼다. 전환하면 후보 근거 검증의 한도를 올려야 한다. 비교 근거 검증은 한도 400에서 최대 371토큰까지 써서 여유가 적다.
- **표본이 작다.** 비교는 19개, 그중 정답이 있는 것은 8개다. 방향은 분명하지만 점수 차이를 정확하다고 읽으면 안 된다. 엑사원은 1회만 쟀다.
- **정답 FAQ 기준의 한계.** 비교 질문의 정답은 기존 프로브의 고정 근거이고, 그 밖에 질문에 답할 수 있는 FAQ가 더 있을 수 있다. 후보 질문의 정답도 기존 평가셋의 정답 슬롯이다.
- 후보 질문 107개는 이전 develop(엑사원 앞단)에서 검색이 비었던 질문이다. 지금 코드에서 검색이 비는 질문과 다를 수 있다.

## 판단

- 두 호출 모두 gpt-oss가 엑사원보다 낫거나 비슷했다. 걱정했던 인용문 복사는 비교 검증에서 100%, 후보 검증에서 97%로 엑사원보다 정확했고, 정답이 없을 때 FAQ를 잘못 고르는 비율도 26%에서 3%로 낮았다. 같은 입력의 반복 결과도 대체로 같았다(93~95%).
- 전환 전에 해야 할 일: 후보 근거 검증의 출력 한도 조정, 복합 질문 입력에서의 확인, 비교 근거 검증의 한도 여유 점검.

## 재현

```bash
# 엑사원 (Ollama 실행 중이어야 함)
RESOLVER_PROBE=true RESOLVER_PROBE_MODELS=exaone RESOLVER_PROBE_OUT=build/resolver-exaone.jsonl \
  ./gradlew test --tests '*EvidenceResolverClientProbe'
# gpt-oss (Bedrock API 키를 AWS_BEARER_TOKEN_BEDROCK로 지정, 유료)
RESOLVER_PROBE=true RESOLVER_PROBE_MODELS=gptoss RESOLVER_PROBE_OUT=build/resolver-gptoss.jsonl \
  ./gradlew test --tests '*EvidenceResolverClientProbe'
# 집계
python3 -I scripts/evidence-resolver-evaluation/score_resolvers.py \
  scripts/evidence-resolver-evaluation/results/resolver-exaone.jsonl.gz \
  scripts/evidence-resolver-evaluation/results/resolver-gptoss.jsonl.gz
```

`RESOLVER_PROBE_KINDS=comparison` 또는 `candidate`로 한 호출만 돌릴 수 있고, `RESOLVER_PROBE_LIMIT`으로 질문 수를 줄일 수 있다.

## 파일

| 파일 | 내용 |
|---|---|
| `src/test/java/com/telme/probe/EvidenceResolverClientProbe.java` | 두 호출을 선택한 모델로 직접 만들어 실행하는 측정 프로그램 |
| `src/test/java/com/telme/probe/ProbeModelClients.java` | 엑사원과 gpt-oss를 같은 방식으로 부르고 호출마다 기록하는 클라이언트 |
| `scripts/evidence-resolver-evaluation/comparison-cases.json`, `candidate-cases.json` | 질문 19개, 107개 |
| `scripts/evidence-resolver-evaluation/score_resolvers.py` | 결과 집계 |
| `scripts/evidence-resolver-evaluation/results/*.jsonl.gz` | 두 모델의 원시 결과(합계 약 325KB). 모델 원문 출력과 후보 FAQ 본문이 들어 있다 |
