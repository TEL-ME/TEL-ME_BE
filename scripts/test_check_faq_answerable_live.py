#!/usr/bin/env python3
"""check_faq_answerable_live.py 판정 규칙 검증. 앱을 띄우지 않고 가짜 채팅 클라이언트로 되묻기 흐름을 흉내 낸다."""
import unittest

from check_faq_answerable_live import (
    ANSWERED, CLARIFICATION_UNVERIFIED, CLARIFIED_ANSWERED, FAILED, measure, outcome, previous_results,
    recheck_targets, summarize)

GROUNDED = {"messageType": "ANSWER", "answerBasis": "GROUNDED", "followUps": []}
NO_EVIDENCE = {"messageType": "ANSWER", "answerBasis": "NO_EVIDENCE", "followUps": []}


def clarification(*options):
    return {"messageType": "CLARIFICATION", "answerBasis": None, "followUps": list(options)}


class FakeClient:
    """질문마다 정해 둔 응답을 차례로 돌려준다. 보낸 내용과 채팅방을 기록한다."""

    def __init__(self, replies):
        self.replies = {question: list(answers) for question, answers in replies.items()}
        self.sent = []
        self.sessions = 0
        self.current = None

    def open_session(self, title):
        self.sessions += 1
        self.current = title
        return self.sessions

    def send(self, session, content):
        self.sent.append((session, content))
        return self.replies[self.current].pop(0)


class OutcomeTest(unittest.TestCase):
    def test_grounded_right_away(self):
        self.assertEqual(outcome(FakeClient({"q": [GROUNDED]}), "q"), ANSWERED)

    def test_answers_clarification_with_first_option_in_same_session(self):
        client = FakeClient({"q": [clarification("예", "아니요"), GROUNDED]})

        self.assertEqual(outcome(client, "q"), CLARIFIED_ANSWERED)
        self.assertEqual(client.sent, [(1, "q"), (1, "예")])

    def test_follows_several_clarifications(self):
        client = FakeClient({"q": [clarification("개인", "법인"), clarification("예", "아니요"), GROUNDED]})

        self.assertEqual(outcome(client, "q"), CLARIFIED_ANSWERED)
        self.assertEqual([content for _, content in client.sent], ["q", "개인", "예"])

    def test_clarification_without_options_cannot_be_verified(self):
        self.assertEqual(outcome(FakeClient({"q": [clarification()]}), "q"), CLARIFICATION_UNVERIFIED)

    def test_clarification_then_no_evidence_fails(self):
        self.assertEqual(outcome(FakeClient({"q": [clarification("예"), NO_EVIDENCE]}), "q"), FAILED)

    def test_endless_clarification_fails(self):
        client = FakeClient({"q": [clarification("예")] * 4})

        self.assertEqual(outcome(client, "q"), FAILED)
        self.assertEqual(len(client.sent), 4)


class MeasureTest(unittest.TestCase):
    def test_retries_failures_and_records_failed_twice(self):
        client = FakeClient({
            "a": [GROUNDED],
            "b": [NO_EVIDENCE, GROUNDED],
            "c": [NO_EVIDENCE, clarification()],
        })
        items = [{"slot_id": slot, "question": slot} for slot in ("a", "b", "c")]

        results = measure(client, items)

        self.assertEqual(results["a"], {"outcome": ANSWERED, "failedTwice": False})
        self.assertEqual(results["b"], {"outcome": FAILED, "failedTwice": False})
        self.assertEqual(results["c"], {"outcome": FAILED, "failedTwice": True})

    def test_summary_puts_unverified_and_failed_into_slot_ids(self):
        results = {
            "A": {"outcome": ANSWERED, "failedTwice": False},
            "B": {"outcome": CLARIFIED_ANSWERED, "failedTwice": False},
            "C": {"outcome": CLARIFICATION_UNVERIFIED, "failedTwice": True},
            "D": {"outcome": FAILED, "failedTwice": False},
        }

        summary = summarize(results, 4, "test")

        self.assertEqual(summary["slotIds"], ["C", "D"])
        self.assertEqual(summary["clarifiedAnsweredSlotIds"], ["B"])
        self.assertEqual(summary["clarificationUnverifiedSlotIds"], ["C"])
        self.assertEqual((summary["answered"], summary["failedTwice"]), (1, 1))


class RecheckTest(unittest.TestCase):
    PREVIOUS = {"slotIds": ["C", "D"], "clarifiedAnsweredSlotIds": ["B"], "clarificationUnverifiedSlotIds": ["C"]}
    SLOTS = ["A", "B", "C", "D", "E", "F"]

    def test_previous_file_round_trips(self):
        results = previous_results(self.PREVIOUS, self.SLOTS)

        self.assertEqual({slot: r["outcome"] for slot, r in results.items()}, {
            "A": ANSWERED, "B": CLARIFIED_ANSWERED, "C": CLARIFICATION_UNVERIFIED, "D": FAILED,
            "E": ANSWERED, "F": ANSWERED})
        self.assertEqual(summarize(results, 6, "test")["slotIds"], self.PREVIOUS["slotIds"])

    def test_targets_are_watched_slots_plus_a_fixed_sample(self):
        targets, sampled = recheck_targets(self.PREVIOUS, self.SLOTS, sample=2, seed=0)

        self.assertTrue({"B", "C", "D"} <= set(targets))
        self.assertEqual(len(sampled), 2)
        self.assertTrue(set(sampled) <= {"A", "E", "F"})
        self.assertEqual(recheck_targets(self.PREVIOUS, self.SLOTS, sample=2, seed=0), (targets, sampled))


if __name__ == "__main__":
    unittest.main()
