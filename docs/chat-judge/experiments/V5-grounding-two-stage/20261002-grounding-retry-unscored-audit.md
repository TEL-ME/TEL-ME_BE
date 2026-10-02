# 근거성 Judge 미채점 사례 점검

## 기존 35건에서 확인한 원인

기존 vLLM 재시도 결과의 35건을 사람이 답변·FAQ·Judge 기록과 대조했다.

| 유형 | 건수 | 사례 |
|---|---:|---|
| 답변 밖 내용을 claim으로 만들거나 답변 범위를 넓힘 | 32 | 질문·FAQ의 내용을 답변 주장에 섞거나 조건을 일반화함 |
| 답변 문구를 정확한 인용 구간으로 복구하지 못함 | 3 | USIM-0011, SERVICE-0056, SERVICE-0063 |

사용자 확인에 따라 `NAME_CHANGE-0038`은 claim 추출 오류로 분류했다. Judge가 답변에 없는, 조건을 제거한 일반 주장을 만들었다.

## 새 2단계 Judge 재실행

위 35건을 최종적으로는 **vLLM 0.29.0과 Qwen3-14B-AWQ**에서 동시성 8로 재실행했다. 첫 단계는 답변만 받아 원문 claim을 추출하고, 두 번째 단계는 claim과 FAQ 답변만 받아 의미를 판정한다. 동적 JSON Schema가 추출된 claim의 정확한 텍스트와 개수를 제한한다. WSL2 UVA 문제는 `test/vllm_patch/buffer_utils.py`로 기존 테스트 환경과 같이 우회했다.

결과는 34건 검증 통과, 1건 미채점이다. 미채점 `USIM-0011`에서 claim 추출기가 “2,750원이며”를 “2,750원입니다”로 바꿨고, 코드가 원문과 다르다고 거부했다. 이 실패는 사람이 답을 대신 정해 넣지 않고 미채점으로 보존했다. 판정 수와 의미는 [vLLM 재실행 보고서](20261002-grounding-two-stage-35-results.md)와 [최종 원시 기록](20261002-grounding-two-stage-vllm-retry-35-raw.json.gz)을 본다.

**실행 환경 정정:** 작업 중 첫 실행을 잘못 Ollama로 수행했다. 그 결과는 `20261002-grounding-two-stage-ollama-retry-35-raw.json.gz`로 보존했지만 최종 vLLM 결과로 사용하지 않는다. 최종 결과는 기존 실험과 같은 vLLM 0.29.0 / AWQ / 동시성 8 / WSL2 우회 패치 설정에서 얻었다.

## 범위

35건 결과는 출력 형식과 원문 일치 검사의 통과 여부를 보여준다. 500건 전체의 정확도나 Judge와 사람 간 일치율을 뜻하지 않는다. 별도 40건 사람 검토가 완료되어야 의미 판정의 신뢰도를 평가할 수 있다: [눈가림 검토 자료](20261002-grounding-human-review-blind.json), [검토 절차](20261002-grounding-human-review-method.md).
