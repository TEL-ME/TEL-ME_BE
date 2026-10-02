"""대규모 Judge 평가셋의 재현성과 판정 기준 배치를 검증한다."""

import copy
import json
import unittest
from collections import Counter

from scripts.chat_judge.experiments.v3_fixed_500_case_validation import build_judge_validation_v2 as builder
from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v3_fixed_500_case_validation import run_judge_validation_v2 as runner


def inputs():
    return [json.loads(path.read_text(encoding="utf-8")) for path in
            (builder.CATALOG, builder.PILOT, builder.V1, builder.FEWSHOT,
             builder.EQUIVALENCE_LABELS)]


class JudgeValidationV2Test(unittest.TestCase):
    def test_reused_adequacy_requires_identical_request_and_model(self):
        case = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))["cases"][0]
        metadata = {
            "datasetSha256": "dataset", "catalogSha256": "catalog", "judgeModel": "qwen3:14b",
            "judgeModelDigest": "digest", "judgeOllamaVersion": "0.34.0",
            "adequacyRubricSha256": judge.sha256(judge.ADEQUACY_RUBRIC.encode("utf-8")),
        }
        previous = {**metadata, "cases": [{
            "caseId": case["caseId"], "adequacy": {
                "request": judge.judge_request("qwen3:14b", "adequacy", case["adequacyInput"]),
                "result": {"coverage": "MISSED", "abstention": "APPROPRIATE"},
            },
        }]}
        result = {"cases": [copy.deepcopy(case)]}
        runner.reuse_adequacy(result, previous, metadata)
        self.assertEqual(previous["cases"][0]["adequacy"], result["cases"][0]["adequacy"])
        previous["cases"][0]["adequacy"]["request"]["messages"][0]["content"] = "changed"
        with self.assertRaises(ValueError):
            runner.reuse_adequacy({"cases": [copy.deepcopy(case)]}, previous, metadata)

    def test_runner_skips_unlabelled_coverage(self):
        dataset = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
        cases = copy.deepcopy(dataset["cases"][:1])
        case = cases[0]
        case["grounding"] = {"result": {"overall": case["expected"]["grounding"]}}
        case["coverage"] = {"result": {"coverage": "MISSED"}}
        case["abstentionDecision"] = {"label": case["expected"]["abstention"],
                                      "requiresReview": False}
        case["judgeStatus"] = "SCORED"
        summary = runner.summarize(cases)
        self.assertEqual(1, summary["scored"])
        self.assertEqual(0 if case["expected"]["coverage"] is None else 1,
                         summary["byAxis"]["coverage"]["labelled"])

    def test_summary_uses_final_abstention_decision(self):
        case = copy.deepcopy(json.loads(builder.OUTPUT.read_text(encoding="utf-8"))["cases"][0])
        case["expected"]["abstention"] = "SHOULD_ABSTAIN"
        case["grounding"] = {"result": {"overall": case["expected"]["grounding"]}}
        case["coverage"] = {"result": {"coverage": "MISSED"}}
        case["abstentionDecision"] = {"label": "SHOULD_ABSTAIN", "requiresReview": False}
        case["judgeStatus"] = "SCORED"
        self.assertEqual(1, runner.summarize([case])["byAxis"]["abstention"]["correct"])
        case["abstentionDecision"] = {"label": "REVIEW", "requiresReview": True}
        summary = runner.summarize([case])
        self.assertEqual(0, summary["byAxis"]["abstention"]["scored"])
        self.assertEqual([case["caseId"]], summary["abstentionReviewCaseIds"])

    def test_reuse_rejects_grounding_from_previous_rubric(self):
        archived = runner.load_result(builder.ROOT / "docs/chat-judge/experiments/V3-fixed-500-case-validation/20261001-validation-v2-contract-raw.json.gz")
        dataset = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
        required = ("datasetSha256", "catalogSha256", "judgeModel", "judgeModelDigest",
                    "judgeOllamaVersion", "groundingRubricSha256", "groundingSchemaSha256")
        metadata = {key: archived[key] for key in required}
        result = {"cases": copy.deepcopy(dataset["cases"])}
        with self.assertRaises(ValueError):
            runner.reuse_grounding(result, archived, metadata)

    def test_reused_abstention_signals_require_exact_request(self):
        case = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))["cases"][0]
        required = ("datasetSha256", "catalogSha256", "judgeModel", "judgeModelDigest",
                    "judgeOllamaVersion", "abstentionRubricSha256", "abstentionSchemaSha256")
        metadata = {key: "same" for key in required}
        metadata["judgeModel"] = "qwen3:14b"
        source = case["adequacyInput"]["sources"][0]
        record = {"request": judge.judge_request("qwen3:14b", "abstention", case["adequacyInput"]),
                  "result": {"answerIsRefusal": True, "evidenceAnswerability": "ENOUGH",
                             "evidenceQuotes": [{"sourceId": source["sourceId"], "quote": source["answer"]}],
                             "reason": "근거가 있음"}}
        previous = {**metadata, "cases": [{"caseId": case["caseId"], "abstentionSignals": record}]}
        result = {"cases": [copy.deepcopy(case)]}
        runner.reuse_abstention_signals(result, previous, metadata)
        self.assertEqual(record, result["cases"][0]["abstentionSignals"])
        previous["cases"][0]["abstentionSignals"]["request"]["messages"][0]["content"] = "changed"
        with self.assertRaises(ValueError):
            runner.reuse_abstention_signals({"cases": [copy.deepcopy(case)]}, previous, metadata)

    def test_saved_dataset_is_reproducible_and_balanced(self):
        catalog, pilot, v1, fewshot, equivalence = inputs()
        saved = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
        self.assertEqual(saved, builder.build_dataset(catalog, pilot, v1, fewshot, equivalence))
        cases = saved["cases"]
        self.assertEqual(500, len(cases))
        self.assertEqual(500, len({case["groundingInput"]["question"] for case in cases}))
        self.assertEqual({"NOT_APPLICABLE": 150, "SHOULD_ABSTAIN": 100,
                          "OVER_REFUSAL": 100, "APPROPRIATE": 150},
                         dict(Counter(case["expected"]["abstention"] for case in cases)))
        self.assertEqual(50, sum(case["expected"]["coverage"] == "PARTIAL" for case in cases))
        self.assertEqual(100, sum(case["expected"]["coverage"] is None for case in cases))

    def test_grounding_input_never_contains_answer_key(self):
        dataset = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
        for case in dataset["cases"]:
            self.assertFalse({"expected", "requiredFacts", "goldSourceSlotIds", "expectedBehavior"}
                             & case["groundingInput"].keys())
            self.assertNotIn("expected", case["adequacyInput"])
            request = judge.judge_prompt_input("adequacy", case["adequacyInput"])
            self.assertFalse({"expectedBehavior", "missingFact", "goldSourceSlotIds",
                              "goldSourceGroups", "actualSourceSlotIds", "answerBasis"} & request.keys())
            self.assertTrue(all("faqId" not in source for source in request["sources"]))
            coverage = judge.judge_prompt_input("coverage", case["adequacyInput"])
            self.assertFalse({"sources", "expectedBehavior", "goldSourceSlotIds", "answerBasis"}
                             & coverage.keys())
            self.assertIn("requiredFacts", coverage)

    def test_modified_source_or_gold_label_fails_validation(self):
        catalog, pilot, v1, fewshot, equivalence = inputs()
        questions, sources = builder.excluded_questions_and_sources(pilot, v1, fewshot)
        dataset = builder.build_dataset(catalog, pilot, v1, fewshot, equivalence)
        changed = copy.deepcopy(dataset)
        grounded = next(case for case in changed["cases"] if case["behavior"] == "SUPPORTED")
        grounded["expected"]["abstention"] = "APPROPRIATE"
        with self.assertRaises(ValueError):
            builder.validate_dataset(changed, catalog, questions, sources)
        changed = copy.deepcopy(dataset)
        grounded = next(case for case in changed["cases"] if case["behavior"] == "SUPPORTED")
        grounded["groundingInput"]["sources"][0]["answer"] = "오염된 근거"
        with self.assertRaises(ValueError):
            builder.validate_dataset(changed, catalog, questions, sources)
        changed = copy.deepcopy(dataset)
        compound = next(case for case in changed["cases"] if case["behavior"] == "COMPOUND_PARTIAL")
        compound["groundingInput"]["question"] += " 추가 질문"
        compound["adequacyInput"]["question"] += " 추가 질문"
        with self.assertRaises(ValueError):
            builder.validate_dataset(changed, catalog, questions, sources)


if __name__ == "__main__":
    unittest.main()
