# 2026-10-16 Judge 검증셋 v1, Bedrock Judge 후보 비교

[Judge 검증셋](../../README.md#평가셋) 116건(사람 판정은 판정자 A만)을 Bedrock 후보 모델로 판정하고 사람 판정과 비교했다. 숫자는 116건 중 사람과 같은 건수다. 최종 판정은 세 축을 합친 통과·실패이고, 미확정은 일치에서 뺐다.

| 실행 | Judge | 근거성 | 충족도 | 보류 | 최종 판정 | 미확정 | 사람은 실패인데 통과 | 사람은 통과인데 실패 | 비용 | 걸린 시간 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 필수 사실 없음 | Qwen3 235B | 93 | 81 | 93 | 88 | 7 | 16 | 5 | $0.16 | 2분 |
| | DeepSeek V3.2 | 100 | 79 | 98 | 91 | 1 | 24 | 0 | $0.44 | 12분 |
| | Mistral Large 3 | 81 | 83 | 81 | 78 | 22 | 12 | 4 | $0.35 | 3분 |
| | Qwen3 Next 80B | 87 | 66 | 78 | 66 | 21 | 27 | 2 | $0.17 | 3분 |
| 필수 사실 붙임 | Qwen3 235B | 95 | 83 | 95 | 91 | 6 | 15 | 4 | $0.16 | 2분 |
| | DeepSeek V3.2 | 98 | 80 | 95 | 89 | 1 | 21 | 5 | $0.44 | 11분 |
| | Mistral Large 3 | 89 | 78 | 86 | 81 | 18 | 15 | 2 | $0.36 | 3분 |
| 필수 사실 + 엄격 프롬프트 | Qwen3 235B | 91 | 83 | 90 | 88 | 8 | 15 | 5 | $0.16 | 2분 |
| | DeepSeek V3.2 | 95 | 79 | 92 | 83 | 1 | 28 | 4 | $0.46 | 14분 |
| | Mistral Large 3 | 83 | 84 | 81 | 79 | 24 | 9 | 4 | $0.36 | 4분 |

판정 뒤 결합(`combine_judges.py --validation`, 필수 사실 붙인 실행):

| 조합 | 근거성 | 충족도 | 보류 | 최종 판정 | 미확정 | 사람은 실패인데 통과 | 사람은 통과인데 실패 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Qwen3 235B + DeepSeek V3.2, 하나라도 실패면 실패 | 101 | 83 | 98 | 97 | 3 | 10 | 6 |
| 세 모델, 하나라도 실패면 실패 | 95 | 83 | 91 | 97 | 7 | 6 | 6 |
| 세 모델, 다수결 | 95 | 83 | 91 | 96 | 3 | 14 | 3 |

- Qwen3 Next 80B는 충족도가 약해 후보에서 뺐다. 이후 후보는 Qwen3 235B, DeepSeek V3.2, Mistral Large 3다.
- Mistral Large 3는 인용 문장을 원문과 다르게 옮겨 미확정이 많다.
- 엄격 프롬프트(조건·금액·대상을 바꿔 말하면 충족 아님)는 충족도 일치를 올리지 못해 되돌렸다. 충족도 불일치는 대부분 사람이 정확성까지 충족도에 섞어 판정한 경우다.
- 비용은 공개 요금표 기준 추정치(도쿄)다. DeepSeek V3.2는 도쿄 처리량이 막혀 느렸고, 이후 실행은 오레곤을 쓴다([v2 결합](../20261016-v2-judge-ensemble/README.md#deepseek-v32-리전)).

## 파일

원시 파일은 `raw.tar.xz` 하나로 묶었다. 실행마다 `<모델>.json`(필수 사실 없음), `facts-<모델>.json`, `strict-prompt-<모델>.json`이 있고, 각 파일에 요청, 응답, 토큰, 판정이 들어 있다.

```powershell
mkdir .measure/judge-candidates
tar -xf docs/03-evaluation/chat-judge/results/20261016-judge-candidates/raw.tar.xz -C .measure/judge-candidates
python -X utf8 -m scripts.chat_judge.combine_judges .measure/judge-candidates/facts-qwen.json .measure/judge-candidates/facts-deepseek-v3.2.json --validation
python -X utf8 -m scripts.chat_judge.run_judge_validation --bedrock-model qwen --allow-bedrock-call --out <새 결과>
```
