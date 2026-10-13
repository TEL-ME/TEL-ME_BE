import unittest

from gray_zone_threshold_bedrock import evaluate, gray_pairs, input_hash, lines, pick, reusable, served
from simulate_dual_vector import merge


def results(*pairs):
    return [{"slot_id": slot, "score": score} for slot, score in pairs]


QA = results(("a1", 0.80), ("a2", 0.70), ("a3", 0.65), ("a4", 0.90))
QO = results(("b1", 0.90), ("b2", 0.85), ("a1", 0.79))


class ServedTest(unittest.TestCase):

    def test_matches_current_merge_when_lines_are_current(self):
        self.assertEqual(
            merge(QA, QO, 0.72, 0.88, 3),
            served(QA, QO, 0.72, 0.88, lambda slot: True),
        )

    def test_gray_candidate_needs_judge_pass(self):
        self.assertEqual(["b1", "a1"], served(QA, QO, 0.64, 0.84, lambda slot: False))
        self.assertEqual(["b1", "b2", "a1"], served(QA, QO, 0.64, 0.84, lambda slot: slot == "b2"))
        self.assertEqual(["b1", "a1", "a2"], served(QA, QO, 0.64, 0.84, lambda slot: slot == "a2"))

    def test_below_line_is_rejected_even_if_judge_passes(self):
        self.assertEqual(["b1", "a1", "a2"], served(QA, QO, 0.68, 0.86, lambda slot: True))

    def test_only_top_k_of_each_vector(self):
        # a4는 점수가 높아도 Q_A 4위라 후보가 아니다
        self.assertNotIn("a4", served(QA, QO, 0.60, 0.80, lambda slot: True))


class GrayPairsTest(unittest.TestCase):

    def test_collects_gray_candidates_at_floor(self):
        qa = {"E1": {"results": QA}}
        qo = {"E1": {"results": QO}}

        self.assertEqual(
            [("E1", "a2"), ("E1", "a3"), ("E1", "a1"), ("E1", "b2")].sort() or
            sorted([("E1", "a2"), ("E1", "a3"), ("E1", "a1"), ("E1", "b2")]),
            gray_pairs(qa, qo, 0.60, 0.70),
        )


class EvaluateTest(unittest.TestCase):

    def test_candidate_without_slot_id_is_not_counted_as_judge_call(self):
        qa = {"E1": {"type": "UNRELATED", "source": "130",
                     "results": results((None, 0.70), ("a2", 0.69))}}
        qo = {"E1": {"results": results(("b1", 0.70))}}

        result = evaluate(qa, qo, {}, 0.68, 0.88, {("E1", "a2"): False})

        self.assertEqual(1, result["judgeCalls"])
        self.assertEqual(1, result["judgedQuestions"])


class ReusableTest(unittest.TestCase):

    def entry(self, prompt, runs):
        return {"inputHash": input_hash(prompt), "runs": [{"answerable": True}] * runs}

    def test_reuses_only_same_input_and_repeat(self):
        self.assertTrue(reusable(self.entry("질문 A", 3), "질문 A", 3))
        self.assertFalse(reusable(self.entry("질문 A", 3), "질문 B", 3))
        self.assertFalse(reusable(self.entry("질문 A", 1), "질문 A", 3))
        self.assertFalse(reusable({"runs": [{"answerable": True}] * 3}, "질문 A", 3))
        self.assertFalse(reusable(None, "질문 A", 3))


class LinesTest(unittest.TestCase):

    def test_includes_upper_and_floor(self):
        self.assertEqual([0.72, 0.70, 0.68, 0.66, 0.64, 0.62, 0.60], lines(0.72, 0.60))


class PickTest(unittest.TestCase):

    def result(self, qa_line, answered, unrelated_130, calls):
        return {"qaLine": qa_line, "qoLine": 0.88, "judgeCalls": calls,
                "answered": {"공식": set(range(answered))},
                "unrelatedPassed": {"130": set(range(unrelated_130)), "supp": set()}}

    def test_keeps_unrelated_per_source_then_max_answered_then_fewer_calls(self):
        base = self.result(0.72, 59, 3, 0)
        runs = [
            base,
            self.result(0.70, 62, 3, 10),
            self.result(0.68, 62, 3, 20),
            self.result(0.66, 66, 4, 30),  # 무관 통과 증가 → 제외
        ]

        self.assertEqual(0.70, pick(base, runs, ["130", "supp"])["qaLine"])


if __name__ == "__main__":
    unittest.main()
