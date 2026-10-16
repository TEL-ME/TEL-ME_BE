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

from scripts.chat_judge.experiments.v6_live_chat_pipeline import build_chat_pipeline_eval as builder
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline
from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review as human_compare
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
    def test_human_pipeline_failure_is_reported_separately_from_routing_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "human.md"
            path.write_text("""## V6H-026
- 별도 파이프라인 실패 유형: `MISROUTED_CLARIFICATION`
- 근거성 전체 판정: `NOT_APPLICABLE`
  - 기준 1: `MISSED`
- 답변 불가 판정: `NOT_APPLICABLE`
- 실제 전달 근거 충분성: `INSUFFICIENT`
""", encoding="utf-8")
            human, _ = human_compare.load_human_review(path, {"cases": [
                {"reviewId": "V6H-026", "caseId": "ROAMING", "turnIndex": 0}]})
        evaluation = {"turns": [{"caseId": "ROAMING", "turnIndex": 0,
                                  "findings": ["ROUTING_MISMATCH"]}]}
        result = human_compare.compare_pipeline_failures(human, evaluation)
        self.assertEqual(1, result["humanCounts"]["MISROUTED_CLARIFICATION"])
        self.assertEqual(1, result["routingMismatchCount"])
        self.assertEqual({"reviewId": "V6H-026", "humanLabel": "MISROUTED_CLARIFICATION",
                          "routingMismatch": True}, result["rows"][0])

    def test_unscored_abstention_is_not_counted_as_not_applicable(self):
        human = {"H-001": {"caseId": "CASE", "turnIndex": 0, "grounding": "SUPPORTED",
                           "quality": ["COMPLETE"], "abstention": "NOT_APPLICABLE",
                           "answerability": "ENOUGH"}}
        evaluation = {"turns": [{"caseId": "CASE", "turnIndex": 0,
                                  "grounding": {"result": {"overall": "SUPPORTED"}},
                                  "quality": {"result": {"groups": [{"outcome": "COMPLETE"}]}},
                                  "abstention": {"result": {"evidenceAnswerability": "ENOUGH"}},
                                  "abstentionDecision": None}]}
        counts, _ = human_compare.compare(human, evaluation)
        self.assertEqual(1, counts["abstention"]["unresolved"])
        self.assertEqual(0, counts["abstention"]["match"])

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
    def test_generated_suite_contains_questions_not_fake_answers_or_fixed_judge_labels(self):
        def load(name):
            directory = "scripts/chat_judge/data" if name.startswith("chat_judge_") else "scripts/data"
            return json.loads((builder.ROOT / directory / name).read_text(encoding="utf-8"))
        cases = builder.build_cases(load("chat_judge_validation_v2.json"), load("chat_judge_pilot.json"),
                                    load("faq_full_1150.json"))
        self.assertEqual(508, len(cases))
        self.assertEqual(509, sum(len(case["turns"]) for case in cases))
        self.assertEqual(50, sum(case["category"] == "COMPOUND" for case in cases))
        for case in cases:
            for turn in case["turns"]:
                self.assertNotIn("answer", turn)
                self.assertNotIn("expected", turn)
                self.assertNotIn("groundingInput", turn)
        no_evidence_question = next(case for case in cases if case["caseId"] == "PIPE-BILLING-0097")
        self.assertEqual("ANSWER", no_evidence_question["turns"][0]["expectedBehavior"])
        self.assertTrue(no_evidence_question["turns"][0]["qualityReferenceGroups"])

    def test_smaller_run_keeps_all_flow_scenarios_and_spans_categories(self):
        cases = json.loads(builder.DEFAULT_OUTPUT.read_text(encoding="utf-8"))
        chosen = builder.select_cases(cases, 58)
        self.assertEqual(58, len(chosen))
        self.assertEqual(8, sum(case["suite"] == "flow_regression" for case in chosen))
        self.assertEqual(10, len({case["category"] for case in chosen if case["suite"] == "catalog_questions"}))
        with self.assertRaises(ValueError):
            builder.select_cases(cases, 3)

    def test_generation_context_is_not_replaced_with_saved_citations(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        source = turn["searches"][0]["results"][0]
        turn["ragInputs"] = [{"searchResults": [source]}]
        turn["savedSources"] = []
        sources, provenance = pipeline.generation_sources(turn)
        self.assertEqual(1, len(sources))
        self.assertEqual("captured_rag_input", provenance)
        self.assertEqual([], judge.actual_sources(turn))

    def test_source_ids_are_enriched_only_with_exact_catalog_text(self):
        capture = sample_capture()
        source = capture["cases"][0]["turns"][0]["searches"][0]["results"][0]
        capture["cases"][0]["turns"][0]["ragInputs"] = [{"searchResults": [copy.deepcopy(source)]}]
        enriched = pipeline.enrich_rag_sources(capture, CATALOG)
        self.assertEqual("BILLING-0001", enriched["cases"][0]["turns"][0]["ragInputs"][0]["searchResults"][0]["slotId"])
        self.assertIsNone(source["slotId"])

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
        self.assertEqual([{"sourceId": "BILLING-0001", "answer": "policy text lives here"}],
                         data["referenceGroups"])
        self.assertNotIn("question", data["referenceGroups"][0])

    def test_abstention_schema_binds_each_quote_to_its_actual_source(self):
        data = {"sources": [{"sourceId": "A", "answer": "월 1회 가능합니다."},
                            {"sourceId": "B", "answer": "다음 달부터 가능합니다."}]}
        schema = judge.judge_request("qwen", "abstention", data)["format"]
        branches = schema["properties"]["evidenceQuotes"]["items"]["oneOf"]
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


if __name__ == "__main__":
    unittest.main()
