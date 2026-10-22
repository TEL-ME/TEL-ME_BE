"""FAQ 밖 서비스 데이터 근거, 앱 고정 문구 규칙, 예전 캡처 보강, 이전 판정 재사용 테스트."""

import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from scripts.chat_judge import backfill_capture_service_data as backfill
from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge import run_bedrock_judge
from scripts.chat_judge import service_data
from scripts.chat_judge import evaluate_chat_pipeline as pipeline
from scripts.chat_judge.test_bedrock import FakeClient
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
        for sentence in service_data.SERVICE_GUIDANCE + service_data.SYSTEM_FALLBACKS + (
                service_data.NO_SEARCH_RESULT, service_data.NO_EVIDENCE):
            self.assertIn(sentence, java)

    def test_plan_task_login_guidance_matches_java_composition(self):
        java = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "src/main/java").rglob("*.java"))
        self.assertIn(f'LOGIN_REQUIRED = "{service_data.PLAN_TASK_LOGIN_REQUIRED}";', java)
        for prefix in service_data.PLAN_TASK_PREFIXES:
            self.assertIn(f'"{prefix}" + PlanTaskSupport.LOGIN_REQUIRED', java)
        self.assertTrue(service_data.is_service_guidance("요금제 변경은 로그인한 뒤 마이페이지의 내 요금제 화면에서 할 수 있어요."))
        self.assertFalse(service_data.is_service_guidance("요금제 변경은 매장에서만 할 수 있어요."))

    def test_recommendation_rule_matches_java_constants(self):
        recommender = (ROOT / "src/main/java/com/telme/consult/service/PlanRecommender.java").read_text(encoding="utf-8")
        answerer = (ROOT / "src/main/java/com/telme/consult/service/MemberRecommendationAnswerer.java").read_text(
            encoding="utf-8")
        self.assertIn(f"MARGIN = {service_data.RECOMMENDATION_MARGIN};", recommender)
        self.assertIn(f"DOWNGRADE_MARGIN = {service_data.DOWNGRADE_MARGIN};", recommender)
        self.assertIn(f"MAX_CHEAPER = {service_data.MAX_CHEAPER};", answerer)
        self.assertIn(f"COOLDOWN_MONTHS = {service_data.COOLDOWN_MONTHS};", answerer)

    def test_recommendation_rule_states_whether_the_current_plan_fits(self):
        member = {"subscription": {"subscription": {"currentPlan": {
            "name": "5G 스탠다드", "dataGb": 50.0, "unlimited": False, "voiceUnlimited": True, "smsUnlimited": True}}},
            "usageDemand": {"dataMb": 29209, "voiceMinutes": 370, "smsCount": 241, "months": 3}}
        self.assertTrue(service_data.recommendation_rule_source(member)["answer"].endswith("평균 사용량에 알맞다."))
        member["usageDemand"]["dataMb"] = 45000
        self.assertIn("알맞지 않다(데이터 부족)", service_data.recommendation_rule_source(member)["answer"])

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
        turn, previous, item = run_bedrock_judge.build_jobs(relabeled, CATALOG)[0]
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
    def test_v2_2_adds_verified_expansion_on_top_of_v2_1(self):
        from scripts.chat_judge import build_service_quality_set as builder
        v21 = builder.build()[0]
        v22, excluded = builder.build(expansion=True)
        self.assertEqual(json.loads(builder.OUTPUT.read_text(encoding="utf-8")), v22)
        self.assertEqual(v21, v22[:len(v21)])
        added = v22[len(v21):]
        partial = [case for case in added if case["suite"] == "paraphrased_partial"]
        for case in partial:
            turn = case["turns"][0]
            self.assertEqual("PARTIAL_ANSWER", turn["expectedBehavior"])
            self.assertTrue(turn["requiredFacts"][-1].endswith("확인할 수 없다"))
            self.assertIn(turn["qualityReferenceGroups"][0]["questionPart"], turn["question"])
        for case in added:
            for group in case["turns"][0]["qualityReferenceGroups"]:
                self.assertIn(group["questionPart"], case["turns"][0]["question"])
                self.assertTrue(group["alternatives"])

    def test_v2_1_adds_plan_table_labels_and_keeps_v2_reproducible(self):
        from scripts.chat_judge import build_service_quality_set as builder
        # 이전 완성본 파일은 두지 않는다. v2는 서비스 데이터 라벨이 없고, v2.2는 v2.1 뒤에 문항을 더한다
        v2, _ = builder.build(service_labels=False)
        self.assertEqual(238, len(v2))
        self.assertFalse(any("answerSources" in turn for case in v2 for turn in case["turns"]))
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
        self.assertEqual(list(v21.values()), builder.build(expansion=True)[0][:len(v21)])


class FlowTest(unittest.TestCase):
    def clarified_turn(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["clarificationExchanges"] = [
            {"outputMessage": {"content": "어떤 업무로 매장을 찾으시나요?", "messageType": "CLARIFICATION"},
             "customerReply": {"whenKey": "serviceType", "content": "유심 재발급이요"}},
            {"outputMessage": {"content": "어느 지역의 매장을 찾으시나요?", "messageType": "CLARIFICATION"},
             "customerReply": {"whenKey": "location", "content": "현재 위치로 찾아주세요"}}]
        return turn

    def test_judge_sees_the_clarification_dialogue_and_the_last_customer_reply(self):
        turn = self.clarified_turn()
        earlier = [{"question": "앞 질문", "answer": "앞 답"}]
        data = judge.prompt_data(turn, earlier, "grounding")
        self.assertEqual("현재 위치로 찾아주세요", data["question"])
        self.assertEqual(["앞 질문", turn["fixture"]["question"], "유심 재발급이요"],
                         [row["question"] for row in data["previousTurns"]])
        self.assertEqual("어느 지역의 매장을 찾으시나요?", data["previousTurns"][-1]["answer"])
        following = judge.completed_dialogue(turn, "최종 답")
        self.assertEqual(("현재 위치로 찾아주세요", "최종 답"), (following[-1]["question"], following[-1]["answer"]))
        plain = sample_capture()["cases"][0]["turns"][0]
        self.assertEqual([], judge.clarification_dialogue(plain)[0])

    def store_turn(self, services):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"].update(expectedBehavior="STORE_LOOKUP", expectedServiceType="USIM_REISSUE")
        turn["outputMessage"] = {"messageType": "STORE_RESULT", "content": "현재 위치 기준으로 찾은 매장이에요.",
                                 "storeResults": [{"storeId": 1, "name": "텔미 강남점", "address": "서울 강남구",
                                                   "phone": "02", "distanceMeters": 300}],
                                 "storeSearchContext": {"label": "현재 위치"}}
        turn["storeDetails"] = [{"storeId": 1, "services": services,
                                 "hours": [{"day_of_week": 6, "open_time": "10:00:00", "close_time": "15:00:00",
                                            "closed": False}]}]
        return turn

    def test_store_lookup_is_judged_by_the_returned_stores(self):
        self.assertEqual("STORE_RESULTS_RETURNED", pipeline.rule_quality(self.store_turn(["USIM_REISSUE"]))["rule"])
        mismatch = pipeline.rule_quality(self.store_turn(["NEW_LINE"]))
        self.assertEqual(("STORE_SERVICE_MISMATCH", "MISSED"), (mismatch["rule"], mismatch["result"]["groups"][0]["outcome"]))
        missing = self.store_turn([])
        missing["outputMessage"] = {"messageType": "CLARIFICATION", "content": "어느 지역이세요?"}
        self.assertEqual("STORE_NOT_RETURNED", pipeline.rule_quality(missing)["rule"])
        capture = sample_capture()
        capture["cases"][0]["turns"][0] = self.store_turn(["USIM_REISSUE"])
        source = service_data.attach_service_sources(capture)["cases"][0]["turns"][0]["serviceSources"][0]
        self.assertEqual(service_data.STORE_SOURCE_ID, source["sourceId"])
        self.assertIn("텔미 강남점: 주소 서울 강남구, 전화 02, 거리 300m, 취급 업무 USIM_REISSUE, 영업시간 토 10:00~15:00",
                      source["answer"])

    def test_member_answers_get_account_data_and_reference_placeholders_are_filled(self):
        capture = plan_capture("가입하신 요금제는 5G 스탠다드예요.")
        case = capture["cases"][0]
        turn = case["turns"][0]
        turn["pipelineTrace"] = {"planAnswer": {"type": "MY_SUBSCRIPTION"}}
        turn["fixture"]["qualityReferenceGroups"] = [{"questionPart": "내 요금제", "alternatives": [
            {"sourceId": service_data.MEMBER_SOURCE_ID}]}]
        case.update(actor="MEMBER", memberData={"subscription": {"subscribed": True, "subscription": {
            "plan": {"name": "5G 스탠다드"}}}, "terminationPenalty": {"penalty": 120000}, "usage": None})
        enriched = pipeline.enrich_rag_sources(capture, CATALOG)["cases"][0]["turns"][0]
        self.assertEqual([service_data.MEMBER_SOURCE_ID], [row["sourceId"] for row in enriched["serviceSources"]])
        self.assertIn("subscription.subscription.plan.name: 5G 스탠다드", enriched["serviceSources"][0]["answer"])
        self.assertIn("terminationPenalty.penalty: 120000",
                      pipeline.reference_groups(enriched["fixture"])[0]["alternatives"][0]["answer"])
        guest = plan_capture("로그인하시면 가입하신 요금제를 확인해 드릴 수 있어요.")
        guest["cases"][0]["turns"][0]["fixture"]["qualityReferenceGroups"] = [
            {"questionPart": "내 요금제", "alternatives": [{"sourceId": service_data.MEMBER_SOURCE_ID}]}]
        with self.assertRaises(ValueError):  # 회원 데이터 없이 회원 라벨을 판정하지 않는다
            pipeline.enrich_rag_sources(guest, CATALOG)

    def test_store_results_must_use_the_location_the_customer_gave(self):
        turn = self.store_turn(["USIM_REISSUE"])
        turn["clarificationExchanges"] = [{"askedKeys": ["location"], "outputMessage": {"content": "어디세요?"},
                                           "customerReply": {"content": "현재 위치로", "coordinates": {"latitude": 37.5,
                                                                                                    "longitude": 127.0}}}]
        self.assertEqual("STORE_LOCATION_MISMATCH", pipeline.rule_quality(turn)["rule"])
        turn["outputMessage"]["storeSearchContext"] = {"type": "CURRENT_LOCATION", "label": "현재 위치"}
        self.assertEqual("STORE_RESULTS_RETURNED", pipeline.rule_quality(turn)["rule"])

    def test_refusal_template_on_unanswerable_question_is_decided_by_code(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"]["expectedBehavior"] = "ABSTAIN"
        for content in (service_data.NO_SEARCH_RESULT, service_data.NO_EVIDENCE + "."):
            turn["outputMessage"] = {"messageType": "ANSWER", "content": content}
            rule = pipeline.rule_quality(turn)
            self.assertEqual(("ABSTAIN_REFUSAL_TEMPLATE", "NOT_APPLICABLE"),
                             (rule["rule"], rule["result"]["groups"][0]["outcome"]))
        turn["outputMessage"]["content"] = "1. 첫 질문은 " + service_data.NO_EVIDENCE + ". 2. 둘째는 가능합니다."
        self.assertIsNone(pipeline.rule_quality(turn))  # 일부만 거절한 답은 모델이 본다
        turn["fixture"]["expectedBehavior"] = "ANSWER"
        turn["outputMessage"]["content"] = service_data.NO_EVIDENCE
        self.assertIsNone(pipeline.rule_quality(turn))  # 답이 있는 질문의 거절은 모델이 미답으로 본다

    def test_direct_answer_facts_apply_only_when_the_app_did_not_ask(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["fixture"].update(requiredFacts=["대리인은 위임장이 필요하다"],
                               requiredFactsIfAnsweredDirectly=["본인은 신분증만", "대리인은 위임장이 필요하다"])
        self.assertEqual(["본인은 신분증만", "대리인은 위임장이 필요하다"], pipeline.required_facts(turn))
        turn["clarificationExchanges"] = [{"outputMessage": {"content": "누가 가세요?"},
                                           "customerReply": {"content": "딸이 가요"}}]
        self.assertEqual(["대리인은 위임장이 필요하다"], pipeline.required_facts(turn))

    def test_asking_an_answered_condition_again_fails_even_with_a_good_final_answer(self):
        turn = self.store_turn(["USIM_REISSUE"])
        turn["clarificationExchanges"] = [
            {"askedKeys": ["location"], "outputMessage": {"content": "어느 지역이세요?"},
             "customerReply": {"whenKey": "location", "content": "현재 위치로"}},
            {"askedKeys": ["serviceType"], "outputMessage": {"content": "어떤 업무세요?"},
             "customerReply": {"whenKey": "serviceType", "content": "유심 재발급"}},
            {"askedKeys": ["location"], "outputMessage": {"content": "어느 지역이세요?"},
             "customerReply": {"whenKey": "location", "content": "현재 위치로"}}]
        item = {"grounding": {"result": {"overall": "NOT_APPLICABLE", "claims": []}},
                "quality": pipeline.rule_quality(turn),
                "abstention": {"result": {"answerIsRefusal": False, "evidenceAnswerability": "INSUFFICIENT"}}}
        pipeline.finalize_turn(turn, item)
        self.assertIs(False, item["answerQualityPass"])
        self.assertIn("REASKED_CONDITION", item["findings"])

    def test_unscripted_clarification_goes_to_human_review(self):
        turn = sample_capture()["cases"][0]["turns"][0]
        turn["outputMessage"] = {"messageType": "CLARIFICATION", "content": "약정 개월 수가 어떻게 되세요?"}
        turn["unansweredClarificationKeys"] = ["contract_months"]
        item = {"grounding": {"result": {"overall": "NOT_APPLICABLE", "claims": []}},
                "quality": pipeline.rule_quality(turn),
                "abstention": {"result": {"answerIsRefusal": False, "evidenceAnswerability": "INSUFFICIENT"}}}
        pipeline.finalize_turn(turn, item)
        self.assertIsNone(item["answerQualityPass"])
        self.assertIn("UNSCRIPTED_CLARIFICATION", item["findings"])

    def test_flow_set_maps_current_location_to_coordinates_and_labels_references(self):
        from scripts.chat_judge import build_service_flow_set as builder
        cases = {case["caseId"]: case for case in builder.build()}
        self.assertEqual(json.loads(builder.OUTPUT.read_text(encoding="utf-8")), list(cases.values()))
        for case in cases.values():
            for turn in case["turns"]:
                for reply in turn["clarificationReplies"]:
                    if reply.get("currentLocation"):
                        self.assertEqual(builder.CURRENT_LOCATIONS[reply["currentLocation"]],
                                         (reply["coordinates"]["latitude"], reply["coordinates"]["longitude"]))
                if case["suite"] == "member_data" and turn["expectedBehavior"] == "ANSWER":
                    self.assertEqual([{"sourceId": service_data.MEMBER_SOURCE_ID}],
                                     turn["qualityReferenceGroups"][0]["alternatives"])
                    self.assertEqual("MEMBER", case["actor"])
                if turn["expectedBehavior"] == "STORE_LOOKUP":
                    self.assertEqual(([], ["STORE"]), (turn["qualityReferenceGroups"], turn["answerSources"]))
                    # 앱이 업무를 되물으면 답할 말이 늘 있다(시나리오에 없으면 기대 업무로 채운 auto 답변)
                    self.assertTrue(any(reply["whenKey"] == "serviceType" for reply in turn["clarificationReplies"]))


class FaqQuestionTextTest(unittest.TestCase):
    def test_judge_never_receives_faq_question_text(self):
        # FAQ 질문 문장은 정책 근거가 아니다. 상황 맥락으로 넘겨 봤더니 근거로 새어 들어가 되돌렸다
        source = {"sourceId": "TERMINATE-0115", "faqId": 1, "question": "해지하는데 할부 한 번에 내는 게 나아요",
                  "answer": "원칙은 한 번에 내는 겁니다. 부담되시면 계속 나눠 내는 것도 됩니다."}
        data = judge.grounding_claim_data({"question": "요금 나눠 내도 돼요?", "sources": [source]}, ["나눠 내도 됩니다."])
        self.assertEqual({"sourceId": "TERMINATE-0115", "answer": source["answer"]}, data["sources"][0])
        self.assertNotIn("situation", judge.judge_request("m", "grounding", data)["messages"][-1]["content"])
        seen = {}

        def stage(url, model, name, rubric, schema, value, timeout):
            seen[name] = value
            return {"request": {"name": name}, "error": "not needed"}
        with patch.object(pipeline, "abstention_stage", side_effect=stage):
            pipeline.abstention_chat("http://unused", "m", {"question": "q", "answer": "a", "sources": [source]}, 5)
        self.assertEqual([{"sourceId": "TERMINATE-0115", "answer": source["answer"]}],
                         seen["faq_answerability"]["sources"])

    def test_validation_capture_fills_faq_question_from_catalog(self):
        from scripts.chat_judge import run_judge_validation as validation
        item = {"id": "X", "category": "c", "stratum": "s", "question": "q", "previousTurns": [], "answer": "a",
                "messageType": "ANSWER", "expectedBehavior": "ANSWER", "referenceGroups": [],
                "deliveredSources": [{"sourceId": "BILLING-0001", "answer": "월 1회 바꿀 수 있습니다."}]}
        capture = validation.to_capture([item], CATALOG)
        self.assertEqual(CATALOG[0]["question"], capture["cases"][0]["turns"][0]["ragInputs"][0]["searchResults"][0]["question"])


if __name__ == "__main__":
    unittest.main()
