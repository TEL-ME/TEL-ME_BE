import unittest

from scripts.chat_judge.experiments.v7_cross_judge.compare_sonnet46 import summarize


def model_row(execution_status="COMPLETED", *, grounding="SUPPORTED", quality="COMPLETE",
              abstention="ANSWER"):
    return {
        "executionStatus": execution_status,
        "axisStatus": {"grounding": "SCORED", "quality": "SCORED", "abstention": "SCORED"},
        "grounding": {"result": {"overall": grounding, "claims": []}},
        "quality": {"result": {"groups": [{"outcome": quality}]}},
        "abstention": {"result": {"answerIsRefusal": False,
                                    "evidenceAnswerability": "ENOUGH",
                                    "questionParts": []}},
        "abstentionDecision": {"label": abstention},
    }


class SonnetComparisonTests(unittest.TestCase):
    def test_counts_three_model_agreement_and_disagreement(self):
        rows = {
            "qwen": {("A", 0): model_row(), ("A", 1): model_row()},
            "gpt-oss-120b": {("A", 0): model_row(), ("A", 1): model_row(grounding="UNSUPPORTED")},
            "sonnet-4-6": {("A", 0): model_row(), ("A", 1): model_row()},
        }
        result = summarize(rows)
        self.assertEqual(2, result["completedPairedTurns"])
        self.assertEqual(1, result["axes"]["grounding"]["allThreeAgree"])
        self.assertEqual([("A", 1)], result["axes"]["grounding"]["disagreementTurns"])
        self.assertEqual(2, result["axes"]["quality"]["allThreeAgree"])
        self.assertEqual(2, result["axes"]["grounding"]["pairs"]["qwen vs sonnet-4-6"]["scored"])

    def test_unscored_axis_is_not_counted_as_three_model_agreement(self):
        missing = model_row()
        missing["axisStatus"]["quality"] = "UNSCORED"
        rows = {alias: {("A", 0): model_row()} for alias in
                ("qwen", "gpt-oss-120b", "sonnet-4-6")}
        rows["sonnet-4-6"][("A", 0)] = missing
        result = summarize(rows)
        self.assertEqual(0, result["axes"]["quality"]["scoredAllThree"])
        self.assertEqual(1, result["axes"]["grounding"]["scoredAllThree"])


if __name__ == "__main__":
    unittest.main()
