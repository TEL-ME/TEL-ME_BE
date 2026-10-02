package com.telme.probe;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.config.LlmConfig;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.config.LlmRetryProperties;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.service.*;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.exception.AnswerGuardException;
import com.telme.rag.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/** Opt-in local Ollama replay; no Spring context, search, database or Judge invocation. */
@EnabledIfEnvironmentVariable(named = "TELME_PROMPT_COMPARE", matches = "true")
class PromptRelationComparisonProbe {
    static final Path RESOURCES = Path.of("src/test/resources/rag/prompt-relations");
    static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test void compareLocalPrompts() throws Exception {
        String url = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("LLM_MODEL", "exaone3.5:7.8b");
        int context = Integer.parseInt(System.getenv().getOrDefault("LLM_CONTEXT_SIZE", "8192"));
        boolean evidenceCheck = Boolean.parseBoolean(System.getenv().getOrDefault("RAG_EVIDENCE_CHECK_ENABLED", "false"));
        LlmProperties props = new LlmProperties(model, Duration.ofSeconds(5), Duration.ofSeconds(60), context);
        RestClient http = new LlmConfig().ollamaRestClient(RestClient.builder(), props, url);
        String digest = modelDigest(http.get().uri("/api/tags").retrieve().body(String.class), model);
        Path replay = Path.of(System.getenv().getOrDefault("TELME_PROMPT_REPLAY", RESOURCES.resolve("replay-cases.json").toString()));
        List<JsonNode> cases = loadCases(replay);
        String baseline = Files.readString(RESOURCES.resolve("rag-answer-v3.txt"));
        String candidate = AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT;
        Set<String> selected = selectedIds(cases, System.getenv("TELME_PROMPT_EVAL_IDS"));
        Path out = Path.of(System.getenv().getOrDefault("TELME_PROMPT_OUT", ".measure/prompt-relations"))
                .resolve("pair-" + java.time.LocalDateTime.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0,8));
        Files.createDirectories(out);
        String revision = new String(new ProcessBuilder("git", "rev-parse", "HEAD").start().getInputStream().readAllBytes()).strip();
        Map<String,Object> fixed = new LinkedHashMap<>();
        fixed.put("code_revision", revision); fixed.put("generator_model", model); fixed.put("model_digest", digest);
        fixed.put("ollama_url", url); fixed.put("context_size", context); fixed.put("temperature", 0.0); fixed.put("max_tokens",1024);
        fixed.put("retry_max_attempts",2); fixed.put("retry_wait_ms",1000); fixed.put("evidence_check_enabled",evidenceCheck);
        fixed.put("evidence_check_max_tokens",300); fixed.put("replay_sha256",hash(Files.readAllBytes(replay)));
        fixed.put("judge", Map.of("status","NOT_RUN","reason","no paid calls; team to record actual Judge version/settings separately"));
        Map<String,String> code = new LinkedHashMap<>();
        for(String name:List.of("rag/service/AnswerGuard.java","rag/service/RagAnswerGenerator.java",
                "rag/service/EvidenceRelevanceChecker.java","rag/service/EvidenceRelevancePromptTemplates.java",
                "rag/converter/AnswerContextConverter.java","llm/converter/OllamaRequestConverter.java"))
            code.put(name,hash(Files.readAllBytes(Path.of("src/main/java/com/telme/"+name))));
        fixed.put("fixed_code_sha256",code);
        fixed.put("probe_sha256",hash(Files.readAllBytes(Path.of("src/test/java/com/telme/probe/PromptRelationComparisonProbe.java"))));
        fixed.put("guard_sha256",code.get("rag/service/AnswerGuard.java"));
        fixed.put("comparison_kind","PROMPT_ONLY"); fixed.put("requested_eval_ids",cases.stream().map(c->c.path("eval_id").asText()).toList());
        fixed.put("selected_eval_ids",selected);
        LlmGenerationRecorder recorder=mock(LlmGenerationRecorder.class);
        LlmClient client=new RetryingLlmClient(new RecordingLlmClient(
                new OllamaClient(http,new OllamaRequestConverter(props),MAPPER),recorder,model),
                new LlmRetryProperties(2,Duration.ofSeconds(1)));
        Map<String,Map<String,Object>> before=new LinkedHashMap<>(), after=new LinkedHashMap<>();
        for(JsonNode c:cases) {
            String id=c.path("eval_id").asText();
            before.put(id,pending(c,selected.contains(id))); after.put(id,pending(c,selected.contains(id)));
        }
        try {
            int index=0;
            for(JsonNode c:cases) {
                if(!selected.contains(c.path("eval_id").asText())) continue;
                // Alternate first side to expose, rather than hide, cold-start/order effects.
                for(String side:index++%2==0?List.of("before","after"):List.of("after","before")) {
                    Map<String,Object> row=runCase(c,client,side.equals("before")?baseline:candidate,
                            side.equals("before")?"rag-answer-v3":AnswerPromptTemplates.PROMPT_VERSION,evidenceCheck);
                    row.put("order_in_pair",side.equals(index%2==1?"before":"after")?1:2);
                    row.put("run_id",out.getFileName()+"-"+side); row.put("comparison_id",out.getFileName().toString());
                    row.put("generator","ollama"); row.put("generator_model",model);
                    row.put("fixed_conditions",fixed);
                    (side.equals("before")?before:after).put(c.path("eval_id").asText(),row);
                    persist(out,before,after,fixed);
                }
            }
            fixed.put("model_digest_after",modelDigest(http.get().uri("/api/tags").retrieve().body(String.class),model));
            fixed.put("model_digest_unchanged",digest.equals(fixed.get("model_digest_after")));
        } finally { persist(out,before,after,fixed); }
        System.out.println("[Prompt replay] results: "+out.toAbsolutePath());
    }

    static Map<String,Object> runCase(JsonNode c,LlmClient client,String prompt,String version,boolean evidenceCheck) {
        Observation o=new Observation();
        LlmClient observed=new LlmClient() {
            public String generate(LlmRequest r) {
                try {
                    o.precheck=client.generate(r);
                    return o.precheck;
                } catch(RuntimeException e) { o.precheckError=e.getClass().getSimpleName(); throw e; }
            }
            public void stream(LlmRequest r,LlmStreamHandler handler) {
                o.requested=true;
                LlmRequest changed=LlmRequest.builder().executionId(r.executionId()).taskType(r.taskType())
                    .systemPrompt(prompt).userPrompt(r.userPrompt()).format(r.format()).temperature(r.temperature())
                    .maxTokens(r.maxTokens()).contextCount(r.contextCount()).promptVersion(version).build();
                o.actualRequest=changed;
                client.stream(changed,new LlmStreamHandler() {
                    public void onToken(String token) { o.raw.append(token); handler.onToken(token); }
                    public void onComplete() { o.completed=true; handler.onComplete(); }
                    public void onError(Throwable e) { handler.onError(e); }
                    public void onRetry(int attempt,Throwable cause) {
                        o.retries.add(Map.of("attempt",attempt,"cause",cause.getClass().getName()));
                        o.raw.setLength(0); o.completed=false; handler.onRetry(attempt,cause);
                    }
                });
            }
        };
        AnswerGuard guard=new AnswerGuard() {
            @Override public String applyEvidencePolicy(String raw,String evidence,String question) {
                o.guardApplied=true;
                try { o.guardFinal=super.applyEvidencePolicy(raw,evidence,question); return o.guardFinal; }
                catch(RuntimeException e) { o.guardError=e.getClass().getSimpleName()+": "+e.getMessage(); throw e; }
            }
        };
        RagAnswerGenerator generator=new RagAnswerGenerator(observed,new AnswerContextConverter(),guard,
                new EvidenceRelevanceChecker(observed,MAPPER,new EvidenceCheckProperties(evidenceCheck,300)),
                mock(LlmGenerationRecorder.class));
        Map<String,Object> row=pending(c,true);
        row.put("prompt_version",version); row.put("prompt_sha256",hash(prompt.getBytes(StandardCharsets.UTF_8)));
        long start=System.nanoTime();
        try {
            var result=generator.generate(AnswerRequest.builder().userQuery(c.path("question").asText())
                    .searchResults(sources(c)).build(),new LlmStreamHandler() {
                        public void onToken(String t) {} public void onComplete() {} public void onError(Throwable e) {}
                    });
            row.put("answer",result.answer()); row.put("status",c.path("sources").isEmpty()?"NO_SEARCH":result.answerBasis().name());
        } catch(RuntimeException e) {
            row.put("status",e instanceof AnswerGuardException?"BLOCKED":e instanceof GeneralException ge
                && ge.getErrorCode()==LlmErrorCode.TIMEOUT?"TIMEOUT":"GENERATION_ERROR");
            row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());
        }
        row.put("elapsed_ms",(System.nanoTime()-start)/1_000_000.0);
        row.put("raw_state",!o.requested?"NOT_GENERATED":!o.completed?"INCOMPLETE":o.raw.toString().isBlank()?"GENERATED_EMPTY":"AVAILABLE");
        row.put("raw_answer",o.completed?o.raw.toString():null);
        row.put("partial_raw",o.requested&&!o.completed?o.raw.toString():null);
        String stage=c.path("sources").isEmpty()?"NO_SEARCH":!o.requested&&o.precheckError!=null?"PRECHECK_ERROR"
            :!o.requested&&o.precheck!=null?"PRECHECK_REJECTED"
            :!o.requested?"NOT_GENERATED":o.guardError!=null?"GUARD_BLOCKED":!o.completed?"MODEL_ERROR"
            :isRefusal(o.raw.toString())?"MODEL_REFUSAL"
            :mentionsRefusal(o.raw.toString())?"UNCONFIRMED_MIXED_REFUSAL"
            :isRefusal(o.guardFinal)?"GUARD_REPLACED"
            :mentionsRefusal(o.guardFinal)?"UNCONFIRMED_MIXED_REFUSAL":"ANSWERED";
        row.put("observed_stage",stage);
        row.put("precheck_response",o.precheck); row.put("precheck_error",o.precheckError); row.put("retry_events",o.retries);
        Map<String,Object> g=new LinkedHashMap<>(); g.put("applied",o.guardApplied); g.put("reason",o.guardError);
        g.put("outcome",!o.guardApplied?"NOT_RUN":o.guardError!=null?"BLOCKED":Objects.equals(o.raw.toString(),o.guardFinal)?"KEPT":"CHANGED");
        g.put("rule_id","UNAVAILABLE"); row.put("guard",g);
        return row;
    }

    static List<JsonNode> loadCases(Path file) throws Exception {
        JsonNode root=MAPPER.readTree(file.toFile());
        if(!root.isArray()) throw new IllegalArgumentException("replay array required");
        List<JsonNode> rows=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(JsonNode row:root) {
            if(!row.path("eval_id").isTextual()||row.path("eval_id").asText().isBlank()||!ids.add(row.path("eval_id").asText())
                ||!row.path("question").isTextual()||row.path("question").asText().isBlank()||!row.path("sources").isArray())
                throw new IllegalArgumentException("invalid/duplicate replay row");
            sources(row); rows.add(row);
        }
        return rows;
    }
    static List<FaqSearchResponse> sources(JsonNode row) {
        List<FaqSearchResponse> found=new ArrayList<>(); int index=0;
        for(JsonNode s:row.path("sources")) {
            String where=row.path("eval_id").asText()+" sources["+index+++"]";
            if(!s.path("question").isTextual()||s.path("question").asText().isBlank()||!s.path("answer").isTextual()
                ||s.path("answer").asText().isBlank()||!s.path("score").isNumber()||!Double.isFinite(s.path("score").asDouble()))
                throw new IllegalArgumentException(where+" invalid FAQ text/score");
            for(String f:List.of("faqId","version","searchRank")) if(s.hasNonNull(f)&&(!s.get(f).isIntegralNumber()
                ||!(f.equals("faqId")?s.get(f).canConvertToLong():s.get(f).canConvertToInt()))) throw new IllegalArgumentException(where+" "+f);
            for(String f:List.of("slotId","category","matchedVariant","updatedAt")) if(s.hasNonNull(f)&&!s.get(f).isTextual()) throw new IllegalArgumentException(where+" "+f);
            found.add(new FaqSearchResponse(s.hasNonNull("faqId")?s.get("faqId").longValue():null,text(s,"slotId"),text(s,"category"),
                s.get("question").textValue(),s.get("answer").textValue(),s.get("score").doubleValue(),
                s.hasNonNull("version")?s.get("version").intValue():null,s.hasNonNull("updatedAt")?LocalDate.parse(s.get("updatedAt").textValue()):null,
                s.hasNonNull("searchRank")?s.get("searchRank").intValue():null,text(s,"matchedVariant")));
        }
        return found;
    }
    static String text(JsonNode n,String f) { return n.hasNonNull(f)?n.get(f).textValue():null; }
    // Observation only: preserve the application's original basis, including punctuation differences.
    static boolean isRefusal(String answer) {
        if(answer==null) return false;
        String value=answer.strip();
        return value.equals(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
            ||value.equals(AnswerPromptTemplates.NO_EVIDENCE_ANSWER.replaceFirst("\\.$",""));
    }
    static boolean mentionsRefusal(String answer) {
        return answer!=null&&answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER.replaceFirst("\\.$",""));
    }
    static String modelDigest(String json,String model) throws Exception {
        for(JsonNode entry:MAPPER.readTree(json).path("models")) if(entry.path("name").asText().equals(model)&&!entry.path("digest").asText().isBlank()) return entry.path("digest").asText();
        throw new IllegalStateException("configured model tag/digest unavailable: "+model);
    }
    static Set<String> selectedIds(List<JsonNode> cases,String filter) {
        Set<String> known=new LinkedHashSet<>();cases.forEach(c->known.add(c.path("eval_id").asText()));
        if(filter==null||filter.isBlank()) return known;
        Set<String> selected=new LinkedHashSet<>(Arrays.asList(filter.split(",")));
        if(!known.containsAll(selected)) throw new IllegalArgumentException("unknown eval IDs"); return selected;
    }
    static Map<String,Object> pending(JsonNode c,boolean selected) {
        Map<String,Object> row=new LinkedHashMap<>();
        for(String key:List.of("eval_id","type","question","sources")) row.put(key,MAPPER.convertValue(c.path(key),Object.class));
        row.put("status",selected?"NOT_PROCESSED":"NOT_SELECTED"); row.put("answer","");row.put("error",null);
        row.put("raw_state","UNAVAILABLE");row.put("observed_stage","NOT_RUN"); return row;
    }
    static void persist(Path dir,Map<String,Map<String,Object>> before,Map<String,Map<String,Object>> after,Map<String,Object> fixed) throws Exception {
        write(dir.resolve("baseline.json"),before.values());write(dir.resolve("candidate.json"),after.values());
        Map<String,Object> meta=new LinkedHashMap<>(fixed);
        meta.put("baseline_status_counts",counts(before));meta.put("candidate_status_counts",counts(after));
        write(dir.resolve("metadata.json"),meta);
        StringBuilder table=new StringBuilder("# Prompt-only replay — labels unconfirmed\n\n| eval ID | question | before status/stage | before raw → final | after status/stage | after raw → final | ms before/after | assessment |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for(String id:before.keySet()) {
            var b=before.get(id);var a=after.get(id);
            table.append("| ").append(id).append(" | ").append(cell(b.get("question"))).append(" | ")
                .append(b.get("status")).append('/').append(b.get("observed_stage")).append(" | ")
                .append(cell(b.get("raw_answer"))).append(" → ").append(cell(b.get("answer"))).append(" | ")
                .append(a.get("status")).append('/').append(a.get("observed_stage")).append(" | ")
                .append(cell(a.get("raw_answer"))).append(" → ").append(cell(a.get("answer"))).append(" | ")
                .append(b.get("elapsed_ms")).append('/').append(a.get("elapsed_ms")).append(" | REVIEW_REQUIRED |\n");
        }
        Files.writeString(dir.resolve("comparison.md"),table.toString());
    }
    static Map<String,Long> counts(Map<String,Map<String,Object>> rows) {
        Map<String,Long> count=new LinkedHashMap<>();rows.values().forEach(r->count.merge(r.get("status").toString(),1L,Long::sum));return count;
    }
    static void write(Path file,Object value) throws Exception {
        Path temp=file.resolveSibling(file.getFileName()+".tmp");MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),value);
        Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    static String cell(Object value) { return Objects.toString(value,"UNAVAILABLE").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("|","\\|").replace("\n","<br>"); }
    static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }catch(Exception e){throw new IllegalStateException(e);} }
    static class Observation {
        boolean requested,completed,guardApplied;String precheck,precheckError,guardFinal,guardError;LlmRequest actualRequest;
        final StringBuilder raw=new StringBuilder();final List<Map<String,Object>> retries=new ArrayList<>();
    }
}
