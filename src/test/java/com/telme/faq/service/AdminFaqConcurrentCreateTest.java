package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.entity.FaqCategory;
import com.telme.global.common.exception.GeneralException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 동시 요청을 재현하려면 트랜잭션이 둘로 나뉘어야 해서 @Transactional을 쓰지 않는다.
// 대신 남은 행을 직접 지운다. 임베딩은 Ollama를 부르므로 목으로 둔다
@SpringBootTest
class AdminFaqConcurrentCreateTest {

    private static final Long ADMIN_ID = 2L;
    private static final String MARK = "동시등록확인용";
    private static final String QUESTION = MARK + " 질문입니다";
    private static final String ANSWER = MARK + " 답변입니다.";

    @Autowired
    private AdminFaqCommandService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FaqEmbeddingSyncService embeddingSyncService;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM faq_embeddings WHERE faq_id IN (SELECT faq_id FROM faqs WHERE question LIKE ?)",
                MARK + "%");
        jdbcTemplate.update("DELETE FROM faqs WHERE question LIKE ?", MARK + "%");
    }

    @Test
    @DisplayName("같은 내용을 동시에 등록해도 한 건만 남는다")
    void 동시에_등록해도_한_건만_남는다() throws Exception {
        List<Future<Object>> results = runTogether(
                () -> service.create(request(), ADMIN_ID),
                () -> service.create(request(), ADMIN_ID));

        long failed = results.stream().filter(this::threw).count();
        assertThat(failed).isEqualTo(1);
        assertThat(countSaved()).isEqualTo(1);
    }

    @Test
    @DisplayName("인덱스가 같은 내용의 두 번째 행을 막는다")
    void 인덱스가_중복_행을_막는다() {
        service.create(request(), ADMIN_ID);

        // 앱 검사를 거치지 않고 DB에 바로 넣어 인덱스만 확인한다
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO faqs (category, question, answer, version, status) VALUES (?, ?, ?, 1, 'ACTIVE')",
                FaqCategory.SERVICE.name(), QUESTION, ANSWER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("삭제한 뒤에는 같은 내용을 다시 넣을 수 있다")
    void 삭제_후에는_다시_넣을_수_있다() {
        Long faqId = service.create(request(), ADMIN_ID).faqId();
        service.delete(faqId, ADMIN_ID);

        service.create(request(), ADMIN_ID);

        assertThat(countSaved()).isEqualTo(2);
    }

    private List<Future<Object>> runTogether(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    pool.submit(waitThen(start, first)),
                    pool.submit(waitThen(start, second)));
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Object> waitThen(CountDownLatch start, Callable<Object> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    private boolean threw(Future<Object> future) {
        try {
            future.get();
            return false;
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(GeneralException.class);
            return true;
        }
    }

    private int countSaved() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM faqs WHERE question LIKE ?", Integer.class, MARK + "%");
    }

    private AdminFaqSaveRequest request() {
        return new AdminFaqSaveRequest(FaqCategory.SERVICE, QUESTION, ANSWER, null, null);
    }
}
