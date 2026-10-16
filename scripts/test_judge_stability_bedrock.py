#!/usr/bin/env python3
"""judge_stability_bedrock.py 집계 규칙 검증. 모델은 부르지 않는다."""
import unittest

from judge_stability_bedrock import (ANSWERABLE, FAILED, NOT_ANSWERABLE, PROMPTS, pair_rates, parse_verdict,
                                     score_run, served_evidence, slots_to_judge, unstable_cases)
import gray_zone_upper_band_bedrock as g

CASES = [
    {"case_id": "A1", "expected_answerable": True},
    {"case_id": "A2", "expected_answerable": True},
    {"case_id": "B1", "expected_answerable": False},
    {"case_id": "B2", "expected_answerable": False},
    {"case_id": "B3", "expected_answerable": False},
    {"case_id": "B4", "expected_answerable": False},
]


class ScoreRunTest(unittest.TestCase):
    def test_any_answerable_counts_as_answerable(self):
        run = {"A1": [NOT_ANSWERABLE, ANSWERABLE], "A2": [ANSWERABLE],
               "B1": [NOT_ANSWERABLE], "B2": [NOT_ANSWERABLE, NOT_ANSWERABLE],
               "B3": [NOT_ANSWERABLE], "B4": [NOT_ANSWERABLE]}

        self.assertEqual(score_run(CASES, run),
                         {"wrongBlock": 0, "blocked": 4, "correct": 6, "failedCases": 0, "passed": True})

    def test_fails_on_two_wrong_blocks_or_too_few_blocks(self):
        two_wrong = {"A1": [NOT_ANSWERABLE], "A2": [FAILED], "B1": [NOT_ANSWERABLE], "B2": [NOT_ANSWERABLE],
                     "B3": [NOT_ANSWERABLE], "B4": [NOT_ANSWERABLE]}
        leaky = {"A1": [ANSWERABLE], "A2": [ANSWERABLE], "B1": [ANSWERABLE], "B2": [NOT_ANSWERABLE],
                 "B3": [NOT_ANSWERABLE], "B4": [NOT_ANSWERABLE]}

        self.assertEqual((score_run(CASES, two_wrong)["wrongBlock"], score_run(CASES, two_wrong)["passed"]), (2, False))
        self.assertEqual(score_run(CASES, two_wrong)["failedCases"], 1)
        self.assertEqual((score_run(CASES, leaky)["blocked"], score_run(CASES, leaky)["passed"]), (3, False))

    def test_cases_without_evidence_are_skipped(self):
        self.assertEqual(score_run(CASES, {"A1": [ANSWERABLE]})["correct"], 1)


class UnstableTest(unittest.TestCase):
    def test_reports_cases_whose_outcome_changed(self):
        runs = [{"A1": [ANSWERABLE, NOT_ANSWERABLE], "B1": [NOT_ANSWERABLE]},
                {"A1": [NOT_ANSWERABLE, NOT_ANSWERABLE], "B1": [NOT_ANSWERABLE]},
                {"A1": [NOT_ANSWERABLE, ANSWERABLE], "B1": [NOT_ANSWERABLE]}]

        self.assertEqual(unstable_cases(runs), ["A1"])


class ParseVerdictTest(unittest.TestCase):
    def test_same_as_served_judge(self):
        self.assertEqual(parse_verdict('{"asked": ["a"], "found": [true], "answerable": true}'), ANSWERABLE)
        self.assertEqual(parse_verdict('앞말 {"answerable": "False"} 뒷말'), NOT_ANSWERABLE)

    def test_unreadable_answer_is_a_failure_not_answerable(self):
        for text in (None, "", "답할 수 있습니다", '{"answerable": "maybe"}', '{"found": [true]}', "{깨진 JSON}"):
            self.assertEqual(parse_verdict(text), FAILED, text)


class ServedTest(unittest.TestCase):
    """운영 JudgingFaqGrayZoneSelector와 같은 근거를 고르는지 본다(질문 0.80 / 0.90 / 0.88, Q_A 0.69 / 0.80 / 0.72)."""

    def test_high_scores_pass_without_judgment_and_low_ones_drop(self):
        verdicts = {}
        evidence = served_evidence([("A", 0.95), ("B", 0.79)], [("C", 0.85), ("D", 0.68)], verdicts.__getitem__)

        self.assertEqual(evidence, ["A", "C"])
        self.assertEqual(slots_to_judge([("A", 0.95), ("B", 0.79)], [("C", 0.85), ("D", 0.68)]), [])

    def test_judged_band_follows_verdict_and_failure_keeps_only_above_previous_threshold(self):
        qo = [("A", 0.89), ("B", 0.85)]
        qa = [("C", 0.75), ("D", 0.70), ("A", 0.71)]
        verdicts = {"A": FAILED, "B": FAILED, "C": NOT_ANSWERABLE, "D": ANSWERABLE}

        self.assertEqual(slots_to_judge(qo, qa), ["A", "B", "C", "D"])
        self.assertEqual(served_evidence(qo, qa, verdicts.__getitem__), ["A", "D"])

    def test_question_vector_first_and_three_at_most(self):
        verdicts = {"E": ANSWERABLE}
        evidence = served_evidence([("A", 0.95), ("B", 0.93)], [("C", 0.90), ("A", 0.85), ("D", 0.81), ("E", 0.75)],
                                   verdicts.__getitem__)

        self.assertEqual(evidence, ["A", "B", "C"])


class PairRateTest(unittest.TestCase):
    def test_rate_ignores_failed_calls(self):
        rates = pair_rates({("A1", "S1"): [ANSWERABLE, NOT_ANSWERABLE, FAILED, ANSWERABLE],
                            ("B1", "S2"): [NOT_ANSWERABLE, NOT_ANSWERABLE]})

        self.assertAlmostEqual(rates[("A1", "S1")], 2 / 3)
        self.assertEqual(rates[("B1", "S2")], 0)


class PromptTest(unittest.TestCase):
    def test_v3_is_the_previous_prompt_and_v4a_is_served(self):
        self.assertEqual(PROMPTS["v3"], g.B3_SYSTEM_PROMPT)
        self.assertEqual(PROMPTS["v4a"], g.B4_SYSTEM_PROMPT)

    def test_each_candidate_adds_one_rule_to_the_previous(self):
        v3, v4a = PROMPTS["v3"].splitlines(), PROMPTS["v4a"].splitlines()
        added = [line for line in v4a if line not in v3]

        self.assertEqual(len(v4a) - len(v3), 2)
        self.assertTrue(added[0].startswith("   - 비교하는 대상들의 값이"))
        v4b = PROMPTS["v4b"].splitlines()
        self.assertEqual(len(v4b) - len(v4a), 2)
        self.assertTrue([line for line in v4b if line not in v4a][0].startswith("   고객이 물은 범위를"))
        v4c = PROMPTS["v4c"].splitlines()
        self.assertEqual(len(v4c) - len(v4b), 2)
        self.assertTrue([line for line in v4c if line not in v4b][0].startswith("   - 그 항목이 FAQ에 적혀 있지 않으면"))


if __name__ == "__main__":
    unittest.main()
