package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.stream.IntStream;

public final class ConditionPromptTemplates {

    private ConditionPromptTemplates() {}

    // 아래 프롬프트를 고치면 함께 올린다. 개선 전후 비교에 쓰인다
    public static final String PROMPT_VERSION = "condition-extract-v1.3";

    public static final String CONDITION_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 상담의 질문 분석기입니다.
        FAQ 답변(A)만 보고, 고객이 누구냐에 따라 답이 갈리는데 그것을 아직 모르는 경우를 찾으십시오.

        [규칙]
        1. 답변(A)에서 아래 같은 말을 찾으십시오. 고객이 어디에 해당하는지에 따라 답이 달라지는 말입니다.
           - 횟수·기간 제한: "한 달에 한 번만", "월 1회", "가입한 달에는"
           - 자격·나이: "만 14세 미만은", "18세 이하는"
           - 선행 조건: "밀린 요금이 없으면", "미납이 있으면 먼저"
           - 종류별 차이: "5G 요금제는", "LTE는", "~에 따라 다릅니다"
           이런 말이 있는데 고객이 자기 상황을 말하지 않았다면 그것을 조건으로 적으십시오.
           고객 질문에 이미 답이 있으면 적지 마십시오.
        2. 고객의 취향이나 선호를 묻지 마십시오. 어느 방법으로 하고 싶은지는 조건이 아닙니다.
        3. 답변(A)에 근거가 없는 조건은 적지 마십시오. 일반 상식으로 떠올린 조건도 적지 마십시오.
        4. 조건의 값을 추측하지 마십시오. 고객이 어떤 상황인지 단정하지 마십시오.
        5. evidence에는 답변(A)의 문장을 한 글자도 바꾸지 말고 그대로 복사하십시오.
           요약하거나 설명하면 안 됩니다. "답변에 ~라고 나와 있습니다" 같은 말을 쓰면 안 됩니다.
           - 올바른 예: "만 14세 이상 18세 이하는 법정대리인 동의서가 필요합니다"
           - 잘못된 예: "답변(A)에서 나이에 따라 다르다고 언급했습니다"
        6. 답변(A)에 고를 수 있는 값이 적혀 있으면 options에 그 값만 적으십시오.
           적혀 있지 않으면 빈 배열로 두십시오. 예·아니요로 답할 질문이면 ["예","아니요"]로 적으십시오.
        7. question은 고객에게 그대로 보여줄 한 문장 질문으로, 존댓말로 쓰십시오.
           괄호로 선택지를 덧붙이지 마십시오. 선택지는 options에만 적습니다.
        8. 중요한 것부터 최대 2개까지만 적으십시오. 더 알 것이 없으면 conditions를 빈 배열로 두십시오.
        9. key는 영문 소문자와 밑줄만 써서 짧게 지으십시오.

        [예시]
        답변(A): "요금제는 한 달에 한 번만 변경할 수 있습니다. 가입한 달에는 변경이 제한됩니다."
        출력: {"conditions":[
          {"key":"joined_this_month","question":"이번 달에 가입하셨나요?","options":["예","아니요"],
           "evidence":"가입한 달에는 변경이 제한됩니다"},
          {"key":"changed_this_month","question":"이번 달에 요금제를 변경하신 적 있나요?","options":["예","아니요"],
           "evidence":"요금제는 한 달에 한 번만 변경할 수 있습니다"}]}

        답변(A): "신분증을 가지고 신청하시면 됩니다. 밀린 요금이 없어야 진행됩니다."
        출력: {"conditions":[
          {"key":"unpaid_bill","question":"현재 밀린 요금이 있으신가요?","options":["예","아니요"],
           "evidence":"밀린 요금이 없어야 진행됩니다"}]}

        답변(A): "청구서는 홈페이지와 고객센터 앱에서 확인하실 수 있습니다."
        출력: {"conditions":[]}

        [출력 형식]
        아래 JSON만 출력하십시오. 설명을 덧붙이지 마십시오.
        {"conditions":[{"key":"","question":"","options":[],"evidence":""}]}
        """;

    public static String buildUserPrompt(String userQuery, List<FaqSearchResponse> sources) {
        return """
            [FAQ 근거]
            %s

            [고객 질문]
            %s""".formatted(formatSources(sources), userQuery);
    }

    private static String formatSources(List<FaqSearchResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "없음";
        }
        return IntStream.range(0, sources.size())
                .mapToObj(index -> "[%d] Q. %s\nA. %s".formatted(
                        index + 1, sources.get(index).question(), sources.get(index).answer()))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("없음");
    }
}
