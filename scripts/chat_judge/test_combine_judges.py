"""모델별 판정 결합 규칙 검사."""

import unittest

from scripts.chat_judge import combine_judges as combine


def label(grounding="SUPPORTED", quality="COMPLETE", abstention="NOT_APPLICABLE", passed=True):
    return {"grounding": grounding, "quality": quality, "abstention": abstention, "pass": passed}


class CombineJudgesTest(unittest.TestCase):
    def test_any_fail_takes_the_strictest_judgment_on_every_axis(self):
        result = combine.combine_any_fail([
            label(),
            label("UNSUPPORTED", "PARTIAL", "SHOULD_ABSTAIN", False)])
        self.assertEqual({"grounding": "UNSUPPORTED", "quality": "PARTIAL", "abstention": "SHOULD_ABSTAIN",
                          "pass": False}, result)

    def test_any_fail_leaves_a_pass_unresolved_when_another_model_could_not_judge(self):
        result = combine.combine_any_fail([label(), label(None, None, None, None)])
        self.assertIsNone(result["pass"])
        self.assertIsNone(result["grounding"])
        self.assertFalse(combine.combine_any_fail([label(passed=False), label(None, None, None, None)])["pass"])

    def test_majority_needs_more_than_half_and_ties_are_unresolved(self):
        self.assertFalse(combine.combine_majority([label(passed=False), label(passed=False), label()])["pass"])
        self.assertTrue(combine.combine_majority([label(passed=False), label(), label()])["pass"])
        self.assertIsNone(combine.combine_majority([label(passed=False), label()])["pass"])

    def test_turn_labels_use_the_worst_subquestion_and_hide_review(self):
        item = {"grounding": {"result": {"overall": "REVIEW"}},
                "quality": {"result": {"groups": [{"outcome": "COMPLETE"}, {"outcome": "PARTIAL"}]}},
                "abstentionDecision": {"label": "APPROPRIATE"}, "answerQualityPass": None}
        self.assertEqual({"grounding": None, "quality": "PARTIAL", "abstention": "APPROPRIATE", "pass": None},
                         combine.turn_labels(item))


if __name__ == "__main__":
    unittest.main()
