import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from generated_questions_eval import generate_all, parse_questions, pick


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


if __name__ == "__main__":
    unittest.main()
