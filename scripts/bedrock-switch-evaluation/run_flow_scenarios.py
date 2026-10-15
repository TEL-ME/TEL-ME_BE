"""되묻기 완주·복합·FAQ+매장 복합·멀티턴 흐름을 실제 채팅 API로 끝까지 진행해 턴별 답변과 근거 FAQ를 남긴다.

단일 질문 평가(180문항)가 보지 않는 흐름을 확인한다. 되물으면 시나리오에 정한 조건 답변을 순서대로 보내고(최대 4턴),
멀티턴 시나리오는 첫 답변 뒤 후속 질문을 항상 보낸다. 매장 시나리오는 '현재 위치' 버튼처럼 좌표(강남역)를 함께 보낸다.
맞는지 여부는 채점하지 않는다. 결과의 답변과 근거 FAQ를 원문과 대조해 사람이 판정한다.

사용: python run_flow_scenarios.py --base-url http://localhost:8080 --database <앱 DB> --output flows.json
"""
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/single-faq-search-evaluation"))
from run_chat_eval import client  # noqa: E402  쿠키(게스트 세션)를 유지하는 API 호출기

GPS = {"latitude": 37.4979, "longitude": 127.0276}

# (id, 묶음, 첫 질문, 되묻기·후속 때 보낼 문장, 좌표 사용, 확인할 사실)
SCENARIOS = [
    ("CL-01", "clarify", "기기변경 하고 싶어요", ["개통한 지 1년 넘었고 남은 할부금은 없어요"], False, "6개월 경과 후 기기변경 가능 절차"),
    ("CL-02", "clarify", "번호이동 하고 싶어요", ["미납 요금은 없고 개통한 지 2년 됐어요"], False, "번호이동 방법"),
    ("CL-03", "clarify", "해지하면 위약금 얼마 나오나요", ["24개월 약정 중에 10개월 썼어요"], False, "약정 잔여 기간 위약금(할인반환금)"),
    ("CL-04", "clarify", "유심 재발급 받고 싶어요", ["유심을 잃어버렸고 제가 직접 매장에 갈 거예요"], False, "분실 재발급 비용·절차"),
    ("CL-05", "clarify", "로밍 요금제 얼마예요", ["일본에 5일 동안 가요"], False, "일 단위/기간권 로밍 요금"),
    ("CL-06", "clarify", "미성년자도 가입할 수 있나요", ["만 15세이고 부모님이랑 같이 갈 거예요"], False, "법정대리인 동의·서류"),
    ("CL-07", "clarify", "명의변경 하려면 뭐 필요해요?", ["양도인이랑 양수인 둘 다 직접 매장에 갈 거예요"], False, "양도인·양수인 신분증"),
    ("CL-08", "clarify", "요금 안 냈는데 정지되나요", ["납부일이 지난 지 20일 됐어요"], False, "미납 기간별 발신·수신 정지"),
    ("CO-01", "compound", "명의 변경에 필요한 서류와 번호 이동에 필요한 서류를 각각 알려줘", ["둘 다 본인이 직접 가요"], False, "두 업무 서류 각각"),
    ("CO-02", "compound", "명의 변경에 필요한 서류와 명의 변경 시 무료 해외 항공권을 주는지 각각 알려줘", ["직접 매장에 가요"], False, "서류 + 항공권은 정보 없음"),
    ("CO-03", "compound", "명의 변경과 번호 이동의 필요 서류를 비교해줘", [], False, "두 업무 서류 비교"),
    ("CO-04", "compound", "5G 라이트와 LTE 플러스의 월 요금과 데이터 제공량을 비교해줘", [], False, "45000/8GB vs 49000/15GB"),
    ("CO-05", "compound", "너겟 5G 요금제와 LTE 요금제의 데이터 제공량 차이를 비교해줘", [], False, "근거 없음이어야 함"),
    ("ST-01", "store", "유심 재발급 비용 알려주고 근처 매장도 찾아줘", ["유심 재발급이요"], True, "7,700원 + 근처 매장"),
    ("ST-02", "store", "번호이동 필요 서류랑 가까운 대리점 알려줘", ["번호이동이요"], True, "서류 + 매장"),
    ("ST-03", "store", "명의변경 서류 알려주고 근처에서 명의변경 되는 매장 찾아줘", ["둘 다 직접 가요"], True, "서류 + 명의변경 매장"),
    ("ST-04", "store", "근처에 유심 재발급 되는 매장 알려줘", [], True, "유심 재발급 가능 매장"),
    ("MT-01", "multiturn", "인터넷 해지를 하려고 합니다", ["장비는 어떻게 반납해요?"], False, "해지 장비 반납"),
    ("MT-02", "multiturn", "번호이동을 하려고 합니다", ["수수료는 얼마나 나와요?"], False, "번호이동 수수료"),
    ("MT-03", "multiturn", "로밍 요금제를 신청하려고 합니다", ["그럼 데이터 다 쓰면 어떻게 돼요?"], False, "로밍 데이터 소진 후 속도"),
    ("MT-04", "multiturn", "유심 재발급 비용은 얼마예요?", ["택배로도 받을 수 있나요?"], False, "유심 택배 수령"),
    ("MT-05", "multiturn", "명의변경을 하려고 해요", ["미성년자 자녀한테도 돼요?"], False, "미성년자 명의변경"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--database", required=True, help="근거 FAQ를 조회할 앱 DB 이름")
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    def sql(query):
        return subprocess.run(["docker", "exec", args.postgres_container, "psql", "-U", "telme", "-d", args.database,
                               "-Atc", query], capture_output=True, text=True, encoding="utf-8").stdout.strip()

    def send(call, session_id, text, gps):
        sent = call("POST", f"/api/v1/chat/sessions/{session_id}/messages", {"content": text, **(GPS if gps else {})})
        started = time.time()
        history = {}
        while time.time() - started < 180:
            history = call("GET", f"/api/v1/chat/sessions/{session_id}/messages?size=50")["result"]
            if history.get("runningExecutionId") is None:
                break
            time.sleep(0.5)
        # FAQ+매장 복합은 FAQ 답변과 매장 결과가 따로 올 수 있어 이 질문에 대한 답변을 모두 모은다
        user_id = (sent.get("result") or {}).get("messageId")
        messages = history.get("messages", [])
        replies = [m for m in messages if m["role"] == "ASSISTANT" and m.get("replyToMessageId") == user_id] \
            or ([m for m in messages if m["role"] == "ASSISTANT"] or [{}])[-1:]
        ids = [str(m["messageId"]) for m in replies if m.get("messageId")]
        sources = sql("SELECT coalesce(json_agg(json_build_object('slot',f.slot_id,'q',f.question)),'[]') "
                      f"FROM message_sources s JOIN faqs f ON f.faq_id=s.faq_id WHERE s.message_id IN ({','.join(ids)})") \
            if ids else "[]"
        return {"user": text, "types": [m.get("messageType") for m in replies],
                "basis": [m.get("answerBasis") for m in replies],
                "content": "\n---\n".join(m.get("content") or "" for m in replies),
                "stores": sum(len(m.get("storeResults") or []) for m in replies),
                "sources": json.loads(sources or "[]"), "seconds": round(time.time() - started, 1)}

    rows = []
    for scenario_id, group, first, replies, gps, fact in SCENARIOS:
        call = client(args.base_url)
        session_id = call("POST", "/api/v1/chat/sessions", {})["result"]["sessionId"]
        turns = [send(call, session_id, first, gps)]
        queue = list(replies)
        while queue and len(turns) < 4 and (group == "multiturn" or "CLARIFICATION" in turns[-1]["types"]):
            turns.append(send(call, session_id, queue.pop(0), gps))
        rows.append({"id": scenario_id, "group": group, "fact": fact, "sessionId": session_id, "turns": turns})
        args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
        last = turns[-1]
        print(f"{scenario_id} 턴{len(turns)} 마지막={last['types']}/{last['basis']} 매장{last['stores']} "
              f"근거{[s['slot'] for s in last['sources']]}", flush=True)


if __name__ == "__main__":
    main()
