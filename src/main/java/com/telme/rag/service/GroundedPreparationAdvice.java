package com.telme.rag.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 준비물의 충분성을 새로 단정하지 않고, 확인된 문의 범위의 근거만 복원한다. */
final class GroundedPreparationAdvice {
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|\\R+");
    private static final Pattern EXCLUSIVE_ID = Pattern.compile(
            "신분증(?:은|는|을|이)?(?:한장|하나)?(?:만|뿐|이면|으로충분)|신분증(?:이|일)?뿐|오직신분증");
    private static final Pattern NO_OTHER_DOCUMENTS = Pattern.compile(
            "(?:다른|추가|별도)(?:의)?(?:서류|준비물)(?:는|가|은|이)?(?:전혀)?"
                    + "(?:필요(?:가)?없|불필요|없)");
    private static final Pattern UNCONFIRMED = Pattern.compile(
            "(?:가능한지|필요한지|되는지).*(?:확인할수없|확인되지않|확인이필요|모르)");
    private static final Pattern DOCUMENT_WAIVER = Pattern.compile(
            "위임장(?:은|는|이|을)?(?:준비|지참|제출)?"
                    + "(?:없이|불필요|필요없|필요하지않|하지않아도|안해도)");
    private static final Pattern PREPARATION = Pattern.compile("지참|가지고|준비|필요|방문|제출|불가능|불가");
    private static final Pattern PREPARATION_CLAIM = Pattern.compile(
            "신분증|위임장|준비물|서류|방문|(?:명의자|직원|통신사).*(?:확인|절차)");
    private static final Pattern PREPARATION_QUERY = Pattern.compile("준비물|신분증|서류|위임장|방문|절차");
    private static final Pattern SUBJECT = Pattern.compile("esim|e-sim|e심|이심|유심|로밍|요금제|인터넷|휴대폰|5g|lte");
    private static final Pattern TASK = Pattern.compile("재발급|발급|재가입|가입|변경|해지|취소|교체|정지|개통");
    private static final Pattern PERSON = Pattern.compile(
            "어머니|엄마|아버지|아빠|부모님|큰형|작은형|형|누나|언니|오빠|동생|배우자|남편|아내|자녀|가족|친구|동료");
    private static final Pattern NAME = Pattern.compile("(?<![가-힣])([가-힣]{2,4})\\s*(?:씨|님)(?:의)?");
    private static final Pattern OWNER_NAME = Pattern.compile(
            "(?<![가-힣])([가-힣]{2,4})의(?=\\s*(?:(?:분실|실물|물리)\\s*)?"
                    + "(?:유심|eSIM|이심|e심|로밍|요금제|인터넷|휴대폰|회선))", Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_OWNERS = Set.of(
            "명의자", "본인", "고객", "사용자", "대리인", "미성년자", "법인");
    private static final Pattern LINE = Pattern.compile(
            "(?:첫번째|두번째|세번째|첫|두|세|다른|본인|제|내|[a-z]|\\d+번?)회선");
    private static final Pattern DOCUMENT_OWNER = Pattern.compile(
            "(?:명의자본인|명의자|대리인|미성년자|법인|본인)(?:의)?"
                    + "(?:(?:명의(?:의)?|원본|실물|유효한))*(?:신분증|위임장|서류)");
    private static final Pattern VISITOR = Pattern.compile("명의자본인|대리인|미성년자|법인|본인");

    private GroundedPreparationAdvice() {}

    static String filter(String answer, String context, String query, List<String> sourceAnswers,
            Consumer<String> verifyBeforeRestore) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        List<String> statements = List.of(SENTENCE.split(answer.strip()));
        List<String> evidence = readEvidence(context, sourceAnswers);
        boolean knownBoundaries = sourceAnswers != null || evidence.size() == 1;
        Set<String> verbatim = new LinkedHashSet<>();
        evidence.forEach(source -> verbatim.addAll(Arrays.asList(SENTENCE.split(source))));
        boolean preparationQuery = PREPARATION_QUERY.matcher(query == null ? "" : query).find();
        boolean guardPreparation = preparationQuery
                && evidence.stream().anyMatch(GroundedPreparationAdvice::isPreparation);
        boolean repair = statements.stream().filter(sentence -> isGuardedStatement(sentence, guardPreparation))
                .anyMatch(sentence -> isExclusiveClaim(sentence) || !verbatim.contains(sentence)
                        || (sourceAnswers != null && guardPreparation));
        if (!repair) {
            return answer;
        }
        Scope requested = Scope.read(query);
        Scope reference = requested;
        // 생략된 상품·업무를 서로 다른 FAQ에서 가져와 임의로 연결하지 않는다.
        Scope global = Scope.read(evidence.size() == 1 ? evidence.getFirst() : null);
        // 근거 개수에 따라 원문 복원이 수치 오류를 덮어 검사 결과를 바꾸지 않도록 먼저 검증한다.
        verifyBeforeRestore.accept(answer);
        if (canKeepCompleteQuote(answer, evidence, requested, global)) {
            return evidence.getFirst();
        }
        if (knownBoundaries && evidence.size() == 1 && preparationQuery && isPreparation(evidence.getFirst())
                && canRestoreComplete(statements, reference, global, guardPreparation)) {
            return evidence.getFirst();
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String sentence : statements) {
            Scope current = Scope.read(sentence).inherit(reference);
            if (isGuardedStatement(sentence, guardPreparation)
                    && (isExclusiveClaim(sentence) || !verbatim.contains(sentence)
                            || (sourceAnswers != null && guardPreparation))) {
                List<String> candidates = evidence.stream().filter(GroundedPreparationAdvice::isPreparation)
                        .filter(source -> requested.matches(Scope.read(source), global))
                        .filter(source -> current.matches(Scope.read(source), global)).toList();
                // 여러 근거나 다른 대상의 절차를 임의로 골라 누락된 조건을 꾸미지 않는다.
                if (knownBoundaries && candidates.size() == 1) {
                    kept.addAll(Arrays.asList(SENTENCE.split(candidates.getFirst())));
                }
            } else {
                kept.add(sentence);
            }
            reference = current;
        }
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : String.join(" ", kept);
    }

    private static List<String> readEvidence(String context, List<String> sourceAnswers) {
        if (sourceAnswers != null) {
            return sourceAnswers.stream().filter(source -> source != null && !source.isBlank())
                    .map(String::strip).distinct().toList();
        }
        return context == null ? List.of() : context.lines()
                .map(String::strip).filter(line -> !line.isBlank()).distinct().toList();
    }

    private static boolean canKeepCompleteQuote(String answer, List<String> evidence, Scope query, Scope global) {
        if (evidence.size() != 1 || !compact(answer).equals(compact(evidence.getFirst()))) {
            return false;
        }
        Scope source = Scope.read(evidence.getFirst());
        // 방문자를 특정하지 않은 서류 소유 안내 전체를 그대로 인용한 경우는 새 방문 허용 주장이 아니다.
        Scope withoutVisitor = new Scope(query.subjects, query.tasks, query.people, query.lines, Set.of());
        return source.visitors.isEmpty() && withoutVisitor.matches(source, global);
    }

    private static boolean canRestoreComplete(List<String> statements, Scope query, Scope source,
            boolean guardPreparation) {
        if (!query.matches(source, source)) {
            return false;
        }
        Scope current = query;
        for (String statement : statements) {
            current = Scope.read(statement).inherit(current);
            if (isGuardedStatement(statement, guardPreparation) && !current.matches(source, source)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGuardedStatement(String statement, boolean guardPreparation) {
        return isExclusiveClaim(statement) || (guardPreparation && isPreparationClaim(statement));
    }

    private static boolean isExclusiveClaim(String sentence) {
        String text = compact(sentence);
        return !UNCONFIRMED.matcher(text).find()
                && (EXCLUSIVE_ID.matcher(text).find() || NO_OTHER_DOCUMENTS.matcher(text).find()
                || DOCUMENT_WAIVER.matcher(text).find());
    }

    private static boolean isPreparationClaim(String sentence) {
        String text = compact(sentence);
        return !UNCONFIRMED.matcher(text).find()
                && (isExclusiveClaim(sentence) || PREPARATION_CLAIM.matcher(text).find());
    }

    private static boolean isPreparation(String source) {
        return source.contains("신분증") && PREPARATION.matcher(source).find();
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static Set<String> find(String text, Pattern pattern) {
        Set<String> values = new LinkedHashSet<>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            values.add(switch (value) {
                case "e-sim", "e심", "이심" -> "esim";
                case "엄마" -> "어머니";
                case "아빠" -> "아버지";
                case "명의자본인" -> "본인";
                case "첫회선", "첫번째회선", "1번회선" -> "1회선";
                case "두회선", "두번째회선", "2번회선" -> "2회선";
                case "세회선", "세번째회선", "3번회선" -> "3회선";
                case "제회선", "내회선" -> "본인회선";
                default -> value;
            });
        }
        return values;
    }

    private record Scope(Set<String> subjects, Set<String> tasks, Set<String> people,
            Set<String> lines, Set<String> visitors) {
        static Scope read(String source) {
            String text = compact(source);
            Set<String> people = find(text, PERSON);
            var names = NAME.matcher(source == null ? "" : source);
            while (names.find()) {
                people.add(names.group(1));
            }
            var owners = OWNER_NAME.matcher(source == null ? "" : source);
            while (owners.find()) {
                String owner = owners.group(1);
                if (!GENERIC_OWNERS.contains(owner) && !PERSON.matcher(owner).matches()) {
                    people.add(owner);
                }
            }
            return new Scope(find(text, SUBJECT), find(text, TASK), people,
                    find(text, LINE), find(DOCUMENT_OWNER.matcher(text).replaceAll(""), VISITOR));
        }

        Scope inherit(Scope previous) {
            boolean changed = !subjects.isEmpty() && !previous.subjects.isEmpty()
                    && !subjects.equals(previous.subjects);
            if (changed) {
                return this;
            }
            return new Scope(orElse(subjects, previous.subjects), orElse(tasks, previous.tasks),
                    orElse(people, previous.people), orElse(lines, previous.lines),
                    orElse(visitors, previous.visitors));
        }

        boolean matches(Scope source, Scope global) {
            Set<String> sourceSubjects = orElse(source.subjects, global.subjects);
            Set<String> sourceTasks = orElse(source.tasks, global.tasks);
            return subjects.equals(sourceSubjects)
                    && compatible(tasks, sourceTasks) && compatible(people, source.people)
                    && compatible(lines, source.lines)
                    && (visitors.isEmpty() || source.visitors.containsAll(visitors));
        }

        private static boolean compatible(Set<String> expected, Set<String> actual) {
            return actual.isEmpty() || expected.equals(actual);
        }

        private static Set<String> orElse(Set<String> values, Set<String> fallback) {
            return values.isEmpty() ? fallback : values;
        }
    }
}
