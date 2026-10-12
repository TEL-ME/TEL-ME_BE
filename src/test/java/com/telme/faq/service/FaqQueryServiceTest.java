package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.dto.req.FaqListRequest;
import com.telme.faq.dto.req.FaqSort;
import com.telme.faq.dto.res.FaqResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqCategory;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 로컬에 적재된 FAQ와 섞이지 않도록 표시어로 거른다. 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class FaqQueryServiceTest {

    private static final String MARKER = "ZQUSER";
    private static final long SEED_MESSAGE_ID = 2L;

    @Autowired
    private FaqQueryService service;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long citedTwice;
    private Long citedOnce;
    private Long neverCited;
    private Long hidden;
    private Long deleted;

    @BeforeEach
    void setUp() {
        citedTwice = save(FaqCategory.USIM, "유심을 바꾸려면 어떻게 하나요?", Faq.Status.ACTIVE);
        citedOnce = save(FaqCategory.BILLING, "요금은 언제 빠지나요?", Faq.Status.ACTIVE);
        neverCited = save(FaqCategory.USIM, "eSIM은 무엇인가요?", Faq.Status.ACTIVE);
        hidden = save(FaqCategory.USIM, "숨긴 안내입니다.", Faq.Status.HIDDEN);
        deleted = save(FaqCategory.USIM, "지운 안내입니다.", Faq.Status.DELETED);

        cite(citedTwice, 2);
        cite(citedOnce, 1);
        // 가장 많이 쓰였어도 숨긴 FAQ는 나오면 안 된다
        cite(hidden, 3);
    }

    @Test
    @DisplayName("기본 정렬은 자주 묻는 순이고, 공개 중인 FAQ만 나온다")
    void 기본은_자주_묻는_순이다() {
        assertThat(ids(new FaqListRequest(MARKER, null, null, null, null)))
                .containsExactly(citedTwice, citedOnce, neverCited);
    }

    @Test
    @DisplayName("최근 수정 순은 수정 시각이 같으면 FAQ ID 역순이다")
    void 최근_수정_순으로_정렬한다() {
        // 한 트랜잭션에서 저장해 updatedAt이 모두 같다
        assertThat(ids(new FaqListRequest(MARKER, null, FaqSort.RECENT, null, null)))
                .containsExactly(neverCited, citedOnce, citedTwice);
    }

    @Test
    @DisplayName("카테고리로 거를 수 있다")
    void 카테고리로_거른다() {
        assertThat(ids(new FaqListRequest(MARKER, FaqCategory.BILLING, null, null, null)))
                .containsExactly(citedOnce);
    }

    @Test
    @DisplayName("상세는 공개 중인 FAQ의 답변을 주고, 숨김·삭제는 없는 FAQ처럼 404다")
    void 숨김_삭제는_상세에서_404다() {
        assertThat(service.getFaq(citedTwice).answer()).startsWith(MARKER);
        assertThatThrownBy(() -> service.getFaq(hidden)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.getFaq(deleted)).isInstanceOf(GeneralException.class);
    }

    private List<Long> ids(FaqListRequest request) {
        return service.getFaqs(request).faqs().stream().map(FaqResponse::faqId).toList();
    }

    private Long save(FaqCategory category, String question, Faq.Status status) {
        return faqRepository.saveAndFlush(Faq.builder()
                .category(category.name())
                .question(MARKER + " " + question)
                .answer(MARKER + " 답변입니다.")
                .status(status)
                .build()).getFaqId();
    }

    // 답변 근거로 쓰인 횟수만큼 message_sources에 넣는다
    private void cite(Long faqId, int times) {
        for (int i = 0; i < times; i++) {
            jdbcTemplate.update(
                    "INSERT INTO message_sources (message_id, faq_id, search_rank, score) VALUES (?, ?, ?, ?)",
                    SEED_MESSAGE_ID, faqId, 1, 0.9);
        }
    }
}