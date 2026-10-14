package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 실제 빈 연결 확인. 모델은 가짜라 분류가 규칙으로 넘어가고, 요금제 목록 질문은 FAQ 검색 없이 요금제 테이블로 답한다
@SpringBootTest
@Transactional
class PlanAnswerWiringTest {

    @Autowired @Qualifier("consultAnswerProvider") AnswerProvider answers;
    @Autowired UserRepository userRepository;
    @Autowired GuestRepository guestRepository;
    @Autowired ChatSessionRepository sessionRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionCommandService commandService;

    @Test
    void 실제_연결된_답변_경로가_요금제_목록_질문에_요금제_테이블로_답한다() {
        AnswerInput input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "요금제 종류 알려줘", "요금제 종류 알려줘", Map.of());

        GeneratedAnswer answer = answers.generate(input);

        assertThat(answer.answer().content()).contains("5G 스탠다드", "월 65,000원", "알뜰 미니");
        assertThat(answer.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void 비회원_채팅방의_내_요금제_질문에는_로그인_안내를_준다() {
        Guest guest = guestRepository.save(Guest.issue(Duration.ofDays(30), Clock.systemUTC()));
        ChatSession session = sessionRepository.save(ChatSession.builder().guestId(guest.getGuestId()).build());

        GeneratedAnswer answer = answers.generate(input(session, "내 요금제 뭐야"));

        assertThat(answer.answer().content()).contains("로그인하시면 가입하신 요금제를 확인해 드릴 수 있어요");
        assertThat(answer.answer().followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 가입하지_않은_회원_채팅방의_내_요금제_질문에는_가입_안내를_준다() {
        ChatSession session = memberSession();

        GeneratedAnswer answer = answers.generate(input(session, "내 요금제 뭐야"));

        assertThat(answer.answer().content()).contains("아직 가입한 요금제가 없어요");
    }

    @Test
    void 가입한_회원_채팅방에서는_내_요금제와_위약금을_구독_정보로_답한다() {
        ChatSession session = memberSession();
        Long planId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("FIVE_G_STANDARD")).findFirst().orElseThrow().getPlanId();
        commandService.subscribe(session.getUserId(), planId, null, true);

        assertThat(answers.generate(input(session, "내 요금제 뭐야")).answer().content())
                .contains("5G 스탠다드 요금제에 가입하셨어요", "월정액 65,000원");
        assertThat(answers.generate(input(session, "내 위약금 알려줘")).answer().content())
                .contains("아직 개통 전이라 받은 할인이 없어서");
    }

    @Test
    void 남의_채팅방이_아니라_이_채팅방_주인의_정보만_읽는다() {
        ChatSession mine = memberSession();
        ChatSession other = memberSession();
        Long planId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("LTE_PLUS")).findFirst().orElseThrow().getPlanId();
        commandService.subscribe(other.getUserId(), planId, null);

        assertThat(answers.generate(input(mine, "내 요금제 뭐야")).answer().content())
                .contains("아직 가입한 요금제가 없어요").doesNotContain("LTE 플러스");
        assertThat(answers.generate(input(other, "내 요금제 뭐야")).answer().content()).contains("LTE 플러스");
    }

    private ChatSession memberSession() {
        User user = userRepository.save(User.builder()
                .email("wiring-" + java.util.UUID.randomUUID() + "@test.com").name("회원").build());
        return sessionRepository.save(ChatSession.builder().userId(user.getUserId()).build());
    }

    private static AnswerInput input(ChatSession session, String question) {
        return new AnswerInput(1L, session.getSessionId(), 3L, Purpose.GENERAL_FAQ, question, question, Map.of());
    }
}
