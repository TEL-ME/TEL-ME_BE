"""Judge 독립 검증셋의 구성과 채점 기준을 확인한다."""

import copy
import json
import unittest

import judge_chat_flow as judge
import judge_validation as validation


def inputs():
    return [json.loads(path.read_text(encoding="utf-8")) for path in
            (validation.FIXTURE, judge.DEFAULT_CATALOG, validation.PILOT, validation.FEWSHOT)]


class JudgeValidationTest(unittest.TestCase):
    def test_archived_comparison_preserves_every_raw_judge_call(self):
        path = validation.ROOT / "docs/chat-judge/20261001-validation-v1-results.json"
        result = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(36, len(result["cases"]))
        self.assertEqual(validation.summarize(result["cases"]), result["summary"])
        self.assertEqual(36, result["summary"]["scored"]["fewshot"])
        for case in result["cases"]:
            for mode in ("baseline", "fewshot"):
                for kind in ("grounding", "adequacy"):
                    self.assertIn("request", case[mode][kind])
                    self.assertIn("rawResponse", case[mode][kind])

    def test_labels_cover_all_major_outcomes_with_at_least_four_cases(self):
        cases = validation.build_cases(*inputs())
        self.assertEqual(36, len(cases))
        for axis, expected in {
            "grounding": {"SUPPORTED": 12, "UNSUPPORTED": 8, "NOT_APPLICABLE": 16},
            "coverage": {"COMPLETE": 8, "PARTIAL": 8, "MISSED": 16, "NOT_APPLICABLE": 4},
            "abstention": {"NOT_APPLICABLE": 12, "SHOULD_ABSTAIN": 8,
                           "OVER_REFUSAL": 8, "APPROPRIATE": 8},
        }.items():
            actual = {label: sum(case["expected"][axis] == label for case in cases)
                      for label in expected}
            self.assertEqual(expected, actual)

    def test_holdout_questions_and_sources_do_not_leak_into_examples(self):
        fixture, catalog, pilot, fewshot = inputs()
        cases = validation.build_cases(fixture, catalog, pilot, fewshot)
        fewshot_questions = {example["input"]["question"]
                             for kind in ("grounding", "adequacy") for example in fewshot[kind]}
        self.assertFalse({case["groundingInput"]["question"] for case in cases} & fewshot_questions)
        self.assertNotIn("goldSourceSlotIds", cases[0]["groundingInput"])
        leaked = copy.deepcopy(fixture)
        leaked["outOfScope"][0]["question"] = fewshot["adequacy"][0]["input"]["question"]
        with self.assertRaises(ValueError):
            validation.build_cases(leaked, catalog, pilot, fewshot)

    def test_unsupported_and_refusal_cases_use_different_evidence(self):
        cases = {case["caseId"]: case for case in validation.build_cases(*inputs())}
        wrong = cases["PLAN_CHANGE_wrong"]
        self.assertEqual("SHOULD_ABSTAIN", wrong["expected"]["abstention"])
        self.assertEqual(1, len(wrong["groundingInput"]["sources"]))
        refused_with = cases["PLAN_CHANGE_refusalWithSource"]
        refused_without = cases["PLAN_CHANGE_refusalWithoutSource"]
        self.assertEqual("OVER_REFUSAL", refused_with["expected"]["abstention"])
        self.assertEqual("APPROPRIATE", refused_without["expected"]["abstention"])
        self.assertEqual(1, len(refused_with["adequacyInput"]["sources"]))
        self.assertEqual([], refused_without["adequacyInput"]["sources"])
        self.assertEqual(refused_with["adequacyInput"]["requiredFacts"],
                         refused_without["adequacyInput"]["requiredFacts"])


if __name__ == "__main__":
    unittest.main()
