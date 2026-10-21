#!/usr/bin/env python3
"""check_suggested_questions_live.py 대표 질문 확인 검증. 앱을 띄우지 않고 가짜 채팅 클라이언트로 되묻기 흐름을 흉내 낸다."""
import csv
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import check_suggested_questions_live as live
from check_suggested_questions_live import check_representative, follow_clarifications

QUESTION = "명의변경 시 필요한 서류를 정리해서 알려주세요."
OTHER = "명의변경 수수료가 있나요?"
SLOTS = {QUESTION: "NAME_CHANGE-0007", OTHER: "NAME_CHANGE-0003"}


def answer(top_question, basis="GROUNDED"):
    return {"messageType": "ANSWER", "answerBasis": basis, "followUps": [], "content": "답변",
            "sources": [{"title": top_question, "searchRank": 1}] if top_question else []}


def clarification(*options):
    return {"messageType": "CLARIFICATION", "answerBasis": None, "followUps": list(options), "content": "되묻기",
            "sources": []}


class FakeClient:
    """채팅방을 열 때마다 정해 둔 응답 묶음을 하나씩 꺼내 차례로 돌려준다. 보낸 내용과 채팅방을 기록한다."""

    def __init__(self, *sessions):
        self.sessions = [list(replies) for replies in sessions]
        self.sent = []
        self.opened = 0

    def open_session(self, title):
        self.opened += 1
        return self.opened

    def send(self, session, content):
        self.sent.append((session, content))
        return self.sessions[session - 1].pop(0)


class FollowClarificationsTest(unittest.TestCase):
    def test_answers_first_option_in_same_session(self):
        client = FakeClient([clarification("본인", "대리인"), answer(QUESTION)])

        reply, clarified, stuck = follow_clarifications(client, QUESTION)

        self.assertEqual((reply["answerBasis"], clarified, stuck), ("GROUNDED", True, False))
        self.assertEqual(client.sent, [(1, QUESTION), (1, "본인")])

    def test_clarification_without_options_stops(self):
        _, clarified, stuck = follow_clarifications(FakeClient([clarification()]), QUESTION)

        self.assertEqual((clarified, stuck), (False, True))


class CheckRepresentativeTest(unittest.TestCase):
    def check(self, client):
        return check_representative(client, QUESTION, SLOTS[QUESTION], SLOTS)

    def test_grounded_with_own_faq_first_passes_once(self):
        client = FakeClient([answer(QUESTION)])

        attempts = self.check(client)

        self.assertEqual([(a["passed"], a["clarified"]) for a in attempts], [(True, False)])
        self.assertEqual(client.opened, 1)

    def test_follows_clarification_to_final_answer(self):
        attempts = self.check(FakeClient([clarification("본인", "대리인"), answer(QUESTION)]))

        self.assertEqual([(a["passed"], a["clarified"]) for a in attempts], [(True, True)])

    def test_other_faq_first_fails_and_is_measured_again(self):
        client = FakeClient([answer(OTHER)], [answer(OTHER)])

        attempts = self.check(client)

        self.assertEqual([(a["passed"], a["slot"]) for a in attempts],
                         [(False, SLOTS[OTHER]), (False, SLOTS[OTHER])])
        self.assertEqual(client.opened, 2)

    def test_keeps_both_when_second_attempt_passes(self):
        attempts = self.check(FakeClient([answer(QUESTION, "NO_EVIDENCE")], [answer(QUESTION)]))

        self.assertEqual([a["passed"] for a in attempts], [False, True])

    def test_clarification_without_options_fails(self):
        attempts = self.check(FakeClient([clarification()], [clarification()]))

        self.assertEqual([a["passed"] for a in attempts], [False, False])


class RunRepresentativesTest(unittest.TestCase):
    """대표 질문 전체 실행: 같은 대표 질문을 쓰는 정책은 한 번만 재고, 최종 답 기준으로 검토표를 쓴다."""

    def test_measures_shared_question_once_and_writes_final_result(self):
        links = {"representativeQuestions": {
            "NAME_CHANGE-01": {"slotId": SLOTS[QUESTION], "question": QUESTION},
            "NAME_CHANGE-04": {"slotId": SLOTS[QUESTION], "question": QUESTION},
            "NAME_CHANGE-02": {"slotId": SLOTS[OTHER], "question": OTHER},
        }}
        # 정렬 순서(slot_id): NAME_CHANGE-0003(OTHER) → NAME_CHANGE-0007(QUESTION)
        client = FakeClient([answer(OTHER)], [clarification("본인"), answer(QUESTION)])
        with tempfile.TemporaryDirectory() as tmp:
            links_path, out = Path(tmp) / "links.json", Path(tmp) / "out.csv"
            links_path.write_text(json.dumps(links), encoding="utf-8")
            with mock.patch.object(live, "POLICY_LINKS", links_path), redirect_stdout(io.StringIO()) as printed:
                live.run_representatives(client, out, SLOTS)
            rows = list(csv.reader(out.open(encoding="utf-8-sig")))

        self.assertEqual(client.opened, 2)
        self.assertEqual([r[0] for r in rows[1:]], ["NAME_CHANGE-02", "NAME_CHANGE-01, NAME_CHANGE-04"])
        self.assertEqual([(r[3], r[6]) for r in rows[1:]], [("N", "Y"), ("Y", "Y")])
        self.assertIn("바로 답 1, 되묻기 후 답 1, 실패 0", printed.getvalue())


if __name__ == "__main__":
    unittest.main()
