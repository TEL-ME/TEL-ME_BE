"""TELME-227 앱 캡처 비교(results/telme227/captures.tar.xz를 푼 디렉터리). 사용: python compare_plan_capture.py <develop 캡처 디렉터리들(,로)> <branch 캡처 디렉터리들(,로)>
요금제 표 답(planQuestionClassifications에 PLAN_CATALOG)이 걸린 턴의 답 차이, 목록 조건 호출 시간, Bedrock 토큰 비용을 낸다."""
import json
import statistics
import sys

PRICE_IN, PRICE_OUT = 0.18, 0.73  # 도쿄 gpt-oss-120b, 100만 토큰당 달러
CONDITION_HEAD = "당신은 통신사 챗봇에서 요금제 목록"
SUBSCRIBER_HEAD = "당신은 통신사 챗봇에서 요금제 추천 질문을 읽고"


def load(dirs):
    turns = {}
    calls = []
    for d in dirs.split(","):
        capture = json.load(open(d + "/capture.json", encoding="utf-8"))
        for case in capture["cases"]:
            for index, turn in enumerate(case["turns"]):
                key = (case["caseId"], index)
                trace = turn.get("pipelineTrace") or {}
                plan = trace.get("planQuestionClassifications") or []
                turns[key] = {
                    "question": turn["fixture"]["question"],
                    "answer": (turn.get("outputMessage") or {}).get("content") or "",
                    "plan": plan,
                    "subscribers": trace.get("planSubscribers") or [],
                    "durationMs": turn.get("durationMs"),
                }
                calls.extend(turn.get("bedrockCalls") or [])
    return turns, calls


def catalog(turn):
    return any(record.get("type") in ("PLAN_CATALOG", "RECOMMENDATION") for record in turn["plan"])


def cost(calls):
    tokens_in = sum(call.get("inputTokens") or 0 for call in calls)
    tokens_out = sum(call.get("outputTokens") or 0 for call in calls)
    return tokens_in, tokens_out, tokens_in / 1e6 * PRICE_IN + tokens_out / 1e6 * PRICE_OUT


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def main():
    develop, develop_calls = load(sys.argv[1])
    branch, branch_calls = load(sys.argv[2])
    for name, calls in (("develop", develop_calls), ("branch", branch_calls)):
        tokens_in, tokens_out, dollars = cost(calls)
        print("%s Bedrock 호출 %d건, 입력 %d, 출력 %d 토큰, $%.3f" % (name, len(calls), tokens_in, tokens_out, dollars))
    conditions = [call for call in branch_calls if (call.get("system") or "").startswith(CONDITION_HEAD)]
    if conditions:
        elapsed = [call.get("elapsedMs") or 0 for call in conditions]
        print("목록 조건 호출 %d건, elapsedMs p50 %d, p95 %d, 최대 %d" % (
            len(conditions), statistics.median(elapsed), pct(elapsed, 95), max(elapsed)))
        tokens_in, tokens_out, dollars = cost(conditions)
        print("  목록 조건 호출 비용 $%.4f (입력 %d, 출력 %d)" % (dollars, tokens_in, tokens_out))
    subscribers = [call for call in branch_calls if (call.get("system") or "").startswith(SUBSCRIBER_HEAD)]
    if subscribers:
        elapsed = [call.get("elapsedMs") or 0 for call in subscribers]
        print("추천 대상 호출 %d건, elapsedMs p50 %d, p95 %d, 최대 %d" % (
            len(subscribers), statistics.median(elapsed), pct(elapsed, 95), max(elapsed)))
    shared = sorted(set(develop) & set(branch))
    plan_turns = [key for key in shared if catalog(develop[key]) or catalog(branch[key])]
    changed = [key for key in plan_turns if develop[key]["answer"] != branch[key]["answer"]]
    print("공통 턴 %d, 요금제 표 턴 %d, 그중 답이 바뀐 턴 %d" % (len(shared), len(plan_turns), len(changed)))
    for key in changed:
        d, b = develop[key], branch[key]
        print("\n%s #%d  %s" % (key[0], key[1], d["question"]))
        print("  dev 분류:", json.dumps(d["plan"], ensure_ascii=False)[:300])
        print("  br  분류:", json.dumps(b["plan"], ensure_ascii=False)[:300])
        if b["subscribers"]:
            print("  br  대상:", json.dumps(b["subscribers"], ensure_ascii=False)[:300])
        print("  dev 답:", d["answer"][:200].replace("\n", " / "))
        print("  br  답:", b["answer"][:200].replace("\n", " / "))


if __name__ == "__main__":
    main()
