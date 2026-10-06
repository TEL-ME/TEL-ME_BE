"""Offline tests for pairwise comparison accounting."""

import unittest

from scripts.chat_judge.experiments.v7_cross_judge import compare_models


def item(grounding, quality, label, refusal, answerability):
    return {
        "executionStatus": "COMPLETED",
        "axisStatus": {"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"},
        "grounding": {"result": {"overall": grounding, "claims": [{"verdict": grounding}]}},
        "quality": {"result": {"groups": [{"outcome": quality}]}},
        "abstentionDecision": {"label": label},
        "abstention": {"result": {"answerIsRefusal": refusal,
                                    "evidenceAnswerability": answerability},
                       "questionParts": [{"evidenceAnswerability": answerability}]},
    }


class CompareModelsTest(unittest.TestCase):
    def test_pairwise_agreement_counts_only_scored_axes(self):
        qwen_item = item("SUPPORTED", "COMPLETE", "APPROPRIATE", False, "ENOUGH")
        gpt_item = item("UNSUPPORTED", "COMPLETE", "SHOULD_ABSTAIN", False, "INSUFFICIENT")
        result = compare_models.summarize({("c", 0): qwen_item}, {("c", 0): gpt_item})

        self.assertEqual(1, result["axes"]["grounding"]["disagreements"])
        self.assertEqual(1, result["axes"]["quality"]["agreements"])
        self.assertEqual(1, result["axes"]["abstention"]["disagreements"])
        self.assertEqual(1, result["turnsWithAnyDisagreement"])

    def test_unscored_axis_is_excluded_from_pairwise_rate(self):
        qwen_item = item("SUPPORTED", "COMPLETE", "APPROPRIATE", False, "ENOUGH")
        gpt_item = item("SUPPORTED", "COMPLETE", "APPROPRIATE", False, "ENOUGH")
        gpt_item["axisStatus"]["quality"] = "UNSCORED"
        result = compare_models.summarize({("c", 0): qwen_item}, {("c", 0): gpt_item})

        self.assertEqual(0, result["axes"]["quality"]["scoredPairs"])
        self.assertEqual(1, result["axes"]["quality"]["missingGptOss"])
        self.assertEqual(1, result["axes"]["grounding"]["agreements"])


if __name__ == "__main__":
    unittest.main()
