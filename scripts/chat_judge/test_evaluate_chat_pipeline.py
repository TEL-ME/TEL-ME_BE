"""Regression checks for real API scoring, denominators and parallel isolation."""

import copy
import io
import json
from pathlib import Path
import threading
import time
import tempfile
import unittest
from unittest.mock import patch

from scripts.chat_judge import evaluate_chat_pipeline as pipeline
from scripts.chat_judge import run_chat_pipeline_eval as runner
from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.test_judge_chat_flow import sample_capture, CATALOG


def scored_item(turn, overall="SUPPORTED", quality="COMPLETE", refusal=False, enough="ENOUGH"):
    item = {"executionStatus": turn["executionStatus"], "expectedBehavior": turn["fixture"]["expectedBehavior"],
        "grounding": {"result": {"overall": overall, "claims": [] if overall == "NOT_APPLICABLE" else [
            {"claim": "월 1회", "verdict": overall, "sourceIds": ["BILLING-0001"] if overall == "SUPPORTED" else [], "reason": "test"}]}},
        "quality": {"result": {"groups": [{"groupIndex": 0, "outcome": quality, "answerQuotes": ["월 1회"], "reason": "test"}]}},
        "abstention": {"result": {"answerIsRefusal": refusal, "evidenceAnswerability": enough,
                                  "evidenceQuotes": [], "reason": "test"}}}
    return pipeline.finalize_turn(turn, item)


class PipelineEvaluationTest(unittest.TestCase):
    @patch.object(pipeline.urllib.request, "urlopen")
    @patch.object(pipeline, "abstention_chat")
    @patch.object(pipeline.judge, "vllm_chat")
    @patch.object(pipeline, "quality_chat")
    def test_resume_preserves_failed_axes_and_rejects_changed_input(self, quality_chat, vllm_chat,
                                                                    abstention_chat, urlopen):
        def models(*args, **kwargs):
            return io.BytesIO(json.dumps({"data": [{"id": "qwen3-14b-awq"}]}).encode())
        urlopen.side_effect = models
        turn = sample_capture()["cases"][0]["turns"][0]
        templates = scored_item(turn)
        quality_chat.return_value = {"error": "invalid citation", "rawResponse": "unchanged"}
        vllm_chat.side_effect = lambda url, model, axis, data, **kwargs: templates[axis]
        abstention_chat.return_value = templates["abstention"]
        prior = pipeline.evaluate(sample_capture(), CATALOG, "http://unused", "qwen3-14b-awq", retries=0)
        quality_chat.reset_mock()
        vllm_chat.reset_mock()
        abstention_chat.reset_mock()
        resumed = pipeline.evaluate(sample_capture(), CATALOG, "http://unused", "qwen3-14b-awq", prior=prior)
        quality_chat.assert_not_called()
        vllm_chat.assert_not_called()
        abstention_chat.assert_not_called()
        self.assertEqual("unchanged", resumed["turns"][0]["quality"]["rawResponse"])
        changed = sample_capture()
        changed["cases"][0]["turns"][0]["fixture"]["question"] += " 변경"
        with self.assertRaisesRegex(ValueError, "Resume identity differs"):
            pipeline.evaluate(changed, CATALOG, "http://unused", "qwen3-14b-awq", prior=prior)

    def test_checkpoint_retries_a_temporary_windows_reader_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "result.json.gz"
            real_replace = Path.replace
            attempts = []
            def replace(source, destination):
                attempts.append(source)
                if len(attempts) < 3:
                    raise PermissionError("reader still open")
                return real_replace(source, destination)
            with patch.object(Path, "replace", replace), patch.object(pipeline.time, "sleep"):
                pipeline.checkpoint(target, {"rawResponse": "preserved"})
            self.assertEqual(3, len(attempts))
            self.assertEqual({"rawResponse": "preserved"}, pipeline.load_json(target))
    def test_smaller_run_keeps_all_flow_scenarios_and_spans_categories(self):
        cases = json.loads(runner.DEFAULT_FIXTURE.read_text(encoding="utf-8"))
        chosen = runner.select_cases(cases, 40)
        self.assertEqual(40, len(chosen))
        self.assertEqual(8, sum(case["suite"] == "flow_regression" for case in chosen))
        self.assertGreaterEqual(len({case["category"] for case in chosen}), 10)
        with self.assertRaises(ValueError):
            runner.select_cases(cases, 3)

    def test_generation_context_is_not_replaced_with_saved_citations(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        source = turn["searches"][0]["results"][0]
        turn["ragInputs"] = [{"searchResults": [source]}]
        turn["savedSources"] = []
        sources, provenance = pipeline.generation_sources(turn)
        self.assertEqual(1, len(sources))
        self.assertEqual("captured_rag_input", provenance)
        self.assertEqual([], judge.actual_sources(turn))

    def test_saved_faq_candidate_answer_is_added_to_generation_context(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        source = turn["searches"][0]["results"][0]
        turn["ragInputs"] = [{"searchResults": [source]}]
        candidate = {"faqId": 777, "slotId": "TERMINATE-0001", "question": "해지 신청 절차를 알려주세요.",
                     "answer": "매장, 고객센터, 홈페이지에서 신청하면 즉시 해지됩니다."}
        unrelated = {"faqId": 778, "slotId": "TERMINATE-0002", "question": "해지하면 번호는요?", "answer": "사라집니다."}
        turn["pipelineTrace"] = {"searchResults": [{"kind": "FAQ_CANDIDATE", "sources": [candidate, unrelated]}]}
        turn["savedSources"] = [{"faqId": source["faqId"]}, {"faqId": 777}]
        sources, provenance = pipeline.generation_sources(turn)
        self.assertEqual([source["faqId"], 777], [row["faqId"] for row in sources])
        self.assertEqual("captured_rag_input+saved_sources", provenance)
        self.assertEqual("매장, 고객센터, 홈페이지에서 신청하면 즉시 해지됩니다.", sources[1]["answer"])

    def test_source_ids_are_enriched_only_with_exact_catalog_text(self):
        capture = sample_capture()
        source = capture["cases"][0]["turns"][0]["searches"][0]["results"][0]
        capture["cases"][0]["turns"][0]["ragInputs"] = [{"searchResults": [copy.deepcopy(source)]}]
        enriched = pipeline.enrich_rag_sources(capture, CATALOG)
        self.assertEqual("BILLING-0001", enriched["cases"][0]["turns"][0]["ragInputs"][0]["searchResults"][0]["slotId"])
        self.assertIsNone(source["slotId"])

    def test_saved_faq_candidate_without_slot_id_is_counted_as_the_same_source(self):
        # 생성 모델을 거치지 않은 FAQ 후보 답변: 근거가 실행 기록에만 있고 DB에 slot_id가 없다
        capture = sample_capture()
        turn = capture["cases"][0]["turns"][0]
        source = turn["searches"][0]["results"][0]
        turn["ragInputs"] = []
        turn["pipelineTrace"] = {"searchResults": [{"kind": "FAQ_CANDIDATE", "sources": [copy.deepcopy(source)]}]}
        turn["savedSources"] = [{"faqId": source["faqId"]}]
        enriched = pipeline.enrich_rag_sources(capture, CATALOG)["cases"][0]["turns"][0]
        self.assertEqual({"applicable": True, "groupCount": 1, "candidateHits": 1,
                          "ragInputHits": 1, "savedSourceHits": 1}, pipeline.retrieval_stages(enriched))
        self.assertEqual(["BILLING-0001"], [row["sourceId"] for row in pipeline.generation_sources(enriched)[0]])

    def test_database_name_is_limited_to_names_safe_in_sql(self):
        for name in ("telme_judge_eval", "telme_judge_20261022", "telme_judge_214_store"):
            self.assertTrue(runner.DATABASE_NAME.fullmatch(name), name)
        for name in ("telme_judge_2026-10-22", "telme_judge_", "telme_eval", "Telme_judge_x",
                     "telme_judge_x; DROP DATABASE telme", "telme_judge_" + "a" * 41):
            self.assertFalse(runner.DATABASE_NAME.fullmatch(name), name)

    def test_quality_citations_and_order_are_validated(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        result = {"groups": [{"groupIndex": 0, "outcome": "COMPLETE", "answerQuotes": ["월 1회"], "reason": "test"}]}
        pipeline.validate_quality(result, turn)
        result["groups"][0]["answerQuotes"] = ["월 2회"]
        with self.assertRaises(ValueError):
            pipeline.validate_quality(result, turn)
        result["groups"][0]["answerQuotes"] = []
        with self.assertRaises(ValueError):
            pipeline.validate_quality(result, turn)
        result["groups"][0].update(groupIndex=1, outcome="MISSED")
        with self.assertRaises(ValueError):
            pipeline.validate_quality(result, turn)

    def test_quality_schema_only_allows_actual_answer_paragraphs(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["outputMessage"]["content"] = "월 1회 변경 가능합니다.\n가입한 달에는 불가능합니다."
        request = pipeline.quality_request("qwen", turn, [])
        allowed = request["response_format"]["json_schema"]["schema"]["properties"]["groups"]["items"]["properties"]["answerQuotes"]["items"]["enum"]
        self.assertEqual(["월 1회 변경 가능합니다.", "가입한 달에는 불가능합니다."], allowed)
        self.assertNotIn("월 1회까지만 가능하다", allowed)

    def test_quality_reference_uses_faq_answer_without_faq_question(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["qualityReferenceGroups"] = [{
            "sourceId": "BILLING-0001", "question": "faq question is metadata",
            "answer": "policy text lives here",
        }]
        request = pipeline.quality_request("qwen", turn, [])
        data = json.loads(request["messages"][1]["content"])
        self.assertEqual([{"questionPart": turn["fixture"]["question"], "alternatives": [
            {"sourceId": "BILLING-0001", "answer": "policy text lives here"}]}], data["referenceGroups"])
        self.assertNotIn("faq question is metadata", request["messages"][1]["content"])

    @patch.object(pipeline, "abstention_stage")
    def test_evidence_schema_binds_each_quote_to_its_actual_source(self, stage):
        schemas = {}

        def respond(url, model, name, rubric, schema, data, timeout):
            schemas[name] = schema
            return {"request": {"name": name}, "error": "not needed"}
        stage.side_effect = respond
        data = {"question": "언제 바꿔요?", "answer": "답", "previousTurns": [], "confirmedConditions": [],
                "sources": [{"sourceId": "A", "answer": "월 1회 가능합니다."},
                            {"sourceId": "B", "answer": "다음 달부터 가능합니다."}]}
        pipeline.abstention_chat("http://unused", "qwen", data, 5)
        quotes = schemas["faq_answerability"]["properties"]["parts"]["items"]["properties"]["evidenceQuotes"]
        branches = quotes["items"]["oneOf"]
        self.assertEqual("A", branches[0]["properties"]["sourceId"]["const"])
        self.assertEqual(["월 1회 가능합니다."], branches[0]["properties"]["quote"]["enum"])
        self.assertEqual("B", branches[1]["properties"]["sourceId"]["const"])

    @patch.object(pipeline, "abstention_stage")
    def test_answerability_receives_question_and_sources_without_generated_answer(self, stage):
        seen = []
        def respond(url, model, name, rubric, schema, data, timeout):
            seen.append((name, copy.deepcopy(data)))
            if name == "answer_refusal":
                return {"request": {"name": name}, "result": {"answerIsRefusal": False,
                    "reason": "A substantive answer was given."}}
            return {"request": {"name": name}, "result": {
                "parts": [
                    {"partIndex": 0, "evidenceAnswerability": "ENOUGH",
                     "evidenceQuotes": [{"sourceId": "A", "quote": "A는 가능합니다."}], "reason": "A is answered."},
                    {"partIndex": 1, "evidenceAnswerability": "INSUFFICIENT",
                     "evidenceQuotes": [], "reason": "No evidence for B."}],
                "reason": "The second question has no source."}}
        stage.side_effect = respond
        data = {"question": "A와 B는 가능한가요?", "questionParts": ["A는 가능한가요?", "B는 가능한가요?"],
                "previousTurns": [],
                "answer": "A도 B도 가능합니다.", "confirmedConditions": [],
                "sources": [{"sourceId": "A", "question": "A는 가능한가요?", "answer": "A는 가능합니다."}]}
        record = pipeline.abstention_chat("http://unused", "qwen", data, 5)
        self.assertEqual("INSUFFICIENT", record["result"]["evidenceAnswerability"])
        self.assertEqual(["answer_refusal", "faq_answerability"], [name for name, _ in seen])
        self.assertNotIn("answer", seen[1][1])
        self.assertNotIn("sources", seen[0][1])
        self.assertEqual(data["question"], seen[1][1]["question"])

    def test_supported_but_partial_is_not_quality_success(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, quality="PARTIAL")
        self.assertFalse(item["answerQualityPass"])
        self.assertFalse(item["hasUnsupportedClaim"])

    def test_unsubstantiated_extra_fails_even_when_question_is_answered(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, overall="UNSUPPORTED")
        self.assertFalse(item["answerQualityPass"])
        self.assertEqual("SHOULD_ABSTAIN", item["abstentionDecision"]["label"])

    def test_ambiguous_support_is_review_not_hallucination(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, overall="REVIEW")
        self.assertTrue(item["requiresReview"])
        self.assertIsNone(item["answerQualityPass"])
        self.assertEqual("REVIEW", item["abstentionDecision"]["label"])
        result = item["grounding"]["result"]
        judge.validate_result("grounding", result, set(), answer=turn["outputMessage"]["content"])

    def test_empty_claim_extraction_cannot_pass_a_complete_factual_answer(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, overall="NOT_APPLICABLE")
        self.assertTrue(item["requiresReview"])
        self.assertIsNone(item["answerQualityPass"])

    def test_sentence_fallback_is_reviewed_and_excluded_from_automatic_grounding_rate(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, overall="SUPPORTED")
        item["grounding"]["claimExtractionFallback"] = "EXACT_ANSWER_SENTENCES"
        pipeline.finalize_turn(turn, item)
        metrics = pipeline.summarize([item])
        self.assertTrue(item["requiresReview"])
        self.assertIsNone(item["answerQualityPass"])
        self.assertEqual(0, metrics["groundingCoverage"]["numerator"])

    def test_valid_axis_survives_another_axis_failure(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        item = scored_item(turn, overall="UNSUPPORTED")
        item["quality"] = {"error": "invalid quote"}
        pipeline.finalize_turn(turn, item)
        metrics = pipeline.summarize([item])
        self.assertEqual(1, metrics["axisScored"]["grounding"])
        self.assertEqual(0, metrics["axisScored"]["quality"])
        self.assertEqual(100, metrics["hallucinatedAnswerRate"]["percent"])
        self.assertEqual(1, metrics["unscoredTurns"])
        self.assertEqual([0.0, 100.0], metrics["qualitySuccessBoundsPercent"])

    def test_execution_failure_stays_in_quality_denominator(self):
        good = sample_capture()["cases"][0]["turns"][0]
        failed = copy.deepcopy(good)
        failed["executionStatus"] = "FAILED"
        item = pipeline.finalize_turn(failed, {"executionStatus": "FAILED"})
        metrics = pipeline.summarize([scored_item(good), item])
        self.assertEqual({"numerator": 1, "denominator": 2, "percent": 50.0}, metrics["qualitySuccess"])
        self.assertEqual(1, metrics["unscoredTurns"])

    def test_answerless_refusals_do_not_dilute_factual_hallucination_rate(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        outside = copy.deepcopy(turn)
        outside["fixture"]["expectedBehavior"] = "OUT_OF_SCOPE"
        outside["fixture"]["expectedIntent"] = "UNKNOWN"
        metrics = pipeline.summarize([scored_item(turn, overall="UNSUPPORTED"),
            scored_item(outside, overall="NOT_APPLICABLE", quality="NOT_APPLICABLE", refusal=True, enough="INSUFFICIENT")])
        self.assertEqual(100, metrics["hallucinatedAnswerRate"]["percent"])
        self.assertEqual(50, metrics["unsupportedAnswersPerAllTurns"]["percent"])

    def test_partial_capture_does_not_hide_unexecuted_questions(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        metrics = pipeline.summarize([scored_item(turn)], planned=4)
        self.assertEqual([25.0, 100.0], metrics["qualitySuccessBoundsPercent"])
        self.assertEqual(3, metrics["unresolvedQualityTurns"])

    def test_candidate_rag_and_saved_hits_are_measured_separately(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["goldSourceGroups"] = [["BILLING-0001"], ["BILLING-0002"]]
        source = turn["searches"][0]["results"][0]
        source["slotId"] = "BILLING-0001"
        turn["ragInputs"] = [{"searchResults": [source]}]
        turn["savedSources"] = []
        self.assertEqual({"applicable": True, "groupCount": 2, "candidateHits": 1,
                          "ragInputHits": 1, "savedSourceHits": 0}, pipeline.retrieval_stages(turn))

    def test_outside_refusals_do_not_dilute_over_refusal_rate(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        outside = copy.deepcopy(turn)
        outside["fixture"].update(expectedBehavior="OUT_OF_SCOPE", expectedIntent="UNKNOWN")
        metrics = pipeline.summarize([scored_item(turn, refusal=True), scored_item(outside,
            overall="NOT_APPLICABLE", quality="NOT_APPLICABLE", refusal=True, enough="INSUFFICIENT")])
        self.assertEqual({"numerator": 1, "denominator": 1, "percent": 100.0}, metrics["overRefusalRate"])

    @patch.object(pipeline.urllib.request, "urlopen")
    @patch.object(pipeline, "abstention_chat")
    @patch.object(pipeline.judge, "vllm_chat")
    @patch.object(pipeline, "quality_chat")
    def test_parallel_requests_retry_failures_and_preserve_every_attempt(self, quality_chat, vllm_chat,
                                                                           abstention_chat, urlopen):
        urlopen.return_value = io.BytesIO(json.dumps({"data": [{"id": "qwen3-14b-awq"}]}).encode())
        lock = threading.Lock()
        active, peak, calls = 0, 0, {}
        turn = sample_capture()["cases"][0]["turns"][0]
        templates = scored_item(turn)
        def remote(url, model, axis, data, **kwargs):
            nonlocal active, peak
            with lock:
                active += 1
                peak = max(peak, active)
                calls[axis] = calls.get(axis, 0) + 1
                attempt = calls[axis]
            time.sleep(.025)
            with lock:
                active -= 1
            if axis == "grounding" and attempt == 1:
                raise judge.JudgeCallError({"error": "bad extraction", "rawResponse": "original"})
            return templates[axis]
        vllm_chat.side_effect = remote
        quality_chat.return_value = templates["quality"]
        abstention_chat.side_effect = lambda url, model, data, timeout: remote(
            url, model, "abstention", data)
        checkpoints = []
        evaluation = pipeline.evaluate(sample_capture(), CATALOG, "http://unused", "qwen3-14b-awq",
                                       on_checkpoint=lambda value: checkpoints.append(copy.deepcopy(value)))
        self.assertGreaterEqual(peak, 2)
        self.assertLessEqual(peak, 8)
        self.assertEqual(2, len(evaluation["turns"][0]["grounding"]["evaluationAttempts"]))
        self.assertEqual("original", evaluation["turns"][0]["grounding"]["evaluationAttempts"][0]["rawResponse"])
        self.assertEqual(100, evaluation["summary"]["qualitySuccess"]["percent"])
        self.assertTrue(checkpoints)



class RuleBasedJudgmentTest(unittest.TestCase):
    """모델 없이 정할 수 있는 판정은 코드가 정하고 모델을 부르지 않는다."""

    def turn(self, behavior="ANSWER", message_type="ANSWER", content="월 1회 바꿀 수 있습니다."):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["expectedBehavior"] = behavior
        turn["outputMessage"] = {"content": content, "messageType": message_type}
        return turn

    @patch.object(pipeline, "abstention_stage")
    def test_empty_sources_are_insufficient_without_calling_the_evidence_model(self, stage):
        stage.return_value = {"request": {"stage": "refusal"}, "result": {"answerIsRefusal": True, "reason": "거절"}}
        record = pipeline.abstention_chat("http://unused", "judge", {
            "question": "너겟 요금제 혜택이 뭐예요?", "answer": "확인할 수 있는 정보가 없어요.", "sources": []}, 5)
        self.assertEqual(1, stage.call_count)
        self.assertEqual("answer_refusal", stage.call_args.args[2])
        self.assertEqual("EMPTY_SOURCES", record["stages"]["evidence"]["rule"])
        self.assertEqual("INSUFFICIENT", record["result"]["evidenceAnswerability"])
        grounding = {"overall": "NOT_APPLICABLE", "claims": []}
        self.assertEqual("APPROPRIATE", judge.decide_abstention(grounding, record["result"])["label"])

    @patch.object(pipeline, "quality_chat")
    def test_out_of_scope_quality_is_not_applicable_without_a_model_call(self, quality_chat):
        turn = self.turn("OUT_OF_SCOPE", content="통신 서비스 관련 질문을 알려 주세요.")
        record = pipeline.rule_quality(turn)
        self.assertEqual("OUT_OF_SCOPE_QUESTION", record["rule"])
        item = scored_item(turn, overall="NOT_APPLICABLE", refusal=True, enough="INSUFFICIENT")
        item["quality"] = record
        pipeline.finalize_turn(turn, item)
        self.assertTrue(item["resolutionComplete"])
        quality_chat.assert_not_called()

    def test_clarification_is_accepted_only_when_the_answer_depends_on_a_customer_condition(self):
        accepted = self.turn("ANSWER_OR_CLARIFY", "CLARIFICATION", "약정 기간이 얼마나 남으셨나요?")
        record = pipeline.rule_quality(accepted)
        self.assertEqual("CLARIFICATION_ACCEPTED", record["rule"])
        item = scored_item(accepted, overall="NOT_APPLICABLE")
        item["quality"] = record
        pipeline.finalize_turn(accepted, item)
        self.assertTrue(item["resolutionComplete"])
        self.assertNotIn("UNNECESSARY_CLARIFICATION", item["findings"])
        self.assertTrue(item["answerQualityPass"])

        needless = self.turn("ANSWER", "CLARIFICATION", "어떤 요금제를 쓰고 계신가요?")
        record = pipeline.rule_quality(needless)
        self.assertEqual("MISSED", record["result"]["groups"][0]["outcome"])
        item = scored_item(needless, overall="NOT_APPLICABLE")
        item["quality"] = record
        pipeline.finalize_turn(needless, item)
        self.assertIn("UNNECESSARY_CLARIFICATION", item["findings"])
        self.assertFalse(item["answerQualityPass"])

    def test_safe_no_information_reply_satisfies_an_unanswerable_question(self):
        turn = self.turn("ABSTAIN", content="관련 안내 정보를 찾지 못했습니다.")
        item = scored_item(turn, overall="NOT_APPLICABLE", quality="NOT_APPLICABLE", refusal=True, enough="INSUFFICIENT")
        self.assertTrue(item["resolutionComplete"])
        self.assertTrue(item["answerQualityPass"])
        missed = scored_item(turn, overall="NOT_APPLICABLE", quality="MISSED", refusal=True, enough="INSUFFICIENT")
        self.assertFalse(missed["answerQualityPass"])

    def test_an_actual_answer_is_still_judged_by_the_model(self):
        self.assertIsNone(pipeline.rule_quality(self.turn("ANSWER_OR_CLARIFY")))
        self.assertIsNone(pipeline.rule_quality(self.turn("ANSWER")))


if __name__ == "__main__":
    unittest.main()
