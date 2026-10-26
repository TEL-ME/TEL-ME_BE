"""Judge 검증셋 변환과 사람 판정 비교 검사."""

import json
import tempfile
import unittest
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge import run_judge_validation as validation

ROOT = Path(__file__).resolve().parents[2]


class JudgeValidationTest(unittest.TestCase):
    def test_dataset_converts_to_a_valid_capture_without_faq_question_text(self):
        data = json.loads(validation.DATASET.read_text(encoding="utf-8"))
        capture = validation.to_capture(data["items"])
        judge.validate_capture(capture)
        judged = [turn for case in capture["cases"] for turn in case["turns"] if turn["executionStatus"] == "COMPLETED"]
        self.assertEqual(len(data["items"]), len(judged))
        self.assertTrue(all(result["question"] == "" for turn in judged
                            for search in turn["searches"] for result in search["results"]))

    def test_review_form_has_no_existing_labels(self):
        form = (ROOT / "docs/03-evaluation/chat-judge/judge-validation-v1-review-form.md").read_text(encoding="utf-8")
        self.assertNotIn("SHOULD_ABSTAIN |", form)
        for item in json.loads(validation.DATASET.read_text(encoding="utf-8"))["items"][:5]:
            self.assertIn(f"`{item['id']}`", form)

    def test_filled_review_and_agreement(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "b.md"
            path.write_text("## 1. `X:0`\n- 근거성: `UNSUPPORTED`\n- 충족도: COMPLETE\n- 보류: `SHOULD_ABSTAIN`\n"
                            "## 2. `Y:0`\n- 근거성: \n- 충족도: \n- 보류: \n", encoding="utf-8")
            human_b = validation.review_b(path)
        self.assertEqual({"X:0": {"grounding": "UNSUPPORTED", "quality": "COMPLETE", "abstention": "SHOULD_ABSTAIN"}}, human_b)
        human_a = {"X:0": {"grounding": "UNSUPPORTED", "quality": "PARTIAL", "abstention": "SHOULD_ABSTAIN"}}
        model = {"X:0": {"grounding": "SUPPORTED", "quality": "REVIEW", "abstention": "SHOULD_ABSTAIN"}}
        report = validation.compare(human_a, model, ["X:0"])
        self.assertEqual(1, report["missedUnsupported"])
        self.assertEqual(1, report["quality"]["unresolved"])
        self.assertEqual(1, report["abstention"]["match"])


if __name__ == "__main__":
    unittest.main()
