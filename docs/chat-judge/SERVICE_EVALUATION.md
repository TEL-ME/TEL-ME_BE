# 측정 절차

실제 채팅 API로 평가셋 질문의 답변을 모으고(캡처), 저장한 답변을 Judge로 판정한 뒤 모델별 판정을 결합한다. 판정 기준과 코드 규칙은 [평가 기준](CHAT_PIPELINE_QUALITY_CRITERIA.md)에 있다.

## 1. 캡처

```powershell
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --capture-only --out .measure/<실행 이름>
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --fixture scripts/chat_judge/data/chat_pipeline_flows_v2.json --capture-only --out .measure/<실행 이름>
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --limit 40 --capture-only --out .measure/<실행 이름>   # 짧은 확인
```

운영과 같은 경로(Bedrock gpt-oss, 회색 구간 판정, FAQ 되묻기)로 재려면 아래처럼 실행한다. 테스트 설정(`src/test/resources/application.properties`)이 세 기능을 꺼 두므로 환경 변수로 다시 켠다.

```bash
export AWS_PROFILE=telme-bedrock-eval POSTGRES_PORT=5440   LLM_BEDROCK_ENABLED=true LLM_BEDROCK_REGION=ap-northeast-1   SEARCH_GRAYZONE_ENABLED=true TELME_CONSULT_FAQCLARIFICATION_ENABLED=true   ROUTING_REQUEST_INVENTORY_ENABLED=false TELME_SUBSCRIPTION_DEMO_MODE=true
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --capture-only   --postgres-container telme-flow-postgres --database telme_judge_<날짜> --out .measure/<실행 이름>
```

- `SEARCH_GRAYZONE_ENABLED`, `TELME_CONSULT_FAQCLARIFICATION_ENABLED`는 테스트 설정 파일보다 우선하는 이름(점·대시를 뺀 형태)이다. `SEARCH_GRAY_ZONE_ENABLED`처럼 앱 설정의 기본값 이름으로는 테스트 설정을 덮지 못한다.
- 리전과 설정 값은 운영 배포 설정(`infra/terraform/config.tf`)을 따른다. 측정 뒤 캡처의 `appConfig`에서 실제 값을 확인한다.

- 기본 입력은 서비스 품질셋 v2.2다. `--limit`은 대화 수이고, 흐름 회귀 대화는 모두 넣은 뒤 분야·질문 유형을 돌아가며 고른다.
- 캡처 프로브(`ChatJudgeCaptureProbe`)가 Spring 채팅 API로 질문을 보내고 실행 기록을 모은다. 기대 동작, 필수 사실, 정답 FAQ는 평가용으로만 쓰고 앱에 전달하지 않는다.
  - 앱이 조건을 되물으면 평가셋의 고객 답변을 보낸다. '현재 위치 사용'은 좌표로 보낸다. 같은 조건을 다시 물으면 같은 답을 한 번 더 하고, 평가셋에 없는 조건을 물으면 멈춘다.
  - '현재 위치 사용'으로 답한 뒤 되묻기가 이어지는 동안에는 그 좌표를 다음 답에도 붙인다. 프론트(`heldCoords.ts`)와 같은 동작이다. 서버는 좌표를 저장하지 않아, 붙이지 않으면 업무에 답한 뒤 지역을 다시 묻는다.
  - `actor`가 `MEMBER`인 대화는 테스트 회원(`judge-member@telme.test`) 세션으로 보낸다. 평가 DB에 없으면 만든다.
- **평가 DB는 `telme_judge_`로 시작하는 DB만 쓴다.** 기본은 `telme_judge_eval`이고, `--database`로 측정마다 새 DB를 줄 수 있다. 없으면 `--postgres-container`의 `telme` DB를 복제한다. 다른 컨테이너의 DB를 쓰려면 `POSTGRES_PORT`를 맞춘다(예: `telme-flow-postgres`는 5440).
- 앱 설정은 환경 변수 그대로 쓰고, 실제 적용된 값을 캡처의 `appConfig`에 남긴다. 회색 구간 근거 판정이 앱 쪽에서 Bedrock을 부르므로, 그 호출을 막으려면 `SEARCH_GRAY_ZONE_ENABLED=false`로 끈다.
- 지명으로 찾는 매장 대화는 `KAKAO_REST_API_KEY`가 있어야 한다. 없으면 실행기가 빼고 `manifest.json`의 `skippedCases`에 남긴다.
- 로컬 생성 모델(EXAONE, `telme-ollama`)로 돌릴 때 16GB GPU에서는 생성이 끝난 뒤 Ollama를 내리고 판정용 vLLM을 올린다. 실행기가 그 순서로 컨테이너를 켜고 끈다.

캡처 파일(`capture.json`)에는 턴마다 질문과 라벨, 라우팅, 검색 결과, 실제 RAG 입력(`ragInputs`), 생성 호출 기록, 저장 답변, 저장 근거, 실행 단계 기록(`pipelineTrace`), 되묻기 대화(`clarificationExchanges`), 매장 상세(`storeDetails`), Bedrock 호출 기록(`bedrockCalls`: 모델, 시스템 프롬프트 첫 줄, 입출력 토큰, 지연, 종료 이유)이 있다. `--record-raw`를 주면 `bedrockCalls`에 원본 요청·응답도 남긴다. 상담 내용이 들어가므로 로컬 분석에만 쓰고 결과 묶음(`raw.tar.xz`)에는 넣지 않는다. 실행 전체에는 요금제 표(`serviceData`), 적용된 설정(`appConfig`), 회원 대화의 계정 데이터(`memberData`)가 있다. 실행 단계 기록이 없는 예전 캡처는 평가 DB가 남아 있으면 `backfill_capture_service_data.py`로 채운다.

## 2. 판정

**Bedrock(대표 수치)**

```powershell
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model qwen --allow-bedrock-call --out <qwen.json.gz>
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model deepseek-v3.2-usw2 --workers 16 --allow-bedrock-call --out <deepseek.json.gz>
python -X utf8 -m scripts.chat_judge.run_bedrock_judge <캡처> --model mistral-large-3 --allow-bedrock-call --out <mistral.json.gz>
```

- 대표 수치는 세 모델 결합이다([판정 모델 선정](README.md#판정-모델-선정)). 모델은 하나씩 따로 돌리고, 서로 기다릴 필요가 없어 동시에 돌린다. 별칭과 요금은 `bedrock.py`의 `MODELS`, `PRICES`에 있다. DeepSeek V3.2는 도쿄 리전의 처리량이 막혀 오레곤(`deepseek-v3.2-usw2`)을 쓴다.
- 유료 호출이라 `--allow-bedrock-call`을 줘야 하고, 기존 결과를 덮어쓰지 않는다.
- `--prior <이전 결과>`를 주면 판정 입력(답변, 근거, 판정에 쓰는 라벨, 이전 대화)이 같은 턴은 이전 판정을 재사용하고 바뀐 턴만 부른다. 결과에 판정 입력 해시(`judgeInputSha256`)가 남는다.
- 같은 모델을 다시 돌려도 최종 판정의 약 5%가 바뀐다. 최종 수치는 2~3회 반복해 범위로 보고한다.

**vLLM(로컬)**

```powershell
python -u -X utf8 -m scripts.chat_judge.run_chat_pipeline_eval --capture .measure/<실행 이름>/capture.json
```

`telme-judge-vllm-bounded` 컨테이너의 Qwen3-14B-AWQ(vLLM 0.29.0, 문맥 4096, 동시 8)로 같은 프롬프트와 코드 규칙을 쓴다. 중단된 판정은 같은 코드, 입력, 설정일 때만 이어서 한다(`evaluate_chat_pipeline.py --resume`). 완료된 오류 판정도 보존한다.

## 3. 결합과 집계

```powershell
python -X utf8 -m scripts.chat_judge.combine_judges <qwen.json.gz> <deepseek.json.gz> <mistral.json.gz> --capture <캡처> --out <결합.json>
```

- 기본 규칙은 하나라도 실패면 실패다(`--rule majority`는 다수결). 근거성은 한쪽이라도 근거 없음이면 근거 없음, 충족도는 가장 낮은 판정, 보류는 한쪽이라도 지어낸 답이나 과도한 거절을 잡으면 그 판정이다. 판정하지 못한 모델이 있고 나머지가 모두 통과면 미확정이다.
- `--capture`를 주면 저장된 판정에 지금 코드 규칙을 다시 적용한 뒤 합친다(모델 호출 없음).
- 출력에는 전체·묶음별 통과율, 95% 신뢰구간, 근거 없는 답 비율, 묶음 평균 통과율(`suiteMacroPassPercent`)이 있다. `--validation`은 Judge 검증셋 결과를 사람 판정과 비교한다.

## 4. 결과 남기기

측정 결과는 `docs/chat-judge/results/<날짜>-<이름>/`에 `README.md`와 원시 파일 묶음 `raw.tar.xz` 하나로 남긴다. 모델끼리 겹치는 요청 본문이 많아 묶어서 압축하면 1/10로 준다. README에는 실행 조건(코드 커밋, 생성 모델, 평가 DB, 앱 설정), 비용, 결과 표, 해석, 다시 집계하는 명령을 적는다.

```powershell
mkdir .measure/<이름>
tar -xf docs/chat-judge/results/<폴더>/raw.tar.xz -C .measure/<이름>
```

## 5. 해석할 때 주의할 것

- 고정 평가셋에서 자동 Judge가 추정한 품질이다. 실제 이용자 전체의 환각률이나 사람이 확정한 점수로 표현하지 않는다.
- 묶음 표본이 19~120개라 묶음 수치의 신뢰구간이 넓다. 같은 질문으로 전후를 비교한다.
- 최종 저장 답변을 판정하며, 사용자가 받은 최종 SSE 내용과의 일치는 점수에 넣지 않는다.
- 라벨을 바꾸면 이전 캡처에 `relabel_capture.py`로 새 라벨을 입히고 같은 답변으로 다시 판정해 비교한다.
