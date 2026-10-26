"""T2 임베딩 회귀: 같은 질문을 Ollama bge-m3와 TEI bge-m3로 임베딩해 벡터와 검색 결과가 같은지 본다.

DB의 FAQ 벡터는 Ollama bge-m3로 만든 것이다. 질문 벡터만 TEI로 바꿨을 때 검색 상위 FAQ가 그대로인지 확인한다.
두 서버를 동시에 띄워 둔 상태에서 앱 EC2에서 돌린다(GPU 메모리는 Ollama EXAONE + TEI가 함께 들어간다).

사용: python3 embed_check.py --ollama http://<GPU>:11434 --tei http://<GPU>:8080 --questions 100
"""
import argparse
import json
import math
import subprocess
import urllib.request

from load_chat import DEFAULT_SET, pick_questions


def post(url, body):
    request = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(request, timeout=60))


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    return dot / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


def top_faqs(vector, column, k, container):
    literal = "[" + ",".join(f"{v:.7f}" for v in vector) + "]"
    query = (f"SELECT string_agg(faq_id::text, ',' ORDER BY d) FROM (SELECT faq_id, {column} <=> '{literal}' AS d "
             f"FROM faq_embeddings WHERE {column} IS NOT NULL ORDER BY d LIMIT {k}) t")
    out = subprocess.run(["sudo", "docker", "exec", "-i", container, "psql", "-U", "telme", "-d", "telme", "-At"],
                         input=query, capture_output=True, text=True).stdout.strip()
    return out.split(",") if out else []


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ollama", required=True)
    parser.add_argument("--tei", required=True)
    parser.add_argument("--questions", type=int, default=100)
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--db-container", default="telme-db")
    args = parser.parse_args()

    questions = [q for _, _, q in pick_questions(DEFAULT_SET, args.questions)]
    ollama = post(args.ollama + "/api/embed", {"model": "bge-m3", "input": questions})["embeddings"]
    # TEI는 한 번에 받는 문장 수가 정해져 있다(gpu-compose.yml의 --max-client-batch-size 64)
    tei = [vector for start in range(0, len(questions), 32)
           for vector in post(args.tei + "/embed", {"inputs": questions[start:start + 32], "normalize": True})]
    sims = [cosine(a, b) for a, b in zip(ollama, tei)]
    for column in ("embedding", "embedding_question"):
        top1 = topk = 0
        for a, b in zip(ollama, tei):
            ra, rb = top_faqs(a, column, args.k, args.db_container), top_faqs(b, column, args.k, args.db_container)
            top1 += ra[:1] == rb[:1]
            topk += ra == rb
        print(f"{column:<20} 1위 같음 {top1}/{len(questions)}  상위 {args.k}개 순서까지 같음 {topk}/{len(questions)}")
    print(f"코사인 유사도 최소 {min(sims):.6f}, 평균 {sum(sims) / len(sims):.6f} ({len(sims)}문장)")


if __name__ == "__main__":
    main()
