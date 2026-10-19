package com.telme.chat.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** 소유자 표현과 방문·준비물 조건을 구분하고 문단과 제목에 같은 대상 판정을 제공한다. */
final class ChatEndSummaryPersonScope {
    private static final String PERSON_ENDING = "$|의|은|는|이|가|도|한테|에게|을|거|것|과|와|[,·.!?]"
            + "|회선|요금|유심|로밍|매장|신규가입|신규개통|가입|개통|번호이동|명의변경|청구|납부|해지"
            + "|단말|기기|휴대폰|핸드폰|실물|물리|분실|[0-9a-z]";
    private static final String OWNED_TARGET = "유심|회선|요금|로밍|매장|가입|개통|번호이동|해지|명의"
            + "|단말|기기|휴대폰|핸드폰|esim|이심"
            + "|(?:[a-z]|[0-9]+번?|첫번째|두번째|세번째)회선";
    private static final String MODIFIER = "실물|물리|분실한|분실|새로운|새|사용중인|사용하는|가입한"
            + "|개통한|재발급받은|잃어버린|첫번째|두번째|세번째";
    private static final String OTHER_PERSON = "어머니|아버지|배우자|자녀|남편|아내|가족|부모님|친구"
            + "|동생|누나|언니|오빠|할머니|할아버지|대리인|아들|딸|(?:친|사촌|큰|작은)?형(?:님)?|(?:직장)?동료";
    private static final Pattern KNOWN_PERSON = Pattern.compile("어머니|아버지|배우자|자녀|남편|아내");
    private static final Pattern FAMILY_PERSON = Pattern.compile(
            "가족(?!\\s*(?:할인|결합))(?=\\s|" + PERSON_ENDING + ")");
    private static final Pattern BROTHER_PERSON = Pattern.compile(
            "(?:^|\\s|우리|저희|제|다른|과|와|[,·])((?:(?:친|사촌|큰|작은)\\s*)?형)(?:님)?"
                    + "(?=\\s|" + PERSON_ENDING + ")");
    private static final Pattern COLLEAGUE_PERSON = Pattern.compile("동료(?=\\s|" + PERSON_ENDING + ")");
    private static final Pattern SELF_REFERENCE = Pattern.compile(
            "(?:^|\\s|[,?!·]|그럼|그리고|아까|앞서|과|와)(제|내|저의|나의|본인의|본인(?:은|는|이|가)?|저는)"
                    + "(?=\\s|$|[.!?,·]|과|와|" + OWNED_TARGET + "|" + MODIFIER + "|" + OTHER_PERSON + ")");
    private static final Pattern OWNED_NOUN_PHRASE = Pattern.compile("^(?:" + MODIFIER + ")*(?:" + OWNED_TARGET + ")");
    private static final Pattern OWNED_NOUN = Pattern.compile(OWNED_TARGET);
    private static final Pattern OTHER_OWNER_HEAD = Pattern.compile("^(?:우리|저희)?(?:" + OTHER_PERSON + ")");
    private static final Pattern COORDINATED_OWNER = Pattern.compile("^(?:과|와|랑|하고)(?:" + OTHER_PERSON + ")");
    private static final Pattern VISITOR_OR_DOCUMENT = Pattern.compile(
            "^(?:신분증|확인|인증|방문|직접방문|(?:직접)?(?:가까운|근처)?"
                    + "(?:매장|대리점|고객센터)(?:에서|으로|에|을?방문))");
    private static final Pattern UNSPECIFIED_PERSON = Pattern.compile(
            "부모님|친구|동생|누나|언니|오빠|형님|할머니|할아버지|대리인"
                    + "|(?:아들|딸)(?:의|이|은|한테|에게|을|회선|요금|유심|로밍|[0-9a-z])");

    private ChatEndSummaryPersonScope() {}

    static String extract(String text) {
        var owners = findOwners(text);
        return owners.size() > 1 ? "MULTIPLE" : owners.stream().findFirst().orElse("");
    }

    static String createGroupingIdentity(String text) {
        var owners = findOwners(text);
        return owners.size() > 1 ? "MULTIPLE:" + String.join("|", owners.stream().sorted().toList())
                : owners.stream().findFirst().orElse("");
    }

    static String createTitlePrefix(String text) {
        var owners = findOwners(text);
        return owners.isEmpty() ? "" : String.join("·", owners.stream()
                .map(owner -> owner.equals("SELF") ? "본인" : owner).toList()) + " ";
    }

    static boolean hasUnspecifiedPerson(String text) {
        String value = normalizePersonText(text);
        return UNSPECIFIED_PERSON.matcher(ChatEndSummaryInquiryLinker.normalizeText(value)).find()
                || FAMILY_PERSON.matcher(value).find() || BROTHER_PERSON.matcher(value).find()
                || COLLEAGUE_PERSON.matcher(value).find();
    }

    private static Set<String> findOwners(String text) {
        String value = normalizePersonText(text);
        Map<Integer, String> owners = new TreeMap<>();
        collectOwners(owners, KNOWN_PERSON, value, null);
        collectOwners(owners, FAMILY_PERSON, value, "가족");
        collectOwners(owners, COLLEAGUE_PERSON, value, "동료");
        var brothers = BROTHER_PERSON.matcher(value);
        while (brothers.find()) {
            owners.put(brothers.start(1), ChatEndSummaryInquiryLinker.normalizeText(brothers.group(1)));
        }
        var self = SELF_REFERENCE.matcher(value);
        while (self.find()) {
            String tail = value.substring(self.end()).split("[.!?,\\n]", 2)[0];
            if (isSelfOwner(self.group(1), ChatEndSummaryInquiryLinker.normalizeText(tail))) {
                owners.put(self.start(1), "SELF");
            }
        }
        return new LinkedHashSet<>(owners.values());
    }

    private static boolean isSelfOwner(String reference, String tail) {
        if (tail.isBlank() || COORDINATED_OWNER.matcher(tail).find()) {
            return true;
        }
        if (OTHER_OWNER_HEAD.matcher(tail).find() || VISITOR_OR_DOCUMENT.matcher(tail).find()) {
            return false;
        }
        // 명시적인 소유격은 수식어가 있어도 대상을 확인하되 주어 표현은 명사구가 직접 이어져야 한다.
        return Set.of("제", "내", "저의", "나의", "본인의").contains(reference)
                ? OWNED_NOUN.matcher(tail).find() : OWNED_NOUN_PHRASE.matcher(tail).find();
    }

    private static void collectOwners(Map<Integer, String> owners, Pattern pattern, String text, String label) {
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            owners.put(matcher.start(), label == null ? matcher.group() : label);
        }
    }

    private static String normalizePersonText(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replace("엄마", "어머니").replace("아빠", "아버지");
    }
}
