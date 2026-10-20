#!/usr/bin/env python3

"""FAQ 질문 답변 확인: FAQ 질문을 그대로 실행 중인 앱에 보내 최종적으로 근거 있는 답(GROUNDED)이 나오는지 본다

추천 버튼은 FAQ 질문 문장을 다시 보낸다. 그 문장으로 답이 안 나오는 FAQ는 버튼으로 쓰면 눌러도 "정보 없음"이 뜬다.
되묻기(CLARIFICATION)가 나오면 고객이 버튼을 누르듯 첫 번째 선택지로 같은 채팅방에서 답하고, 최종 답이 GROUNDED인지 본다.
선택지가 없는 되묻기는 자동으로 이어갈 수 없어 실패로 세고 따로 남긴다.
결과는 scripts/data/faq_unanswerable.json에 쓰고, build_suggested_question_data.py가 slotIds를 읽어
  - 대표 질문이 이 목록에 있으면 오류를 낸다
  - 답을 못 할 때 보여 줄 FAQ 후보에서 뺀다(faq-rules.json unanswerableSlotIds)
docs/FOLLOWUP_RECOMMENDATION.md 11.4절. 라우터·검색·답변 생성이 바뀌면 다시 돌린다.

실제 LLM(ollama)으로 띄운 로컬 앱이 필요하다(FAQ 1,150개에 1시간 반 이상). 답이 안 나오면 한 번 더 보내고,
LLM 결과가 매번 같지 않으므로 한 번이라도 안 나오면 목록에 넣는다. 질문마다 새 채팅방을 쓴다.
답변 대기 시간(--timeout)을 넘기면 답이 안 나온 것과 구분해 다시 재고, 다시 잰 결과를 쓴다.
두 번 다 넘기면 버튼으로 눌러도 답이 오지 않으므로 목록에 넣되 timedOutSlotIds에 따로 남긴다.

--recheck: 지난 결과의 실패·되묻기 후 답변 FAQ와, 바로 답이 나오던 FAQ 일부(표본)만 다시 재고 그 결과만 바꾼다.
표본에서 새로 실패가 나오면 되던 것이 깨진 것이라 재지 않은 나머지도 믿을 수 없다. 결과 파일을 쓰지 않고 멈추므로 전체를 다시 잰다.
표본은 실행마다 새로 고르고 쓴 시드를 출력·기록한다. 같은 표본을 다시 보려면 --seed로 준다.
"""

from __future__ import annotations

import argparse
import datetime
import json
import random
from pathlib import Path

from check_suggested_questions_live import Client, follow_clarifications, load_json

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "scripts/data/faq_full_1150.json"
OUT = ROOT / "scripts/data/faq_unanswerable.json"

ANSWERED = "ANSWERED"
CLARIFIED_ANSWERED = "CLARIFIED_ANSWERED"
CLARIFICATION_UNVERIFIED = "CLARIFICATION_UNVERIFIED"
FAILED = "FAILED"
TIMED_OUT = "TIMED_OUT"
FAILURES = {CLARIFICATION_UNVERIFIED, FAILED, TIMED_OUT}


def outcome(client: Client, question: str) -> str:
    """ANSWERED: 바로 근거 답 / CLARIFIED_ANSWERED: 되묻기에 첫 선택지로 답한 뒤 근거 답 /
    CLARIFICATION_UNVERIFIED: 선택지 없는 되묻기라 이어갈 수 없음 / TIMED_OUT: 대기 시간 안에 답을 못 받음 /
    FAILED: 그 밖(근거 없음, 되묻기가 끝나지 않음 등)"""
    reply, clarified, stuck = follow_clarifications(client, question)
    if stuck:
        return CLARIFICATION_UNVERIFIED
    if reply.get("status") == "WAIT_TIMEOUT":
        return TIMED_OUT
    if reply.get("answerBasis") == "GROUNDED":
        return CLARIFIED_ANSWERED if clarified else ANSWERED
    return FAILED


def measure(client: Client, items: list[dict]) -> dict:
    """slot_id별 결과. 실패하면 한 번 더 재고, 두 번 다 실패했는지도 남긴다.
    대기 초과는 답을 못 받은 것일 뿐이라 다시 잰 결과를 쓴다."""
    results = {}
    for i, faq in enumerate(items, 1):
        first = outcome(client, faq["question"])
        second = outcome(client, faq["question"]) if first in FAILURES else None
        results[faq["slot_id"]] = {"outcome": second if first == TIMED_OUT else first,
                                   "failedTwice": second in FAILURES}
        if i % 50 == 0:
            failed = sum(r["outcome"] in FAILURES for r in results.values())
            print(f"{i}/{len(items)} 답이 안 나옴 {failed}", flush=True)
    return results


def summarize(results: dict, total: int, method: str) -> dict:
    by = {kind: sorted(slot for slot, r in results.items() if r["outcome"] == kind)
          for kind in (ANSWERED, CLARIFIED_ANSWERED, CLARIFICATION_UNVERIFIED, FAILED, TIMED_OUT)}
    return {
        "measuredOn": datetime.date.today().isoformat(),
        "method": method,
        "total": total,
        "answered": len(by[ANSWERED]),
        "clarifiedAnswered": len(by[CLARIFIED_ANSWERED]),
        "clarifiedAnsweredSlotIds": by[CLARIFIED_ANSWERED],
        "failedTwice": sum(r["failedTwice"] for r in results.values()),
        # build_suggested_question_data.py가 읽는 목록: 버튼으로 쓰면 최종 답이 안 나오는 FAQ
        "slotIds": sorted(by[CLARIFICATION_UNVERIFIED] + by[FAILED] + by[TIMED_OUT]),
        "clarificationUnverified": len(by[CLARIFICATION_UNVERIFIED]),
        "clarificationUnverifiedSlotIds": by[CLARIFICATION_UNVERIFIED],
        # 두 번 다 대기 시간을 넘긴 FAQ. 답이 없는 것인지 앱이 느렸던 것인지 몰라 따로 남긴다
        "timedOut": len(by[TIMED_OUT]),
        "timedOutSlotIds": by[TIMED_OUT],
    }


def previous_results(previous: dict, all_slots: list[str]) -> dict:
    """지난 결과 파일을 slot_id별 결과로 되돌린다. 목록에 없는 FAQ는 바로 답이 나온 것이다."""
    unverified = set(previous.get("clarificationUnverifiedSlotIds", []))
    timed_out = set(previous.get("timedOutSlotIds", []))
    clarified = set(previous.get("clarifiedAnsweredSlotIds", []))
    failed = set(previous["slotIds"]) - unverified - timed_out
    results = {}
    for slot in all_slots:
        kind = (CLARIFICATION_UNVERIFIED if slot in unverified else TIMED_OUT if slot in timed_out
                else FAILED if slot in failed else CLARIFIED_ANSWERED if slot in clarified else ANSWERED)
        results[slot] = {"outcome": kind, "failedTwice": False}
    return results


def recheck_targets(previous: dict, all_slots: list[str], sample: int, seed: int) -> tuple[list[str], list[str]]:
    """다시 잴 slot_id(지난 실패 + 되묻기 후 답변 + 바로 답 표본)와 그중 표본."""
    watched = set(previous["slotIds"]) | set(previous.get("clarifiedAnsweredSlotIds", []))
    answered = sorted(slot for slot in all_slots if slot not in watched)
    picked = random.Random(seed).sample(answered, min(sample, len(answered)))
    return sorted(watched | set(picked)), sorted(picked)


def recheck(client: Client, previous: dict, all_items: list[dict], sample: int, seed: int
            ) -> tuple[dict, list[str], list[str]]:
    """지난 결과에 다시 잰 결과를 덮은 전체 결과, 표본, 그중 이번에 실패한 표본(되던 것이 깨진 것)."""
    all_slots = [faq["slot_id"] for faq in all_items]
    targets, sampled = recheck_targets(previous, all_slots, sample, seed)
    items = [faq for faq in all_items if faq["slot_id"] in set(targets)]
    print(f"다시 잴 FAQ {len(items)}개 (지난 실패·되묻기 후 답변 {len(items) - len(sampled)}, "
          f"바로 답 표본 {len(sampled)}, 시드 {seed})")
    rechecked = measure(client, items)
    broken = [f"{slot}({rechecked[slot]['outcome']})" for slot in sampled if rechecked[slot]["outcome"] in FAILURES]
    return previous_results(previous, all_slots) | rechecked, sampled, broken


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=float, default=180, help="질문 하나당 답변 대기 시간(초)")
    parser.add_argument("--limit", type=int,
                        help="앞에서부터 이 개수만 확인(동작 확인용, 결과 파일은 쓰지 않음). --recheck와 같이 쓸 수 없다")
    parser.add_argument("--recheck", action="store_true",
                        help="지난 결과의 실패·되묻기 후 답변 FAQ와 바로 답 표본만 다시 재서 그 결과만 바꾼다")
    parser.add_argument("--sample", type=int, default=100, help="--recheck에서 다시 잴 바로 답 FAQ 표본 수")
    parser.add_argument("--seed", type=int,
                        help="--recheck 표본을 고를 때 쓰는 값. 없으면 실행마다 새로 고르고 쓴 값을 출력·기록한다")
    args = parser.parse_args()
    # --recheck는 끝까지 재야 결과를 쓰므로, --limit으로 저장을 건너뛰면 오래 돈 재측정이 버려진다
    if args.recheck and args.limit:
        parser.error("--recheck와 --limit은 같이 쓸 수 없습니다(--limit이면 재측정 결과를 저장하지 않습니다)")
    seed = args.seed if args.seed is not None else random.randrange(1_000_000)

    client = Client(args.base_url, args.timeout)
    all_items = sorted(load_json(SRC), key=lambda x: x["slot_id"])
    all_slots = [faq["slot_id"] for faq in all_items]
    method = ("ACTIVE FAQ 질문을 그대로 채팅에 보내 최종 근거 있는 답(GROUNDED)이 나오는지 본다. 되묻기가 나오면 첫 번째 "
              "선택지로 같은 채팅방에서 답한다(최대 3번). 선택지 없는 되묻기는 이어갈 수 없어 실패로 센다. "
              "실패하면 한 번 더 보내고, 한 번이라도 실패하면 slotIds에 넣는다. 대기 시간을 넘기면 다시 잰 결과를 쓰고, "
              "두 번 다 넘기면 slotIds에 넣고 timedOutSlotIds에 따로 남긴다")

    if args.recheck:
        results, sampled, broken = recheck(client, load_json(OUT), all_items, args.sample, seed)
        if broken:
            raise SystemExit(f"바로 답이 나오던 표본 {len(broken)}개가 이번엔 실패했습니다(시드 {seed}): {broken}\n"
                             f"재지 않은 나머지도 믿을 수 없어 {OUT.relative_to(ROOT)}를 쓰지 않았습니다. "
                             "대기 초과(TIMED_OUT)뿐이면 앱 상태를 확인하고 같은 --seed로 다시, "
                             "아니면 전체를 다시 재세요(--recheck 없이 실행)")
        method += (f". {datetime.date.today().isoformat()} 부분 재측정(--recheck, 표본 {len(sampled)}개 "
                   f"시드 {seed} 모두 통과)")
    else:
        items = all_items[:args.limit] if args.limit else all_items
        results = measure(client, items)

    summary = summarize(results, len(all_slots) if args.recheck else len(items), method)
    print(f"{summary['total']}개 중 답이 안 나옴 {len(summary['slotIds'])}개 (두 번 다 {summary['failedTwice']}개, "
          f"그중 선택지 없는 되묻기 {summary['clarificationUnverified']}개, 대기 초과 {summary['timedOut']}개), "
          f"되묻기 후 답 {summary['clarifiedAnswered']}개, 바로 답 {summary['answered']}개")
    if args.limit:
        return
    OUT.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}를 썼습니다. build_suggested_question_data.py를 다시 실행하세요")


if __name__ == "__main__":
    main()
