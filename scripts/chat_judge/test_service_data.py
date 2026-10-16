"""FAQ 밖 서비스 데이터 근거, 앱 고정 문구 규칙, 예전 캡처 보강, 이전 판정 재사용 테스트."""

import copy
import json
from pathlib import Path
import unittest

from scripts.chat_judge import backfill_capture_service_data as backfill
from scripts.chat_judge import run_bedrock_judge
from scripts.chat_judge import service_data
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline
from scripts.chat_judge.experiments.v7_cross_judge.test_v7 import FakeClient
from scripts.chat_judge.test_judge_chat_flow import CATALOG, sample_capture

ROOT = Path(__file__).resolve().parents[2]


def plan(name, network, target, fee, data_gb, unlimited=False):
    return {"code": name, "name": name, "network_type": network, "target_group": target, "monthly_fee": fee,
            "data_gb": data_gb, "is_unlimited": unlimited, "voice_minutes": 300, "is_voice_unlimited": False,
            "sms_count": 100, "is_sms_unlimited": True, "status": "ACTIVE", "sort_order": 1}


PLANS = [plan("5G 프리미엄", "FIVE_G", "GENERAL", 85000, 150), plan("알뜰 미니", "BUDGET", "GENERAL", 15000, 1.5),
         plan("5G 시니어", "FIVE_G", "SENIOR", 49000, 20),
         {**plan("LTE 옛날", "LTE", "GENERAL", 10000, 1), "status": "DISCONTINUED"}]
ELIGIBILITY = {"youthMinAge": 14, "youthMaxAge": 18, "seniorMinAge": 65}


def plan_capture(content="일반 요금제 중 가장 저렴한 요금제예요.\n\n[알뜰]\n- 알뜰 미니: 월 15,000원 · 데이터 1.5GB"):
    capture = sample_capture()
    turn = capture["cases"][0]["turns"][0]
    turn.update(searches=[], ragInputs=[], savedSources=[], pipelineTrace={"planAnswer": {"type": "PLAN_CATALOG"}},
                outputMessage={"content": content, "messageType": "ANSWER", "answerBasis": "GROUNDED"})
    capture["serviceData"] = {"plans": PLANS, "planEligibility": ELIGIBILITY}
    return capture


class AppMessageTest(unittest.TestCase):
    def test_every_fixed_message_exists_verbatim_in_java_source(self):
        java = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "src/main/java").rglob("*.java"))
        for sentence in service_data.SERVICE_GUIDANCE + service_data.SYSTEM_FALLBACKS:
            self.assertIn(sentence, java)

    def test_service_guidance_matches_whole_template_sentences_only(self):
        self.assertTrue(service_data.is_service_guidance("로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요."))
        self.assertTrue(service_data.is_service_guidance("요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요"))
        self.assertFalse(service_data.is_service_guidance("알뜰 미니: 월 15,000원 · 데이터 1.5GB"))
        self.assertFalse(service_data.is_service_guidance("확인해 드릴"))
        self.assertTrue(service_data.system_fallback(" 현재 질문을 안전하게 나누어 처리하기 어려워요.\n궁금한 내용을 하나씩 알려주세요."))
        self.assertFalse(service_data.system_fallback(
            "저는 통신 서비스와 매장 관련 질문을 도와드려요. 요금제, 로밍, 유심 또는 매장에 대해 궁금한 내용을 알려주세요."))


class PlanSourceTest(unittest.TestCase):
    def test_plan_table_lists_active_plans_with_derived_counts_and_eligibility(self):
        text = service_data.plan_table_source(PLANS, ELIGIBILITY)["answer"]
        self.assertIn("알뜰 미니: 알뜰 요금제, 일반 대상, 월정액 15,000원, 데이터 1.5GB, 통화 300분, 문자 무제한", text)
        self.assertNotIn("LTE 옛날", text)
        self.assertIn("판매 중인 요금제는 총 3종이다(5G 2종, 알뜰 1종).", text)
        self.assertIn("일반 요금제(청소년·시니어 제외)는 2종이다(5G 1종, 알뜰 1종).", text)
        self.assertIn("일반 요금제 중 가장 저렴한 요금제는 알뜰 미니(월 15,000원)이고", text)
        self.assertIn("시니어 요금제는 만 65세 이상만 가입할 수 있다.", text)

    def test_plan_answer_turn_gets_plan_table_as_generation_source(self):
        enriched = pipeline.enrich_rag_sources(plan_capture(), CATALOG)
        sources, provenance = pipeline.generation_sources(enriched["cases"][0]["turns"][0])
        self.assertEqual([service_data.PLAN_SOURCE_ID], [source["sourceId"] for source in sources])
        self.assertEqual("captured_rag_input+service_data", provenance)
        # 근거가 생겼으니 근거 없음 규칙으로 판정을 끝내지 않는다
        self.assertIsNone(pipeline.rule_evidence({"sources": sources, "questionParts": ["질문"]}))

    def test_plan_answer_without_plan_table_stops_instead_of_judging_against_nothing(self):
        capture = plan_capture()
        del capture["serviceData"]
        with self.assertRaises(ValueError):
            pipeline.enrich_rag_sources(capture, CATALOG)

    def test_legacy_capture_without_trace_is_unchanged(self):
        enriched = pipeline.enrich_rag_sources(sample_capture(), CATALOG)
        self.assertNotIn("serviceSources", enriched["cases"][0]["turns"][0])


class CodeRuleTest(unittest.TestCase):
    def fallback_turn(self, expected):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["expectedBehavior"] = expected
        turn["outputMessage"] = {"content": service_data.SYSTEM_FALLBACKS[0], "messageType": "ANSWER"}
        return turn

    def test_system_fallback_is_not_an_abstention_or_out_of_scope_reply(self):
        for expected in ("ABSTAIN", "OUT_OF_SCOPE", "ANSWER"):
            turn = self.fallback_turn(expected)
            rule = pipeline.rule_quality(turn)
            self.assertEqual("SYSTEM_FALLBACK", rule["rule"])
            item = {"grounding": {"result": {"overall": "NOT_APPLICABLE", "claims": []}}, "quality": rule,
                    "abstention": {"result": {"answerIsRefusal": True, "evidenceAnswerability": "INSUFFICIENT"}}}
            pipeline.finalize_turn(turn, item)
            self.assertIs(False, item["answerQualityPass"], expected)
            self.assertIn("SYSTEM_FALLBACK", item["findings"])

    def test_login_guidance_is_accepted_only_for_member_data_questions(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"].update(expectedBehavior="ABSTAIN", answerSources=["MEMBER"])
        turn["outputMessage"] = {"messageType": "ANSWER", "content": "로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요. "
                                                                     "요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요."}
        self.assertEqual("LOGIN_GUIDANCE_ACCEPTED", pipeline.rule_quality(turn)["rule"])
        turn["fixture"]["answerSources"] = []
        self.assertIsNone(pipeline.rule_quality(turn))
        turn["fixture"]["answerSources"] = ["MEMBER"]
        turn["outputMessage"]["content"] = "요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요."
        self.assertIsNone(pipeline.rule_quality(turn))

    def test_rescoring_replaces_an_old_model_quality_with_the_new_rule_and_keeps_it(self):
        turn = self.fallback_turn("ABSTAIN")
        model = {"result": {"groups": [{"groupIndex": 0, "outcome": "NOT_APPLICABLE", "answerQuotes": [], "reason": ""}]}}
        item = {"quality": copy.deepcopy(model)}
        pipeline.apply_code_rules(turn, item)
        self.assertEqual("SYSTEM_FALLBACK", item["quality"]["rule"])
        self.assertEqual(model, item["modelQuality"])

    def test_service_guidance_claim_leaves_grounding_and_keeps_model_verdict(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        grounding = {"overall": "UNSUPPORTED", "claims": [
            {"claim": "로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.", "verdict": "UNSUPPORTED",
             "sourceIds": [], "reason": "근거 없음"}]}
        item = {"grounding": {"result": grounding}}
        pipeline.apply_code_rules(turn, item)
        claim = grounding["claims"][0]
        self.assertEqual(("IRRELEVANT", "UNSUPPORTED", "SERVICE_GUIDANCE"),
                         (claim["verdict"], claim["modelVerdict"], claim["rule"]))
        self.assertEqual("NOT_APPLICABLE", grounding["overall"])
        pipeline.apply_code_rules(turn, item)  # 다시 적용해도 같다
        self.assertEqual("UNSUPPORTED", claim["modelVerdict"])


class BackfillTest(unittest.TestCase):
    def test_eligibility_is_read_from_java_constants(self):
        self.assertEqual(ELIGIBILITY, backfill.plan_eligibility())

    def test_backfill_refuses_a_database_whose_execution_has_another_question(self):
        capture = sample_capture()
        capture["cases"][0]["turns"][0]["executionId"] = 7
        rows = [{"executionId": 7, "trace": {"planAnswer": {"type": "PLAN_CATALOG"}}, "question": "다른 질문"}]
        with self.assertRaises(ValueError):
            backfill.backfill(copy.deepcopy(capture), rows, PLANS, ELIGIBILITY)
        rows[0]["question"] = capture["cases"][0]["turns"][0]["fixture"]["question"]
        result = backfill.backfill(capture, rows, PLANS, ELIGIBILITY)
        self.assertEqual("PLAN_CATALOG", service_data.plan_answer_type(result["cases"][0]["turns"][0]))
        self.assertEqual(PLANS, result["serviceData"]["plans"])


class PriorReuseTest(unittest.TestCase):
    def prior(self, capture):
        sources = pipeline.generation_sources(pipeline.enrich_rag_sources(capture, CATALOG)["cases"][0]["turns"][0])[0]
        return {"alias": "qwen", "turns": [{
            "caseId": "FAQ_TEST", "turnIndex": 0, "answer": "월 1회 바꿀 수 있습니다.", "sources": sources,
            "grounding": {"result": {"overall": "SUPPORTED", "claims": [
                {"claim": "월 1회 바꿀 수 있습니다.", "verdict": "SUPPORTED", "sourceIds": ["BILLING-0001"], "reason": ""}]}},
            "quality": {"result": {"groups": [{"groupIndex": 0, "outcome": "COMPLETE", "answerQuotes": [], "reason": ""}]}},
            "abstention": {"result": {"answerIsRefusal": False, "evidenceAnswerability": "ENOUGH"}}}]}

    def test_unchanged_turns_reuse_prior_judgment_without_calling_the_model(self):
        capture = sample_capture()
        fake = FakeClient({})
        result = run_bedrock_judge.evaluate(capture, CATALOG, "qwen", client=fake, prior=self.prior(capture),
                                            prior_capture=sample_capture())
        self.assertEqual([], fake.requests)
        self.assertEqual(1, result["reusedTurns"])
        self.assertIs(True, result["turns"][0]["answerQualityPass"])
        self.assertEqual(0, result["usage"]["calls"])
        # 새 결과에는 판정 입력 해시가 남아 다음 재사용 때는 캡처가 필요 없다
        again = run_bedrock_judge.evaluate(capture, CATALOG, "qwen", client=fake, prior=result)
        self.assertEqual(1, again["reusedTurns"])

    def test_changed_label_or_sources_are_not_reused(self):
        capture = sample_capture()
        relabeled = copy.deepcopy(capture)
        relabeled["cases"][0]["turns"][0]["fixture"]["requiredFacts"] = ["월 1회", "다음 날 적용"]
        keys = run_bedrock_judge.prior_input_keys(self.prior(capture), capture, CATALOG)
        from scripts.chat_judge.experiments.v7_cross_judge import run_cross_judge
        turn, previous, item = run_cross_judge.build_jobs(relabeled, CATALOG)[0]
        self.assertNotEqual(keys[("FAQ_TEST", 0)], run_bedrock_judge.input_key(turn, previous, item))
        with self.assertRaises(ValueError):  # 해시도 캡처도 없으면 재사용 여부를 알 수 없다
            run_bedrock_judge.prior_input_keys(self.prior(capture), None, CATALOG)
        with self.assertRaises(ValueError):
            run_bedrock_judge.evaluate(capture, CATALOG, "qwen", client=FakeClient({}), prior={"alias": "deepseek-v3.2"})


class RelabelTest(unittest.TestCase):
    def test_relabel_replaces_fixture_only_when_the_question_matches(self):
        from scripts.chat_judge import relabel_capture
        capture = sample_capture()
        fixtures = [{"caseId": "FAQ_TEST", "suite": "paraphrased_single",
                     "turns": [{**capture["cases"][0]["turns"][0]["fixture"], "answerSources": ["FAQ"]}]}]
        self.assertEqual(1, relabel_capture.relabel(capture, fixtures))
        self.assertEqual(["FAQ"], capture["cases"][0]["turns"][0]["fixture"]["answerSources"])
        self.assertEqual("paraphrased_single", capture["cases"][0]["suite"])
        fixtures[0]["turns"][0]["question"] = "다른 질문"
        with self.assertRaises(ValueError):
            relabel_capture.relabel(capture, fixtures)


class ServiceLabelSetTest(unittest.TestCase):
    def test_v2_1_adds_plan_table_labels_and_keeps_v2_reproducible(self):
        from scripts.chat_judge import build_service_quality_set as builder
        v2, _ = builder.build(service_labels=False)
        self.assertEqual(json.loads(builder.OUTPUT_V2.read_text(encoding="utf-8")), v2)
        v21 = {case["caseId"]: case for case in builder.build()[0]}
        senior = v21["V2-NOANSWER-U018"]["turns"][0]
        self.assertEqual(("ANSWER", ["PLAN_TABLE"]), (senior["expectedBehavior"], senior["answerSources"]))
        self.assertIn("5G 시니어: 5G 요금제, 시니어 대상, 월정액 49,000원",
                      senior["qualityReferenceGroups"][0]["alternatives"][0]["answer"])
        cheapest = v21["V2-COMPOUND-C010"]["turns"][0]["qualityReferenceGroups"]
        self.assertEqual(service_data.PLAN_SOURCE_ID, cheapest[1]["alternatives"][-1]["sourceId"])
        self.assertNotIn(service_data.PLAN_SOURCE_ID, [alt["sourceId"] for alt in cheapest[0]["alternatives"]])
        member = v21["V2-NOANSWER-U050"]["turns"][0]
        self.assertEqual(("ABSTAIN", "ANSWER"), (member["expectedBehavior"], member["memberExpectedBehavior"]))
        self.assertEqual(json.loads(builder.OUTPUT.read_text(encoding="utf-8")), list(v21.values()))


if __name__ == "__main__":
    unittest.main()
