#!/usr/bin/env python3
"""check_faq_answerable_live.py 판정 규칙 검증. 앱을 띄우지 않고 가짜 채팅 클라이언트로 되묻기 흐름을 흉내 낸다."""
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

import check_faq_answerable_live as live
from check_faq_answerable_live import (
    ANSWERED, CLARIFICATION_UNVERIFIED, CLARIFIED_ANSWERED, FAILED, TIMED_OUT, measure, outcome, previous_results,
    recheck_targets, summarize)

GROUNDED = {"messageType": "ANSWER", "answerBasis": "GROUNDED", "followUps": []}
NO_EVIDENCE = {"messageType": "ANSWER", "answerBasis": "NO_EVIDENCE", "followUps": []}
# check_suggested_questions_live.Client.send가 대기 시간 안에 답을 못 받으면 돌려주는 값
WAIT_TIMEOUT = {"status": "WAIT_TIMEOUT", "messageType": "", "content": "", "answerBasis": None,
                "followUps": [], "sources": []}


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

    def test_wait_timeout_is_not_failed(self):
        self.assertEqual(outcome(FakeClient({"q": [WAIT_TIMEOUT]}), "q"), TIMED_OUT)
        self.assertEqual(outcome(FakeClient({"q": [clarification("예"), WAIT_TIMEOUT]}), "q"), TIMED_OUT)


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

    def test_wait_timeout_uses_the_retry(self):
        client = FakeClient({
            "slow": [WAIT_TIMEOUT, GROUNDED],
            "slow-then-fail": [WAIT_TIMEOUT, NO_EVIDENCE],
            "stuck": [WAIT_TIMEOUT, WAIT_TIMEOUT],
        })
        items = [{"slot_id": slot, "question": slot} for slot in ("slow", "slow-then-fail", "stuck")]

        results = measure(client, items)

        self.assertEqual(results["slow"], {"outcome": ANSWERED, "failedTwice": False})
        self.assertEqual(results["slow-then-fail"], {"outcome": FAILED, "failedTwice": True})
        self.assertEqual(results["stuck"], {"outcome": TIMED_OUT, "failedTwice": True})

    def test_summary_puts_unverified_failed_and_timed_out_into_slot_ids(self):
        results = {
            "A": {"outcome": ANSWERED, "failedTwice": False},
            "B": {"outcome": CLARIFIED_ANSWERED, "failedTwice": False},
            "C": {"outcome": CLARIFICATION_UNVERIFIED, "failedTwice": True},
            "D": {"outcome": FAILED, "failedTwice": False},
            "E": {"outcome": TIMED_OUT, "failedTwice": True},
        }

        summary = summarize(results, 5, "test")

        self.assertEqual(summary["slotIds"], ["C", "D", "E"])
        self.assertEqual(summary["clarifiedAnsweredSlotIds"], ["B"])
        self.assertEqual(summary["clarificationUnverifiedSlotIds"], ["C"])
        self.assertEqual(summary["timedOutSlotIds"], ["E"])
        self.assertEqual((summary["answered"], summary["failedTwice"]), (1, 2))


class RecheckTest(unittest.TestCase):
    PREVIOUS = {"slotIds": ["C", "D", "G"], "clarifiedAnsweredSlotIds": ["B"], "clarificationUnverifiedSlotIds": ["C"],
                "timedOutSlotIds": ["G"]}
    SLOTS = ["A", "B", "C", "D", "E", "F", "G"]

    def test_previous_file_round_trips(self):
        results = previous_results(self.PREVIOUS, self.SLOTS)

        self.assertEqual({slot: r["outcome"] for slot, r in results.items()}, {
            "A": ANSWERED, "B": CLARIFIED_ANSWERED, "C": CLARIFICATION_UNVERIFIED, "D": FAILED,
            "E": ANSWERED, "F": ANSWERED, "G": TIMED_OUT})
        summary = summarize(results, 7, "test")
        self.assertEqual((summary["slotIds"], summary["timedOutSlotIds"]), (self.PREVIOUS["slotIds"], ["G"]))

    def test_file_without_timed_out_list_reads_as_failed(self):
        previous = {"slotIds": ["D"], "clarifiedAnsweredSlotIds": [], "clarificationUnverifiedSlotIds": []}

        self.assertEqual(previous_results(previous, ["D"])["D"]["outcome"], FAILED)

    def test_targets_are_watched_slots_plus_a_fixed_sample(self):
        targets, sampled = recheck_targets(self.PREVIOUS, self.SLOTS, sample=2, seed=0)

        self.assertTrue({"B", "C", "D", "G"} <= set(targets))
        self.assertEqual(len(sampled), 2)
        self.assertTrue(set(sampled) <= {"A", "E", "F"})
        self.assertEqual(recheck_targets(self.PREVIOUS, self.SLOTS, sample=2, seed=0), (targets, sampled))


class SameReplyClient(FakeClient):
    """질문마다 늘 같은 응답을 돌려준다. 다시 재는 횟수와 상관없이 쓴다."""

    def send(self, session, content):
        self.sent.append((session, content))
        return self.replies[self.current][0]


class RecheckRunTest(unittest.TestCase):
    """--recheck 실행 전체: 표본이 깨지면 결과 파일을 쓰지 않는다."""
    PREVIOUS = {"slotIds": ["C"], "clarifiedAnsweredSlotIds": [], "clarificationUnverifiedSlotIds": []}

    def run_recheck(self, replies, *extra):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, out = root / "faq.json", root / "out.json"
            src.write_text(json.dumps([{"slot_id": s, "question": s} for s in ("A", "B", "C")]), encoding="utf-8")
            out.write_text(json.dumps(self.PREVIOUS), encoding="utf-8")
            client = SameReplyClient(replies)
            argv = ["check_faq_answerable_live.py", "--recheck", "--sample", "2", *extra]
            with mock.patch.object(live, "ROOT", root), mock.patch.object(live, "SRC", src), \
                    mock.patch.object(live, "OUT", out), mock.patch.object(live, "Client", return_value=client), \
                    mock.patch.object(sys, "argv", argv), redirect_stdout(io.StringIO()), \
                    redirect_stderr(io.StringIO()):
                try:
                    live.main()
                    exited = None
                except SystemExit as exc:
                    exited = exc
            self.sent = client.sent
            return exited, json.loads(out.read_text(encoding="utf-8"))

    def test_broken_sample_stops_without_writing(self):
        exited, written = self.run_recheck({"A": [NO_EVIDENCE], "B": [GROUNDED], "C": [GROUNDED]})

        self.assertIsNotNone(exited)
        self.assertNotEqual(exited.code, 0)
        self.assertEqual(written, self.PREVIOUS)

    def test_healthy_sample_writes_merged_result(self):
        exited, written = self.run_recheck({"A": [GROUNDED], "B": [GROUNDED], "C": [GROUNDED]})

        self.assertIsNone(exited)
        self.assertEqual(written["slotIds"], [])
        self.assertEqual((written["total"], written["answered"]), (3, 3))

    def test_records_the_seed_it_used(self):
        _, given = self.run_recheck({"A": [GROUNDED], "B": [GROUNDED], "C": [GROUNDED]}, "--seed", "7")
        _, drawn = self.run_recheck({"A": [GROUNDED], "B": [GROUNDED], "C": [GROUNDED]})

        self.assertIn("시드 7 ", given["method"])
        self.assertRegex(drawn["method"], r"시드 \d+ 모두 통과")

    def test_limit_with_recheck_stops_before_measuring(self):
        exited, written = self.run_recheck({"A": [GROUNDED], "B": [GROUNDED], "C": [GROUNDED]}, "--limit", "1")

        self.assertEqual(exited.code, 2)
        self.assertEqual(self.sent, [])
        self.assertEqual(written, self.PREVIOUS)


if __name__ == "__main__":
    unittest.main()
