package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatInputInspectorTest {
    private final ChatInputInspector inspector = new ChatInputInspector(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(
            strings = {
                "씨발",
                "씨 팔",
                "씨.발",
                "씨*발",
                "씨123발",
                "개새끼",
                "개새키",
                "병신아",
                "븅신",
                "ㅅㅂ",
                "ㅆㅂ",
                "ㅂㅅ",
                "ㅄ",
                "ㅅ.ㅂ 요금 왜 이래",
                "너 ㅂㅅ아",
                "ㅈㄹ",
                "지랄하네",
                "이거ㅅㅂ",
                "서비스ㅂㅅ같아",
                "씨\u200B발",
                "병쉰",
                "개새기",
                "씨벌",
                "시발"
            })
    @DisplayName("명확한 욕설과 지원하는 초성·변형을 감지한다")
    void 욕설과_초성을_감지한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "안녕하세요",
                "감사합니다",
                "요금 때문에 짜증 나요",
                "서비스가 최악이에요. 해지 방법 알려주세요",
                "시발점이 뭐예요?",
                "시발역 안내",
                "시발열차 시간",
                "18개월 약정",
                "18시 방문",
                "월 7700원",
                "ㅂㅅ역 근처 매장",
                "개통 비용 알려주세요"
            })
    @DisplayName("정상 단어·숫자·불만과 정의하지 않은 초성 조합은 차단하지 않는다")
    void 정상_입력은_보존한다(String input) {
        InputInspection result = inspector.inspect(input);
        assertThat(result.hasProfanity()).isFalse();
        assertThat(result.content()).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "유심 다시 발급받고 싶어요",
                "택배는 몇 시 발송돼요?",
                "오후 3시 발송인가요",
                "새 요금제 출시 발표 언제예요?",
                "개통 시 발신 제한이 있나요?",
                "병 신청은 어디서 해요",
                "아저씨 발 사이즈",
                "유심 다시발급받고 싶어요",
                "새 요금제 출시발표 언제예요?",
                "개통시발신 제한이 있나요?",
                "병신청은 어디서 해요",
                "아저씨발사이즈",
                "몇시발송돼요?",
                "영수증은 몇 시 발행되나요?",
                "다시.발급받고 싶어요",
                "신청서 다시\n발급해주세요",
                "유심 다시\u200B발급해주세요",
                "입대할 병 신임 교육은 어디서 하나요?"
            })
    @DisplayName("정상 단어의 일부를 공백·구분자 너머로 연결하거나 긴 단어에서 잘라 욕설로 판단하지 않는다")
    void 정상_단어의_경계를_넘어_욕설을_만들지_않는다(String input) {
        InputInspection result = inspector.inspect(input);
        assertThat(result.hasProfanity()).isFalse();
        assertThat(result.detections()).isEmpty();
        assertThat(result.content()).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "씨 발",
                "병 신아",
                "씨.발놈아",
                "개 새 끼야",
                "너는 병신같아",
                "너는 병신같은 놈이야",
                "지랄하지마",
                "씨발입니다",
                "출시 발표 언제예요? 씨발",
                "아저씨 발 사이즈, 너는 병신아",
                "유심 다시 발급받고 싶어요. 이거ㅅㅂ",
                "시발역 안내, 서비스ㅂㅅ같아"
            })
    @DisplayName("단어 경계를 확인해도 지원하는 분리 욕설·어미와 다른 구간의 실제 욕설은 감지한다")
    void 정상_문장과_섞인_실제_욕설은_계속_감지한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "씨발ㅋㅋ",
                "씨발ㅎㅎ",
                "씨발ㅠㅠ",
                "씨발ㅜㅜ",
                "ㅅㅂㅋㅋ",
                "ㅂㅅㅠㅠ",
                "병신아ㅋㅋ",
                "씨.발ㅋㅋ",
                "서비스ㅂㅅ같은 서비스",
                "씨발아\n\n요금 문의"
            })
    @DisplayName("지원하는 웃음·울음 자모와 어미가 붙어도 명확한 욕설은 감지한다")
    void 지원_자모와_어미가_붙은_욕설을_감지한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "다시 발급ㅋㅋ",
                "출시 발표ㅠㅠ",
                "시발점ㅎㅎ",
                "시발역ㅋㅋ",
                "병 신청ㅎㅎ",
                "아저씨 발 사이즈ㅠㅠ",
                "ㅂㅅ역ㅋㅋ"
            })
    @DisplayName("자모 지원을 넓혀도 정상 단어와 지원하지 않는 초성 조합은 보호한다")
    void 장식_자모가_있어도_정상_단어는_보존한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "'씨발'이라고 들었는데 어떻게 신고하나요?",
                "상담원이 저한테 '병신'이라고 했어요. 신고 방법 알려주세요",
                "직원이 '씨발'이라고 말했어요",
                "친구가 나에게 ‘병신’이라고 했어요",
                "상대방이 ‘ㅅㅂ’라고 불렀어요"
            })
    @DisplayName("명확한 수신·타인 발화 설명에서는 해당 인용 구간만 예외 처리한다")
    void 명확한_피해_인용을_보존한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "너는 '병신'이라고 했어요",
                "상담원에게 '병신'이라고 했어요",
                "직원에게 '씨발'이라고 말했어요",
                "상담원이 '병신'이라고 했어요. 너도 씨발",
                "'씨발'이라고 들었어요. 너도 병신아"
            })
    @DisplayName("발화 주체가 다른 인용이나 피해 설명 밖의 실제 욕설까지 함께 허용하지 않는다")
    void 인용_예외가_다른_공격을_허용하지_않는다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "'ㅂㅅ'이 무슨 뜻이에요?",
                "'병신'이라는 욕을 들었어요",
                "상담원한테 ‘병신’이라는 욕을 들었어요",
                "ㅅㅂ이 무슨 뜻이에요?",
                "병신이 무슨 뜻이에요?"
            })
    @DisplayName("명확한 단어 설명과 인용 피해 설명에서 해당 구간만 예외 처리한다")
    void 지원하는_인용은_통과한다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "너는 '병신'이야",
                "'병신'이라는 욕을 들었어요. 너도 개새끼야",
                "씨발 '병신'이 무슨 뜻이에요?",
                "시발점 같은 소리 하지마 씨발"
            })
    @DisplayName("인용·정상 단어가 있어도 다른 구간의 욕설은 감지한다")
    void 예외로_전체_문장을_허용하지_않는다(String input) {
        assertThat(inspector.inspect(input).hasProfanity()).isTrue();
    }

    @Test
    @DisplayName("민감번호만 치환하고 남은 상담 질문은 유지한다")
    void 개인정보를_가리고_질문을_유지한다() {
        String input = "주민등록번호 990101-1234567, 카드 4111-1111-1111-1111인데 유심 비용 알려주세요";
        InputInspection result = inspector.inspect(input);
        assertThat(result.content()).isEqualTo("주민등록번호 [주민등록번호], 카드 [카드번호]인데 유심 비용 알려주세요");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.toString()).doesNotContain("990101", "4111");
        assertThat(result.detections())
                .extracting(InputInspection.Detection::ruleId)
                .containsExactly("PII_RESIDENT_NUMBER", "PII_PAYMENT_CARD");
    }

    @Test
    @DisplayName("개인정보와 욕설이 섞여도 안전한 내용과 욕설 사유만 반환한다")
    void 개인정보를_가린_욕설_입력을_반환한다() {
        InputInspection result = inspector.inspect("씨발 카드 4111111111111111 요금 왜 이래");
        assertThat(result.hasProfanity()).isTrue();
        assertThat(result.content()).isEqualTo("씨발 카드 [카드번호] 요금 왜 이래");
        assertThat(result.content()).doesNotContain("4111111111111111");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "990101-1234567",
                "4111 1111 1111 1111",
                "제 카드번호는 4111111111111111입니다",
                "카드 4111111111111111",
                "주민번호 990101-1234567, 카드번호 4111111111111111"
            })
    @DisplayName("번호만 제시한 입력은 욕설로 세지 않고 문의 재입력이 필요하다")
    void 문의가_남지_않으면_재입력_대상이다(String input) {
        InputInspection result = inspector.inspect(input);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isFalse();
        assertThat(result.hasProfanity()).isFalse();
    }

    @Test
    @DisplayName("전각 숫자와 지원 구분자도 원문 위치를 유지하면서 마스킹한다")
    void 전각_개인정보도_마스킹한다() {
        var result = inspector.inspect("카드 ４１１１－１１１１－１１１１－１１１１ 요금 문의");
        assertThat(result.content()).isEqualTo("카드 [카드번호] 요금 문의");
        assertThat(result.hasQuestion()).isTrue();
        var resident = inspector.inspect("주민등록번호 ９９０１０１–１２３４５６７ 유심 비용");
        assertThat(resident.content()).isEqualTo("주민등록번호 [주민등록번호] 유심 비용");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123원 요금 문의", "500원 요금 문의", "17개 문의", "123번 문의"})
    @DisplayName("카드번호 뒤의 별도 숫자 때문에 유효한 카드번호 마스킹을 놓치지 않는다")
    void 카드번호_뒤의_금액과_수량을_유지한다(String tail) {
        var result = inspector.inspect("카드 4111111111111111 " + tail);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.content()).isEqualTo("카드 [카드번호] " + tail);
        assertThat(result.hasQuestion()).isTrue();
    }

    @Test
    @DisplayName("허용 길이 안의 긴 소개 문구도 스택 오류 없이 재입력 대상으로 판단한다")
    void 긴_소개_문구가_있어도_검사를_마친다() {
        String input = "4111111111111111 " + "제".repeat(1900);
        assertThat(input.length()).isLessThanOrEqualTo(2000);
        var result = inspector.inspect(input);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isFalse();
    }

    @Test
    @DisplayName("반복된 소개 표현 뒤의 실제 문의는 보존한다")
    void 긴_입력_뒤의_정상_문의를_유지한다() {
        var result = inspector.inspect("4111111111111111 " + "카드번호".repeat(300) + "문의");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890123456", "4111111111111112", "991332-1234567"})
    @DisplayName("임의의 긴 숫자·검증값 불일치·유효하지 않은 날짜를 민감번호로 단정하지 않는다")
    void 지원_형식에_맞지_않는_숫자는_보존한다(String input) {
        assertThat(inspector.inspect(input).content()).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2221000000000009", "2720990000000007"})
    @DisplayName("2로 시작하는 지원 Mastercard 범위의 번호도 마스킹한다")
    void 이_시리즈_카드번호를_마스킹한다(String number) {
        var result = inspector.inspect("카드 " + number + " 요금 문의");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.content()).isEqualTo("카드 [카드번호] 요금 문의");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2220990000000002", "2721000000000004"})
    @DisplayName("지원 범위 밖의 2로 시작하는 긴 숫자는 카드로 단정하지 않는다")
    void 이_시리즈_범위의_경계를_검증한다(String number) {
        assertThat(inspector.inspect(number).wasMasked()).isFalse();
    }
}
