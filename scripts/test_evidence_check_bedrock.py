import unittest

from evidence_check_bedrock import (
    B2_SYSTEM_PROMPT,
    B3_SYSTEM_PROMPT,
    build_context,
    parse_answerable,
    summarize,
    user_prompt,
)


def case(case_id, expected, votes, evidence=True):
    return {
        "caseId": case_id,
        "expectedAnswerable": expected,
        "evidence": [{"question": "q", "answer": "a"}] if evidence else [],
        "runs": [{"answerable": v, "elapsedMs": 100} for v in votes],
    }


class ParseAnswerableTest(unittest.TestCase):

    def test_parses_json_with_surrounding_text(self):
        self.assertEqual(
            (False, True),
            parse_answerable('판정:\n{"asked": ["비용"], "found": [false], "answerable": false}'),
        )

    def test_fails_open_like_checker(self):
        self.assertEqual((True, False), parse_answerable(None))
        self.assertEqual((True, False), parse_answerable("모르겠습니다"))
        self.assertEqual((True, False), parse_answerable('{"answerable": '))
        self.assertEqual((True, False), parse_answerable('{"asked": []}'))

    def test_accepts_string_boolean(self):
        self.assertEqual((False, True), parse_answerable('{"answerable": "false"}'))


class PromptFormatTest(unittest.TestCase):

    def test_matches_answer_context_converter(self):
        context = build_context([
            {"question": "Q1", "answer": "A1"},
            {"question": "Q2", "answer": "A2"},
        ])

        self.assertEqual("[1] Q: Q1\nA: A1\n\n[2] Q: Q2\nA: A2", context)
        self.assertEqual("[FAQ]\nctx\n\n[고객 질문]\n질문", user_prompt("ctx", "질문"))


class PromptVariantTest(unittest.TestCase):

    def test_b3_adds_two_lines_to_b2(self):
        # 치환 대상 문구가 바뀌면 B3가 B2와 같아진다
        self.assertEqual(len(B2_SYSTEM_PROMPT.splitlines()) + 2, len(B3_SYSTEM_PROMPT.splitlines()))


class SummarizeTest(unittest.TestCase):

    def test_counts_by_majority_and_skips_cases_without_evidence(self):
        summary = summarize([
            case("A", True, [False, False, True]),   # 잘못 막음
            case("B", False, [False, True, False]),  # 막음, 흔들림
            case("C", True, [True, True, True]),     # 맞힘
            case("D", False, [], evidence=False),    # 판정 안 함
        ])

        self.assertEqual(1, summary["wrongBlock"])
        self.assertEqual(1, summary["blocked"])
        self.assertEqual(2, summary["correct"])
        self.assertEqual(2, summary["flippedCases"])
        self.assertFalse(summary["passed"])


if __name__ == "__main__":
    unittest.main()
