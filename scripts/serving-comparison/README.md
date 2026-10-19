# EXAONE 서빙 비교 (Ollama vs vLLM + TEI)

배포 GPU EC2에서 EXAONE 3.5 7.8B와 bge-m3를 무엇으로 띄울지 정하기 위한 측정 도구다. EXAONE은 평소 대화 요약·세션 제목만 맡고, Bedrock이 실패하면 답변 경로 호출을 대신 받는다(`FallbackLlmClient`). 그래서 **Bedrock 장애 순간에 몰리는 호출을 버티는지**를 본다.

측정은 실제 배포 구성(앱 EC2 → GPU EC2, RDS, us-east-1 Bedrock)에서 하고, 숫자는 앱(채팅 API)으로 잰다. 로컬 PC에서 잰 엑사원 시간은 쓰지 않는다.

## 파일

| 파일 | 내용 |
|---|---|
| `gpu-compose.yml` | GPU EC2용. 프로필 `ollama`(EXAONE + bge-m3) 또는 `vllm`(vLLM EXAONE AWQ + TEI bge-m3) 하나만 띄운다 |
| `app-compose.yml` | 앱 EC2용. 측정용 앱 컨테이너(`app.env`에서 설정을 읽음) |
| `fault-refuse.yml` | S1: Bedrock 주소를 127.0.0.1로 돌려 연결이 바로 거부되게 한다 |
| `fault-hang.yml` | S2: Bedrock 주소를 응답하지 않는 서버로 바꿔 호출마다 시간 제한(8초)까지 기다리게 한다 |
| `load_chat.py` | 품질셋 v2.2 단일 턴 질문을 동시 대화 N개로 보내 턴 시간·실패를 기록한다 |
| `calls.sql` | 측정 구간의 LLM 호출을 서빙·작업·결과별로 세고, 서버가 잰 턴 시간을 본다 |

## 앱 설정 (`app.env`)

공통으로 DB(RDS), 전환 플래그(`docs/GPT_OSS_SWITCH.md` "전환 켜기")를 넣고 서빙만 바꾼다. GPU EC2 주소는 사설 IP다.

| | Ollama | vLLM + TEI |
|---|---|---|
| `LLM_PROVIDER` | `ollama` | `vllm` |
| 주소 | `OLLAMA_URL=http://<GPU 사설 IP>:11434` | `VLLM_URL=http://<GPU 사설 IP>:8000` |
| 임베딩 | (주소를 비우면 `OLLAMA_URL`) | `EMBEDDING_PROVIDER=tei`, `EMBEDDING_URL=http://<GPU 사설 IP>:8080` |
| `LLM_MODEL` | `exaone3.5:7.8b` | `exaone3.5:7.8b` (vLLM `--served-model-name`과 같음) |

```bash
# 공통 (전환 켜기)
ROUTING_BEDROCK=true
ROUTING_REQUEST_INVENTORY_ENABLED=false
CHAT_QUESTION_RESOLUTION_BEDROCK=true
CONSULT_PLAN_QUESTION_BEDROCK=true
CONSULT_FAQ_CLARIFICATION_BEDROCK=true
RAG_ANSWER_BEDROCK=true
```

- 작업별 temperature·출력 한도는 두 서빙이 같다(`VllmRequestConverter`가 Ollama 변환 값을 그대로 옮긴다). JSON 스키마는 vLLM에서도 강제된다(`response_format`).
- 호출 기록(`llm_generations.request_options.provider`)에 `ollama`, `vllm`, `bedrock`이 남는다.
- S2는 평문 HTTP 주소로 바꾸므로 IAM 역할 같은 서명 방식 자격 증명으로 띄운다.

## 준비

1. **DB**: RDS에 FAQ와 질문 벡터를 넣고 확인한다. `select count(embedding_question) from faq_embeddings`가 1,152가 아니면 멈춘다. TEI로 바꾼 뒤에는 T2(임베딩 회귀) 전까지 같은 벡터를 쓴다.
2. **GPU EC2**: `gpu-compose.yml`을 올리고 한 프로필을 띄운다. Ollama는 `ollama pull exaone3.5:7.8b`, `ollama pull bge-m3`를 먼저 한다. vLLM·TEI는 처음 뜰 때 모델을 받는다(수 분).
3. **앱 EC2**: 앱 이미지(`APP_IMAGE`)와 `app.env`를 두고 `app-compose.yml`로 띄운다. 부하 스크립트도 앱 EC2에서 `localhost:8080`으로 보낸다(ALB는 두 구성에 똑같이 더해지는 몇 ms라 뺀다).
4. **같은 조건 맞추기**: `MAX_CONCURRENT`(Ollama `OLLAMA_NUM_PARALLEL`, vLLM `--max-num-seqs`)를 같게 둔다. 기본 8.

## 시나리오

| | Bedrock | 내용 | 동시 대화 N | 질문 수 |
|---|---|---|---|---|
| S0 평상시 | 정상 | 기준선. 엑사원은 요약·제목만 | 1, 5, 10, 20 | 100 |
| S1 즉시 장애 | `fault-refuse.yml` | 모든 답변 경로 호출이 바로 엑사원으로 넘어간다 | 1, 5, 10, 20 | 100 |
| S2 응답 없음 | `fault-hang.yml` | 호출마다 8초를 기다린 뒤 넘어간다 | 1, 5, 10 | 20 |
| S3 오래 쉰 뒤 장애 | `fault-refuse.yml` | 15분 동안 엑사원 호출 없이 둔 뒤 S1을 N=5로 시작. Ollama는 `OLLAMA_KEEP_ALIVE` 기본(10m)과 `-1` 두 번 | 5 | 20 |
| T4 정확도 | 엑사원만 | 문맥 해석 213 등 평가셋 하나를 두 서빙으로 1회씩 | — | — |

- Bedrock 비용은 S0에서만 생긴다(S1·S2는 요청이 Bedrock에 닿지 않는다). 질문 400개 × 턴당 약 6호출 ≈ 2,400호출, 약 $1. 실행 전에 다시 계산해 승인받는다.
- **앱은 채팅 턴을 동시에 4개까지만 처리한다**(`ChatProcessingDispatcher` 스레드 4, 대기열 100). N이 4보다 크면 나머지는 앱 안에서 기다리므로, 엑사원에 동시에 가는 요청은 대략 4~5개(+ 요약·제목 1)다. 이 상한이 서빙 차이보다 먼저 드러날 수 있어 `calls.sql`의 서버 턴 시간과 함께 본다.

```bash
# 예: vLLM 구성, S1, N=5
docker compose -f app-compose.yml -f fault-refuse.yml up -d
python3 load_chat.py --concurrency 5 --label vllm-S1-N5 --output results/vllm-S1-N5.jsonl
psql "$DATABASE_URL" -v since="'<meta.startedAt>'" -v until="'<끝난 시각>'" -f calls.sql
# 결과 표
python3 load_chat.py --summarize results/*.jsonl
```

GPU 메모리·사용률은 측정 동안 GPU EC2에서 `nvidia-smi --query-gpu=timestamp,memory.used,utilization.gpu --format=csv -l 1 > gpu-<label>.csv`로 남긴다.

## 로컬 동작 확인 (2026-10-19, 숫자는 근거로 쓰지 않음)

로컬 Ollama 엑사원과 측정 DB 복사본으로 장애 주입과 기록만 확인했다.

- S1: Bedrock 호출이 모두 `CONNECTION_FAILED`(약 2ms)로 끝나고, 같은 호출이 엑사원으로 넘어가 4턴 모두 답했다.
- S2: Bedrock 호출이 모두 `TIMEOUT`(8.0초)으로 끝난 뒤 엑사원으로 넘어갔다. 질문 2개에서 8초 시간 초과가 23번 나왔고 두 턴 모두 1분을 넘었다. 대부분 Bedrock 대기라 서빙과 관계없다. CloudFront(30초)·ALB(60초) 기본 시간 제한을 넘는 길이라, Bedrock이 느려지는 장애에는 대체 경로에 차단 장치(연속 실패 시 일정 시간 바로 엑사원)가 필요하다는 근거다.
- 9번 회색 구간 판정은 엑사원 대체가 없어 장애 동안 판정 실패로 처리된다(`EVIDENCE_CHECK`의 bedrock 실패에 포함).

## 끝나면

- GPU EC2를 측정만 하려고 띄웠으면 종료하고 볼륨·스팟 요청이 남지 않았는지 확인한다.
- 결과(`results/`, `gpu-*.csv`, `calls.sql` 출력)는 압축해 결과 문서와 함께 남긴다.
