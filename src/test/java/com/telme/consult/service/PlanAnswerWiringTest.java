package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

// 실제 빈 연결 확인. 모델은 가짜라 분류가 규칙으로 넘어가고, 요금제 목록 질문은 FAQ 검색 없이 요금제 테이블로 답한다
@SpringBootTest
class PlanAnswerWiringTest {

    @Autowired @Qualifier("consultAnswerProvider") AnswerProvider answers;

    @Test
    void 실제_연결된_답변_경로가_요금제_목록_질문에_요금제_테이블로_답한다() {
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "요금제 종류 알려줘", "요금제 종류 알려줘", Map.of());

        GeneratedAnswer answer = answers.generate(input);

        assertThat(answer.answer().content()).contains("5G 스탠다드", "월 65,000원", "알뜰 미니");
        assertThat(answer.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(answer.sources()).isEmpty();
    }
}
