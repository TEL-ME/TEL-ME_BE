#!/bin/bash
export AWS_BEARER_TOKEN_BEDROCK=<.env에서 읽음, 저장하지 않음>
cd /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/125a7a71-09b3-41f1-be68-b3952a85e7ae/scratchpad/telme186-snapshot
for set in a b; do
  date +%s > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/gstart-$set.txt
  ANSWER_PROBE=true ANSWER_PROBE_INPUT=/Users/choib/claude-book/TELME/TEL-ME_BE/scripts/answer-generation-evaluation/inputs-$set.json ANSWER_PROBE_OUT=/private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/answers-$set-gptoss.jsonl \
  ANSWER_PROBE_MODELS=gptoss ANSWER_PROBE_GPTOSS_REPEATS=3 LLM_BEDROCK_REGION=ap-northeast-1 ANSWER_PROBE_MAX_TOKENS=1000 ANSWER_PROBE_TIMEOUT_SECONDS=60 \
  ./gradlew test --tests '*AnswerGenerationProbe' --rerun-tasks > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/gptoss-gradle-$set.log 2>&1
  echo $? > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/gexit-$set.txt
  date +%s > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/gend-$set.txt
done
