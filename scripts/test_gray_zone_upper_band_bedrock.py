#!/usr/bin/env python3
"""gray_zone_upper_band_bedrock.py 계산 검증. DB, Ollama, Bedrock을 부르지 않는다."""
import unittest

from gray_zone_upper_band_bedrock import (cache_entry, input_hash, keep, majority, needs_judgment,
                                          pairs_to_judge, parse_answerable, score_eval, score_holdout, served,
                                          user_prompt)

CURRENT = (0.88, 0.72, 0.72)  # 판정 없음
LOWER_ONLY = (0.80, 0.71, 0.72)  # 아래로만 넓힘 (TELME-122 구조)
UPPER = (0.80, 0.71, 0.80)  # 위로도 넓힘


def always(value):
    return lambda slot: value


class KeepTest(unittest.TestCase):
    def test_upper_line_passes_without_verdict(self):
        self.assertTrue(keep(0.80, 0.80, 0.71, False))

    def test_below_floor_is_dropped_even_if_judged_true(self):
        self.assertFalse(keep(0.709, 0.80, 0.71, True))

    def test_floor_is_inside_band(self):
        self.assertTrue(keep(0.71, 0.80, 0.71, True))
        self.assertFalse(keep(0.71, 0.80, 0.71, False))

    def test_just_below_upper_follows_verdict(self):
        self.assertFalse(keep(0.799, 0.80, 0.71, False))


class ServedTest(unittest.TestCase):
    def test_current_config_never_asks_judge(self):
        def fail(slot):
            raise AssertionError("판정 없음 구성에서 판정기를 불렀다")
        picked = served([("A", 0.90), ("B", 0.85)], [("C", 0.75), ("D", 0.70)], *CURRENT, fail)
        self.assertEqual(picked, ["A", "C"])

    def test_upper_band_rejects_what_used_to_pass(self):
        qa = [("C", 0.75)]
        self.assertEqual(served([], qa, *LOWER_ONLY, always(False)), ["C"])
        self.assertEqual(served([], qa, *UPPER, always(False)), [])

    def test_question_vector_first_dedupe_and_cut_to_three(self):
        qo = [("A", 0.90), ("B", 0.89)]
        qa = [("B", 0.85), ("C", 0.84), ("D", 0.83)]
        self.assertEqual(served(qo, qa, *UPPER, always(True)), ["A", "B", "C"])

    def test_rejected_slot_frees_room(self):
        qo = [("A", 0.90), ("B", 0.85)]
        qa = [("C", 0.81), ("D", 0.79)]
        verdicts = {"B": False, "D": True}
        self.assertEqual(served(qo, qa, *UPPER, verdicts.get), ["A", "C", "D"])

    def test_same_slot_judged_once_per_question(self):
        self.assertEqual(needs_judgment([("A", 0.85)], [("A", 0.75), ("B", 0.70)], *UPPER), ["A"])


class MajorityTest(unittest.TestCase):
    def test_two_of_three(self):
        self.assertTrue(majority([True, True, False]))
        self.assertFalse(majority([True, False, False]))

    def test_tie_is_false(self):
        self.assertFalse(majority([True, False]))


class ParseAnswerableTest(unittest.TestCase):
    def test_reads_json_inside_text(self):
        self.assertFalse(parse_answerable('결과: {"asked": ["요금"], "found": [false], "answerable": false}'))

    def test_fail_open(self):
        for text in (None, "", "판단 불가", "{깨짐", '{"asked": []}', '{"answerable": "모름"}'):
            with self.subTest(text=text):
                self.assertTrue(parse_answerable(text))

    def test_string_boolean(self):
        self.assertFalse(parse_answerable('{"answerable": "false"}'))


class CacheTest(unittest.TestCase):
    OK = {"answerable": False, "error": None, "raw": '{"answerable": false}'}
    FAILED = {"answerable": True, "error": "HTTP 503", "raw": None}

    def test_call_error_is_not_cached(self):
        self.assertIsNone(cache_entry([self.OK, self.OK, self.FAILED], "h"))

    def test_broken_json_without_error_stays_fail_open(self):
        broken = {"answerable": True, "error": None, "raw": "깨짐"}
        self.assertTrue(cache_entry([broken, broken, self.OK], "h")["answerable"])

    def test_entry_keeps_hash_and_majority(self):
        entry = cache_entry([self.OK, self.OK, self.OK], "h")
        self.assertEqual((entry["answerable"], entry["inputHash"]), (False, "h"))

    def test_changed_faq_text_changes_hash(self):
        before = input_hash(user_prompt("유심 비용은?", "2,750원입니다.", "eSIM 비용은?"))
        after = input_hash(user_prompt("유심 비용은?", "2,750원(부가세 포함)입니다.", "eSIM 비용은?"))
        self.assertNotEqual(before, after)

    def test_rejudges_missing_changed_and_unhashed_pairs_only(self):
        cached = {"same": {"inputHash": "a"}, "changed": {"inputHash": "old"}, "unhashed": {"answerable": True}}
        hashes = {"same": "a", "changed": "b", "unhashed": "c", "new": "d"}
        self.assertEqual(pairs_to_judge(hashes.keys(), cached, hashes), ["changed", "new", "unhashed"])


class ScoreTest(unittest.TestCase):
    ROWS = [
        {"id": "EVAL-001", "qo": [], "qa": [("A", 0.75)]},  # 정답, 판정 true
        {"id": "EVAL-002", "qo": [], "qa": [("X", 0.75), ("B", 0.71)]},  # 공식 B, 다중 X
        {"id": "EVAL-081", "qo": [], "qa": [("Z", 0.76)]},  # 기존 무관
        {"id": "EVAL-161", "qo": [], "qa": [("Z", 0.90)]},  # 보강 무관, 위 선 위
    ]
    OFFICIAL = {"EVAL-001": {"A"}, "EVAL-002": {"B"}}
    MULTI = {"EVAL-001": {"A"}, "EVAL-002": {"B", "X"}}
    UNRELATED = {"EVAL-081", "EVAL-161"}
    VERDICTS = {"EVAL-001|A": True, "EVAL-002|X": False, "EVAL-002|B": True, "EVAL-081|Z": False}

    def score(self, config):
        return score_eval(self.ROWS, self.OFFICIAL, self.MULTI, self.UNRELATED, config, self.VERDICTS)

    def test_current(self):
        self.assertEqual(self.score(CURRENT), {"official": 1, "multi": 2, "unrelatedOld": 1,
                                               "unrelatedSupplement": 1, "judgedQuestions": 0})

    def test_upper_band(self):
        self.assertEqual(self.score(UPPER), {"official": 2, "multi": 2, "unrelatedOld": 0,
                                             "unrelatedSupplement": 1, "judgedQuestions": 3})

    def test_holdout_counts_questions_with_any_result(self):
        rows = [{"id": "H-1", "qo": [], "qa": [("A", 0.75)]}, {"id": "H-2", "qo": [], "qa": [("B", 0.60)]}]
        self.assertEqual(score_holdout(rows, CURRENT, {}), 1)
        self.assertEqual(score_holdout(rows, UPPER, {"H-1|A": False}), 0)


if __name__ == "__main__":
    unittest.main()
