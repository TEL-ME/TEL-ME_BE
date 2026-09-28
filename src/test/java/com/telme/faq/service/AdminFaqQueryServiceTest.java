package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.dto.req.AdminFaqSearchRequest;
import com.telme.faq.dto.req.AdminFaqSort;
import com.telme.faq.dto.req.AdminFaqStatusFilter;
import com.telme.faq.dto.res.AdminFaqListItemResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqRepository;
import com.telme.global.common.exception.GeneralException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 로컬에 적재된 FAQ 1,150건과 섞이지 않도록 이 테스트 전용 카테고리로 거른다.
// 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminFaqQueryServiceTest {

    private static final String CATEGORY = "TEST-ADMIN-FAQ";
    private static final long SEED_MESSAGE_ID = 2L;

    @Autowired
    private AdminFaqQueryService service;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cited;
    private Long neverCited;
    private Long hidden;
    private Long percent;

    @BeforeEach
    void setUp() {
        cited = save("해지 위약금은 얼마인가요?", "남은 약정 개월수에 따라 달라집니다.", Faq.Status.ACTIVE);
        neverCited = save("eSIM은 무엇인가요?", "유심 없이 개통하는 방식입니다.", Faq.Status.ACTIVE);
        hidden = save("사용하지 않는 안내입니다.", "숨긴 문서입니다.", Faq.Status.HIDDEN);
        percent = save("결합 할인은 얼마인가요?", "회선당 10% 할인됩니다.", Faq.Status.ACTIVE);

        cite(cited);
        cite(cited);
    }

    @Test
    @DisplayName("한 번도 근거로 쓰이지 않은 FAQ도 인용 0건으로 목록에 나온다")
    void 인용_0건인_FAQ도_목록에_나온다() {
        List<AdminFaqListItemResponse> faqs = search(AdminFaqSort.RECENT, AdminFaqStatusFilter.ACTIVE, null).faqs();

        assertThat(faqs).extracting(AdminFaqListItemResponse::faqId)
                .containsExactlyInAnyOrder(cited, neverCited, percent);
        assertThat(citationOf(faqs, neverCited)).isZero();
        assertThat(citationOf(faqs, cited)).isEqualTo(2L);
    }

    @Test
    @DisplayName("인용 적은 순으로 정렬하면 인용 0건이 먼저 나온다")
    void 인용_적은_순으로_정렬한다() {
        List<AdminFaqListItemResponse> faqs =
                search(AdminFaqSort.CITATION_ASC, AdminFaqStatusFilter.ACTIVE, null).faqs();

        assertThat(faqs).extracting(AdminFaqListItemResponse::faqId).containsExactly(neverCited, percent, cited);
    }

    @Test
    @DisplayName("인용 많은 순으로 정렬하면 순서가 뒤집힌다")
    void 인용_많은_순으로_정렬한다() {
        List<AdminFaqListItemResponse> faqs =
                search(AdminFaqSort.CITATION_DESC, AdminFaqStatusFilter.ACTIVE, null).faqs();

        assertThat(faqs).extracting(AdminFaqListItemResponse::faqId).containsExactly(cited, neverCited, percent);
    }

    @Test
    @DisplayName("최근 수정 순은 수정 시각이 같으면 faqId 내림차순으로 가른다")
    void 최근_수정_순은_faqId로_동점을_가른다() {
        // 한 트랜잭션에서 저장해 updatedAt이 모두 같다. 두 번째 정렬 기준이 없으면 순서가 흔들린다
        assertThat(search(AdminFaqSort.RECENT, AdminFaqStatusFilter.ACTIVE, null).faqs())
                .extracting(AdminFaqListItemResponse::faqId)
                .containsExactly(percent, neverCited, cited);
    }

    @Test
    @DisplayName("인용순 정렬도 페이지를 나눠 조회하면 중복이나 누락이 없다")
    void 인용순_정렬도_페이징된다() {
        AdminFaqListResponse first = searchPage(AdminFaqSort.CITATION_ASC, 0, 2);
        AdminFaqListResponse second = searchPage(AdminFaqSort.CITATION_ASC, 1, 2);

        // group by 쿼리는 count 쿼리를 따로 주고 있어 전체 건수가 조인 결과로 부풀지 않는지 본다
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);

        List<Long> merged = Stream.concat(first.faqs().stream(), second.faqs().stream())
                .map(AdminFaqListItemResponse::faqId)
                .toList();
        assertThat(merged).containsExactly(neverCited, percent, cited);
    }

    @Test
    @DisplayName("상태를 주지 않으면 ACTIVE만 나오고 ALL이면 숨김까지 나온다")
    void 기본_상태는_ACTIVE만_조회한다() {
        assertThat(search(null, null, null).faqs()).extracting(AdminFaqListItemResponse::faqId)
                .doesNotContain(hidden);
        assertThat(search(null, AdminFaqStatusFilter.ALL, null).faqs()).extracting(AdminFaqListItemResponse::faqId)
                .contains(hidden);
    }

    @Test
    @DisplayName("검색어는 질문과 답변 본문에서 모두 찾는다")
    void 검색어로_질문과_답변을_찾는다() {
        assertThat(search(null, null, "eSIM").faqs()).extracting(AdminFaqListItemResponse::faqId)
                .containsExactly(neverCited);
        assertThat(search(null, null, "약정").faqs()).extracting(AdminFaqListItemResponse::faqId)
                .containsExactly(cited);
    }

    @Test
    @DisplayName("검색어의 %는 와일드카드가 아니라 글자 그대로 찾는다")
    void 검색어의_퍼센트를_글자로_찾는다() {
        assertThat(search(null, null, "10%").faqs()).extracting(AdminFaqListItemResponse::faqId)
                .containsExactly(percent);
        // 이스케이프가 없으면 패턴이 %%%가 되어 전체가 나온다
        assertThat(search(null, null, "%").faqs()).extracting(AdminFaqListItemResponse::faqId)
                .containsExactly(percent);
    }

    @Test
    @DisplayName("검색어의 _는 아무 글자나가 아니라 글자 그대로 찾는다")
    void 검색어의_언더바를_글자로_찾는다() {
        assertThat(search(null, null, "_").faqs()).isEmpty();
    }

    @Test
    @DisplayName("단건 조회는 인용 횟수를 함께 반환한다")
    void 단건_조회는_인용_횟수를_함께_준다() {
        assertThat(service.getFaq(cited).citationCount()).isEqualTo(2L);
        assertThat(service.getFaq(neverCited).citationCount()).isZero();
    }

    @Test
    @DisplayName("없는 FAQ를 조회하면 FAQ404-0을 던진다")
    void 없는_FAQ는_예외를_던진다() {
        assertThatThrownBy(() -> service.getFaq(-1L)).isInstanceOf(GeneralException.class);
    }

    private AdminFaqListResponse search(AdminFaqSort sort, AdminFaqStatusFilter status, String keyword) {
        return service.getFaqs(new AdminFaqSearchRequest(keyword, CATEGORY, status, sort, null, null));
    }

    private AdminFaqListResponse searchPage(AdminFaqSort sort, int page, int size) {
        return service.getFaqs(new AdminFaqSearchRequest(null, CATEGORY, null, sort, page, size));
    }

    private long citationOf(List<AdminFaqListItemResponse> faqs, Long faqId) {
        return faqs.stream()
                .filter(faq -> faq.faqId().equals(faqId))
                .findFirst()
                .orElseThrow()
                .citationCount();
    }

    private Long save(String question, String answer, Faq.Status status) {
        return faqRepository.saveAndFlush(Faq.builder()
                .category(CATEGORY)
                .question(question)
                .answer(answer)
                .status(status)
                .build()).getFaqId();
    }

    private void cite(Long faqId) {
        jdbcTemplate.update(
                "INSERT INTO message_sources (message_id, faq_id, search_rank, score) VALUES (?, ?, ?, ?)",
                SEED_MESSAGE_ID, faqId, 1, 0.9);
    }
}
