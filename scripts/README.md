# FAQ 생성, 검증 스크립트

`docs/POLICY.md`와 `docs/FAQ_TAXONOMY.md`를 기준으로 FAQ 데이터를 만들고 검증

| 스크립트 | 역할 |
| --- | --- |
| `generate_faq.py` | 카테고리 × 질문유형 × 페르소나 × 사유 조합표를 목표 건수에 맞춰 배분 |
| `check_policy.py` | 답변 수치를 참조 정책 항목의 값과 대조 |
| `check_duplicates.py` | 임베딩 유사도로 중복 쌍 탐지 |
| `check_eval_questions.py` | 검색 품질 평가셋(`eval_questions_30.json`)의 정답 매핑·유사도 검증 |
| `measure_search_quality.py` | 평가셋을 검색 API에 돌려 Recall@k/MRR 계산 |
| `telme_docs.py` | 공통 문서 파서 (직접 실행하면 파싱 결과 요약) |

## 준비

`generate_faq.py`, `check_policy.py`는 파이썬 3.11 이상만 있으면 된다. 외부 패키지 없음.

`check_duplicates.py`만 Ollama가 필요

```bash
docker compose --profile ollama up -d
docker exec telme-ollama ollama pull bge-m3
```

1,000건 규모에서 쌍 비교가 느리면 numpy를 넣는다.
(없으면 순수 파이썬으로 동작)

```bash
python3 -m venv venv && source venv/bin/activate
pip install -r scripts/requirements.txt
```

## 1. 조합표 생성

```bash
python3 scripts/generate_faq.py --summary
python3 scripts/generate_faq.py --out scripts/data/faq_slots_1150.json
python3 scripts/generate_faq.py --sample 30 --out scripts/data/faq_slots_30.json
```

문장은 생성하지 않는다. 어떤 조합을 몇 건 써야 하는지와 각 칸에 인용할 정책 항목
(`policy_ref`, 제목, 핵심 수치)만 내보낸다. 여기에 `question`, `answer`를 채운 것이
`docs/FAQ_TAXONOMY.md` 7절의 산출물 형식.

`trigger`는 참고값이다. 같은 조합 안에서 문장을 벌리기 위한 변형 장치라, 배정된 사유가
정책 항목과 어색하면 무시하고 자연스러운 쪽으로 쓴다.

## 2. 정책 대조

```bash
python3 scripts/check_policy.py scripts/data/faq_sample_30.json
python3 scripts/check_policy.py --self-test
```

검사 항목

- 필수 필드(`category`, `question`, `answer`, `policy_ref`, `question_type`, `persona`) 존재
- `category`, `policy_ref`, `question_type`, `persona`가 문서에 정의된 값인지
- `policy_ref`가 그 카테고리의 항목이 맞는지
- 답변 수치가 `policy_ref`와 `extra_policy_refs`의 허용값 안에 있는지
- `extra_policy_refs`가 문자열 배열이고 `question_type`이 `COMPARE`인지
- `extra_policy_refs`가 실제로 존재하는 항목이고, 선언한 만큼 실제로 인용했는지

허용값 = (`policy_ref` ∪ `extra_policy_refs`) 각각의 정책 항목 블록 ∪ 정책 값 색인 행 ∪ 전체 공통 전제

### COMPARE의 교차 인용

`COMPARE` 답변은 정책 항목을 둘 이상 인용(`FAQ_TAXONOMY.md` 2절)

대표 항목만 `policy_ref`에 적으면 다른 항목의 정상 수치도 "정책에 없는 수치"로 걸림

인용한 나머지 항목을 `extra_policy_refs`에 적으면 그 항목의 수치까지 허용값이 됨

```json
{
  "category": "BILLING",
  "policy_ref": "BILLING-01",
  "question_type": "COMPARE",
  "persona": "EXPERIENCED",
  "extra_policy_refs": ["BILLING-02"],
  "question": "요금제 바꾸면 청구가 어떻게 되나요? 납부일도 같이 알려주세요",
  "answer": "요금제는 월 1회 변경할 수 있고 신청일 다음 날 00:00부터 적용됩니다. 청구서는 매월 10일 발송되고 납부 기한은 매월 25일입니다."
}
```

`generate_faq.py`는 `COMPARE` 조합에만 `"extra_policy_refs": []`를 미리 넣어 준다.
(비워 두면 대표 항목만 검사)

`COMPARE`가 아닌 질문유형이 값을 채우면 지적
(다른 유형에서도 받아주면 허용값을 넓히는 우회로가 됨)

필드가 있는데 문자열 배열이 아니면 `extra_policy_refs 형식 오류`
(배열을 빠뜨린 `"BILLING-02"`, 원소에 숫자가 섞인 경우, `""`·`0`·`{}`·`false`·`null`)

필드가 아예 없으면 지적하지 않는다. 있는데 값이 틀린 것과 구분

남발을 막기 위해, 선언한 항목의 수치가 답변에 하나도 없으면 `인용하지 않은 extra_policy_refs`로 지적

수치가 없는 항목(구비 서류 등)은 대조할 것이 없으므로 제외

단위가 붙은 수치만 본다.
`7,700원` `2~3 영업일` `24개월` `1년` `1월` `50GB` `09:00` `5.9%`는 보고,
단위 없는 맨숫자(`5G`, `114`, `1588-0000`)는 추출하지 않는다.

단위 목록은 `telme_docs.py`의 `_UNITS`. 교대(`|`)는 앞에서부터 매칭되므로
`개월`이 `월`보다, `영업일`이 `일`보다 앞에 있어야 한다.

수치가 없는 정책 항목(구비 서류 등)을 참조하면서 답변에 수치를 넣으면 전부 걸린다(의도됨).

## 3. 중복 탐지

```bash
python3 scripts/check_duplicates.py scripts/data/faq_sample_30.json
python3 scripts/check_duplicates.py scripts/data/faq_sample_30.json --threshold 0.93 --field both
python3 scripts/check_duplicates.py --self-test
```

`bge-m3`로 임베딩해 코사인 유사도가 임계값(기본 `0.95`) 이상인 쌍을 출력한다.
임계값 미만이어도 최고 유사도를 함께 찍는다.

- `--batch` 기본 50
- 임베딩은 `scripts/data/.embed_cache.json`에 캐시된다(git 제외)
- `--field both`는 질문+답변을 이어 붙여 본다. 질문이 달라도 답변이 같은 건을 찾을 때 쓴다.

## 4. 검색 품질 평가셋 검증

```bash
python3 scripts/check_eval_questions.py scripts/data/eval_questions_30.json
python3 scripts/check_eval_questions.py scripts/data/eval_questions_30.json --live
python3 scripts/check_eval_questions.py --self-test
```

`data/eval_questions_30.json`은 Recall@k/MRR 측정용 질문 30건이다. 

각 질문은
`SIMILAR`(원문 재표현) / `VARIANT`(같은 FAQ가 정답이지만 표현, 상황을 크게 바꿈) / `UNRELATED`(30건 어디에도 정답 없음) 중 하나로 라벨링되고, 정답은 `faq_id`가 아니라
`expected_content_hash`(대상 FAQ의 `SHA-256(question + answer)`)로 매핑된다.

`faq_id`는 적재할 때마다 DB가 새로 발급해 재적재하면 깨지지만, `content_hash`는 문장 내용에서만 정해지므로 몇 번을 다시 적재해도 살아남는다.

- 정적 검사(기본, Ollama 불필요): 정확히 30건인지, `type`이 세 값 중 하나인지,
  `SIMILAR`/`VARIANT`의 `expected_content_hash`가 `faq_sample_30.json`에 실제로 존재하는
  해시인지(수동 편집 사고로 어긋나지 않았는지), `UNRELATED`는 해시와 `expected_slot_id`가
  `null`인지 확인. 

- 유형별 10건씩인지, 카테고리 10종마다 `SIMILAR`·`VARIANT`가 정확히 1건씩인지(해시로 찾은 FAQ의 `category` 기준이라 `expected_slot_id`를 잘못 적어도 못 속임),
  그리고 `expected_slot_id`가 해시가 가리키는 FAQ의 `slot_id`와 일치하는지 분포도 확인

- `--live`(Ollama 필요, `check_duplicates.py`와 같은 임베딩 경로 재사용): 각 `SIMILAR`/
  `VARIANT` 질문을 실제로 임베딩해서 자신의 정답 FAQ가 30건 중 최고 유사도로 나오는지 확인하고,
  `UNRELATED` 10건의 유사도 분포(최댓값/평균)를 출력한다. 이 평가셋을 넘기기 전에
  "이 질문이 실제로 의도한 FAQ를 가리키는가"를 미리 실측해두는 단계.

- `--self-test`: 일부러 틀린 예시 10건으로 정적 검사가 내는 지적 12종(`STATIC_KINDS`)이 전부 실제로
  걸리는지 확인한다. 검사 종류를 추가하면 `STATIC_KINDS`와 픽스처에 같이 넣어야 통과.

## 5. 검색 품질 측정

```bash
python3 scripts/measure_search_quality.py scripts/data/eval_smoke.json
python3 scripts/measure_search_quality.py scripts/data/eval_questions_30.json --experiment 기준선
python3 scripts/measure_search_quality.py --self-test
```

`check_eval_questions.py`가 검증한 평가셋을 실제 검색 API(`/api/v1/faq/search`, TELME-38)에 돌려
Recall@1/3/5, MRR을 계산한다. `content_hash`는 `check_eval_questions.py`에서 그대로 import해서 쓴다.

**순위 실험(Recall@k·MRR)은 서버를 `SEARCH_SIMILARITY_THRESHOLD=0`으로 띄우고 돌린다.**
`PgvectorFaqSearchService`가 임계값 미만 후보를 순위를 매기기 전에 잘라내므로, 임계값을 켠 채로
재면 "랭킹 품질"이 아니라 "임계값 통과 후 랭킹 품질"이 섞여서 측정된다. 임계값 자체를 정하는 건
별도 단계(2주차 캘리브레이션)에서 한다.

- `--top-k`(기본 3), `--api-url`(기본 `http://localhost:8080/api/v1/faq/search`),
  `--timeout`(기본 20초 — 서버의 `embedding.search-read-timeout`(15초)보다 길어야 함)
- `--experiment`를 주면 튜닝 실험 기록표에 붙여넣을 수 있는 한 줄
  (`| 실험 | 변경 | Recall@1 | Recall@3 | MRR | 담당 |`)도 같이 출력. `--change`/`--owner`(기본 A)로
  나머지 칸을 채운다.
- 긍정 질문(`SIMILAR`/`VARIANT`)이 하나도 없으면 recall/mrr은 측정하지 않은 것으로 처리한다 —
  `--experiment`와 같이 쓰면 가짜 `0.000` 행 대신 에러로 중단한다.
- threshold=0으로 돌리면 "정답 hit score(최소/중앙값)"와 "UNRELATED top-1 score(최댓값)"도 같이
  찍는다. 정답 hit score 최소값이 UNRELATED top-1 최댓값보다 높으면 그 사이가 임계값 후보고,
  두 분포가 겹치면(반대가 되면) 깔끔하게 나누는 값 자체가 없다는 뜻이라 그것대로 유용한 진단이다.
- 정답을 못 찾은 질문은 `eval_id`로 나열된다. 그중에서도 같은 정답 FAQ를 공유하는 `SIMILAR`/
  `VARIANT`가 **둘 다** 한 번도 안 나온 경우만 따로 "적재 누락 의심"으로 표시한다 — 한쪽만 못
  찾았으면 그 FAQ는 실제로 있는 거라(다른 쪽이 찾았으니) 검색 품질 문제고, 둘 다 못 찾았으면
  애초에 DB에 없을 가능성이 높다.
- `--self-test`: 손으로 계산한 기대값으로 Recall@k/MRR 계산 로직 자체를 검증한다
  (경계값, 무관 질문 제외, 긍정 질문 0건 등 7건).

## 자기 검증

네 검사 스크립트 모두 `--self-test`가 있다(`check_policy.py`는 20건, `check_duplicates.py`는 2건,
`check_eval_questions.py`는 10건, `measure_search_quality.py`는 7건).

통과만 봐서는 검사가 실제로 도는지 알 수 없어, 일부러 틀린 건을 넣어 잡히는지 확인한다.

문서 파싱에서도 표를 못 찾거나 행 수가 기대와 다르면 0건으로 넘어가지 않고 `DocumentError`를 던진다.

## 산출물

| 파일 | 내용 |
| --- | --- |
| `data/faq_sample_30.json` | 검색 품질 측정용 샘플 30건. 카테고리 10종 × 3건, 질문유형 6건씩, 페르소나 10건씩 |
| `data/eval_questions_30.json` | 검색 품질 평가 질문 30건. `SIMILAR`/`VARIANT` 각 10건(카테고리 10종 대칭 커버) + `UNRELATED` 10건(완전 무관 4 + 도메인 인접 6). 정답은 `expected_content_hash`로 매핑 |
| `data/eval_smoke.json` | `measure_search_quality.py` 스모크 테스트용 더미 질문 2건. dev 시드(`V2__seed_sample_data.sql`)의 더미 FAQ 2건 기준인데, 그 시드의 임베딩 자체가 실제 Ollama 값이 아니라 고정 패턴이라 실제 쿼리와 유사도가 임계값을 넘을 가능성이 낮다(실측으로는 항상 빈 배열이었으나, 가짜 벡터라는 사실 자체가 그걸 보장하지는 않는다) — 검색 품질이 아니라 스크립트가 API와 정상 통신하는지 확인하는 용도 |
| `data/faq_slots_1150.json` | `generate_faq.py --out`로 생성한 1,150건 조합표(문장 없음). `slot_id`로 끝까지 추적 |
| `data/faq_full_300.json` | 1차 300건(카테고리 10종 × 30건). 조합표에서 (질문유형 × 페르소나) 15조합마다 2건씩, 정책 항목이 고르게 섞이도록 고른 부분집합. `faq_sample_30.json` 30건의 `question`·`answer`를 문자 그대로 포함(`content_hash` 동일). 단 `slot_id`·`trigger`는 1,150건 조합표 기준이라 샘플 표의 `S01` 번호·사유와는 다르다 |
| `data/faq_full_1150.json` | 전체 1,150건. 앞 300건은 `faq_full_300.json`과 동일하고(`content_hash` 불변) 뒤 850건이 나머지 슬롯. 모든 항목이 `slot_id`·`trigger`를 갖고 있어 `faq_slots_1150.json`의 어느 칸에서 나왔는지 역추적된다. 분포는 `generate_faq.py --summary`와 정확히 일치 |

`faq_full_300.json`을 남겨두는 이유: 적재를 300건 → 1,150건 두 번에 나눠서 건수 증가에 따른 Recall 변화를 재기 위해서다.
1차 300건은 2차에서 한 글자도 고치지 않았다(고치면 `content_hash`가 바뀌어 `eval_questions_30.json`의 정답 매핑이 끊긴다).

두 파일 모두 `check_policy.py`, `check_duplicates.py`(question / `--field both`, 임계값 0.95)를 전체 건 기준으로 통과했다.

`slot_id`, `question_type`, `persona`, `trigger`, `extra_policy_refs`는 생성, 검증용 메타데이터다.
`faqs` 테이블에는 넣지 않고 적재 시점에 제외한다.
`version`은 적재 시 `1`, `content_hash`는 `question + answer`의 SHA-256으로
적재 스크립트가 계산한다.