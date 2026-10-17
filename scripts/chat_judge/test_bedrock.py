"""Offline contract tests for the V7 frozen fixture and three-model consensus."""

import copy
import json
import unittest

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge import evaluate_chat_pipeline as v6
from scripts.chat_judge import bedrock
from scripts.chat_judge.test_judge_chat_flow import sample_capture, CATALOG


class FakeClient:
    def __init__(self, result):
        self.result = result
        self.requests = []

    def converse(self, **request):
        self.requests.append(request)
        return {"stopReason": "end_turn", "output": {"message": {"content": [
            {"text": json.dumps(self.result)}]}}, "usage": {"inputTokens": 3, "outputTokens": 2}}


class V7Test(unittest.TestCase):
    def test_confirmed_condition_cannot_support_policy_claim(self):
        claim = {"claim": "기본요금은 무료입니다", "verdict": "SUPPORTED", "sourceIds": [],
                 "reason": "서울 지역 조건"}
        with self.assertRaisesRegex(ValueError, "FAQ 근거 ID"):
            judge.validate_result("grounding", {"claims": [claim], "overall": "SUPPORTED"}, set(),
                                  {"region": "서울"}, answer=claim["claim"])

    def test_v7_quality_prompt_preserves_question_parts_and_alternatives(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["qualityReferenceGroups"] = [{"questionPart": "몇 번?", "alternatives": [
            {"sourceId": "A", "answer": "월 1회"}, {"sourceId": "B", "answer": "한 달에 한 번"}]}]
        request = bedrock.v7_quality_request("model", turn, [])
        data = json.loads(request["messages"][1]["content"])
        self.assertEqual("몇 번?", data["referenceGroups"][0]["questionPart"])
        self.assertEqual(2, len(data["referenceGroups"][0]["alternatives"]))
        self.assertNotIn("question", data["referenceGroups"][0]["alternatives"][0])

    def test_bedrock_schema_removes_unsupported_constraints_without_losing_verdicts(self):
        result = bedrock.bedrock_schema(judge.GROUNDING_SCHEMA)
        claim = result["properties"]["claims"]["items"]
        self.assertNotIn("oneOf", claim)
        self.assertEqual(["SUPPORTED", "UNSUPPORTED", "IRRELEVANT", "REVIEW"],
                         claim["properties"]["verdict"]["enum"])

    def test_bedrock_request_uses_model_specific_strict_option(self):
        payload = {"messages": [{"role": "system", "content": "Judge"},
                                {"role": "user", "content": "data"}],
                   "response_format": {"json_schema": {"schema": {"type": "object", "properties": {
                       "ok": {"type": "boolean"}}, "required": ["ok"], "additionalProperties": False}}}}
        for alias in bedrock.MODELS:
            fake = FakeClient({"ok": True})
            result = bedrock.BedrockJudge(alias, client=fake).stage("test", payload)
            self.assertEqual({"ok": True}, result["result"])
            request = fake.requests[0]
            self.assertEqual(bedrock.MODELS[alias]["modelId"], request["modelId"])
            prompt_json = alias == "gpt-oss-120b" or bedrock.MODELS[alias].get("format") == "prompt_json"
            self.assertEqual(not prompt_json, "outputConfig" in request)
            self.assertEqual(alias in {"luna", "gpt-oss-120b", "sonnet-4-6"},
                             "additionalModelRequestFields" in request)

    def test_sonnet_46_explicitly_disables_thinking(self):
        payload = {"messages": [{"role": "system", "content": "Judge"},
                                {"role": "user", "content": "data"}],
                   "response_format": {"json_schema": {"schema": {"type": "object",
                       "properties": {"ok": {"type": "boolean"}}, "required": ["ok"],
                       "additionalProperties": False}}}}
        fake = FakeClient({"ok": True})
        bedrock.BedrockJudge("sonnet-4-6", client=fake).stage("test", payload)
        request = fake.requests[0]
        self.assertEqual("jp.anthropic.claude-sonnet-4-6", request["modelId"])
        self.assertEqual({"thinking": {"type": "disabled"}},
                         request["additionalModelRequestFields"])

    def test_gpt_oss_uses_prompt_json_and_bounded_low_reasoning(self):
        payload = {"messages": [{"role": "system", "content": "Judge"},
                                {"role": "user", "content": "data"}],
                   "response_format": {"json_schema": {"schema": {"type": "object",
                       "properties": {"ok": {"type": "boolean"}}, "required": ["ok"],
                       "additionalProperties": False}}}}
        fake = FakeClient({"ok": True})
        result = bedrock.BedrockJudge("gpt-oss-120b", client=fake).stage("test", payload)
        self.assertEqual({"ok": True}, result["result"])
        request = fake.requests[0]
        self.assertNotIn("outputConfig", request)
        self.assertEqual(2048, request["inferenceConfig"]["maxTokens"])
        self.assertEqual(0, request["inferenceConfig"]["temperature"])
        self.assertEqual("low", request["additionalModelRequestFields"]["reasoning_effort"])
        self.assertIn("JSON Schema", request["system"][0]["text"])

    def test_qwen_quality_uses_prompt_json_with_local_validation(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["qualityReferenceGroups"] = [{"questionPart": "변경 횟수",
            "alternatives": [{"sourceId": "BILLING-0001", "answer": "월 1회"}]}]
        fake = FakeClient({"groups": [{"groupIndex": 0, "outcome": "COMPLETE",
            "answerQuotes": ["월 1회 바꿀 수 있습니다."], "reason": "근거 있음"}]})
        result = bedrock.BedrockJudge("qwen", client=fake).quality(turn, [])
        self.assertEqual("COMPLETE", result["result"]["groups"][0]["outcome"])
        self.assertEqual("prompt_json_validated", result["formatMode"])
        self.assertNotIn("outputConfig", fake.requests[0])
        self.assertIn('"outcome": "<select verdict>"', fake.requests[0]["system"][0]["text"])

    def test_missing_empty_citations_on_insufficient_part_are_normalized(self):
        client = bedrock.BedrockJudge("qwen", client=FakeClient({}))
        responses = {
            "answer_refusal": {"answerIsRefusal": True, "reason": "거절"},
            "faq_answerability": {"parts": [{"partIndex": 0,
                "evidenceAnswerability": "INSUFFICIENT", "reason": "근거 없음"}], "reason": "근거 없음"},
        }
        client.stage = lambda name, payload: {"result": responses[name]}
        result = client.abstention({"question": "배송비가 무료인가요?", "answer": "확인할 수 없습니다.",
            "previousTurns": [], "sources": [], "confirmedConditions": []})
        self.assertEqual("INSUFFICIENT", result["result"]["evidenceAnswerability"])
        self.assertEqual([], result["stages"]["evidence"]["result"]["parts"][0].get("evidenceQuotes", []))



class V7RuleTest(unittest.TestCase):
    def test_bedrock_judge_uses_the_same_rules_without_calling_the_model(self):
        fake = FakeClient({"answerIsRefusal": True, "reason": "거절"})
        client = bedrock.BedrockJudge("qwen", client=fake)
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["expectedBehavior"] = "OUT_OF_SCOPE"
        self.assertEqual("OUT_OF_SCOPE_QUESTION", client.quality(turn, [])["rule"])
        self.assertEqual([], fake.requests)
        record = client.abstention({"question": "내 이번 달 요금 얼마야?", "answer": "확인할 수 없어요.", "sources": []})
        self.assertEqual(1, len(fake.requests))
        self.assertEqual("EMPTY_SOURCES", record["stages"]["evidence"]["rule"])
        self.assertEqual("INSUFFICIENT", record["result"]["evidenceAnswerability"])


class V7PromptJsonCandidateTest(unittest.TestCase):
    def test_prompt_json_candidates_ask_for_json_and_strip_code_fences(self):
        class Fenced(FakeClient):
            def converse(self, **request):
                self.requests.append(request)
                return {"stopReason": "end_turn", "output": {"message": {"content": [
                    {"text": "```json\n" + json.dumps(self.result) + "\n```"}]}},
                    "usage": {"inputTokens": 3, "outputTokens": 2}}
        for alias in ("mistral-large-3", "deepseek-v3.2", "deepseek-v3.2-usw2", "qwen3-next-80b"):
            fake = Fenced({"answerIsRefusal": False, "reason": "답함"})
            payload = {"messages": [{"role": "system", "content": "rubric"}, {"role": "user", "content": "{}"}],
                       "response_format": {"json_schema": {"schema": v6.REFUSAL_SCHEMA}}}
            record = bedrock.BedrockJudge(alias, client=fake).stage("answer_refusal", payload)
            self.assertEqual({"answerIsRefusal": False, "reason": "답함"}, record["result"])
            self.assertNotIn("outputConfig", fake.requests[0])
            self.assertIn("JSON Schema", fake.requests[0]["system"][0]["text"])
            self.assertEqual(bedrock.MODELS[alias]["modelId"], fake.requests[0]["modelId"])
            self.assertIn(alias, bedrock.PRICES)


if __name__ == "__main__":
    unittest.main()
