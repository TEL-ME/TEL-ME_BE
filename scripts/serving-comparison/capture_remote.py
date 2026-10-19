"""T4 정확도: 서비스 품질셋 답변을 원격 GPU의 엑사원(Ollama 또는 vLLM)으로 캡처한다.

S1(Bedrock 즉시 장애)과 같은 조건으로, Bedrock을 끄고 모든 LLM 호출을 엑사원이 받게 한다. 회색 구간 판정과 후보 판정기는
Bedrock 전용이라 장애 때처럼 꺼진 상태(예전 기준)가 된다. 캡처는 이 PC의 평가 DB(telme-flow-postgres 5440,
telme_judge_eval)에서 돌고, 엑사원·임베딩만 GPU EC2를 부른다. 판정은 docs/chat-judge/SERVICE_EVALUATION.md 2~3절대로 한다.

사용(저장소 루트에서): python -X utf8 scripts/serving-comparison/capture_remote.py --provider vllm --gpu <GPU 공인 IP> --out .measure/t4-vllm
"""
import argparse
import json
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.chat_judge.evaluate_chat_pipeline import checkpoint, load_json  # noqa: E402
from scripts.chat_judge.run_chat_pipeline_eval import DEFAULT_FIXTURE, select_cases  # noqa: E402

SWITCH_FLAGS = {
    "LLM_BEDROCK_ENABLED": "false",
    "SEARCH_GRAY_ZONE_ENABLED": "false",
    "ROUTING_BEDROCK": "true",
    "ROUTING_REQUEST_INVENTORY_ENABLED": "false",
    "CHAT_QUESTION_RESOLUTION_BEDROCK": "true",
    "CONSULT_PLAN_QUESTION_BEDROCK": "true",
    "CONSULT_FAQ_CLARIFICATION_BEDROCK": "true",
    "RAG_ANSWER_BEDROCK": "true",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--provider", choices=["ollama", "vllm"], required=True)
    parser.add_argument("--gpu", required=True, help="GPU EC2 주소")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    parser.add_argument("--postgres-port", default="5440", help="두 서빙을 동시에 캡처하면 DB 컨테이너를 따로 둔다")
    args = parser.parse_args()

    count = subprocess.run(["docker", "exec", args.postgres_container, "psql", "-U", "telme", "-d", "telme_judge_eval",
                            "-Atc", "select count(embedding_question) from faq_embeddings"],
                           capture_output=True, text=True, check=True).stdout.strip()
    if count != "1152":
        raise SystemExit(f"평가 DB 질문 벡터가 1152가 아니다: {count}")

    out = args.out.resolve()
    out.mkdir(parents=True)
    fixtures = select_cases(load_json(DEFAULT_FIXTURE), 0)
    if not (os.environ.get("KAKAO_REST_API_KEY") or os.environ.get("KAKAO_CLIENT_ID")):
        fixtures = [case for case in fixtures if not case.get("requiresKakao")]
    checkpoint(out / "questions.json", fixtures)
    serving = ({"OLLAMA_URL": f"http://{args.gpu}:11434"} if args.provider == "ollama" else
               {"VLLM_URL": f"http://{args.gpu}:8000", "OLLAMA_URL": f"http://{args.gpu}:11434",
                "EMBEDDING_PROVIDER": "tei", "EMBEDDING_URL": f"http://{args.gpu}:8080"})
    checkpoint(out / "manifest.json", {"createdAt": datetime.now().isoformat(), "provider": args.provider,
                                       "settings": {**SWITCH_FLAGS, **serving}, "plannedConversations": len(fixtures),
                                       "plannedTurns": sum(len(case["turns"]) for case in fixtures)})
    env = dict(os.environ)
    env.update(SWITCH_FLAGS)
    env.update(serving)
    env.update({"TELME_CHAT_JUDGE_PROBE": "true", "TELME_CHAT_JUDGE_FIXTURE": str(out / "questions.json"),
                "TELME_CHAT_JUDGE_OUT": str(out / "capture.json"), "TELME_CHAT_JUDGE_LLM_PROVIDER": args.provider,
                "POSTGRES_PORT": args.postgres_port, "POSTGRES_DB": "telme_judge_eval", "PYTHONIOENCODING": "utf-8"})
    gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    with (out / "capture-gradle.log").open("wb") as log:
        subprocess.run([gradle, "test", "--tests", "com.telme.probe.ChatJudgeCaptureProbe", "--rerun-tasks"],
                       cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    if not load_json(out / "capture.json").get("captureComplete"):
        raise SystemExit("캡처가 끝나지 않았다. 부분 결과는 남겨 둔다")
    print(f"캡처 완료: {out / 'capture.json'}")


if __name__ == "__main__":
    main()
