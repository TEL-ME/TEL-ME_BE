import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from generated_questions_eval import generate_all, needs_generation, parse_questions, pick
from gray_zone_threshold_bedrock import content_hash


class ParseQuestionsTest(unittest.TestCase):

    def test_dedupes_strips_and_caps(self):
        text = '앞말 {"questions": ["a", "a", " b ", "", "c", "d", "e"]} 뒷말'

        self.assertEqual(["a", "b", "c", "d"], parse_questions(text))

    def test_returns_empty_on_broken_output(self):
        self.assertEqual([], parse_questions(None))
        self.assertEqual([], parse_questions('{"questions": "a"}'))
        self.assertEqual([], parse_questions('{"questions": ['))


class PickTest(unittest.TestCase):

    def row(self, line, answered, unrelated):
        return {"qoLine": line, "answered": {"공식": set(range(answered))},
                "unrelatedPassed": {"130": set(range(unrelated[0])), "supp": set(range(unrelated[1]))}}

    def test_strict_and_relaxed_rules(self):
        base = self.row(0.88, 59, (3, 5))
        rows = [self.row(0.90, 60, (3, 5)), self.row(0.86, 66, (3, 6)), self.row(0.84, 70, (4, 6))]

        self.assertEqual(0.90, pick(base, rows, ["130", "supp"], 0)["qoLine"])
        self.assertEqual(0.86, pick(base, rows, ["130", "supp"], 1)["qoLine"])


class GenerateAllTest(unittest.TestCase):

    def test_does_not_cache_errors_or_empty_results(self):
        faqs = [{"slot_id": s, "question": "q", "answer": "a"} for s in ("OK", "ERROR", "EMPTY")]
        replies = [('{"questions": ["x"]}', None, {}), (None, "HTTP 503", {}), ('{"questions": []}', None, {})]

        cache = {}
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.dict(os.environ, {"BEDROCK_API_KEY": "test"}), \
                mock.patch("generated_questions_eval.call_model", side_effect=replies), \
                self.assertRaises(SystemExit):
            generate_all(faqs, cache, Path(tmp) / "cache.json", "openai.gpt-oss-120b-1:0", 1450)

        self.assertEqual(["OK"], list(cache))


class NeedsGenerationTest(unittest.TestCase):

    faq = {"slot_id": "A", "question": "q", "answer": "a"}

    def entry(self, **fields):
        return {"hash": content_hash("q", "a"), "questions": ["x"], "error": None, **fields}

    def test_regenerates_stored_errors_and_empty_results(self):
        self.assertFalse(needs_generation(self.entry(), self.faq))
        self.assertTrue(needs_generation(None, self.faq))
        self.assertTrue(needs_generation(self.entry(hash="old"), self.faq))
        self.assertTrue(needs_generation(self.entry(error="HTTP 503"), self.faq))
        self.assertTrue(needs_generation(self.entry(questions=[]), self.faq))


if __name__ == "__main__":
    unittest.main()
