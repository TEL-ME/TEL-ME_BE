# TELME-186 EXAONE 실행 환경 대조표 (실행 전)

- 기준: TELME-183 실제 실행 manifest (`scripts/*/results/TELME-183-20261017/results.zip` 내부 `results/manifest.json`, `native-runtime-observed.json`, `memory-observed.txt`)
- 확인일: 2026-10-18 (Asia/Seoul). 이 문서 작성 시점에는 평가 호출을 하지 않았다. 모델은 로드 상태 확인만 하고 생성은 하지 않았다.
- 프롬프트·가드 고정은 합의한 비교 조건이다. TELME-178(#167) 측정 당시 상태 전체의 완전 재현이 아니다.

판정: ✅ 일치 / ⚠️ 원본 기록 없음(추정해서 설정하지 않음) / ❌ 불일치

## 1. 런타임과 모델

| 항목 | TELME-183 기록 | 현재 실측 | 판정 |
| --- | --- | --- | --- |
| Ollama 버전 | 0.34.0, 네이티브 macOS | 0.34.0 (`/private/tmp/telme-exaone-reproduction/runtime/0.34.0/ollama`) | ✅ |
| 배포본 SHA-256 | `dd12b00b…e8ef734`, 공식 체크섬 일치 | `dd12b00b…e8ef734`, 공식 `sha256sum.txt`와 일치 | ✅ |
| 압축 해제본 | 기록 없음 | 배포본을 새로 풀어 대조: 바이너리·라이브러리 전부 동일, macOS 메타데이터 `._mlx.metallib` 2개만 추가 | ✅ (메타데이터만 차이) |
| 모델 | `exaone3.5:7.8b`, Q4_K_M | 동일 | ✅ |
| manifest digest | `c7c4e3d1ca22fe9225f18b35eb719f67e2ca96a42e7fd17294a45b83ba8fbf03` | 동일 (manifest 파일 SHA-256 직접 계산) | ✅ |
| 레이어 blob 6개 | 기록 없음 | config·model·template·system·license·params 모두 파일 SHA-256 = digest | ✅ |
| template·params·system | 별도 텍스트 기록 없음 | manifest digest가 각 레이어 digest를 포함하므로 동일로 판정. params `repeat_penalty 1`, `stop [|endofturn|]`, `temperature 1`(요청 값이 덮어씀) | ✅ (digest 근거) |
| 로드 크기 (`/api/ps` size·size_vram) | 5,717,517,925 B (5452.6 MiB) | 5,953,447,525 B (5677.6 MiB), 두 번 로드해도 같음 | ❌ +225 MiB |

로드 크기 차이는 llama.cpp 로그의 `CPU_Mapped model buffer 225.00 MiB`와 크기가 같다. 현재는 출력 레이어를 GPU 매핑 버퍼(4545.64 MiB)에 포함해 보고하고, TELME-183 값은 fit 예측치(model 4320 + context 1024 + compute 108 MiB)와 같다. 183의 서버 로그가 보존되지 않아 원인을 확정하지 못했다.

## 2. 서버 설정

| 항목 | TELME-183 기록 | 현재 (0.34.0 `server config` 로그) | 판정 |
| --- | --- | --- | --- |
| 주소 | 127.0.0.1:11436 | `OLLAMA_HOST=127.0.0.1:11436` | ✅ |
| 컨텍스트 | 8192 | `OLLAMA_CONTEXT_LENGTH=8192`, ps `context_length 8192`, llama `n_ctx 8192` | ✅ |
| keep-alive | 10m | `OLLAMA_KEEP_ALIVE=10m0s` | ✅ |
| 병렬 | 명시 안 함, 서버 기본 1 관측 | 명시 안 함, `OLLAMA_NUM_PARALLEL:1`, `n_slots = 1` | ✅ |
| cloud | 비활성 | `OLLAMA_NO_CLOUD=1`, `Ollama cloud disabled: true` | ✅ |
| flash attention | 기록 없음 | env 미설정(false) → llama.cpp `auto`, 실제 `Flash Attention enabled` | ⚠️ |
| KV cache 형식 | 기록 없음 | 미설정 → f16 (K 512 + V 512 MiB) | ⚠️ |
| MAX_LOADED_MODELS·SCHED_SPREAD·GPU_OVERHEAD·LLAMA_ARG_FIT | 기록 없음 | 전부 미설정(기본값) | ⚠️ |
| 모델 경로 | 기록 없음 | `/Users/choib/.ollama/models` (기본값) | ⚠️ |

⚠️ 항목은 TELME-183 manifest에 값이 없다. 추정값을 넣지 않고 미설정(기본값)으로 둔다.

## 3. 생성 옵션 (코드 기준, 실제 요청 캡처는 아직 안 함)

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 호출 경로 | `RagAnswerGenerator` → `OllamaClient.stream` | `RagAnswerGenerator.java:122` |
| temperature | 0.0 | `OllamaRequestConverter` `RAG_ANSWER` 기본값, 요청에서 지정 안 함 |
| num_predict | 1024 | 같은 기본값 |
| num_ctx | 8192 | `ProbeModelClients.exaone()`의 `LlmProperties` |
| format·think·keep_alive·seed | 없음 | 요청 DTO에 keep_alive·seed 필드 없음. exaone3.5는 think 대상 아님 |
| 문항 동시성 | 1 | `AnswerGenerationProbe.java:71` (exaone 스레드 풀 1) |
| 클라이언트 시간 제한 | 연결 5초, 읽기 180초 | `ProbeModelClients.exaone()` |

TELME-178의 EXAONE 결과는 스트리밍 경로라 호출별 옵션이 기록되지 않았다(`calls: []`). 위 값은 코드에서 읽은 것이며 실행 시 실제 요청으로 한 번 더 확인해야 한다.

## 4. 하드웨어·OS·메모리

| 항목 | TELME-183 기록 | 현재 실측 | 판정 |
| --- | --- | --- | --- |
| 칩 | Apple M4 | Apple M4 (Mac16,10), CPU 10코어(P4+E6), GPU Metal 4 | ✅ |
| OS | macOS (버전 기록 없음) | macOS 26.6.2 (25G83). 설치일 2026-08-26, 부팅 2026-10-08 이후 재부팅 없음 → 183 측정(10-17)과 같은 부팅 세션 | ✅ (설치 이력 근거) |
| 통합 메모리 | 17,179,869,184 B | 17,179,869,184 B | ✅ |
| Metal 가용 용량 | 11.8 GiB | 11.8 GiB (`inference compute total="11.8 GiB"`) | ✅ |
| GPU wired 제한 | 기록 없음 | `iogpu.wired_limit_mb=0` (기본값) | ⚠️ 설정하지 않음 |
| 원본 RAM 할당 제한 | 미제공 (`Host RAM allocation limits not supplied`) | 설정하지 않음 | ⚠️ 설정하지 않음 |
| 스왑 | 관측 9216M 중 8698.25M 사용 | 7168M 중 6074.88M 사용 | ❌ 부하 상태 다름 (통제 불가) |
| 다른 Ollama | 정리 후 0.40.0 존재 기록 | 0.40.0이 11434에서 실행 중, 로드된 모델 없음 | ⚠️ 측정 중 상태 기록 없음 |

## 5. 프롬프트·가드 고정 (합의한 비교 조건)

| 항목 | 고정값 | 근거 |
| --- | --- | --- |
| 답변 프롬프트 | `rag-answer-v4.7` 측정 당시 본문 (`LG U+`), blob `09f7bc7e6e467c7c2e979ae9854aca4d8911f915` | 91737c3d부터 9e633361(가드 보정 후 B 재측정)까지 같은 blob |
| 주의 | 같은 `v4.7` 라벨로 `TELME` 본문인 커밋(e9bb5dd5, blob `edd3597e`)이 있다 | 라벨이 아니라 blob으로 고정한다 |
| 가드 | #167 최종 `AnswerGuard`, blob `191e71e89e5f17a830cf94b2698e0e0b6391412c` | 2bcfaaef = d556e53c = 현재 develop |
| 나머지 코드 | develop `35b33f37` | d556e53c 이후 `rag`·`llm/converter`·`llm/client`·`probe` 변경 없음 |

실행 스냅샷은 develop을 별도 디렉터리에 추출하고 `AnswerPromptTemplates.java`만 위 blob으로 바꾼다. 브랜치의 운영 코드는 바꾸지 않는다.

## 6. 실행 중 확인한 사항과 한계 (실행 후 추가)

- 요청 캡처는 로깅 프록시(`ollama_logging_proxy.py`, 11437 → 11436)로 했다. 1,086건 모두 같은 요청 옵션(`temperature 0.0`, `num_predict 1024`, `num_ctx 8192`), 모델 `exaone3.5:7.8b`, `format`·`think` 없음, 종료 사유 `stop`, HTTP 200이다. 코드에서 읽은 값(3절)과 일치한다.
- 서버 기동 로그의 `server config`로 컨텍스트 8192, keep-alive 10m0s, 병렬 1, cloud 비활성, flash attention env 미설정(llama.cpp auto로 활성)을 다시 확인했다.
- 로드 크기는 평가 중에도 5,953,447,525 B로 TELME-183(5,717,517,925 B)과 다르다. 원인은 확정하지 못했다(1절). 생성 결과에 영향이 있는지는 확인하지 못했다.
- 평가 직전 이전 세션에서 남은 0.34.0 서버(PID 56471, 환경변수 출처 불명)가 11436을 쓰고 있어 종료하고 같은 설정으로 다시 띄웠다. 평가 결과는 다시 띄운 서버의 것이다.
- 첫 시도는 프록시가 chunked 요청 본문을 읽지 못해 멈췄다. 프록시를 고친 뒤 처음부터 다시 실행했고, 중단된 시도의 결과는 쓰지 않았다.
- 측정 중 스왑은 약 6.6 GiB 사용(7,168M 중)이었고 183 측정 당시(8.7 GiB)와 다르다. 평가 동안 `caffeinate`로 잠자기를 막았다. 시간은 관측값이며 성능 비교로 쓰지 않는다.
- 원본 RAM 할당 제한은 기록이 없어 설정하지 않았다. `iogpu.wired_limit_mb`도 기본값(0)이다.
