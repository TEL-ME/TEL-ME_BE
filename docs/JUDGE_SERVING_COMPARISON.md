# 근거 판정기 모델·서빙 방식별 실측 비교

근거 판정기(`EvidenceRelevanceChecker`)를 어떤 모델로, 어떤 검색 기준선에서 켤지 정하기 위해 실제 채팅 API로 10개 구성을 비교했다. 운영 코드는 바꾸지 않았다.

## 측정 조건

- 코드: develop `2a73ba35`
- 평가셋: [180건](../scripts/data/eval_questions_180_multigold.json) (FAQ로 답할 수 있음 110 / FAQ에 답 없음 70), 문항마다 새 세션
- 판정기: 앱의 현행 판정기(프롬프트 `evidence-check-v2`, 상위 3개 근거를 한 번에 판정, 판정 실패 시 통과)를 `RAG_EVIDENCE_CHECK_ENABLED=true`로 켬
- 기준선: 현행 Q_A 0.72 / 질문 0.88, 낮춘 기준선 Q_A 0.71 / 질문 0.80(#139 추천값)
- 생성 모델
  - Ollama: 로컬 PC의 EXAONE 3.5 7.8B(Q4_K_M)
  - vLLM: AWS g6.xlarge(L4 24GB) 서울 스팟의 EXAONE 3.5 7.8B Instruct AWQ
  - Bedrock: gpt-oss-120b, 도쿄 리전(서울 리전에는 이 모델이 없음)
- 임베딩: 모든 구성에서 로컬 Ollama bge-m3

앱의 LLM 호출은 평가용 라우터(`judge_router.py`)를 거친다. 라우터는 판정 요청을 시스템 프롬프트의 첫 문장으로 구분해 지정한 판정 모델로 보내고, 나머지 생성 요청은 지정한 생성 모델로 보낸다.

## 결과

| 항목 | A | B | C | D | E | A2 | B2 | C2 | D2 | E2 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 생성 | Ollama | vLLM | vLLM | Ollama | Bedrock | Ollama | vLLM | vLLM | Ollama | Bedrock |
| 판정 | EXAONE | EXAONE | Bedrock | Bedrock | Bedrock | EXAONE | EXAONE | Bedrock | Bedrock | Bedrock |
| 기준선 | 현행 | 현행 | 현행 | 현행 | 현행 | 0.71/0.80 | 0.71/0.80 | 0.71/0.80 | 0.71/0.80 | 0.71/0.80 |
| 답변 가능 110 중 정답 FAQ 검색 | 65 | 66 | 67 | 67 | 58 | 74 | 75 | 76 | 74 | 68 |
| 답변 가능 110 중 근거 있는 답변 | 34 | 44 | 43 | 33 | 44 | 39 | 46 | 46 | 41 | 49 |
| 답 없음 70 중 근거 있는 답변 (낮을수록 좋음) | 5 | 5 | **0** | 1 | 2 | 9 | 10 | 4 | 4 | 3 |
| 판정기가 막은 질문 (답변 가능 / 답 없음) | 5 / 2 | 2 / 3 | 1 / 10 | 1 / 9 | 1 / 7 | 3 / 7 | 2 / 5 | 1 / 16 | 1 / 16 | 1 / 15 |
| 정답 FAQ를 찾고도 막은 수 | 4 | 2 | 0 | 0 | 0 | 2 | 2 | 0 | 0 | 0 |
| 판정 1회 중앙값 | 0.22초 | 0.19초 | 0.45초 | 0.39초 | 0.41초 | 0.20초 | 0.19초 | 0.39초 | 0.39초 | 0.38초 |
| 응답 시간 중앙값 | 2.08초 | 2.95초 | 3.04초 | 2.09초 | 2.12초 | 2.11초 | 3.35초 | 3.35초 | 2.14초 | 2.12초 |
| 응답 시간 p95 | 3.35초 | 5.39초 | 5.40초 | 3.38초 | 3.38초 | 3.31초 | 5.41초 | 5.43초 | 3.38초 | 3.74초 |

판정기를 끈 현재 운영 상태(Ollama, 현행 기준선)는 정답 FAQ 검색 66, 근거 있는 답변 34, 답 없음에 근거 있는 답변 5, 응답 시간 중앙값 2.08초였다. 판정 호출은 모든 구성에서 실패 0건이다. 각 구성은 1회씩 측정했고, 반복하면 1~2건은 실행마다 달라진다.

## 해석

- **EXAONE 판정은 쓰지 않는다.** 현행 기준선(A·B)에서 답 없음에 근거 있는 답변이 판정기를 끈 상태와 같은 5건이라 거르는 효과가 없고, 정답 FAQ를 찾은 질문을 2~4건 막았다. 낮춘 기준선(A2·B2)에서는 9~10건으로 늘었다.
- **Bedrock 판정은 정답을 막지 않았다.** C·D·C2·D2 모두 정답 FAQ를 찾은 질문은 막지 않았다. 막은 답변 가능 질문 1건(EVAL-140)은 원래 정답 FAQ를 찾지 못한 질문이다.
- **기준선을 낮출지는 맞바꿈이다.** Bedrock 판정 기준으로 0.71/0.80은 현행보다 근거 있는 답변 +3(C 43 → C2 46), 답 없음에 근거 있는 답변 +4(0 → 4)다.
- **판정 지연은 작다.** 판정은 근거 묶음마다 1회라 1회 약 0.4초이고, 응답 시간 중앙값은 판정기 유무와 거의 같다.
- **vLLM과 Ollama의 차이는 서빙 방식 차이다.** 같은 EXAONE이지만 양자화(AWQ / Q4_K_M)가 달라 vLLM 쪽이 조건을 되묻는 대신 바로 답한 경우가 많았다. 응답이 약 1초 느린 것은 로컬 PC에서 EC2까지의 왕복 때문이다.
- **E·E2는 라우팅까지 바뀌었다.** E·E2는 판정기뿐 아니라 라우팅, 문맥 판정, 답변 등 모든 LLM 호출을 gpt-oss-120b로 보냈다. 그래서 다른 구성과 달리 검색 단계도 바뀌었다. gpt-oss가 답변 가능 질문을 범위 밖으로 분류해 검색하지 않은 경우가 많아(E 13건, E2 기준 C2·D2 대비 6건) 정답 FAQ 검색이 줄었다. 답변 생성 모델만의 효과를 보려면 답변 생성 요청만 Bedrock으로 보내 다시 측정해야 한다.

## 한계

- #139의 회색 구간 구조(낮춘 선 ~ 현행 선 후보만 하나씩 B3 프롬프트로 판정, 실패 시 제외)와 다르다. 방향은 같지만 #139 수치와 직접 비교하지 않는다.
- 검색·근거 단계 수치다. 답변 문장이 근거에 충실한지는 따로 판정하지 않았다. 이전 대화가 있는 질문과 복합 질문은 평가셋에 없다.
- Bedrock의 JSON 스키마 강제(`outputConfig`)를 gpt-oss에 쓰면 깨진 JSON이 나왔다(5회 중 5회, `{` 중복). 라우터는 스키마 요청(문맥 판정, 상담 종료 요약)을 "이 스키마를 따르는 JSON만 출력" 지시로 바꿔 보냈다. 생성까지 Bedrock으로 옮기려면 앱의 LLM 클라이언트에서도 같은 처리가 필요하다.
- Bedrock 도쿄 리전을 쓰면 상담 질문과 FAQ 본문이 국외로 전송된다.

## 비용

- EC2 g6.xlarge 스팟(시간당 약 $0.48): 측정 실행 약 $0.75
- Bedrock gpt-oss-120b: 판정 약 300회 약 50원, E·E2의 전체 호출 883회 약 $0.35(입력 193만 · 출력 10만 토큰)

## 재현

원시 결과는 [results.json.gz](../scripts/judge-serving-evaluation/results.json.gz)에 구성별 채팅 결과, 판정 차단 기록, 라우터 호출 기록을 담았다. 표는 다음 명령으로 다시 계산한다.

```bash
python scripts/judge-serving-evaluation/compare_configs.py scripts/judge-serving-evaluation/results.json.gz A B C D E A2 B2 C2 D2 E2
```

구성을 다시 측정하려면 앱 jar를 빌드한 뒤 `run_config.sh`를 실행한다. 템플릿 DB를 복사한 독립 DB를 만들고 측정이 끝나면 지운다.

```bash
./gradlew bootJar -x test
# 로컬 구성 (Bedrock은 AWS 프로필 telme-bedrock-eval 사용)
bash scripts/judge-serving-evaluation/run_config.sh D true --chat ollama --judge bedrock
QA_T=0.71 QO_T=0.80 bash scripts/judge-serving-evaluation/run_config.sh D2 true --chat ollama --judge bedrock

# vLLM 구성: 스팟 기동 → 측정 → 종료까지 한 번에
AWS_PROFILE=... AWS_REGION=ap-northeast-2 AMI_ID=... KEY_NAME=... SUBNET_ID=... SG_ID=... \
  bash scripts/judge-serving-evaluation/run_vllm_configs.sh "B true --chat vllm --judge chat" "C true --chat vllm --judge bedrock"
```

`run_vllm_configs.sh`는 `user-data-vllm-exaone.sh`로 인스턴스를 띄운다. 이 스크립트는 vLLM 이미지와 모델을 NVMe에 받도록 containerd와 docker 저장소를 함께 옮기고, 120분 자동 종료를 건다. 측정이 끝나거나 중간에 실패해도 인스턴스를 종료하고 스팟 요청을 취소한다.
