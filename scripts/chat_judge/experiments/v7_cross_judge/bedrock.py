"""Bedrock Converse adapter with schema-constrained output and local semantic validation."""

import copy
import json
import time

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6


MODELS = {
    "qwen": {"region": "ap-northeast-1", "modelId": "qwen.qwen3-235b-a22b-2507-v1:0"},
    "sonnet": {"region": "ap-northeast-1", "modelId": "global.anthropic.claude-sonnet-5-5"},
    "sonnet-4-6": {"region": "ap-northeast-1", "modelId": "jp.anthropic.claude-sonnet-4-6"},
    "luna": {"region": "ap-northeast-1", "modelId": "global.openai.gpt-5.6-luna"},
    "gpt-oss-120b": {"region": "ap-northeast-1", "modelId": "openai.gpt-oss-120b-1:0"},
}

DEFAULT_MODELS = ("qwen", "sonnet", "luna")


def bedrock_schema(schema):
    """Remove unsupported Bedrock grammar constraints; Python validators remain authoritative."""
    if isinstance(schema, list):
        return [bedrock_schema(value) for value in schema]
    if not isinstance(schema, dict):
        return schema
    if "oneOf" in schema:
        branches = schema["oneOf"]
        first = copy.deepcopy(branches[0])
        if all(branch.get("type") == "object" for branch in branches):
            for key, field in first.get("properties", {}).items():
                variants = [branch.get("properties", {}).get(key, {}) for branch in branches]
                enums = [value for variant in variants for value in
                         variant.get("enum", [variant["const"]] if "const" in variant else [])]
                if enums:
                    field.pop("const", None)
                    field["enum"] = list(dict.fromkeys(enums))
                if field.get("type") == "array" and "items" not in field:
                    field["items"] = {"type": "string"}
            return bedrock_schema(first)
        raise ValueError("Unsupported oneOf shape in Judge schema")
    unsupported = {"minimum", "maximum", "multipleOf", "minLength", "maxLength", "maxItems"}
    result = {key: bedrock_schema(value) for key, value in schema.items() if key not in unsupported}
    if result.get("minItems", 0) > 1:
        result.pop("minItems")
    return result


def v7_quality_request(model, turn, previous):
    payload = v6.quality_request(model, turn, previous)
    data = json.loads(payload["messages"][1]["content"])
    data["referenceGroups"] = copy.deepcopy(turn["fixture"].get("qualityReferenceGroups", []))
    payload["messages"][0]["content"] += (
        "\nEach referenceGroups entry has questionPart and alternatives. Assess the questionPart "
        "using every validated alternative in that same group; never borrow another group's facts. "
        "questionPart labels identify what to assess and are not policy evidence. Only FAQ answer "
        "text is reference policy evidence. Alternatives may differ in extra details; require only "
        "facts responsive to the user's question."
    )
    payload["messages"][1]["content"] = judge.canonical(data)
    return payload


class BedrockJudge:
    def __init__(self, alias, profile="telme-bedrock-eval", client=None):
        if alias not in MODELS:
            raise ValueError(f"Unknown Bedrock model: {alias}")
        self.alias = alias
        self.config = MODELS[alias]
        if client is None:
            import boto3
            client = boto3.Session(profile_name=profile, region_name=self.config["region"]).client(
                "bedrock-runtime")
        self.client = client

    def stage(self, name, payload):
        schema = payload.get("format") or payload["response_format"]["json_schema"]["schema"]
        request = {
            "modelId": self.config["modelId"],
            "messages": [{"role": message["role"], "content": [{"text": message["content"]}]}
                         for message in payload["messages"] if message["role"] != "system"],
            "system": [{"text": message["content"]} for message in payload["messages"]
                       if message["role"] == "system"],
            "inferenceConfig": {"maxTokens": 4096},
            "outputConfig": {"textFormat": {"type": "json_schema", "structure": {"jsonSchema": {
                "name": name, "schema": judge.canonical(bedrock_schema(schema))}}}},
        }
        if self.alias == "qwen":
            request["inferenceConfig"]["temperature"] = 0
            if name in {"question_resolution", "faq_answerability"}:
                # Qwen's Bedrock grammar occasionally emits only whitespace until maxTokens.
                # Prompted compact JSON is validated just as strictly after the response.
                request.pop("outputConfig")
                request["inferenceConfig"]["maxTokens"] = 2048
                if name == "question_resolution":
                    data = json.loads(payload["messages"][-1]["content"])
                    count = max(1, len(data.get("referenceGroups", [])))
                    shape = {"groups": [{"groupIndex": index, "outcome": "<select verdict>",
                                         "answerQuotes": ["exact assistant-answer paragraph"],
                                         "reason": "brief reason"}
                                        for index in range(count)]}
                    instruction = ("Return compact JSON only in this exact field structure: "
                                   + json.dumps(shape, ensure_ascii=False)
                                   + ". Replace every placeholder with an actual value. Choose the correct "
                                     "outcome for each group: COMPLETE, PARTIAL, MISSED, NOT_APPLICABLE, or REVIEW. "
                                     "COMPLETE and PARTIAL require an exact assistant-answer quote. "
                                     "Use one brief reason per group and no pretty printing.")
                else:
                    data = json.loads(payload["messages"][-1]["content"])
                    count = len(data.get("questionParts") or [data["question"]])
                    shape = {"parts": [{"partIndex": index, "evidenceAnswerability": "<select verdict>",
                                        "evidenceQuotes": [{"sourceId": "actual ID",
                                                            "quote": "exact FAQ answer sentence"}],
                                        "reason": "brief reason"}
                                       for index in range(count)], "reason": "brief reason"}
                    instruction = ("Return compact JSON only in this exact field structure: "
                                   + json.dumps(shape, ensure_ascii=False)
                                   + ". Replace every placeholder with an actual value. Choose ENOUGH, "
                                     "INSUFFICIENT, or UNCERTAIN for each part. "
                                     "Every ENOUGH part must contain at least one evidenceQuotes entry "
                                     "with sourceId and an exact quote copied from that FAQ answer. "
                                     "Otherwise do not choose ENOUGH. Use brief reasons and no pretty printing.")
                request["system"][0]["text"] += "\n" + instruction
        if self.alias == "gpt-oss-120b":
            # GPT-OSS returned malformed JSON despite Bedrock's schema-constrained mode.
            # Ask for JSON explicitly and rely on the same local validators as the other judges.
            request.pop("outputConfig")
            request["inferenceConfig"].update(maxTokens=2048, temperature=0)
            request["additionalModelRequestFields"] = {"reasoning_effort": "low"}
            request["system"][0]["text"] += (
                "\nReturn exactly one compact JSON object matching this JSON Schema. "
                "Do not include markdown, analysis, or text outside the JSON object. Schema: "
                + judge.canonical(bedrock_schema(schema))
            )
        if self.alias == "sonnet-4-6":
            # Explicitly disable Claude extended thinking as requested.
            request["additionalModelRequestFields"] = {"thinking": {"type": "disabled"}}
        if self.alias == "luna":
            request["additionalModelRequestFields"] = {"text": {"format": {"strict": True}}}
        record = {"request": request, "inputSha256": judge.sha256(request),
                  "formatMode": "structured" if "outputConfig" in request else "prompt_json_validated"}
        start = time.monotonic()
        try:
            raw = self.client.converse(**request)
            record["rawResponse"] = raw
            if raw.get("stopReason") == "max_tokens" and self.alias == "qwen" and "outputConfig" in request:
                fallback = copy.deepcopy(request)
                fallback.pop("outputConfig")
                fallback["inferenceConfig"]["maxTokens"] = 2048
                if name == "claim_extraction":
                    shape = '{"claims":[{"quote":"exact assistant-answer span"}]}'
                elif name == "grounding_judgment":
                    shape = ('{"claims":[{"claim":"exact input claim","verdict":"SUPPORTED",'
                             '"sourceIds":["actual FAQ source ID"],"reason":"brief reason"}],'
                             '"overall":"SUPPORTED"}')
                else:
                    shape = '{"answerIsRefusal":false,"reason":"brief reason"}'
                fallback["system"][0]["text"] += ("\nReturn compact JSON only in this exact field "
                    "structure: " + shape + ". Preserve every input claim exactly and choose the "
                    "correct verdict. Use brief reasons and no pretty printing.")
                record["fallbackRequest"] = fallback
                record["fallbackInputSha256"] = judge.sha256(fallback)
                raw = self.client.converse(**fallback)
                record["fallbackResponse"] = raw
                record["formatMode"] = "structured_then_prompt_fallback"
            if raw.get("stopReason") != "end_turn":
                raise ValueError(f"Incomplete Bedrock output: {raw.get('stopReason')}")
            content = "".join(block.get("text", "") for block in raw["output"]["message"]["content"])
            record["result"] = json.loads(content)
            usage = raw.get("usage") or {}
            record["promptTokens"] = usage.get("inputTokens")
            record["outputTokens"] = usage.get("outputTokens")
        except Exception as error:
            record["error"] = f"{type(error).__name__}: {error}"
        record["durationMs"] = round((time.monotonic() - start) * 1000)
        return record

    def grounding(self, data):
        extraction = self.stage("claim_extraction", judge.claim_extraction_request(
            self.config["modelId"], data.get("answer", "")))
        record = {"claimExtraction": extraction}
        if "result" not in extraction:
            record["error"] = extraction["error"]
            return record
        try:
            claims = judge.validate_claim_extraction(extraction["result"], data.get("answer", ""))
        except (ValueError, KeyError, TypeError) as error:
            record["error"] = f"{type(error).__name__}: {error}"
            return record
        payload = judge.judge_request(self.config["modelId"], "grounding",
                                      judge.grounding_claim_data(data, claims))
        semantic = self.stage("grounding_judgment", payload)
        record["semantic"] = semantic
        if "result" not in semantic:
            record["error"] = semantic["error"]
            return record
        try:
            record["result"] = judge.validate_result("grounding", semantic["result"],
                {source["sourceId"] for source in data["sources"]},
                data.get("confirmedConditions"), answer=data.get("answer"),
                sources=data["sources"], expected_claims=claims)
        except (ValueError, KeyError, TypeError) as error:
            record["error"] = f"{type(error).__name__}: {error}"
        return record

    def quality(self, turn, previous):
        stage = self.stage("question_resolution", v7_quality_request(self.config["modelId"], turn, previous))
        if "result" in stage:
            try:
                stage["result"] = v6.validate_quality(stage["result"], turn)
            except (ValueError, KeyError, TypeError) as error:
                stage.pop("result")
                stage["error"] = f"{type(error).__name__}: {error}"
        return stage

    def abstention(self, data):
        refusal_input = {key: data[key] for key in ("question", "previousTurns", "answer") if key in data}
        evidence_input = {key: data[key] for key in
                          ("question", "questionParts", "previousTurns", "sources", "confirmedConditions")
                          if key in data}
        evidence_input["questionParts"] = evidence_input.get("questionParts") or [data["question"]]
        evidence_input["sources"] = [{key: source[key] for key in ("sourceId", "answer") if key in source}
                                     for source in evidence_input.get("sources", [])]
        def payload(name, rubric, schema, value):
            return {"model": self.config["modelId"], "messages": [
                {"role": "system", "content": rubric}, {"role": "user", "content": judge.canonical(value)}],
                "response_format": {"json_schema": {"schema": schema}}}
        refusal = self.stage("answer_refusal", payload("answer_refusal", v6.REFUSAL_RUBRIC,
                              v6.REFUSAL_SCHEMA, refusal_input))
        evidence_schema = copy.deepcopy(v6.EVIDENCE_SCHEMA)
        citation = evidence_schema["properties"]["parts"]["items"]["properties"]["evidenceQuotes"]["items"]
        if evidence_input["sources"]:
            citation["properties"]["sourceId"]["enum"] = [source["sourceId"]
                                                             for source in evidence_input["sources"]]
            allowed_quotes = list(dict.fromkeys(line.strip() for source in evidence_input["sources"]
                for line in source["answer"].splitlines() if line.strip()))
            if allowed_quotes:
                citation["properties"]["quote"]["enum"] = allowed_quotes
        evidence_rubric = (v6.EVIDENCE_RUBRIC + "\nIf a part is ENOUGH, provide at least one exact "
                           "FAQ answer quote. If no exact supporting quote can be selected, use UNCERTAIN.")
        evidence = self.stage("faq_answerability", payload("faq_answerability", evidence_rubric,
                               evidence_schema, evidence_input))
        record = {"stages": {"refusal": refusal, "evidence": evidence}}
        if "result" not in refusal or "result" not in evidence:
            record["error"] = "; ".join(stage.get("error", "") for stage in (refusal, evidence) if "error" in stage)
            return record
        try:
            answer = refusal["result"]
            parts = copy.deepcopy(evidence["result"]["parts"])
            if (set(answer) != {"answerIsRefusal", "reason"} or type(answer["answerIsRefusal"]) is not bool
                    or not isinstance(answer["reason"], str)
                    or set(evidence["result"]) != {"parts", "reason"}
                    or len(parts) != len(evidence_input["questionParts"])):
                raise ValueError("Invalid refusal or evidence output")
            statuses, quotes = [], []
            sources = {source["sourceId"]: source["answer"] for source in evidence_input["sources"]}
            for index, part in enumerate(parts):
                if (isinstance(part, dict) and "evidenceQuotes" not in part
                        and part.get("evidenceAnswerability") in {"INSUFFICIENT", "UNCERTAIN"}):
                    part["evidenceQuotes"] = []
                if (set(part) != {"partIndex", "evidenceAnswerability", "evidenceQuotes", "reason"}
                        or type(part["partIndex"]) is not int or part["partIndex"] != index
                        or part["evidenceAnswerability"] not in {"ENOUGH", "INSUFFICIENT", "UNCERTAIN"}
                        or not isinstance(part["reason"], str)
                        or not isinstance(part["evidenceQuotes"], list)):
                    raise ValueError("Invalid ordered evidence part")
                if part["evidenceAnswerability"] == "ENOUGH" and not part["evidenceQuotes"]:
                    raise ValueError("ENOUGH part lacks a FAQ quote")
                for citation in part["evidenceQuotes"]:
                    if (not isinstance(citation, dict) or set(citation) != {"sourceId", "quote"}
                            or citation["sourceId"] not in sources or not isinstance(citation["quote"], str)
                            or not citation["quote"].strip() or citation["quote"] not in sources[citation["sourceId"]]):
                        raise ValueError("Evidence quote does not appear in its FAQ answer")
                statuses.append(part["evidenceAnswerability"])
                quotes.extend(part["evidenceQuotes"])
            overall = ("INSUFFICIENT" if "INSUFFICIENT" in statuses else
                       "UNCERTAIN" if "UNCERTAIN" in statuses else "ENOUGH")
            merged = {"answerIsRefusal": answer["answerIsRefusal"], "evidenceAnswerability": overall,
                      "evidenceQuotes": quotes, "reason": evidence["result"]["reason"]}
            record["result"] = judge.validate_result("abstention", merged, set(sources),
                                                      sources=evidence_input["sources"])
            record["questionParts"] = parts
        except (ValueError, KeyError, TypeError) as error:
            record["error"] = f"{type(error).__name__}: {error}"
        return record
