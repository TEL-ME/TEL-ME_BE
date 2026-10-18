# 라벨 검증 지침

다른 에이전트가 `LABEL_GUIDE.md`(같은 폴더)에 따라 만든 라벨을 FAQ 카탈로그 전체와 대조해 검증한다.
먼저 `LABEL_GUIDE.md`를 읽는다. 라벨 기준은 그 문서와 같다.

## 자료

- FAQ 카탈로그: `D:\finalproject\TEL-ME_BE_180\scripts\data\faq_full_1150.json` (answer 본문만 정책 근거)
- 라벨 입력(질문·대화·조건): `label-in\<묶음>.json`
- 작성된 라벨: `label-out\<묶음>.jsonl`
- 데이터셋 참고 정보: `label-ref.json`의 항목별 `datasetMeta`. A 묶음은 평가셋 설계자가 정한 정답 FAQ(`goldSlotIds`)와 설명(`note`, `risk`, `expectNoAnswer`)이 있다.
  설계자 정보도 틀릴 수 있다. 라벨과 다르면 카탈로그를 직접 읽어 어느 쪽이 맞는지 판단한다. B 묶음은 정답 정보가 없다.

다른 파일(평가셋, 결과, 챗봇 답변)은 열지 않는다.

## 항목마다 확인할 것

1. `expectedBehavior`가 맞는가. 특히 ABSTAIN/PARTIAL_ANSWER라고 했는데 카탈로그에 답이 있는지, ANSWER라고 했는데 실제로는 다른 상황의 FAQ인지를 Grep으로 여러 표현을 찾아 확인한다.
2. `parts`의 `sourceIds`가 그 질문 부분에 answer 본문으로 직접 답하는가(틀린 것은 빼고), 같은 정책을 말하는 FAQ가 빠지지 않았는가(빠진 것은 더한다).
3. `requiredFacts`가 질문이 묻는 사실만 담는가, 카탈로그 answer 본문과 일치하는가, 조건(customerConditions, askedButUnanswered)을 반영했는가.
4. 질문 부분 나누기가 맞는가(묻는 것이 두 가지면 두 부분).

## 출력 (항목마다 한 줄 JSONL, 입력 순서대로)

```json
{"id": "...", "verdict": "AGREE", "label": { ...최종 라벨, LABEL_GUIDE 출력 형식 그대로... }, "changes": "", "reviewReason": ""}
```

- `verdict`
  - `AGREE`: 라벨이 맞다. `label`은 원래 라벨 그대로.
  - `FIXED`: 고쳤다. `label`은 고친 라벨, `changes`에 무엇을 왜 바꿨는지(한국어, 근거 slot_id 포함).
  - `DISPUTE`: 카탈로그만으로 판단이 갈린다(FAQ끼리 충돌, 질문 해석이 둘로 갈림, 설계자 정답과 카탈로그가 어긋나는데 확신할 수 없음). `label`은 네가 더 맞다고 보는 쪽, `reviewReason`에 사람이 봐야 할 점.
- 사소한 문구 차이(같은 사실을 다르게 쓴 requiredFacts)는 고치지 않는다. 판정 결과가 달라질 차이만 고친다.

입력 항목 수와 출력 줄 수가 같아야 한다.
