import unittest

from scripts.chat_judge.judge_answers import parse_verdict


class ParseVerdictTest(unittest.TestCase):

    def test_parses_valid_json(self):
        self.assertEqual(
            ("unsupported", "근거 없음"),
            parse_verdict('{"verdict":"unsupported","reason":"근거 없음"}'),
        )

    def test_does_not_confuse_unsupported_with_supported(self):
        self.assertEqual(("unsupported", ""), parse_verdict("unsupported"))
        self.assertEqual(
            ("unsupported", ""),
            parse_verdict("판정: unsupported 입니다"),
        )

    def test_rejects_ambiguous_fallback(self):
        verdict, _ = parse_verdict("supported가 아니라 unsupported입니다")

        self.assertIsNone(verdict)


if __name__ == "__main__":
    unittest.main()
