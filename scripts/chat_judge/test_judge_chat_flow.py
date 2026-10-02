"""로컬 모델 호출 없이 평가 입력과 실패 격리를 검증한다."""

import copy
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v1_pilot_fewshot import compare_chat_judge as comparison


def sample_capture():
    return {
        "schemaVersion": 1,
        "cases": [{
            "caseId": "FAQ_TEST",
            "turns": [{
                "fixture": {
                    "question": "요금제는 몇 번 바꿀 수 있나요?",
                    "expectedIntent": "FAQ",
                    "expectedBehavior": "ANSWER",
                    "goldSourceSlotIds": ["BILLING-0001"],
                    "requiredFacts": ["월 1회"],
                },
                "executionStatus": "COMPLETED",
                "route": {"intent": "FAQ"},
                "searches": [{"query": "요금제 변경", "results": [{
                    "faqId": 3,
                    "slotId": None,
                    "question": "요금제는 몇 번 바꿀 수 있나요?",
                    "answer": "월 1회 바꿀 수 있습니다.",
                }]}],
                "outputMessage": {"content": "월 1회 바꿀 수 있습니다.", "messageType": "ANSWER"},
            }],
        }],
    }


CATALOG = [{
    "slot_id": "BILLING-0001",
    "question": "요금제는 몇 번 바꿀 수 있나요?",
    "answer": "월 1회 바꿀 수 있습니다.",
}]


class ChatJudgeTest(unittest.TestCase):
    def test_fewshot_comparison_uses_same_capture_and_manual_labels(self):
        root = Path(__file__).resolve().parents[2] / "docs/chat-judge/experiments/V1-pilot-fewshot"
        manual, baseline, fewshot = [
            json.loads((root / name).read_text(encoding="utf-8"))
            for name in ("20261001-manual-labels.json", "20261001-reconciled.json",
                         "20261001-fewshot-judged.json")
        ]
        result = comparison.compare(manual, baseline, fewshot)
        self.assertEqual(9, result["turns"])
        self.assertEqual(6, result["matchingHuman"]["candidate"]["abstention"])
        self.assertEqual(9, result["matchingHuman"]["candidate"]["finalAbstention"])
        changed = copy.deepcopy(fewshot)
        changed["generatorCaptureSha256"] = "different"
        with self.assertRaises(ValueError):
            comparison.compare(manual, baseline, changed)

    def test_fewshot_examples_are_valid_and_separate_from_pilot(self):
        root = Path(__file__).resolve().parents[2]
        capture = json.loads((root / "docs/chat-judge/experiments/V1-pilot-fewshot/20261001-capture.json").read_text(encoding="utf-8"))
        fewshot = json.loads((root / "scripts/chat_judge/data/chat_judge_fewshot.json").read_text(encoding="utf-8"))
        self.assertIs(fewshot, judge.validate_fewshot(fewshot, capture))
        leaked = copy.deepcopy(fewshot)
        leaked["adequacy"][0]["input"]["question"] = capture["cases"][0]["turns"][0]["fixture"]["question"]
        with self.assertRaises(ValueError):
            judge.validate_fewshot(leaked, capture)
        leaked = copy.deepcopy(fewshot)
        leaked["grounding"][0]["output"]["claims"][0]["sourceIds"] = ["not-in-example"]
        with self.assertRaises(ValueError):
            judge.validate_fewshot(leaked, capture)

    @patch.object(judge.urllib.request, "urlopen")
    def test_fewshot_messages_alternate_and_zero_shot_is_unchanged(self, urlopen):
        outputs = [
            {"message": {"content": json.dumps({"claims": []})}},
            {"message": {"content": json.dumps({"claims": [], "overall": "NOT_APPLICABLE"})}},
        ] * 2
        urlopen.side_effect = [io.BytesIO(json.dumps(raw).encode("utf-8")) for raw in outputs]
        root = Path(__file__).resolve().parents[2]
        example = json.loads((root / "scripts/chat_judge/data/chat_judge_fewshot.json").read_text(encoding="utf-8"))["grounding"][0]
        plain = judge.ollama_chat("http://unused", "qwen3:14b", "grounding", {"sources": []})
        self.assertEqual(["system", "user"], [item["role"] for item in plain["request"]["messages"]])
        guided = judge.ollama_chat("http://unused", "qwen3:14b", "grounding", {"sources": []},
                                   examples=[example])
        messages = guided["request"]["messages"]
        self.assertEqual(["system", "user", "assistant", "user"], [item["role"] for item in messages])
        self.assertIn("DEMO-G-01", messages[1]["content"])
        self.assertIn("SUPPORTED", messages[2]["content"])
        self.assertEqual(plain["request"]["messages"][-1], messages[-1])

    def test_rejects_invalid_capture(self):
        capture = sample_capture()
        capture["cases"].append(copy.deepcopy(capture["cases"][0]))
        with self.assertRaises(ValueError):
            judge.validate_capture(capture)
        capture = sample_capture()
        capture["cases"][0]["turns"][0]["fixture"]["expectedBehavior"] = "INVALID"
        with self.assertRaises(ValueError):
            judge.validate_capture(capture)

    def test_catalog_id_requires_exact_question_and_answer(self):
        capture = sample_capture()
        enriched = judge.enrich_source_ids(capture, CATALOG)
        result = enriched["cases"][0]["turns"][0]["searches"][0]["results"][0]
        self.assertEqual("BILLING-0001", result["slotId"])
        self.assertIsNone(capture["cases"][0]["turns"][0]["searches"][0]["results"][0]["slotId"])
        altered = copy.deepcopy(capture)
        altered["cases"][0]["turns"][0]["searches"][0]["results"][0]["answer"] += " 다른 문장"
        self.assertIsNone(judge.enrich_source_ids(altered, CATALOG)["cases"][0]["turns"][0]
                          ["searches"][0]["results"][0]["slotId"])

    def test_rejects_hallucinated_source_and_inconsistent_verdict(self):
        result = {"claims": [{
            "claim": "월 1회", "verdict": "SUPPORTED", "sourceIds": ["없는 ID"], "reason": "근거"
        }], "overall": "SUPPORTED"}
        with self.assertRaises(ValueError):
            judge.validate_result("grounding", result, {"BILLING-0001"})
        result["claims"][0]["sourceIds"] = ["BILLING-0001"]
        result["overall"] = "UNSUPPORTED"
        with self.assertRaises(ValueError):
            judge.validate_result("grounding", result, {"BILLING-0001"})

    def test_unsupported_claim_cannot_cite_a_source(self):
        claim = {
            "claim": "무료입니다.", "verdict": "UNSUPPORTED",
            "sourceIds": ["BILLING-0001"], "reason": "FAQ에 없는 내용",
        }
        result = {"claims": [claim], "overall": "UNSUPPORTED"}
        with self.assertRaisesRegex(ValueError, "지원되지 않은 주장에 근거 ID"):
            judge.validate_result("grounding", result, {"BILLING-0001"})
        claim["sourceIds"] = []
        self.assertIs(result, judge.validate_result("grounding", result, {"BILLING-0001"}))

        branches = judge.GROUNDING_SCHEMA["properties"]["claims"]["items"]["oneOf"]
        unsupported = next(branch for branch in branches
                           if "UNSUPPORTED" in branch["properties"]["verdict"]["enum"])
        self.assertEqual(0, unsupported["properties"]["sourceIds"]["maxItems"])

    def test_supported_claim_requires_a_source_or_confirmed_condition(self):
        result = {"claims": [{
            "claim": "월 1회 변경할 수 있습니다.", "verdict": "SUPPORTED",
            "sourceIds": [], "reason": "근거가 있다고 판단했습니다.",
        }], "overall": "SUPPORTED"}
        with self.assertRaisesRegex(ValueError, "FAQ 근거 또는 확정된 상담 조건"):
            judge.validate_result("grounding", result, {"BILLING-0001"}, [])
        self.assertIs(result, judge.validate_result(
            "grounding", result, set(), [{"name": "지역", "value": "서울"}]
        ))

    def test_grounding_rejects_claim_copied_from_unanswered_faq(self):
        result = {"claims": [{
            "claim": "12개월 미만이면 할인 반환금 100%가 적용됩니다.",
            "verdict": "UNSUPPORTED", "sourceIds": [], "reason": "두 번째 FAQ 내용",
        }], "overall": "UNSUPPORTED"}
        with self.assertRaisesRegex(ValueError, "실제 답변의 원문"):
            judge.validate_result("grounding", result, {"FAQ-2"},
                                  answer="최대 월 100만 원까지만 가능합니다.")

    def test_claim_extraction_accepts_only_exact_ordered_answer_spans(self):
        answer = "월 1회 변경할 수 있고, 위약금은 없습니다."
        self.assertEqual(
            ["월 1회 변경할 수 있고", "위약금은 없습니다."],
            judge.validate_claim_extraction(
                {"claims": [{"quote": "월 1회 변경할 수 있고"}, {"quote": "위약금은 없습니다."}]},
                answer,
            ),
        )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "월 1회 변경 가능합니다."}]}, answer
            )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "질문에 있던 내용입니다."}]}, answer
            )

    def test_claim_extraction_keeps_conditions_negation_and_amounts_verbatim(self):
        answer = "미납 요금이 있으면 완납 후 명의 변경이 가능합니다. 배송비는 무료가 아닙니다."
        claims = ["미납 요금이 있으면 완납 후 명의 변경이 가능합니다.", "배송비는 무료가 아닙니다."]
        self.assertEqual(
            claims,
            judge.validate_claim_extraction({"claims": [{"quote": item} for item in claims]}, answer),
        )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "미납 요금이 있으면 명의 변경이 가능합니다."}]}, answer
            )

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_extracts_from_answer_then_judges_meaning_without_question_leakage(self, urlopen):
        answer = "월 1회 변경할 수 있습니다."
        extraction = {"claims": [{"quote": answer}]}
        semantic = {"claims": [{
            "claim": answer, "verdict": "SUPPORTED",
            "sourceIds": ["FAQ-1"], "reason": "표현은 다르지만 의미가 같습니다.",
        }], "overall": "SUPPORTED"}
        responses = [
            {"message": {"content": json.dumps(extraction, ensure_ascii=False)},
             "prompt_eval_count": 10, "eval_count": 4},
            {"message": {"content": json.dumps(semantic, ensure_ascii=False)},
             "prompt_eval_count": 20, "eval_count": 5},
        ]
        urlopen.side_effect = [io.BytesIO(json.dumps(raw).encode("utf-8")) for raw in responses]
        data = {
            "question": "요금제 변경을 몇 번 할 수 있나요?",
            "answer": answer,
            "sources": [{"sourceId": "FAQ-1", "question": "변경 주기", "answer": "한 달에 한 번 변경 가능합니다."}],
        }

        record = judge.ollama_chat("http://unused", "qwen3:14b", "grounding", data)

        self.assertEqual(2, urlopen.call_count)
        self.assertEqual(2, len(record["attempts"]))
        self.assertEqual(30, record["promptTokens"])
        extraction_input = json.loads(record["claimExtraction"]["request"]["messages"][-1]["content"])
        self.assertEqual({"assistantAnswer": answer}, extraction_input)
        semantic_input = json.loads(record["request"]["messages"][-1]["content"].split("\n", 1)[1])
        self.assertEqual([{"claim": answer}], semantic_input["claims"])
        self.assertEqual("FAQ-1", semantic_input["faqSources"][0]["sourceId"])
        self.assertNotIn("question", semantic_input)
        self.assertNotIn("assistantAnswer", semantic_input)
        self.assertNotIn("question", semantic_input["faqSources"][0])
        self.assertEqual(semantic, record["result"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_rejects_changed_claims_without_repair_or_reusing_verdict(self, urlopen):
        extracted = {"claims": [{"quote": "월 1회 변경할 수 있습니다."}]}
        changed = {"claims": [{
            "claim": "매월 변경 횟수는 제한이 없습니다.", "verdict": "SUPPORTED",
            "sourceIds": ["FAQ-1"], "reason": "변경 가능하다고 판단",
        }], "overall": "SUPPORTED"}
        responses = [
            {"message": {"content": json.dumps(extracted, ensure_ascii=False)}},
            {"message": {"content": json.dumps(changed, ensure_ascii=False)}},
        ]
        urlopen.side_effect = [io.BytesIO(json.dumps(raw).encode("utf-8")) for raw in responses]
        data = {"answer": "월 1회 변경할 수 있습니다.",
                "sources": [{"sourceId": "FAQ-1", "answer": "한 달에 한 번 변경 가능합니다."}]}

        with self.assertRaises(judge.JudgeCallError) as context:
            judge.ollama_chat("http://unused", "qwen3:14b", "grounding", data)

        self.assertEqual(2, urlopen.call_count)
        self.assertNotIn("repairRequest", context.exception.record)
        self.assertIn("ClaimTextMismatch", context.exception.record["error"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_does_not_retry_invalid_extraction(self, urlopen):
        invalid = {"claims": [{"quote": "질문에서 가져온 누출 문장"}]}
        urlopen.return_value.__enter__.return_value = io.BytesIO(json.dumps({
            "message": {"content": json.dumps(invalid, ensure_ascii=False)}
        }).encode("utf-8"))
        data = {"question": "질문에서 가져온 누출 문장", "answer": "답변 문장입니다.",
                "sources": [{"sourceId": "FAQ-1", "answer": "질문에서 가져온 누출 문장"}]}

        with self.assertRaises(judge.JudgeCallError) as context:
            judge.ollama_chat("http://unused", "qwen3:14b", "grounding", data)

        self.assertEqual(1, urlopen.call_count)
        self.assertIn("not an exact ordered span", context.exception.record["error"])
        self.assertNotIn("repairRequest", context.exception.record)

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_prompt_uses_source_id_without_optional_database_id(self, urlopen):
        outputs = [
            {"message": {"content": json.dumps({"claims": []})}},
            {"message": {"content": json.dumps({"claims": [], "overall": "NOT_APPLICABLE"})}},
        ]
        urlopen.side_effect = [io.BytesIO(json.dumps(raw).encode("utf-8")) for raw in outputs]
        data = {
            "question": "요금제 변경 방법은?", "answer": "안내가 어렵습니다.",
            "sources": [{"sourceId": "BILLING-0001", "faqId": None, "answer": "앱에서 변경합니다."}],
        }
        result = judge.ollama_chat("http://unused", "qwen3:14b", "grounding", data)
        prompt = result["request"]["messages"][-1]["content"]
        self.assertIn('"sourceId":"BILLING-0001"', prompt)
        self.assertIn('"claims":[]', prompt)
        self.assertIn('"faqSources":', prompt)
        self.assertNotIn("안내가 어렵습니다.", prompt)
        self.assertNotIn("faqId", prompt)
        self.assertIsNone(data["sources"][0]["faqId"])

    def test_generation_failure_is_distinct_from_retrieval_miss(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["executionStatus"] = "FAILED"
        turn["searches"] = []
        turn["generatorCallRecords"] = [{"task_type": "RAG_ANSWER", "status": "MODEL_ERROR"}]
        self.assertEqual(["PROCESSING_FAILURE", "GENERATION_FAILURE"], judge.stage_findings(turn))
        turn["generatorCallRecords"][0]["status"] = "SUCCESS"
        self.assertEqual(["PROCESSING_FAILURE"], judge.stage_findings(turn))
        turn["executionStatus"] = "COMPLETED"
        self.assertEqual(["RETRIEVAL_MISS"], judge.stage_findings(turn))

    def test_abstention_rules_use_claims_and_saved_answer_basis(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["outputMessage"]["answerBasis"] = "GROUNDED"
        unsupported = {"overall": "UNSUPPORTED"}
        no_claim = {"overall": "NOT_APPLICABLE"}
        adequacy = {"coverage": "COMPLETE", "abstention": "NOT_APPLICABLE"}
        decision = judge.resolve_abstention(turn, unsupported, adequacy)
        self.assertEqual("SHOULD_ABSTAIN", decision["label"])
        self.assertTrue(decision["disagreesWithJudge"])
        self.assertIn("SHOULD_ABSTAIN", judge.stage_findings(turn, unsupported, adequacy, decision))
        turn["fixture"]["expectedBehavior"] = "STORE_LOOKUP"
        self.assertIn("UNSUPPORTED_CLAIM", judge.stage_findings(turn, unsupported, adequacy, decision))
        turn["fixture"]["expectedBehavior"] = "ANSWER"

        turn["outputMessage"]["answerBasis"] = "NO_EVIDENCE"
        turn["searches"] = []
        decision = judge.resolve_abstention(turn, no_claim, adequacy)
        self.assertEqual("APPROPRIATE", decision["label"])
        self.assertEqual("NO_EVIDENCE_FALLBACK", decision["rule"])
        turn["outputMessage"]["answerBasis"] = "OUT_OF_SCOPE"
        self.assertEqual("OVER_REFUSAL", judge.resolve_abstention(turn, no_claim, adequacy)["label"])
        turn["fixture"]["expectedBehavior"] = "OUT_OF_SCOPE"
        self.assertEqual("APPROPRIATE", judge.resolve_abstention(turn, no_claim, adequacy)["label"])

    def test_possible_over_refusal_remains_visible_for_review(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["outputMessage"]["answerBasis"] = "NO_EVIDENCE"
        decision = judge.resolve_abstention(
            turn, {"overall": "NOT_APPLICABLE"}, {"abstention": "OVER_REFUSAL"}
        )
        self.assertEqual("OVER_REFUSAL", decision["label"])
        self.assertTrue(decision["requiresReview"])
        self.assertNotIn("OVER_REFUSAL", judge.stage_findings(
            turn, {"overall": "NOT_APPLICABLE"}, {"coverage": "MISSED", "abstention": "OVER_REFUSAL"}, decision
        ))

    def test_gold_is_excluded_from_grounding_prompt(self):
        turn = judge.enrich_source_ids(sample_capture(), CATALOG)["cases"][0]["turns"][0]
        grounding = judge.prompt_data(turn, [], "grounding")
        adequacy = judge.prompt_data(turn, [], "adequacy")
        self.assertNotIn("goldSourceSlotIds", grounding)
        self.assertNotIn("requiredFacts", grounding)
        self.assertEqual(["BILLING-0001"], adequacy["goldSourceSlotIds"])
        self.assertEqual("BILLING-0001", grounding["sources"][0]["sourceId"])

    def test_adequacy_request_excludes_expected_labels_and_backend_basis(self):
        case = json.loads((Path(__file__).resolve().parents[2]
                           / "scripts/chat_judge/data/chat_judge_validation_v2.json").read_text(encoding="utf-8"))["cases"][0]
        original = copy.deepcopy(case["adequacyInput"])
        request = judge.judge_request("qwen3:14b", "adequacy", original)
        prompt = request["messages"][-1]["content"]
        for key in ("expectedBehavior", "missingFact", "goldSourceSlotIds",
                    "goldSourceGroups", "actualSourceSlotIds", "answerBasis", "faqId"):
            self.assertNotIn(f'"{key}"', prompt)
        self.assertIn('"requiredFacts"', prompt)
        self.assertEqual(case["adequacyInput"], original)

        abstention = judge.judge_prompt_input("abstention", original)
        self.assertEqual({"question", "previousTurns", "answer", "sources"}, set(abstention))
        self.assertNotIn("requiredFacts", judge.judge_request(
            "qwen3:14b", "abstention", original)["messages"][-1]["content"])

    def test_abstention_decision_combines_grounding_and_independent_signals(self):
        signals = {"answerIsRefusal": False, "evidenceAnswerability": "ENOUGH",
                   "evidenceQuotes": [{"sourceId": "BILLING-0001", "quote": "월 1회"}],
                   "reason": "답변했습니다."}
        self.assertEqual("SHOULD_ABSTAIN", judge.decide_abstention(
            {"overall": "UNSUPPORTED"}, signals, "NOT_APPLICABLE")["label"])
        self.assertEqual("NOT_APPLICABLE", judge.decide_abstention(
            {"overall": "SUPPORTED"}, signals)["label"])
        signals["answerIsRefusal"] = True
        self.assertEqual("OVER_REFUSAL", judge.decide_abstention(
            {"overall": "NOT_APPLICABLE"}, signals)["label"])
        signals["evidenceAnswerability"] = "INSUFFICIENT"
        self.assertEqual("APPROPRIATE", judge.decide_abstention(
            {"overall": "NOT_APPLICABLE"}, signals)["label"])
        signals["evidenceAnswerability"] = "UNCERTAIN"
        decision = judge.decide_abstention({"overall": "NOT_APPLICABLE"}, signals)
        self.assertEqual("REVIEW", decision["label"])
        self.assertTrue(decision["requiresReview"])
        self.assertIs(signals, judge.validate_result(
            "abstention", signals, {"BILLING-0001"},
            sources=[{"sourceId": "BILLING-0001", "answer": "월 1회 바꿀 수 있습니다."}],
        ))

    def test_abstention_requires_a_real_faq_quote(self):
        source = {"sourceId": "USIM-0001", "answer": "7,700원이며 택배로 2~3일 걸립니다."}
        result = {"answerIsRefusal": True, "evidenceAnswerability": "ENOUGH",
                  "evidenceQuotes": [], "reason": "근거가 있다고 판단"}
        with self.assertRaisesRegex(ValueError, "FAQ 인용문이 없습니다"):
            judge.validate_result("abstention", result, {"USIM-0001"}, sources=[source])
        result["evidenceQuotes"] = [{"sourceId": "USIM-0001", "quote": "택배비가 포함됩니다"}]
        with self.assertRaisesRegex(ValueError, "실제 검색 근거"):
            judge.validate_result("abstention", result, {"USIM-0001"}, sources=[source])

    def test_coverage_uses_each_required_fact_and_answer_quote(self):
        result = {"coverage": "COMPLETE", "factChecks": [
            {"requiredFact": "데이터 8GB", "answered": True, "answerQuote": "데이터 8GB"},
            {"requiredFact": "통화 100분", "answered": False, "answerQuote": ""},
        ], "missingFacts": [], "reason": "첫 항목만 답함"}
        self.assertIs(result, judge.validate_result(
            "coverage", result, set(), answer="매월 데이터 8GB를 제공합니다.",
            required_facts=["데이터 8GB", "통화 100분"],
        ))
        self.assertEqual("PARTIAL", result["coverage"])
        self.assertEqual(["통화 100분"], result["missingFacts"])
        result["factChecks"][0]["answerQuote"] = "통화 100분"
        with self.assertRaisesRegex(ValueError, "실제 답변"):
            judge.validate_result("coverage", result, set(), answer="매월 데이터 8GB를 제공합니다.",
                                  required_facts=["데이터 8GB", "통화 100분"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_malformed_model_output_preserves_raw_response(self, urlopen):
        urlopen.return_value.__enter__.return_value = io.BytesIO(json.dumps({
            "message": {"content": "{not valid"}, "eval_count": 7
        }).encode("utf-8"))
        with self.assertRaises(judge.JudgeCallError) as context:
            judge.ollama_chat("http://unused", "qwen3:14b", "grounding", {"sources": []})
        record = context.exception.record
        self.assertIn("request", record)
        self.assertEqual("{not valid", record["rawResponse"]["message"]["content"])
        self.assertIn("error", record)

    @patch.object(judge, "model_digest", return_value="fake-digest")
    @patch.object(judge, "ollama_version", return_value="0.34.0")
    def test_failed_judge_call_is_unscored_and_excluded_from_judge_findings(self, *_):
        def caller(_url, _model, kind, _data):
            if kind == "grounding":
                raise judge.JudgeCallError({"error": "invalid JSON", "rawResponse": {"message": {"content": "{"}}})
            return {"result": {
                "coverage": "MISSED", "missingFacts": ["월 1회"],
                "abstention": "OVER_REFUSAL", "reason": "평가 불일치",
            }}

        evaluation = judge.evaluate(sample_capture(), "http://unused", "qwen3:14b", CATALOG, caller)
        turn = evaluation["cases"][0]["turns"][0]
        self.assertEqual("UNSCORED", turn["judgeStatus"])
        self.assertEqual([], turn["findings"])
        self.assertEqual(1, judge.summarize(evaluation)["unscored"])
        self.assertIn("rawResponse", turn["grounding"])

    @patch.object(judge, "model_digest", side_effect=ValueError("model missing"))
    def test_missing_judge_model_leaves_all_turns_unscored(self, _):
        def forbidden_caller(*_args):
            self.fail("모델 정보가 없으면 판정을 요청하지 않아야 합니다.")

        evaluation = judge.evaluate(sample_capture(), "http://unused", "qwen3:14b", CATALOG,
                                    forbidden_caller)
        self.assertEqual(1, judge.summarize(evaluation)["unscored"])
        self.assertIn("model missing", evaluation["cases"][0]["turns"][0]["error"])

    def test_archived_pilot_reclassifies_three_judge_mistakes_without_model_calls(self):
        root = Path(__file__).resolve().parents[2]
        capture = json.loads((root / "docs/chat-judge/experiments/V1-pilot-fewshot/20261001-capture.json").read_text(encoding="utf-8"))
        previous = json.loads((root / "docs/chat-judge/experiments/V1-pilot-fewshot/20261001-judged.json").read_text(encoding="utf-8"))
        catalog = json.loads((root / "scripts/data/faq_full_1150.json").read_text(encoding="utf-8"))
        updated = judge.reconcile_evaluation(capture, previous, catalog)
        decisions = {case["caseId"]: [turn.get("abstentionDecision") for turn in case["turns"]]
                     for case in updated["cases"]}
        self.assertEqual("SHOULD_ABSTAIN", decisions["MIXED"][0]["label"])
        self.assertEqual("APPROPRIATE", decisions["FAQ_COMPOUND"][0]["label"])
        self.assertEqual("APPROPRIATE", decisions["STORE"][0]["label"])
        self.assertEqual(3, updated["summary"]["abstentionJudgeDisagreements"])
        self.assertEqual(2, updated["summary"]["findings"]["SHOULD_ABSTAIN"])
        self.assertEqual(previous["cases"][3]["turns"][0]["adequacy"]["result"]["abstention"],
                         "NOT_APPLICABLE")


if __name__ == "__main__":
    unittest.main()
