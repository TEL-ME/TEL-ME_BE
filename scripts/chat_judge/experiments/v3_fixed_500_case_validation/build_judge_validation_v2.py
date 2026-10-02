#!/usr/bin/env python3
"""FAQ 전 분야에서 중복 없는 Judge 검증 질문 500개를 고정 생성한다."""

import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge


ROOT = Path(__file__).resolve().parents[4]
CATALOG = judge.DEFAULT_CATALOG
OUTPUT = ROOT / "scripts/chat_judge/data/chat_judge_validation_v2.json"
PILOT = ROOT / "scripts/chat_judge/data/chat_judge_pilot.json"
V1 = ROOT / "scripts/chat_judge/data/chat_judge_validation_v1.json"
FEWSHOT = judge.DEFAULT_FEWSHOT
EQUIVALENCE_LABELS = ROOT / "scripts/data/faq_equivalence_labels_v2.json"
CATEGORIES = ("BILLING", "PLAN", "DEVICE", "SUBSCRIBE", "PORTING",
              "TERMINATE", "USIM", "NAME_CHANGE", "ROAMING", "SERVICE")
QUESTION_TYPES = ("FACT", "PROCEDURE", "CONDITION", "TROUBLE", "COMPARE")
BEHAVIORS = ("SUPPORTED", "UNSUPPORTED_EXTRA", "OVER_REFUSAL", "NO_EVIDENCE")
UNSUPPORTED_SUFFIXES = {
    "BILLING": (
        " 추가 납부 수수료 12,345원이 청구됩니다.",
        " 요금 납부 고객에게 매달 23,456원씩 자동 환급됩니다.",
        " 모든 미납 요금은 신청 다음 날 자동 면제됩니다.",
    ),
    "PLAN": (
        " 변경한 달에는 데이터 37GB가 무료로 추가 지급됩니다.",
        " 요금제 변경만 하면 월 기본료가 12,345원 할인됩니다.",
        " 모든 요금제는 약정 없이 첫 6개월이 무료입니다.",
    ),
    "DEVICE": (
        " 기기변경 시 남은 할부금 88,888원이 자동 면제됩니다.",
        " 새 단말을 구입하면 매달 데이터 47GB가 추가 제공됩니다.",
        " 기기변경 고객에게 단말 대금 23,456원이 현금으로 지급됩니다.",
    ),
    "SUBSCRIBE": (
        " 신규 가입자에게 6개월치 요금이 전액 환급됩니다.",
        " 가입 즉시 현금 34,567원이 자동 지급됩니다.",
        " 모든 가입 고객의 단말 할부금은 자동 면제됩니다.",
    ),
    "PORTING": (
        " 번호이동을 신청하면 기존 통신사의 위약금이 항상 면제됩니다.",
        " 번호이동 고객에게 데이터 47GB가 매달 무료로 제공됩니다.",
        " 번호이동 신청만 하면 현금 34,567원이 자동 지급됩니다.",
    ),
    "TERMINATE": (
        " 해지 후에도 30일 동안 모든 유료 서비스를 무료로 사용할 수 있습니다.",
        " 해지하면 미납 요금과 위약금이 모두 자동 면제됩니다.",
        " 해지 신청 고객에게 현금 23,456원이 지급됩니다.",
    ),
    "USIM": (
        " 유심 재발급 시 로밍 데이터 50GB가 자동 제공됩니다.",
        " 유심 교체 고객에게 매달 12,345원이 자동 환급됩니다.",
        " 유심을 다시 받으면 기존 요금제의 약정이 모두 면제됩니다.",
    ),
    "NAME_CHANGE": (
        " 명의변경을 하면 기존 미납 요금이 자동으로 소멸됩니다.",
        " 명의변경 고객에게 매달 데이터 47GB가 무료로 제공됩니다.",
        " 명의변경을 신청하면 현금 34,567원이 자동 지급됩니다.",
    ),
    "ROAMING": (
        " 로밍에 가입하면 모든 해외 통화가 평생 무료입니다.",
        " 로밍 신청 고객에게 여행 경비 23,456원이 지급됩니다.",
        " 모든 로밍 상품은 가입 첫 30일 동안 무료입니다.",
    ),
    "SERVICE": (
        " 부가서비스 신청 고객은 매달 25,000원의 현금 혜택을 받습니다.",
        " 모든 부가서비스는 가입 첫 6개월이 무료입니다.",
        " 부가서비스를 해지하면 기존 통신 요금이 전액 환급됩니다.",
    ),
}
REFUSAL_TEMPLATES = (
    "현재 제공된 근거로는 정확한 안내가 어렵습니다.",
    "확인 가능한 자료가 없어 이 질문에는 답변드릴 수 없습니다.",
    "정확한 정책을 확인하지 못해 안내를 보류하겠습니다.",
    "관련 정보를 확인할 수 없어 고객센터에 문의해 주세요.",
)
OUT_OF_SCOPE_TEMPLATES = (
    "통신 서비스와 관련된 질문을 해주세요.",
    "이 상담은 통신 서비스 문의를 돕고 있습니다. 관련 질문을 남겨주세요.",
    "해당 주제는 상담 범위 밖입니다. 통신 서비스에 관해 문의해 주세요.",
    "통신 서비스와 관련된 내용을 문의해 주시면 안내하겠습니다.",
)
SCOPE_QUESTIONS = (
    "이번 주 나스닥 지수는 얼마나 올랐나요?",
    "김치찌개를 맛있게 끓이는 방법 알려줘",
    "고려 시대의 수도는 어디였나요?",
    "x의 제곱을 미분하면 무엇인가요?",
    "어제 프로야구 경기 결과가 뭐야?",
    "주말에 볼 만한 영화 추천해줘",
    "피아노를 혼자 조율하는 법을 알려줘",
    "베란다 화분에 물을 얼마나 자주 줘야 해?",
    "고양이 사료를 하루에 얼마나 줘야 할까?",
    "오늘 K리그 득점 선두는 누구야?",
    "이 문장을 프랑스어로 번역해줘: 안녕하세요",
    "야간 사진을 찍을 때 셔터 속도는 어떻게 잡아?",
    "마라톤 준비를 위한 러닝 계획을 짜줘",
    "최근 지진이 발생한 지역을 알려줘",
    "게이밍 노트북 GPU는 어떻게 골라?",
    "장거리 비행기 좌석은 어디가 편해?",
    "아파트 전세 계약할 때 주의할 점은?",
    "주식 매도 시점은 어떻게 정하면 좋아?",
    "한국사 시험 공부 순서를 추천해줘",
    "체스에서 퀸을 잘 활용하는 방법은?",
    "북극성과 다른 별을 구분하는 방법은?",
    "파스타 면은 보통 몇 분 삶아야 해?",
    "조선 세종대왕의 즉위 연도는 언제야?",
    "삼각형의 넓이는 어떻게 계산하나요?",
    "올해 프로농구 우승팀은 누구야?",
    "수채화 물감의 번짐을 줄이는 방법은?",
    "기타 줄을 새로 교체하는 순서를 알려줘",
    "실내 바질 잎이 노랗게 변하는 이유는?",
    "강아지 산책은 하루에 몇 번 하는 게 좋아?",
    "세계에서 가장 높은 산의 높이는 얼마야?",
    "독일어로 감사합니다를 어떻게 말해?",
    "카메라 조리개 값은 뭘 뜻하나요?",
    "등산화는 발에 어떻게 맞춰 골라야 해?",
    "목성의 위성은 몇 개인가요?",
    "가정용 커피 원두는 어떻게 보관해?",
    "해외여행 여권 유효기간은 얼마나 남아야 해?",
    "집에서 벽지 얼룩을 지우는 방법은?",
    "채권 가격과 금리의 관계를 설명해줘",
    "영어 듣기 공부를 매일 어떻게 하면 좋아?",
    "바둑에서 포석을 연습하는 방법을 알려줘",
    "달 표면의 중력은 지구와 얼마나 다른가요?",
    "떡볶이 양념을 덜 맵게 만드는 방법은?",
    "로마 제국의 수도는 어디였나요?",
    "원기둥의 부피는 어떻게 구하나요?",
    "배구 경기의 세트 승리 조건은 뭐예요?",
    "붓글씨를 처음 배울 때 필요한 도구는?",
    "드럼 스틱 잡는 방법을 알려줘",
    "장미 화분의 가지치기는 언제 하나요?",
    "반려견 목욕 주기는 어느 정도가 좋아요?",
    "아마존강의 길이는 얼마나 되나요?",
)


def stable_key(value):
    return hashlib.sha256(("telme-judge-v2:" + value).encode("utf-8")).hexdigest()


def variant_index(value, count):
    return int(stable_key(value)[:8], 16) % count


def excluded_questions_and_sources(pilot, v1, fewshot):
    questions = {turn["question"] for case in pilot for turn in case["turns"]}
    questions.update(example["input"]["question"] for kind in ("grounding", "adequacy")
                     for example in fewshot[kind])
    sources = {source_id for case in pilot for turn in case["turns"]
               for source_id in turn["goldSourceSlotIds"]}
    for group in (v1["policies"], v1["simplePolicies"]):
        for item in group:
            sources.add(item["sourceSlotId"])
            questions.add(item["question"])
    questions.update(item["question"] for item in v1["outOfScope"])
    return questions, sources


def choose_faqs(catalog, excluded_questions, excluded_sources):
    available = defaultdict(list)
    for faq in catalog:
        if (faq["question"] in excluded_questions or faq["slot_id"] in excluded_sources
                or not faq["question"].strip() or not faq["answer"].strip()):
            continue
        available[(faq["category"], faq["question_type"])].append(faq)
    selected = []
    for category in CATEGORIES:
        category_personas = Counter()
        for question_type in QUESTION_TYPES:
            count = 8
            candidates = available[(category, question_type)]
            by_persona = defaultdict(list)
            for faq in candidates:
                by_persona[faq["persona"]].append(faq)
            for values in by_persona.values():
                values.sort(key=lambda item: stable_key(item["slot_id"]))
            picks = []
            while len(picks) < count:
                choices = [persona for persona, values in by_persona.items() if values]
                if not choices:
                    raise ValueError(f"FAQ 후보가 부족합니다: {category}/{question_type}")
                persona = min(choices, key=lambda name: (category_personas[name],
                                                         sum(item["persona"] == name for item in picks),
                                                         stable_key(category + question_type + name)))
                picks.append(by_persona[persona].pop(0))
                category_personas[persona] += 1
            selected.extend(picks)
    if len(selected) != 400 or len({faq["question"] for faq in selected}) != 400:
        raise ValueError("서로 다른 FAQ 질문 400개를 고르지 못했습니다.")
    return selected


def labels_for(behavior):
    return {
        "SUPPORTED": {"grounding": "SUPPORTED", "coverage": "COMPLETE", "abstention": "NOT_APPLICABLE"},
        "UNSUPPORTED_EXTRA": {"grounding": "UNSUPPORTED", "coverage": None, "abstention": "SHOULD_ABSTAIN"},
        "OVER_REFUSAL": {"grounding": "NOT_APPLICABLE", "coverage": "MISSED", "abstention": "OVER_REFUSAL"},
        "NO_EVIDENCE": {"grounding": "NOT_APPLICABLE", "coverage": "MISSED", "abstention": "APPROPRIATE"},
        "OUT_OF_SCOPE": {"grounding": "NOT_APPLICABLE", "coverage": "NOT_APPLICABLE", "abstention": "APPROPRIATE"},
        "COMPOUND_PARTIAL": {"grounding": "SUPPORTED", "coverage": "PARTIAL", "abstention": "NOT_APPLICABLE"},
    }[behavior]


def unsupported_suffix(faq):
    variants = UNSUPPORTED_SUFFIXES[faq["category"]]
    return variants[variant_index(faq["slot_id"], len(variants))]


def accepted_source_ids(faq, equivalence):
    if faq is None:
        return []
    ids = equivalence.get("byPrimarySourceId", {}).get(faq["slot_id"], {}).get(
        "acceptedSourceIds", [faq["slot_id"]]
    )
    if faq["slot_id"] not in ids:
        raise ValueError(f"Primary FAQ ID missing from its accepted group: {faq['slot_id']}")
    return ids


def make_case(faq, behavior, equivalence=None):
    equivalence = equivalence or {}
    source_id = faq["slot_id"] if faq else None
    question = faq["question"] if faq else None
    if behavior == "SUPPORTED":
        answer = faq["answer"]
    elif behavior == "UNSUPPORTED_EXTRA":
        answer = faq["answer"].rstrip() + unsupported_suffix(faq)
    elif behavior in {"OVER_REFUSAL", "NO_EVIDENCE"}:
        answer = REFUSAL_TEMPLATES[variant_index(faq["slot_id"], len(REFUSAL_TEMPLATES))]
    else:
        answer = OUT_OF_SCOPE_TEMPLATES[0]
    sources = [] if behavior in {"NO_EVIDENCE", "OUT_OF_SCOPE"} else [{
        "sourceId": source_id,
        "faqId": None,
        "question": faq["question"],
        "answer": faq["answer"],
        "rank": 1,
    }]
    basis = "OUT_OF_SCOPE" if behavior == "OUT_OF_SCOPE" else (
        "NO_EVIDENCE" if behavior in {"OVER_REFUSAL", "NO_EVIDENCE"} else "GROUNDED")
    common = {"question": question, "previousTurns": [], "answer": answer,
              "answerBasis": basis, "messageType": "ANSWER", "sources": sources}
    return {
        "caseId": source_id if faq else None,
        "category": faq["category"] if faq else "OUT_OF_SCOPE",
        "questionType": faq["question_type"] if faq else "OUT_OF_SCOPE",
        "persona": faq["persona"] if faq else None,
        "behavior": behavior,
        "expected": labels_for(behavior),
        "groundingInput": {**common, "confirmedConditions": []},
        "adequacyInput": {**common, "expectedBehavior": "OUT_OF_SCOPE" if not faq else "ANSWER",
                          "requiredFacts": [faq["answer"]] if faq else [],
                          "missingFact": None,
                          "goldSourceSlotIds": [source_id] if faq else [],
                          "goldSourceGroups": ([accepted_source_ids(faq, equivalence)] if faq else []),
                          "actualSourceSlotIds": [source["sourceId"] for source in sources]},
    }


def make_compound_case(first, second, index, equivalence=None):
    equivalence = equivalence or {}
    question = first["question"].rstrip("?？ ") + " 그리고 " + second["question"]
    sources = [{"sourceId": faq["slot_id"], "faqId": None,
                "question": faq["question"], "answer": faq["answer"], "rank": rank}
               for rank, faq in enumerate((first, second), 1)]
    common = {"question": question, "previousTurns": [], "answer": first["answer"],
              "answerBasis": "GROUNDED", "messageType": "ANSWER", "sources": sources}
    return {
        "caseId": f"COMPOUND-{index:03d}",
        "category": "COMPOUND",
        "questionType": "COMPOUND",
        "persona": None,
        "behavior": "COMPOUND_PARTIAL",
        "expected": labels_for("COMPOUND_PARTIAL"),
        "groundingInput": {**common, "confirmedConditions": []},
        "adequacyInput": {**common, "expectedBehavior": "ANSWER",
                          "requiredFacts": [first["answer"], second["answer"]],
                          "missingFact": None,
                          "goldSourceSlotIds": [first["slot_id"], second["slot_id"]],
                          "goldSourceGroups": [accepted_source_ids(first, equivalence),
                                               accepted_source_ids(second, equivalence)],
                          "actualSourceSlotIds": [first["slot_id"], second["slot_id"]]},
    }


def choose_compound_pairs(catalog, already_selected, excluded_questions, excluded_sources):
    used_ids = {faq["slot_id"] for faq in already_selected} | excluded_sources
    by_category = {}
    for category in CATEGORIES:
        candidates = [faq for faq in catalog if faq["category"] == category
                      and faq["slot_id"] not in used_ids and faq["question"] not in excluded_questions
                      and len(faq["question"].strip()) >= 20 and faq["answer"].strip()]
        candidates.sort(key=lambda faq: (faq["question_type"] not in {"FACT", "PROCEDURE", "CONDITION"},
                                         stable_key("compound:" + faq["slot_id"])))
        if len(candidates) < 10:
            raise ValueError(f"복합 질문 FAQ가 부족합니다: {category}")
        by_category[category] = candidates[:10]
    pairs = []
    for index, category in enumerate(CATEGORIES):
        other = CATEGORIES[(index + 5) % len(CATEGORIES)]
        for number in range(5):
            pairs.append((by_category[category][number], by_category[other][number + 5]))
    if len({faq["slot_id"] for pair in pairs for faq in pair}) != 100:
        raise ValueError("복합 질문에 같은 FAQ를 반복 사용했습니다.")
    return pairs


def assign_behaviors(chosen):
    """분야별 할당량을 지키면서 질문 유형마다 행동 라벨을 고르게 배치한다."""
    by_category = {category: [faq for faq in chosen if faq["category"] == category]
                   for category in CATEGORIES}
    target = {behavior: 20 for behavior in BEHAVIORS}
    for attempt in range(100):
        rng = random.Random(2601 + attempt)
        assignment = {}
        for category, faqs in by_category.items():
            labels = [behavior for behavior in BEHAVIORS for _ in range(10)]
            rng.shuffle(labels)
            assignment.update((faq["slot_id"], label) for faq, label in zip(faqs, labels))
        counts = Counter((faq["question_type"], assignment[faq["slot_id"]]) for faq in chosen)
        def mismatch():
            return sum(abs(counts[(question_type, behavior)] - target[behavior])
                       for question_type in QUESTION_TYPES for behavior in BEHAVIORS)
        for _ in range(200):
            if mismatch() == 0:
                return assignment
            best = None
            best_change = 0
            for faqs in by_category.values():
                for index, left in enumerate(faqs):
                    for right in faqs[index + 1:]:
                        lt, rt = left["question_type"], right["question_type"]
                        lb, rb = assignment[left["slot_id"]], assignment[right["slot_id"]]
                        if lt == rt or lb == rb:
                            continue
                        keys = ((lt, lb), (rt, rb), (lt, rb), (rt, lb))
                        before = sum(abs(counts[key] - target[key[1]]) for key in keys)
                        after = (abs(counts[(lt, lb)] - 1 - target[lb])
                                 + abs(counts[(rt, rb)] - 1 - target[rb])
                                 + abs(counts[(lt, rb)] + 1 - target[rb])
                                 + abs(counts[(rt, lb)] + 1 - target[lb]))
                        change = after - before
                        if change < best_change:
                            best, best_change = (left, right, lt, rt, lb, rb), change
            if best is None:
                break
            left, right, lt, rt, lb, rb = best
            assignment[left["slot_id"]], assignment[right["slot_id"]] = rb, lb
            counts[(lt, lb)] -= 1
            counts[(rt, rb)] -= 1
            counts[(lt, rb)] += 1
            counts[(rt, lb)] += 1
    raise ValueError("분야와 질문 유형을 동시에 균형 있게 배치하지 못했습니다.")


def build_dataset(catalog, pilot, v1, fewshot, equivalence=None):
    excluded_questions, excluded_sources = excluded_questions_and_sources(pilot, v1, fewshot)
    chosen = choose_faqs(catalog, excluded_questions, excluded_sources)
    assignment = assign_behaviors(chosen)
    cases = [make_case(faq, assignment[faq["slot_id"]], equivalence) for faq in chosen]
    pairs = choose_compound_pairs(catalog, chosen, excluded_questions, excluded_sources)
    cases.extend(make_compound_case(first, second, index, equivalence)
                 for index, (first, second) in enumerate(pairs, 1))
    for index, question in enumerate(SCOPE_QUESTIONS, 1):
        item = make_case(None, "OUT_OF_SCOPE", equivalence)
        item["caseId"] = f"OUT-{index:03d}"
        item["groundingInput"]["question"] = question
        item["adequacyInput"]["question"] = question
        answer = OUT_OF_SCOPE_TEMPLATES[variant_index(question, len(OUT_OF_SCOPE_TEMPLATES))]
        item["groundingInput"]["answer"] = answer
        item["adequacyInput"]["answer"] = answer
        cases.append(item)
    result = {
        "schemaVersion": 2,
        "name": "judge_validation_v2",
        "kind": "CONTROLLED_JUDGE_VALIDATION",
        "catalogSha256": judge.sha256(catalog),
        "selectionMethod": "deterministic_sha256_stratified",
        "labelPolicy": "RULE_CONSTRUCTED; unsupported coverage is intentionally unlabelled",
        "goldSourceGroupPolicy": (equivalence or {}).get("labelPolicy", "primary FAQ only"),
        "goldSourceGroupCompleteness": (equivalence or {}).get(
            "completeness", "PRIMARY_ONLY; equivalent FAQ alternatives are not included"
        ),
        "goldSourceGroupLabelsSha256": judge.sha256(equivalence or {}),
        "cases": cases,
    }
    validate_dataset(result, catalog, excluded_questions, excluded_sources)
    return result


def validate_dataset(dataset, catalog, excluded_questions, excluded_sources):
    if dataset.get("schemaVersion") != 2 or dataset.get("catalogSha256") != judge.sha256(catalog):
        raise ValueError("검증셋 버전 또는 FAQ 원본 해시가 맞지 않습니다.")
    cases = dataset["cases"]
    if len(cases) != 500:
        raise ValueError("검증 항목은 정확히 500개여야 합니다.")
    questions = [case["groundingInput"]["question"] for case in cases]
    ids = [case["caseId"] for case in cases]
    if len(questions) != len(set(questions)) or len(ids) != len(set(ids)):
        raise ValueError("질문 또는 caseId가 중복됐습니다.")
    if set(questions) & excluded_questions or set(ids) & excluded_sources:
        raise ValueError("기존 평가 또는 예시와 겹칩니다.")
    categories = Counter(case["category"] for case in cases)
    if (any(categories[category] != 40 for category in CATEGORIES)
            or categories["OUT_OF_SCOPE"] != 50 or categories["COMPOUND"] != 50):
        raise ValueError(f"분야별 질문 수가 균형을 벗어났습니다: {categories}")
    types = Counter(case["questionType"] for case in cases if case["category"] != "OUT_OF_SCOPE")
    if any(types[question_type] != 80 for question_type in QUESTION_TYPES):
        raise ValueError(f"질문 유형별 질문 수가 균형을 벗어났습니다: {types}")
    for category in CATEGORIES:
        personas = Counter(case["persona"] for case in cases if case["category"] == category)
        if max(personas.values()) - min(personas.values()) > 1 or len(personas) != 3:
            raise ValueError(f"페르소나별 질문 수가 치우쳤습니다: {category}: {personas}")
    behavior_counts = Counter(case["behavior"] for case in cases)
    if behavior_counts != {"SUPPORTED": 100, "UNSUPPORTED_EXTRA": 100, "OVER_REFUSAL": 100,
                           "NO_EVIDENCE": 100, "OUT_OF_SCOPE": 50, "COMPOUND_PARTIAL": 50}:
        raise ValueError(f"판정 유형별 질문 수가 맞지 않습니다: {behavior_counts}")
    for category in CATEGORIES:
        counts = Counter(case["behavior"] for case in cases if case["category"] == category)
        if counts != {behavior: 10 for behavior in BEHAVIORS}:
            raise ValueError(f"분야별 판정 라벨이 치우쳤습니다: {category}: {counts}")
    for question_type in QUESTION_TYPES:
        counts = Counter(case["behavior"] for case in cases if case["questionType"] == question_type)
        if counts != {behavior: 20 for behavior in BEHAVIORS}:
            raise ValueError(f"질문 유형의 판정 라벨이 치우쳤습니다: {question_type}: {counts}")
    catalog_by_id = {faq["slot_id"]: faq for faq in catalog}
    all_source_ids = [source_id for case in cases
                      for source_id in case["adequacyInput"]["goldSourceSlotIds"]]
    if len(all_source_ids) != len(set(all_source_ids)):
        raise ValueError("서로 다른 평가 질문에서 정답 FAQ를 재사용했습니다.")
    for case in cases:
        behavior = case["behavior"]
        if case["expected"] != labels_for(behavior):
            raise ValueError(f"판정 라벨이 맞지 않습니다: {case['caseId']}")
        ground = case["groundingInput"]
        adequacy = case["adequacyInput"]
        groups = adequacy.get("goldSourceGroups")
        if not isinstance(groups, list) or any(
                not isinstance(group, list) or not group
                or not all(isinstance(source_id, str) and source_id for source_id in group)
                for group in groups):
            raise ValueError(f"goldSourceGroups invalid: {case['caseId']}")
        if any(source_id not in catalog_by_id for group in groups for source_id in group):
            raise ValueError(f"goldSourceGroups references an unknown FAQ: {case['caseId']}")
        if ground["question"] != adequacy["question"] or ground["answer"] != adequacy["answer"]:
            raise ValueError("근거와 충실도 판정 입력이 다릅니다.")
        if any(ground[key] != adequacy[key] for key in
               ("previousTurns", "answerBasis", "messageType", "sources")):
            raise ValueError("근거와 충실도 판정의 공통 입력이 다릅니다.")
        if adequacy["actualSourceSlotIds"] != [source["sourceId"] for source in ground["sources"]]:
            raise ValueError("검색 근거 ID와 실제 검색 결과가 다릅니다.")
        if {"requiredFacts", "goldSourceSlotIds", "expectedBehavior"} & ground.keys():
            raise ValueError("근거 판정 입력에 정답 정보가 포함됐습니다.")
        source_id = case["caseId"]
        if behavior == "COMPOUND_PARTIAL":
            if case["category"] != "COMPOUND" or case["questionType"] != "COMPOUND":
                raise ValueError("복합 질문의 분류가 올바르지 않습니다.")
            source_ids = adequacy["goldSourceSlotIds"]
            if len(groups) != 2 or any(source_id not in group for source_id, group in zip(source_ids, groups)):
                raise ValueError("Compound gold groups must have one OR group per required subquestion.")
            if any(catalog_by_id[candidate]["category"] != catalog_by_id[source_id]["category"]
                   for source_id, group in zip(source_ids, groups) for candidate in group):
                raise ValueError("Compound alternatives must stay within the corresponding FAQ category.")
            if len(source_ids) != 2 or len(set(source_ids)) != 2 or len(ground["sources"]) != 2:
                raise ValueError("복합 질문의 두 근거가 누락됐습니다.")
            if set(source_ids) & excluded_sources:
                raise ValueError("복합 질문에 이전 평가의 근거가 포함됐습니다.")
            first, second = (catalog_by_id[item] for item in source_ids)
            if first["category"] == second["category"] or ground["answer"] != first["answer"]:
                raise ValueError("복합 질문의 답변과 분야가 올바르지 않습니다.")
            expected_question = first["question"].rstrip("?？ ") + " 그리고 " + second["question"]
            if ground["question"] != expected_question:
                raise ValueError("복합 질문이 원본 FAQ 질문과 다릅니다.")
            if adequacy["expectedBehavior"] != "ANSWER":
                raise ValueError("복합 질문의 기대 동작이 올바르지 않습니다.")
            if adequacy["requiredFacts"] != [first["answer"], second["answer"]]:
                raise ValueError("복합 질문의 기대 사실이 누락됐습니다.")
            if [item["answer"] for item in ground["sources"]] != [first["answer"], second["answer"]]:
                raise ValueError("복합 질문의 검색 근거가 FAQ 원문과 다릅니다.")
        elif behavior != "OUT_OF_SCOPE":
            faq = catalog_by_id[source_id]
            if (case["category"] != faq["category"] or case["questionType"] != faq["question_type"]
                    or case["persona"] != faq["persona"]):
                raise ValueError("FAQ 메타데이터가 원본과 다릅니다.")
            if ground["question"] != faq["question"] or adequacy["requiredFacts"] != [faq["answer"]]:
                raise ValueError(f"FAQ 원본과 입력이 다릅니다: {source_id}")
            if adequacy["goldSourceSlotIds"] != [source_id] or adequacy["expectedBehavior"] != "ANSWER":
                raise ValueError("단일 FAQ 질문의 기대 근거 또는 동작이 맞지 않습니다.")
            if behavior == "SUPPORTED" and ground["answer"] != faq["answer"]:
                raise ValueError("정답 답변이 FAQ 원문과 다릅니다.")
            if len(groups) != 1 or source_id not in groups[0]:
                raise ValueError(f"FAQ gold group is inconsistent: {source_id}")
            if any(catalog_by_id[candidate]["category"] != faq["category"] for candidate in groups[0]):
                raise ValueError(f"FAQ gold alternatives cross categories: {source_id}")
            suffix = unsupported_suffix(faq)
            if behavior == "UNSUPPORTED_EXTRA" and suffix.strip() in faq["answer"]:
                raise ValueError("추가한 근거 밖 문장이 이미 FAQ에 있습니다.")
            if behavior == "UNSUPPORTED_EXTRA" and ground["answer"] != (
                    faq["answer"].rstrip() + suffix):
                raise ValueError("근거 없는 주장 예시가 정해진 규칙과 다릅니다.")
            if behavior in {"OVER_REFUSAL", "NO_EVIDENCE"} and ground["answer"] != (
                    REFUSAL_TEMPLATES[variant_index(source_id, len(REFUSAL_TEMPLATES))]):
                raise ValueError("답변 보류 문구가 정해진 규칙과 다릅니다.")
            if behavior not in {"NO_EVIDENCE"}:
                source = ground["sources"]
                if len(source) != 1 or source[0]["sourceId"] != source_id or source[0]["answer"] != faq["answer"]:
                    raise ValueError(f"실제 근거가 FAQ 원본과 다릅니다: {source_id}")
        if behavior in {"NO_EVIDENCE", "OUT_OF_SCOPE"} and ground["sources"]:
            raise ValueError("근거가 없는 유형에 검색 결과가 있습니다.")
        if behavior == "OUT_OF_SCOPE" and ground["answer"] != (
                OUT_OF_SCOPE_TEMPLATES[variant_index(ground["question"], len(OUT_OF_SCOPE_TEMPLATES))]):
            raise ValueError("범위 밖 질문의 응답 문구가 정해진 규칙과 다릅니다.")
        if behavior == "OUT_OF_SCOPE" and (adequacy["goldSourceSlotIds"]
                                           or adequacy["requiredFacts"]
                                           or groups
                                           or adequacy["expectedBehavior"] != "OUT_OF_SCOPE"):
            raise ValueError("범위 밖 질문에 정답 FAQ가 포함됐습니다.")
        if behavior == "OUT_OF_SCOPE" and (case["category"] != "OUT_OF_SCOPE"
                                           or case["questionType"] != "OUT_OF_SCOPE"):
            raise ValueError("범위 밖 질문의 분류가 올바르지 않습니다.")
    return dataset


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    pilot = json.loads(PILOT.read_text(encoding="utf-8"))
    v1 = json.loads(V1.read_text(encoding="utf-8"))
    fewshot = json.loads(FEWSHOT.read_text(encoding="utf-8"))
    equivalence = json.loads(EQUIVALENCE_LABELS.read_text(encoding="utf-8"))
    dataset = build_dataset(catalog, pilot, v1, fewshot, equivalence)
    OUTPUT.write_text(json.dumps(dataset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(dataset['cases'])}개 질문: {OUTPUT}")


if __name__ == "__main__":
    main()
