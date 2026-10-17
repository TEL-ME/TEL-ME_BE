"""로컬 모델 호출 없이 평가 입력과 실패 격리를 검증한다."""

import copy
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.chat_judge import judge_chat_flow as judge


def sample_capture():
    return {
        "schemaVersion": 1,
        "cases": [{
            "caseId": "FAQ_TEST",
            "turns": [{
                "fixture": {
                    "question": "요금제는 몇 번 바꿀 수 있나요?",
                    "expectedIntent": "FAQ",
                    "expectedBehavior": "ANSWER",
                    "goldSourceSlotIds": ["BILLING-0001"],
                    "requiredFacts": ["월 1회"],
                },
                "executionStatus": "COMPLETED",
                "route": {"intent": "FAQ"},
                "searches": [{"query": "요금제 변경", "results": [{
                    "faqId": 3,
                    "slotId": None,
                    "question": "요금제는 몇 번 바꿀 수 있나요?",
                    "answer": "월 1회 바꿀 수 있습니다.",
                }]}],
                "outputMessage": {"content": "월 1회 바꿀 수 있습니다.", "messageType": "ANSWER"},
            }],
        }],
    }


CATALOG = [{
    "slot_id": "BILLING-0001",
    "question": "요금제는 몇 번 바꿀 수 있나요?",
    "answer": "월 1회 바꿀 수 있습니다.",
}]


class ChatJudgeTest(unittest.TestCase):
    def test_rejects_invalid_capture(self):
        capture = sample_capture()
        capture["cases"].append(copy.deepcopy(capture["cases"][0]))
        with self.assertRaises(ValueError):
            judge.validate_capture(capture)
        capture = sample_capture()
        capture["cases"][0]["turns"][0]["fixture"]["expectedBehavior"] = "INVALID"
        with self.assertRaises(ValueError):
            judge.validate_capture(capture)

    def test_catalog_id_requires_exact_question_and_answer(self):
        capture = sample_capture()
        enriched = judge.enrich_source_ids(capture, CATALOG)
        result = enriched["cases"][0]["turns"][0]["searches"][0]["results"][0]
        self.assertEqual("BILLING-0001", result["slotId"])
        self.assertIsNone(capture["cases"][0]["turns"][0]["searches"][0]["results"][0]["slotId"])
        altered = copy.deepcopy(capture)
        altered["cases"][0]["turns"][0]["searches"][0]["results"][0]["answer"] += " 다른 문장"
        self.assertIsNone(judge.enrich_source_ids(altered, CATALOG)["cases"][0]["turns"][0]
                          ["searches"][0]["results"][0]["slotId"])

    def test_rejects_hallucinated_source_and_inconsistent_verdict(self):
        result = {"claims": [{
            "claim": "월 1회", "verdict": "SUPPORTED", "sourceIds": ["없는 ID"], "reason": "근거"
        }], "overall": "SUPPORTED"}
        with self.assertRaises(ValueError):
            judge.validate_result("grounding", result, {"BILLING-0001"})
        result["claims"][0]["sourceIds"] = ["BILLING-0001"]
        result["overall"] = "UNSUPPORTED"
        with self.assertRaises(ValueError):
            judge.validate_result("grounding", result, {"BILLING-0001"})

    def test_unsupported_claim_cannot_cite_a_source(self):
        claim = {
            "claim": "무료입니다.", "verdict": "UNSUPPORTED",
            "sourceIds": ["BILLING-0001"], "reason": "FAQ에 없는 내용",
        }
        result = {"claims": [claim], "overall": "UNSUPPORTED"}
        with self.assertRaisesRegex(ValueError, "지원되지 않은 주장에 근거 ID"):
            judge.validate_result("grounding", result, {"BILLING-0001"})
        claim["sourceIds"] = []
        self.assertIs(result, judge.validate_result("grounding", result, {"BILLING-0001"}))

        branches = judge.GROUNDING_SCHEMA["properties"]["claims"]["items"]["oneOf"]
        unsupported = next(branch for branch in branches
                           if "UNSUPPORTED" in branch["properties"]["verdict"]["enum"])
        self.assertEqual(0, unsupported["properties"]["sourceIds"]["maxItems"])

    def test_supported_policy_claim_requires_faq_even_with_confirmed_condition(self):
        result = {"claims": [{
            "claim": "월 1회 변경할 수 있습니다.", "verdict": "SUPPORTED",
            "sourceIds": [], "reason": "근거가 있다고 판단했습니다.",
        }], "overall": "SUPPORTED"}
        with self.assertRaisesRegex(ValueError, "FAQ 근거 ID"):
            judge.validate_result("grounding", result, {"BILLING-0001"}, [])
        with self.assertRaisesRegex(ValueError, "FAQ 근거 ID"):
            judge.validate_result("grounding", result, set(), [{"name": "지역", "value": "서울"}])

    def test_grounding_rejects_claim_copied_from_unanswered_faq(self):
        result = {"claims": [{
            "claim": "12개월 미만이면 할인 반환금 100%가 적용됩니다.",
            "verdict": "UNSUPPORTED", "sourceIds": [], "reason": "두 번째 FAQ 내용",
        }], "overall": "UNSUPPORTED"}
        with self.assertRaisesRegex(ValueError, "실제 답변의 원문"):
            judge.validate_result("grounding", result, {"FAQ-2"},
                                  answer="최대 월 100만 원까지만 가능합니다.")

    def test_claim_extraction_accepts_only_exact_ordered_answer_spans(self):
        answer = "월 1회 변경할 수 있고, 위약금은 없습니다."
        self.assertEqual(
            ["월 1회 변경할 수 있고", "위약금은 없습니다."],
            judge.validate_claim_extraction(
                {"claims": [{"quote": "월 1회 변경할 수 있고"}, {"quote": "위약금은 없습니다."}]},
                answer,
            ),
        )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "월 1회 변경 가능합니다."}]}, answer
            )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "질문에 있던 내용입니다."}]}, answer
            )

    def test_claim_extraction_keeps_conditions_negation_and_amounts_verbatim(self):
        answer = "미납 요금이 있으면 완납 후 명의 변경이 가능합니다. 배송비는 무료가 아닙니다."
        claims = ["미납 요금이 있으면 완납 후 명의 변경이 가능합니다.", "배송비는 무료가 아닙니다."]
        self.assertEqual(
            claims,
            judge.validate_claim_extraction({"claims": [{"quote": item} for item in claims]}, answer),
        )
        with self.assertRaises(judge.ClaimTextMismatch):
            judge.validate_claim_extraction(
                {"claims": [{"quote": "미납 요금이 있으면 명의 변경이 가능합니다."}]}, answer
            )

    def test_grounding_fallback_uses_exact_sentences_and_normalizes_only_aggregate(self):
        answer = "미납 요금이 있으면 완납 후 변경할 수 있습니다. 배송비는 무료가 아닙니다."
        spans = judge.sentence_spans(answer)
        self.assertEqual(["미납 요금이 있으면 완납 후 변경할 수 있습니다.",
                          "배송비는 무료가 아닙니다."], spans)
        result = {"claims": [
            {"claim": spans[0], "verdict": "SUPPORTED", "sourceIds": ["FAQ-1"], "reason": "근거 있음"},
            {"claim": spans[1], "verdict": "UNSUPPORTED", "sourceIds": [], "reason": "근거 없음"}],
            "overall": "SUPPORTED"}
        self.assertEqual("SUPPORTED", judge.normalize_grounding_overall(result))
        self.assertEqual("UNSUPPORTED", result["overall"])
        self.assertIsNone(judge.normalize_grounding_overall(result))
        judge.validate_result("grounding", result, {"FAQ-1"}, answer=answer, expected_claims=spans)

    @staticmethod
    def completion(content, prompt_tokens=None, completion_tokens=None):
        """vLLM OpenAI 호환 응답 하나."""
        body = {"choices": [{"message": {"content": content}}]}
        if prompt_tokens is not None:
            body["usage"] = {"prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens}
        return io.BytesIO(json.dumps(body, ensure_ascii=False).encode("utf-8"))

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_extracts_from_answer_then_judges_meaning_without_question_leakage(self, urlopen):
        answer = "월 1회 변경할 수 있습니다."
        extraction = {"claims": [{"quote": answer}]}
        semantic = {"claims": [{
            "claim": answer, "verdict": "SUPPORTED",
            "sourceIds": ["FAQ-1"], "reason": "표현은 다르지만 의미가 같습니다.",
        }], "overall": "SUPPORTED"}
        urlopen.side_effect = [self.completion(json.dumps(extraction, ensure_ascii=False), 10, 4),
                               self.completion(json.dumps(semantic, ensure_ascii=False), 20, 5)]
        data = {
            "question": "요금제 변경을 몇 번 할 수 있나요?",
            "answer": answer,
            "sources": [{"sourceId": "FAQ-1", "question": "변경 주기", "answer": "한 달에 한 번 변경 가능합니다."}],
        }

        record = judge.vllm_chat("http://unused", "qwen3-14b-awq", "grounding", data)

        self.assertEqual(2, urlopen.call_count)
        self.assertEqual(2, len(record["attempts"]))
        self.assertEqual(30, record["promptTokens"])
        extraction_input = json.loads(record["claimExtraction"]["request"]["messages"][-1]["content"])
        self.assertEqual({"assistantAnswer": answer}, extraction_input)
        semantic_input = json.loads(record["request"]["messages"][-1]["content"].split("\n", 1)[1])
        self.assertEqual([{"claim": answer}], semantic_input["claims"])
        self.assertNotIn("question", semantic_input)
        self.assertNotIn("assistantAnswer", semantic_input)
        # FAQ 질문 문장은 근거가 아니라 상황(situation)으로만 넘어간다
        self.assertEqual({"sourceId": "FAQ-1", "situation": "변경 주기", "answer": "한 달에 한 번 변경 가능합니다."},
                         semantic_input["faqSources"][0])
        self.assertEqual(semantic, record["result"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_rejects_changed_claims_without_repair_or_reusing_verdict(self, urlopen):
        extracted = {"claims": [{"quote": "월 1회 변경할 수 있습니다."}]}
        changed = {"claims": [{
            "claim": "매월 변경 횟수는 제한이 없습니다.", "verdict": "SUPPORTED",
            "sourceIds": ["FAQ-1"], "reason": "변경 가능하다고 판단",
        }], "overall": "SUPPORTED"}
        urlopen.side_effect = [self.completion(json.dumps(extracted, ensure_ascii=False)),
                               self.completion(json.dumps(changed, ensure_ascii=False))]
        data = {"answer": "월 1회 변경할 수 있습니다.",
                "sources": [{"sourceId": "FAQ-1", "answer": "한 달에 한 번 변경 가능합니다."}]}

        with self.assertRaises(judge.JudgeCallError) as context:
            judge.vllm_chat("http://unused", "qwen3-14b-awq", "grounding", data)

        self.assertEqual(2, urlopen.call_count)
        self.assertNotIn("repairRequest", context.exception.record)
        self.assertIn("ClaimTextMismatch", context.exception.record["error"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_does_not_retry_invalid_extraction(self, urlopen):
        invalid = {"claims": [{"quote": "질문에서 가져온 누출 문장"}]}
        urlopen.side_effect = [self.completion(json.dumps(invalid, ensure_ascii=False))]
        data = {"question": "질문에서 가져온 누출 문장", "answer": "답변 문장입니다.",
                "sources": [{"sourceId": "FAQ-1", "answer": "질문에서 가져온 누출 문장"}]}

        with self.assertRaises(judge.JudgeCallError) as context:
            judge.vllm_chat("http://unused", "qwen3-14b-awq", "grounding", data)

        self.assertEqual(1, urlopen.call_count)
        self.assertIn("not an exact ordered span", context.exception.record["error"])
        self.assertNotIn("repairRequest", context.exception.record)

    @patch.object(judge.urllib.request, "urlopen")
    def test_grounding_prompt_uses_source_id_without_optional_database_id(self, urlopen):
        urlopen.side_effect = [self.completion(json.dumps({"claims": []})),
                               self.completion(json.dumps({"claims": [], "overall": "NOT_APPLICABLE"}))]
        data = {
            "question": "요금제 변경 방법은?", "answer": "안내가 어렵습니다.",
            "sources": [{"sourceId": "BILLING-0001", "faqId": None, "answer": "앱에서 변경합니다."}],
        }
        result = judge.vllm_chat("http://unused", "qwen3-14b-awq", "grounding", data)
        prompt = result["request"]["messages"][-1]["content"]
        self.assertIn('"sourceId":"BILLING-0001"', prompt)
        self.assertIn('"claims":[]', prompt)
        self.assertIn('"faqSources":', prompt)
        self.assertNotIn("안내가 어렵습니다.", prompt)
        self.assertNotIn("faqId", prompt)
        self.assertIsNone(data["sources"][0]["faqId"])

    @patch.object(judge.urllib.request, "urlopen")
    def test_malformed_model_output_preserves_raw_response(self, urlopen):
        urlopen.side_effect = [self.completion("{not valid")]
        with self.assertRaises(judge.JudgeCallError) as context:
            judge.vllm_chat("http://unused", "qwen3-14b-awq", "grounding", {"answer": "답", "sources": []})
        record = context.exception.record
        self.assertIn("request", record)
        self.assertEqual("{not valid", record["rawResponse"]["choices"][0]["message"]["content"])
        self.assertIn("error", record)

    def test_only_grounding_is_judged_through_vllm(self):
        with self.assertRaises(judge.JudgeCallError):
            judge.vllm_chat("http://unused", "qwen3-14b-awq", "adequacy", {"answer": "답", "sources": []})

    def test_gold_is_excluded_from_grounding_prompt(self):
        turn = judge.enrich_source_ids(sample_capture(), CATALOG)["cases"][0]["turns"][0]
        grounding = judge.prompt_data(turn, [], "grounding")
        adequacy = judge.prompt_data(turn, [], "adequacy")
        self.assertNotIn("goldSourceSlotIds", grounding)
        self.assertNotIn("requiredFacts", grounding)
        self.assertEqual(["BILLING-0001"], adequacy["goldSourceSlotIds"])
        self.assertEqual("BILLING-0001", grounding["sources"][0]["sourceId"])

    def test_abstention_decision_combines_grounding_and_independent_signals(self):
        signals = {"answerIsRefusal": False, "evidenceAnswerability": "ENOUGH",
                   "evidenceQuotes": [{"sourceId": "BILLING-0001", "quote": "월 1회"}],
                   "reason": "답변했습니다."}
        self.assertEqual("SHOULD_ABSTAIN", judge.decide_abstention(
            {"overall": "UNSUPPORTED"}, signals, "NOT_APPLICABLE")["label"])
        self.assertEqual("NOT_APPLICABLE", judge.decide_abstention(
            {"overall": "SUPPORTED"}, signals)["label"])
        signals["answerIsRefusal"] = True
        self.assertEqual("OVER_REFUSAL", judge.decide_abstention(
            {"overall": "NOT_APPLICABLE"}, signals)["label"])
        signals["evidenceAnswerability"] = "INSUFFICIENT"
        self.assertEqual("APPROPRIATE", judge.decide_abstention(
            {"overall": "NOT_APPLICABLE"}, signals)["label"])
        signals["evidenceAnswerability"] = "UNCERTAIN"
        decision = judge.decide_abstention({"overall": "NOT_APPLICABLE"}, signals)
        self.assertEqual("REVIEW", decision["label"])
        self.assertTrue(decision["requiresReview"])
        self.assertIs(signals, judge.validate_result(
            "abstention", signals, {"BILLING-0001"},
            sources=[{"sourceId": "BILLING-0001", "answer": "월 1회 바꿀 수 있습니다."}],
        ))

    def test_abstention_requires_a_real_faq_quote(self):
        source = {"sourceId": "USIM-0001", "answer": "7,700원이며 택배로 2~3일 걸립니다."}
        result = {"answerIsRefusal": True, "evidenceAnswerability": "ENOUGH",
                  "evidenceQuotes": [], "reason": "근거가 있다고 판단"}
        with self.assertRaisesRegex(ValueError, "FAQ 인용문이 없습니다"):
            judge.validate_result("abstention", result, {"USIM-0001"}, sources=[source])
        result["evidenceQuotes"] = [{"sourceId": "USIM-0001", "quote": "택배비가 포함됩니다"}]
        with self.assertRaisesRegex(ValueError, "실제 검색 근거"):
            judge.validate_result("abstention", result, {"USIM-0001"}, sources=[source])


if __name__ == "__main__":
    unittest.main()
