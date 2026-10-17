package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TranslationNumbersTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "재발급 비용은 7,700원입니다. | The reissue fee is KRW 7,700.",
            "월 30만 원까지 쓸 수 있어요. | You can use up to KRW 300,000 per month.",
            "보증금은 1억 5000만 원입니다. | The deposit is KRW 150,000,000.",
            "신청 후 2~3영업일이 걸립니다. | It takes 2-3 business days after you apply.",
            "3월 5일부터 적용됩니다. | It applies from March 5.",
            "속도가 1.5배 빨라요. | It is 1.5 times faster.",
            "고객센터 114로 문의하세요. 상담은 09:00부터예요. | Contact the customer center at 114. Support starts at 9:00.",
            "신청할 수 있습니다. | You may apply.",
            "요금은 7,700원이고 7,700원은 다음 달에 청구됩니다. | The fee is KRW 7,700, billed next month."
    })
    @DisplayName("숫자 값이 같으면 표기가 달라도 같은 번역으로 본다")
    void 숫자가_같다(String korean, String english) {
        assertThat(TranslationNumbers.same(korean, english)).isTrue();
    }
    
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "재발급 비용은 7,700원입니다. | The reissue fee is KRW 7,000.",
            "신청 후 2~3영업일이 걸립니다. | It takes a few business days after you apply.",
            "재발급 비용은 7,700원입니다. | The reissue fee is KRW 7,700 and it takes 3 days.",
            "월 30만 원까지 쓸 수 있어요. | You can use up to KRW 30 per month."
    })
    @DisplayName("숫자가 바뀌거나, 빠지거나, 새로 생기면 다른 번역으로 본다")
    void 숫자가_다르다(String korean, String english) {
        assertThat(TranslationNumbers.same(korean, english)).isFalse();
    }
}
