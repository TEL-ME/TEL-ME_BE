#!/bin/bash
cd /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/125a7a71-09b3-41f1-be68-b3952a85e7ae/scratchpad/telme186-snapshot
for set in a b; do
  date +%s > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/start-$set.txt
  ANSWER_PROBE=true ANSWER_PROBE_INPUT=/Users/choib/claude-book/TELME/TEL-ME_BE/scripts/answer-generation-evaluation/inputs-$set.json ANSWER_PROBE_OUT=/private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/answers-$set-exaone.jsonl \
  ANSWER_PROBE_MODELS=exaone ANSWER_PROBE_EXAONE_REPEATS=3 \
  ANSWER_PROBE_OLLAMA_URL=http://127.0.0.1:11437 ANSWER_PROBE_OLLAMA_MODEL=exaone3.5:7.8b \
  ./gradlew test --tests '*AnswerGenerationProbe' --rerun-tasks -i > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/gradle-$set.log 2>&1
  echo $? > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/exit-$set.txt
  date +%s > /private/tmp/claude-501/-Users-choib-claude-book-TELME-TEL-ME-BE/a41328bb-995a-4eee-af45-06eb49f1161b/scratchpad/run/end-$set.txt
done
