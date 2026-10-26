"""TELME-121 비교 입력을 생성한다. 실제 모델 호출은 Spring 평가 실행기가 담당한다."""
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEST = pathlib.Path(__file__).resolve().parent


def main():
    baseline = subprocess.check_output(
        ["git", "show", "3516b24:src/main/java/com/telme/chat/service/ChatSummaryPrompt.java"],
        cwd=ROOT, encoding="utf-8")
    system = re.search(r'SYSTEM_PROMPT = """\n(.*?)\n\s*""";', baseline, re.S).group(1)
    system = "\n".join(line[12:] if line.startswith(" " * 12) else line for line in system.splitlines())
    (DEST / "baseline-summary-prompt.json").write_text(
        json.dumps({"commit": "3516b24", "systemPrompt": system}, ensure_ascii=False, indent=2), encoding="utf-8")
    regions = ["강남역", "잠실역", "홍대입구역", "왕십리역", "선릉역", "건대입구역", "신촌역", "노원역", "수원역", "부평역"]
    cases = []
    for variant, region in enumerate(regions):
        next_region = regions[(variant + 3) % len(regions)]
        samples = [
            ("CORRECTION", f"{region} 인근 매장에서 유심 재발급을 알아보고 있어요.",
             "방문 지역을 확인했습니다.", f"지역은 {region}이 아니라 {next_region}으로 정정할게요.",
             "정정한 지역을 확인했습니다.", [5], [next_region, "유심"], [1]),
            ("NEGATION_STATUS", "부모님 명의 휴대폰의 명의변경을 알아보고 있어요.",
             "명의변경 상담입니다.", "명의자는 부모님이 아니라 저예요. 명의변경은 아직 하지 않았어요.",
             "명의자와 미완료 상태를 확인했습니다.", [5], ["저", "아직", "명의변경"], [1]),
            ("SUBJECT_SCOPE", "제 휴대폰은 5G 요금제이고 부모님 휴대폰은 LTE 요금제예요.",
             "각각 다른 명의와 요금제입니다.", "제 휴대폰은 LTE로 변경하려고 해요. 부모님 휴대폰은 그대로 둘 거예요.",
             "변경 예정인 대상은 고객님 휴대폰입니다.", [1, 5], ["부모님", "LTE", "변경하려고"], []),
            ("OPEN_QUESTIONS", "유심 재발급 비용과 해외 로밍 신청 방법을 각각 알려주세요.",
             "유심 재발급 비용은 7,700원입니다. 로밍 신청 방법은 아직 확인하지 못했습니다.",
             "로밍 신청 방법은 아직 답을 못 받았어요. 그 부분을 계속 알아보고 싶어요.",
             "로밍 신청 방법이 미해결 상태입니다.", [1, 5], ["로밍", "아직", "신청"], []),
            ("POLICY_CONTAMINATION", "유심 재발급 방법이 궁금해요.",
             "유심 재발급 비용은 99,999원이고 100일 걸립니다.",
             "그 비용은 제가 말한 사실이 아니고 상담사가 안내한 내용이에요. 실제 정책을 확인해주세요.",
             "이전 안내는 검증된 정책이 아닙니다.", [1, 5], ["유심", "상담사", "확인"], []),
            ("TOPIC_CHANGE", f"{region} 인근 매장 방문을 알아봤어요.",
             "매장 방문 상담입니다.", "매장 상담은 끝났어요. 이제 해외 로밍 데이터 차단 방법이 궁금해요.",
             "새 주제는 해외 로밍 데이터 차단입니다.", [5], ["로밍", "차단", "끝났"], [1]),
        ]
        for category, user, assistant, corrected, followup, required, anchors, obsolete in samples:
            messages = [
                {"messageId": 1, "sequenceNo": 1, "role": "USER", "messageType": "QUESTION", "content": user, "storeResults": None},
                {"messageId": 2, "sequenceNo": 2, "role": "ASSISTANT", "messageType": "ANSWER", "content": assistant, "storeResults": None},
                {"messageId": 3, "sequenceNo": 3, "role": "USER", "messageType": "QUESTION", "content": "고객센터에 전화한 적은 없어요.", "storeResults": None},
                {"messageId": 4, "sequenceNo": 4, "role": "ASSISTANT", "messageType": "ANSWER", "content": "말씀하신 내용을 확인했습니다.", "storeResults": None},
                {"messageId": 5, "sequenceNo": 5, "role": "USER", "messageType": "QUESTION", "content": corrected, "storeResults": None},
                {"messageId": 6, "sequenceNo": 6, "role": "ASSISTANT", "messageType": "ANSWER", "content": followup, "storeResults": None},
            ]
            cases.append({"id": f"{category}-{variant + 1:02}", "category": category,
                          "split": "development" if variant < 5 else "validation",
                          "messages": messages, "requiredMessageIds": required,
                          "requiredAnchors": anchors, "obsoleteMessageIds": obsolete})
    (DEST / "summary-cases.json").write_text(json.dumps({
        "description": "6종 상담 유형의 조건 변형 60건. 독립적인 실제 사용자 대화 60건으로 해석하지 않는다.",
        "cases": cases}, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
