# 2026-10-17 평가셋 라벨 수정과 기준 보완 후 재판정

[v2.1 서비스 데이터 측정](../20261016-v2_1-service-data/README.md)과 [흐름 평가셋 v1](../20261016-flows-v1/README.md)의 답변을 그대로 두고, 평가셋 라벨과 판정 기준을 고친 뒤 판정 입력이 바뀐 턴만 다시 판정했다(`run_bedrock_judge.py --prior`). 사람이 정할 항목은 [라벨 검토 목록](../../LABEL_REVIEW.md)에 있다.

## 바꾼 것

| 대상 | 내용 | 다시 판정한 턴 |
| --- | --- | --- |
| 서비스 품질셋 복합 질문 20개 | 질문 부분 이름이 원래 단일 질문 문장이었다(C015: 고객은 평일 마감을 묻는데 질문 부분은 "토요일에 몇 시까지, 일요일도 열어요?"). 고객 문장에서 나눈 부분 질문으로 바꿨다(`compound_parts.json`, 별도 작성자). 이어서 부분 질문이 묻지 않는 사실 9곳을 필수 사실에서 뺐다([검토 목록](../../LABEL_REVIEW.md) D2, 문항별 결정) | 20 |
| 서비스 품질셋 검수 메모 37건 | 정답 FAQ 검수 메모를 턴의 `reviewNotes`로 옮겼다(판정 입력은 아니다) | 0 |
| 흐름 평가셋 되묻기 8개 | 필수 사실을 두 갈래로 나눴다. 앱이 되물어 고객이 조건을 말했으면 그 상황의 사실, 되묻지 않고 바로 답했으면 갈리는 경우를 모두 안내하는 사실(`requiredFactsIfAnsweredDirectly`, 별도 작성자) | 8 |
| 요금제 추천 답변 | 앱의 추천 규칙(제공량이 평균 사용량의 1.2배 이상이면 알맞다 등)과, 회원이면 지금 요금제가 규칙상 알맞은지를 근거 `SERVICE:RECOMMENDATION_RULE`로 넣었다 | 6 |
| 매장 판정 | 고객이 '현재 위치 사용'으로 답했으면 현재 위치 기준, 지명을 말했으면 지명 기준으로 찾았는지 본다(`STORE_LOCATION_MISMATCH`). 이번 결과에서 걸린 턴은 없다 | 0 |
| 보류 | 답이 없는 질문에 검색 결과 0건 고정 문구로 답한 턴을 `NO_SEARCH_RESULT_FALLBACK`으로 따로 센다. 보류로 인정한다([검토 목록](../../LABEL_REVIEW.md) D1, A 결정) | 0 |

비용은 Qwen $0.07, DeepSeek(오레곤) $0.17, Mistral $0.17다(D2 반영 재판정 8턴 포함).

## 결과 (Qwen3 235B + DeepSeek V3.2, 하나라도 실패면 실패)

| 평가셋 | 묶음 | 수정 전 | 수정 후 |
| --- | --- | --- | --- |
| 서비스 품질셋 v2.1 | 전체 품질 통과 | 38.1% (96/252) | 38.1% (96/252) |
| | 복합 질문 | 1/20 | 1/20 |
| 흐름 평가셋 v1 | 전체 품질 통과 | 35.1% (13/37) | 37.8% (14/37) |
| | 되묻기 | 3/12 | 3/12 |
| | 로그인 회원 | 8/13 | 9/13 |

- 복합 질문 이름을 바로잡고 묻지 않은 사실 9곳을 빼도 통과는 1/20 그대로였다. 앱 답변이 고객이 물은 한쪽 질문 자체를 빠뜨렸기 때문이다. 복합 질문의 실패는 라벨 탓이 아니다.
- 되묻기 8개는 바로 답한 경우의 사실로 판정해도 통과가 3개로 같다. CF06은 근거 없는 답에서 근거 있음·부분 답으로 바뀌었다(두 사람이 함께 가면 신분증만으로 된다는 경우를 빠뜨림).
- MD09 둘째 턴("지금 요금제가 사용량에 알맞아요")은 추천 규칙 근거가 들어가자 세 모델 모두 근거 있음으로 봤고 통과로 바뀌었다.
- 검색 결과 0건 고정 문구는 서비스 품질셋 259턴 중 71턴에 나왔다(답이 없는 질문 28, 답이 있는 질문 35, 범위 밖 7). 답이 없는 질문의 통과 34턴 중 26턴이 이 문구다. 이 문구로 통과한 26턴을 빼고 보면 결합 통과율은 27.8%(70/252)다. 보류로 인정하기로 했으므로 공식 수치는 38.1%다.

## 파일

`raw.tar.xz` 안에 `quality-v2_1/`, `flows-v1/` 폴더로 캡처(새 라벨), 모델별 판정(지금 코드 규칙으로 다시 집계), 결합 판정이 있다.

```powershell
mkdir .measure/label-fixes
tar -xf docs/chat-judge/results/20261017-label-fixes/raw.tar.xz -C .measure/label-fixes
python -X utf8 -m scripts.chat_judge.combine_judges .measure/label-fixes/quality-v2_1/qwen.json .measure/label-fixes/quality-v2_1/deepseek-v3.2-usw2.json --capture .measure/label-fixes/quality-v2_1/capture.json
python -X utf8 -m scripts.chat_judge.combine_judges .measure/label-fixes/flows-v1/qwen.json .measure/label-fixes/flows-v1/deepseek-v3.2-usw2.json --capture .measure/label-fixes/flows-v1/capture.json
```
