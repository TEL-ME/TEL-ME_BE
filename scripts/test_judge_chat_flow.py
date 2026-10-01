"""로컬 모델 호출 없이 평가 입력과 실패 격리를 검증한다."""

import copy
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

import judge_chat_flow as judge
import compare_chat_judge as comparison


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
        root = Path(__file__).resolve().parents[1] / "docs/chat-judge"
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
        root = Path(__file__).resolve().parents[1]
        capture = json.loads((root / "docs/chat-judge/20261001-capture.json").read_text(encoding="utf-8"))
        fewshot = json.loads((root / "scripts/data/chat_judge_fewshot.json").read_text(encoding="utf-8"))
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
        raw = {"message": {"content": json.dumps({"claims": [], "overall": "NOT_APPLICABLE"})}}
        urlopen.return_value.__enter__.side_effect = lambda: io.BytesIO(json.dumps(raw).encode("utf-8"))
        root = Path(__file__).resolve().parents[1]
        example = json.loads((root / "scripts/data/chat_judge_fewshot.json").read_text(encoding="utf-8"))["grounding"][0]
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

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_prompt_uses_source_id_without_optional_database_id(self, urlopen):
        raw = {"message": {"content": json.dumps({"claims": [], "overall": "NOT_APPLICABLE"})}}
        urlopen.return_value.__enter__.side_effect = lambda: io.BytesIO(json.dumps(raw).encode("utf-8"))
        data = {
            "question": "요금제 변경 방법은?", "answer": "안내가 어렵습니다.",
            "sources": [{"sourceId": "BILLING-0001", "faqId": None, "answer": "앱에서 변경합니다."}],
        }
        result = judge.ollama_chat("http://unused", "qwen3:14b", "grounding", data)
        prompt = result["request"]["messages"][-1]["content"]
        self.assertIn('"sourceId":"BILLING-0001"', prompt)
        self.assertIn('"assistantAnswer":"안내가 어렵습니다."', prompt)
        self.assertIn('"faqSources":', prompt)
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
        root = Path(__file__).resolve().parents[1]
        capture = json.loads((root / "docs/chat-judge/20261001-capture.json").read_text(encoding="utf-8"))
        previous = json.loads((root / "docs/chat-judge/20261001-judged.json").read_text(encoding="utf-8"))
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
