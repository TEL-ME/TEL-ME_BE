package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.service.FaqBatchLoader.LoadResult;
import com.telme.global.common.exception.GeneralException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 청크 단위 커밋을 검증해야 해서 @Transactional을 쓰지 않는다. 대신 @AfterEach에서 테스트가 넣은 행을 지운다.
// 애너테이션은 다른 FAQ 통합 테스트와 같게 맞춰 Spring 컨텍스트를 재사용한다
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "faq.search-test-api-enabled=true")
class FaqBatchLoaderTest {

    // 5건이 3청크로 나뉘도록
    private static final int BATCH = 2;

    private static final String MARK = "[FaqBatchLoaderTest]";

    @Autowired
    private FaqBatchLoader loader;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FaqEmbeddingTextAssembler textAssembler;
    @MockitoBean
    private EmbeddingClient embeddingClient;
    @TempDir
    Path tempDir;

    @BeforeEach
    void stubEmbedding() {
        // doAnswer 형태여야 직전 스텁이 thenThrow인 상태에서도 다시 스텁할 수 있다
        doAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                out.add(new float[1024]);
            }
            return out;
        }).when(embeddingClient).embedBatch(anyList());
    }

    @AfterEach
    void cleanup() {
        // faq_embeddings는 ON DELETE CASCADE
        jdbcTemplate.update("DELETE FROM faqs WHERE question LIKE ?", MARK + "%");
    }

    private Path write(String name, List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"slot_id\":\"").append(r[3]).append("\",")
                    .append("\"trigger\":\"무시\",\"question_type\":\"FACT\",\"persona\":\"NOVICE\",")
                    .append("\"category\":\"").append(r[0]).append("\",")
                    .append("\"question\":\"").append(r[1]).append("\",")
                    .append("\"answer\":\"").append(r[2]).append("\",")
                    .append("\"policy_ref\":\"USIM-01\"}");
        }
        sb.append(']');
        Path p = tempDir.resolve(name);
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
        return p;
    }

    // {category, question, answer, slot_id}
    private static String[] row(int n) {
        return new String[]{"USIM", MARK + " 질문 " + n, "답변 " + n, "LOADER-TEST-" + n};
    }

    private int countLoaded() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM faqs f JOIN faq_embeddings e ON e.faq_id = f.faq_id WHERE f.question LIKE ?",
                Integer.class, MARK + "%");
    }

    @Test
    @DisplayName("파일의 건을 faqs와 faq_embeddings에 함께 넣고, slot_id는 저장하고 나머지 메타데이터 필드는 무시한다")
    void 적재하면_두_테이블에_같이_들어간다() throws IOException {
        LoadResult result = loader.load(write("a.json", List.of(row(1), row(2), row(3))), BATCH);

        assertThat(result).isEqualTo(new LoadResult(3, 0, 0, 0, 3));
        assertThat(countLoaded()).isEqualTo(3);
        Integer version = jdbcTemplate.queryForObject(
                "SELECT e.faq_version FROM faq_embeddings e JOIN faqs f ON f.faq_id = e.faq_id WHERE f.question = ?",
                Integer.class, MARK + " 질문 1");
        assertThat(version).isEqualTo(1);
        String hash = jdbcTemplate.queryForObject(
                "SELECT content_hash FROM faqs WHERE question = ?", String.class, MARK + " 질문 1");
        assertThat(hash).isEqualTo(FaqContentHash.of(MARK + " 질문 1", "답변 1"));
        String slotId = jdbcTemplate.queryForObject(
                "SELECT slot_id FROM faqs WHERE question = ?", String.class, MARK + " 질문 1");
        assertThat(slotId).isEqualTo("LOADER-TEST-1");
    }

    @Test
    @DisplayName("같은 파일을 다시 적재하면 slot_id가 같은 건은 건너뛴다")
    void 재실행하면_건너뛴다() throws IOException {
        Path p = write("a.json", List.of(row(1), row(2), row(3)));
        loader.load(p, BATCH);

        LoadResult second = loader.load(p, BATCH);

        assertThat(second).isEqualTo(new LoadResult(3, 0, 3, 0, 0));
        assertThat(countLoaded()).isEqualTo(3);
    }

    @Test
    @DisplayName("증분 파일(기존 3 + 신규 2)은 신규 2건만 들어간다")
    void 증분_적재() throws IOException {
        loader.load(write("a.json", List.of(row(1), row(2), row(3))), BATCH);

        LoadResult result = loader.load(write("b.json", List.of(row(1), row(2), row(3), row(4), row(5))), BATCH);

        assertThat(result).isEqualTo(new LoadResult(5, 0, 3, 0, 2));
        assertThat(countLoaded()).isEqualTo(5);
    }

    @Test
    @DisplayName("파일 안 중복은 첫 건만 넣고 건수를 따로 센다")
    void 파일_내_중복() throws IOException {
        LoadResult result = loader.load(write("d.json", List.of(row(1), row(1), row(2))), BATCH);

        assertThat(result).isEqualTo(new LoadResult(3, 1, 0, 0, 2));
        assertThat(countLoaded()).isEqualTo(2);
    }

    @Test
    @DisplayName("answer가 빈 건이 하나라도 있으면 DB를 건드리기 전에 실패한다")
    void 형식_오류면_아무것도_안_넣는다() throws IOException {
        Path p = write("e.json", List.of(row(1), new String[]{"USIM", MARK + " 질문 X", "", "LOADER-TEST-X"}));

        assertThatThrownBy(() -> loader.load(p, BATCH))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(FaqErrorCode.LOAD_FILE_INVALID);
        assertThat(countLoaded()).isZero();
    }

    @Test
    @DisplayName("같은 slot_id로 내용이 바뀐 건이 들어와도 DB 내용을 덮어쓰지 않는다")
    void 이미_있는_slot은_내용이_달라도_건너뛴다() throws IOException {
        loader.load(write("a.json", List.<String[]>of(row(1))), BATCH);

        LoadResult result = loader.load(write("b.json",
                List.<String[]>of(new String[]{"USIM", MARK + " 질문 1", "바뀐 답변", "LOADER-TEST-1"})), BATCH);

        assertThat(result).isEqualTo(new LoadResult(1, 0, 1, 0, 0));
        assertThat(countLoaded()).isEqualTo(1);
        String answer = jdbcTemplate.queryForObject(
                "SELECT answer FROM faqs WHERE slot_id = ?", String.class, "LOADER-TEST-1");
        assertThat(answer).isEqualTo("답변 1");
    }

    @Test
    @DisplayName("slot_id가 없는 기존 행은 content_hash가 같으면 새로 넣지 않고 slot_id만 채운다")
    void slot_id가_없는_행은_content_hash로_채운다() throws IOException {
        loader.load(write("a.json", List.of(row(1), row(2))), BATCH);
        jdbcTemplate.update("UPDATE faqs SET slot_id = NULL WHERE question LIKE ?", MARK + "%");
        Long faqId = jdbcTemplate.queryForObject(
                "SELECT faq_id FROM faqs WHERE question = ?", Long.class, MARK + " 질문 1");
        clearInvocations(embeddingClient);

        LoadResult result = loader.load(write("b.json", List.of(row(1), row(2), row(3))), BATCH);

        assertThat(result).isEqualTo(new LoadResult(3, 0, 0, 2, 1));
        assertThat(countLoaded()).isEqualTo(3);
        Long sameFaqId = jdbcTemplate.queryForObject(
                "SELECT faq_id FROM faqs WHERE slot_id = ?", Long.class, "LOADER-TEST-1");
        assertThat(sameFaqId).isEqualTo(faqId);
        // 채운 행은 재임베딩하지 않고 신규 1건만 임베딩한다
        verify(embeddingClient).embedBatch(List.of(
                textAssembler.assemble("USIM", MARK + " 질문 3", "답변 3")));
    }

    @Test
    @DisplayName("파일 안에서 slot_id가 같으면 내용이 달라도 첫 건만 넣는다")
    void 파일_내_slot_중복() throws IOException {
        LoadResult result = loader.load(write("c.json", List.of(row(1),
                new String[]{"USIM", MARK + " 질문 9", "답변 9", "LOADER-TEST-1"})), BATCH);

        assertThat(result).isEqualTo(new LoadResult(2, 1, 0, 0, 1));
        String question = jdbcTemplate.queryForObject(
                "SELECT question FROM faqs WHERE slot_id = ?", String.class, "LOADER-TEST-1");
        assertThat(question).isEqualTo(MARK + " 질문 1");
    }

    @Test
    @DisplayName("slot_id가 빈 건이 하나라도 있으면 DB를 건드리기 전에 실패한다")
    void slot_id가_없으면_아무것도_안_넣는다() throws IOException {
        Path p = write("e.json", List.of(row(1), new String[]{"USIM", MARK + " 질문 X", "답변 X", ""}));

        assertThatThrownBy(() -> loader.load(p, BATCH))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(FaqErrorCode.LOAD_FILE_INVALID);
        assertThat(countLoaded()).isZero();
    }

    @Test
    @DisplayName("두 번째 청크에서 실패해도 첫 청크는 커밋되어 남고, 재실행하면 이어서 적재한다")
    void 청크_단위로_커밋된다() throws IOException {
        when(embeddingClient.embedBatch(anyList()))
                .thenReturn(List.of(new float[1024], new float[1024]))
                .thenThrow(new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED));
        Path p = write("f.json", List.of(row(1), row(2), row(3), row(4), row(5)));

        assertThatThrownBy(() -> loader.load(p, BATCH)).isInstanceOf(GeneralException.class);
        assertThat(countLoaded()).isEqualTo(2);

        stubEmbedding();
        LoadResult resumed = loader.load(p, BATCH);
        assertThat(resumed).isEqualTo(new LoadResult(5, 0, 2, 0, 3));
        assertThat(countLoaded()).isEqualTo(5);
    }
}
