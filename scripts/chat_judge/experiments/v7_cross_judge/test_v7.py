"""Offline contract tests for the V7 frozen fixture and three-model consensus."""

import copy
import json
import unittest

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v7_cross_judge import bedrock
from scripts.chat_judge.experiments.v7_cross_judge import build_v7_capture as builder
from scripts.chat_judge.experiments.v7_cross_judge import run_cross_judge as runner
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

    def test_fixture_maps_two_questions_to_all_approved_alternatives(self):
        capture = sample_capture()
        turn = capture["cases"][0]["turns"][0]
        turn["fixture"]["goldSourceGroups"] = [["BILLING-0001", "BILLING-0002"], ["BILLING-0003"]]
        turn["fixture"]["qualityReferenceGroups"] = [
            {"question": "첫 질문?", "sourceId": "BILLING-0001", "answer": "기본 답변"},
            {"question": "둘째 질문?", "sourceId": "BILLING-0003", "answer": "별도 답변"}]
        catalog = CATALOG + [
            {"slot_id": "BILLING-0002", "question": "대체", "answer": "같은 뜻의 대체 답변"},
            {"slot_id": "BILLING-0003", "question": "둘째", "answer": "별도 답변"}]
        built = builder.build_capture(capture, catalog)
        groups = built["cases"][0]["turns"][0]["fixture"]["qualityReferenceGroups"]
        self.assertEqual(["첫 질문?", "둘째 질문?"], [group["questionPart"] for group in groups])
        self.assertEqual(["BILLING-0001", "BILLING-0002"],
                         [row["sourceId"] for row in groups[0]["alternatives"]])
        self.assertEqual(["BILLING-0003"], [row["sourceId"] for row in groups[1]["alternatives"]])
        self.assertEqual("기본 답변", capture["cases"][0]["turns"][0]["fixture"]["qualityReferenceGroups"][0]["answer"])

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
            self.assertEqual(alias != "gpt-oss-120b", "outputConfig" in request)
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

    def test_consensus_requires_all_three_models_and_same_axis_decisions(self):
        item = {"grounding": {"result": {"overall": "SUPPORTED", "claims": []}},
                "quality": {"result": {"groups": [{"outcome": "COMPLETE"}]}},
                "abstention": {"result": {"evidenceAnswerability": "ENOUGH"},
                               "questionParts": [{"evidenceAnswerability": "ENOUGH"}]},
                "abstentionDecision": {"label": "NOT_APPLICABLE"}, "requiresReview": False}
        models = {name: copy.deepcopy(item) for name in bedrock.MODELS}
        self.assertEqual("AGREED", runner.consensus(models)["status"])
        models["luna"]["quality"]["result"]["groups"][0]["outcome"] = "PARTIAL"
        self.assertEqual("HUMAN_REVIEW", runner.consensus(models)["status"])
        self.assertEqual("disagreement", runner.consensus(models)["axes"]["quality"]["reason"])
        models.pop("sonnet")
        self.assertEqual("missing_model", runner.consensus(models)["reason"])

    def test_missing_abstention_decision_requires_human_review(self):
        item = {"grounding": {"result": {"overall": "SUPPORTED", "claims": []}},
                "quality": {"result": {"groups": [{"outcome": "COMPLETE"}]}},
                "abstention": {"result": {"evidenceAnswerability": "ENOUGH"},
                               "questionParts": [{"evidenceAnswerability": "ENOUGH"}]},
                "requiresReview": False}
        models = {name: copy.deepcopy(item) for name in bedrock.MODELS}
        result = runner.consensus(models)
        self.assertEqual("HUMAN_REVIEW", result["status"])
        self.assertEqual("unscored", result["axes"]["abstention"]["reason"])

    def test_unexpected_model_cannot_satisfy_three_model_consensus(self):
        self.assertEqual("missing_model", runner.consensus({name: {} for name in
            ("qwen", "sonnet", "unexpected")})["reason"])

    def test_known_human_false_support_cannot_be_automatically_confirmed(self):
        item = {"grounding": {"result": {"overall": "SUPPORTED", "claims": []}},
                "quality": {"result": {"groups": [{"outcome": "COMPLETE"}]}},
                "abstention": {"result": {"evidenceAnswerability": "ENOUGH"}},
                "abstentionDecision": {"label": "NOT_APPLICABLE"}, "requiresReview": False}
        row = {"models": {name: copy.deepcopy(item) for name in bedrock.MODELS}}
        row["consensus"] = runner.consensus(row["models"])
        runner.apply_regression_gate(row, {"grounding": "UNSUPPORTED", "quality": ["COMPLETE"],
                                          "abstention": "NOT_APPLICABLE", "answerability": "ENOUGH"})
        self.assertEqual("HUMAN_REVIEW", row["consensus"]["status"])
        self.assertEqual(["grounding"], row["consensus"]["regressionMismatches"])


if __name__ == "__main__":
    unittest.main()
