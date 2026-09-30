package com.telme.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.telme.store.config.StoreSearchProperties;
import com.telme.store.entity.StoreServiceType;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 필터 없는 KNN과 필터가 있을 때의 반경 검색이 같은 데이터에서 같은 결과를 내는지 확인한다.
// KNN은 반경 없이 가장 가까운 매장을 찾으므로 멀리 있는 시드 매장이 섞이지 않게 요청 개수를 테스트 매장 수에 맞추고,
// 반경 규칙은 반경 검색에서만 확인한다
// 시드 매장(dev-migration)과 섞이지 않도록 매장이 없는 서해 해상 좌표를 기준점으로 쓰고, 트랜잭션 롤백으로 흔적을 남기지 않는다
@SpringBootTest
@Transactional
class StoreNearbyQueryRepositoryDatabaseTest {

    private static final double BASE_LATITUDE = 36.0;
    private static final double BASE_LONGITUDE = 124.5;
    // 위도 0.001도 ≈ 111.2m
    private static final double METERS_PER_MILLI_DEGREE = 111.195;

    @Autowired
    private StoreNearbyQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    // NEAREST는 필터 없는 검색 전용, NEAREST_MATCHING은 필터 있는 검색 전용이다
    enum Method { NEAREST, MATCHING_WITHIN_RADIUS, NEAREST_MATCHING }

    private static final List<StoreSearchCondition> USIM = List.of(StoreTagCondition.of(
            List.of(StoreTag.USIM_REISSUE)));

    // 영업 중 조건이 있을 때 먼저 쓰는 후보 확인 경로와, 후보로 못 채웠을 때 쓰는 반경 전체 확인 경로
    enum DynamicMethod { NEAREST_CANDIDATES_MATCHING, MATCHING_WITHIN_RADIUS }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"NEAREST", "MATCHING_WITHIN_RADIUS"})
    @DisplayName("가까운 순으로 정렬하고 거리를 함께 반환한다")
    void 가까운_순으로_정렬한다(Method method) {
        long far = insertStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");
        long near = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long middle = insertStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");

        List<StoreNearbyQueryRepository.Row> rows = search(method, 1000, 3, List.of());

        assertThat(rows).extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(near, middle, far);
        assertThat(rows.get(0).distanceMeters()).isCloseTo(METERS_PER_MILLI_DEGREE, within(1.0));
        assertThat(rows.get(2).distanceMeters()).isCloseTo(3 * METERS_PER_MILLI_DEGREE, within(1.0));
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"NEAREST", "MATCHING_WITHIN_RADIUS"})
    @DisplayName("거리가 같으면 매장 ID 순으로 정렬한다")
    void 거리가_같으면_ID순(Method method) {
        long first = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long second = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");

        assertThat(search(method, 1000, 2, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(first, second);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"NEAREST", "MATCHING_WITHIN_RADIUS"})
    @DisplayName("OPEN이 아닌 매장은 가장 가까워도 제외한다")
    void OPEN이_아닌_매장은_제외한다(Method method) {
        insertStore(BASE_LATITUDE, BASE_LONGITUDE, "CLOSED_DOWN");
        insertStore(BASE_LATITUDE, BASE_LONGITUDE, "DELETED");
        long open = insertStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");

        assertThat(search(method, 1000, 1, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(open);
    }

    @Test
    @DisplayName("반경 검색: 반경 밖 매장은 방향과 관계없이 제외하고, 대각선 경계 근처 매장은 포함한다")
    void 반경_경계() {
        Method method = Method.MATCHING_WITHIN_RADIUS;
        long inside = insertStore(BASE_LATITUDE + 0.008, BASE_LONGITUDE, "OPEN");
        // 북동쪽 약 992m
        long diagonal = insertStore(BASE_LATITUDE + 0.0063, BASE_LONGITUDE + 0.0078, "OPEN");
        insertStore(BASE_LATITUDE + 0.010, BASE_LONGITUDE, "OPEN");
        // 위도 36도에서 경도 0.012도 ≈ 1,080m
        insertStore(BASE_LATITUDE, BASE_LONGITUDE + 0.012, "OPEN");

        assertThat(search(method, 1000, 20, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(inside, diagonal);
    }

    @Test
    @DisplayName("반경 검색: 반경 안에 매장이 없으면 빈 목록을 반환한다")
    void 반경_안에_없으면_빈_목록() {
        insertStore(BASE_LATITUDE + 0.05, BASE_LONGITUDE, "OPEN");

        assertThat(search(Method.MATCHING_WITHIN_RADIUS, 1000, 20, List.of())).isEmpty();
    }

    @Test
    @DisplayName("KNN: 반경과 관계없이 가장 가까운 영업 매장을 반환한다")
    void KNN은_반경을_보지_않는다() {
        // 약 50km 떨어진 매장
        long far = insertStore(BASE_LATITUDE + 0.45, BASE_LONGITUDE, "OPEN");

        assertThat(search(Method.NEAREST, 1000, 1, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(far);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"NEAREST", "MATCHING_WITHIN_RADIUS"})
    @DisplayName("요청한 개수만큼만 반환한다")
    void 개수_제한(Method method) {
        long first = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long second = insertStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");
        insertStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");

        assertThat(search(method, 1000, 2, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(first, second);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("업무를 여러 개 고르면 모두 처리할 수 있는 매장만 남긴다")
    void 업무_조건은_모두_만족해야_한다(Method method) {
        long both = insertStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");
        addServices(both, StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.PORT_IN);
        long usimOnly = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        addServices(usimOnly, StoreServiceType.Code.USIM_REISSUE);
        insertStore(BASE_LATITUDE + 0.0005, BASE_LONGITUDE, "OPEN");

        List<StoreSearchCondition> usimAndPortIn = List.of(StoreTagCondition.of(
                List.of(StoreTag.USIM_REISSUE, StoreTag.PORT_IN)));

        assertThat(search(method, 1000, 20, usimAndPortIn))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(both);
        assertThat(search(method, 1000, 20, USIM))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(usimOnly, both);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"NEAREST", "MATCHING_WITHIN_RADIUS"})
    @DisplayName("관리자가 좌표를 고치면 바뀐 좌표로 검색한다")
    void 수정된_좌표로_검색한다(Method method) {
        long moved = insertStore(BASE_LATITUDE + 0.5, BASE_LONGITUDE, "OPEN");
        jdbcTemplate.update("UPDATE stores SET latitude = ?, longitude = ? WHERE store_id = ?",
                BASE_LATITUDE + 0.001, BASE_LONGITUDE, moved);

        assertThat(search(method, 1000, 1, List.of()))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(moved);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("필터 경로: 조건 맞는 매장을 가까운 순, 거리가 같으면 ID 순으로 반환한다")
    void 필터_경로_정렬(Method method) {
        long far = insertUsimStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE);
        long near = insertUsimStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE);
        long nearTwin = insertUsimStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE);
        insertStore(BASE_LATITUDE + 0.0005, BASE_LONGITUDE, "OPEN");

        assertThat(search(method, 1000, 20, USIM))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(near, nearTwin, far);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("필터 경로: 조건이 맞아도 영업 중이 아니면 제외한다")
    void 필터_경로_폐업_제외(Method method) {
        long closed = insertUsimStore(BASE_LATITUDE, BASE_LONGITUDE);
        jdbcTemplate.update("UPDATE stores SET status = 'CLOSED_DOWN' WHERE store_id = ?", closed);
        long open = insertUsimStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE);

        assertThat(search(method, 1000, 20, USIM))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(open);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("필터 경로: 반경 밖은 방향과 관계없이 제외하고, 대각선 경계 근처는 포함한다")
    void 필터_경로_반경_경계(Method method) {
        long inside = insertUsimStore(BASE_LATITUDE + 0.008, BASE_LONGITUDE);
        // 북동쪽 약 992m
        long diagonal = insertUsimStore(BASE_LATITUDE + 0.0063, BASE_LONGITUDE + 0.0078);
        insertUsimStore(BASE_LATITUDE + 0.010, BASE_LONGITUDE);
        // 위도 36도에서 경도 0.012도 ≈ 1,080m
        insertUsimStore(BASE_LATITUDE, BASE_LONGITUDE + 0.012);

        assertThat(search(method, 1000, 20, USIM))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(inside, diagonal);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("필터 경로: 반경 안에 조건 맞는 매장이 없으면 빈 목록을 반환한다")
    void 필터_경로_빈_결과(Method method) {
        insertUsimStore(BASE_LATITUDE + 0.05, BASE_LONGITUDE);
        insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");

        assertThat(search(method, 1000, 20, USIM)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Method.class)
    @DisplayName("요청 개수 경계에서 같은 좌표(같은 건물) 매장이 동점이면 매장 ID가 작은 매장을 넣는다")
    void 경계_동점은_ID순(Method method) {
        List<StoreSearchCondition> conditions = method == Method.NEAREST ? List.of() : USIM;
        long near = insertUsimStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE);
        long first = insertUsimStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE);
        long second = insertUsimStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE);
        long third = insertUsimStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE);

        assertThat(search(method, 1000, 2, conditions))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(near, first);
        assertThat(search(method, 1000, 3, conditions))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(near, first, second);
        assertThat(search(method, 1000, 4, conditions))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(near, first, second, third);
    }

    @Test
    @DisplayName("KNN 필터 경로: 가까운 곳에 조건 안 맞는 매장이 많아도 조건 맞는 매장만 찾는다")
    void KNN_필터_경로는_조건_안_맞는_매장을_건너뛴다() {
        for (int i = 1; i <= 30; i++) {
            insertStore(BASE_LATITUDE + 0.0001 * i, BASE_LONGITUDE, "OPEN");
        }
        long usim = insertUsimStore(BASE_LATITUDE + 0.005, BASE_LONGITUDE);

        assertThat(search(Method.NEAREST_MATCHING, 1000, 1, USIM))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(usim);
    }

    @Test
    @DisplayName("업무를 더하고 빼거나 바꾸면 트리거가 매장 태그를 맞춘다")
    void 트리거가_태그를_맞춘다() {
        long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        assertThat(tags(storeId)).isEmpty();

        addServices(storeId, StoreServiceType.Code.USIM_REISSUE, StoreServiceType.Code.PORT_IN);
        assertThat(tags(storeId)).containsExactly(StoreTag.PORT_IN.id(), StoreTag.USIM_REISSUE.id());

        jdbcTemplate.update("""
                DELETE FROM store_services WHERE store_id = ? AND service_type_id =
                    (SELECT service_type_id FROM store_service_types WHERE code = 'PORT_IN')
                """, storeId);
        assertThat(tags(storeId)).containsExactly(StoreTag.USIM_REISSUE.id());

        addServices(storeId, StoreServiceType.Code.NEW_LINE);
        jdbcTemplate.update("""
                UPDATE store_services SET service_type_id =
                    (SELECT service_type_id FROM store_service_types WHERE code = 'NAME_CHANGE')
                WHERE store_id = ? AND service_type_id =
                    (SELECT service_type_id FROM store_service_types WHERE code = 'NEW_LINE')
                """, storeId);
        assertThat(tags(storeId)).containsExactly(StoreTag.NAME_CHANGE.id(), StoreTag.USIM_REISSUE.id());
    }

    @Test
    @DisplayName("업무별 태그 번호는 Java와 DB 트리거가 같다")
    void 태그_번호는_Java와_DB가_같다() {
        for (StoreServiceType.Code code : StoreServiceType.Code.values()) {
            long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
            addServices(storeId, code);

            assertThat(tags(storeId)).as(code.name()).containsExactly(StoreTag.of(code).id());
        }
    }

    @Test
    @DisplayName("태그 동기화는 매장 수정 시각을 바꾸지 않고, 매장 정보를 직접 고치면 바꾼다")
    void 태그_동기화는_수정_시각을_바꾸지_않는다() {
        // 한 트랜잭션 안에서는 now()가 같아서, 과거 시각으로 넣어 두고 바뀌는지 본다
        long storeId = jdbcTemplate.queryForObject("""
                INSERT INTO stores (name, address, latitude, longitude, status, updated_at)
                VALUES ('테스트 매장', '테스트 주소', ?, ?, 'OPEN', '2000-01-01T00:00:00Z')
                RETURNING store_id
                """, Long.class, BASE_LATITUDE, BASE_LONGITUDE);

        addServices(storeId, StoreServiceType.Code.USIM_REISSUE);
        assertThat(tags(storeId)).containsExactly(StoreTag.USIM_REISSUE.id());
        assertThat(updatedAtYear(storeId)).isEqualTo(2000);

        jdbcTemplate.update("UPDATE stores SET name = '바뀐 매장' WHERE store_id = ?", storeId);
        assertThat(updatedAtYear(storeId)).isNotEqualTo(2000);
    }

    @Test
    @DisplayName("업무가 있는 매장을 지우면 업무도 함께 지워진다(태그 트리거가 삭제를 막지 않는다)")
    void 업무가_있는_매장을_지울_수_있다() {
        long storeId = insertUsimStore(BASE_LATITUDE, BASE_LONGITUDE);

        jdbcTemplate.update("DELETE FROM stores WHERE store_id = ?", storeId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store_services WHERE store_id = ?", Integer.class, storeId)).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"MATCHING_WITHIN_RADIUS", "NEAREST_MATCHING"})
    @DisplayName("정적 조건이 늘어도(업무 외 태그) 같은 태그 조건과 인덱스로 모두 만족하는 매장만 찾는다")
    void 늘어난_정적_조건(Method method) {
        long all = insertStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");
        long partial = insertStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        // 업무 외 정적 조건의 원천은 아직 없어 태그를 직접 넣는다. 트리거는 store_services가 바뀔 때만 태그를 다시 계산한다
        jdbcTemplate.update("UPDATE stores SET tags = '{4,17,23,31}' WHERE store_id = ?", all);
        jdbcTemplate.update("UPDATE stores SET tags = '{4,17,31}' WHERE store_id = ?", partial);

        assertThat(search(method, 1000, 20, List.of(new StoreTagCondition(new TreeSet<>(List.of(4, 17, 23, 31))))))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(all);
        assertThat(search(method, 1000, 20, List.of(new StoreTagCondition(new TreeSet<>(List.of(17, 31))))))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(partial, all);
    }

    @Test
    @DisplayName("태그 조건은 (geog, tags) 공간 인덱스 안에서 걸러진다")
    void 태그_조건은_인덱스_안에서_걸러진다() {
        // 흔한 태그는 옵티마이저가 영업 매장 인덱스 + 필터를 고를 수 있어, 태그 인덱스를 쓸 수 있는지만 보려고
        // 영업 매장 인덱스를 트랜잭션 안에서 지운다(롤백으로 되돌아간다). 데이터가 적어 전체 읽기도 끈다
        jdbcTemplate.execute("DROP INDEX idx_stores_geog_open");
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
        String plan = jdbcTemplate.queryForObject("""
                EXPLAIN (FORMAT JSON) SELECT s.store_id FROM stores s
                WHERE s.status = 'OPEN' AND %s
                ORDER BY s.geog <-> ST_SetSRID(ST_MakePoint(127.0, 37.5), 4326)::geography LIMIT 5
                """.formatted(StoreTagCondition.of(List.of(StoreTag.PORT_IN, StoreTag.USIM_REISSUE)).toSql()),
                String.class);

        assertThat(plan).contains("\"idx_stores_geog_tags\"").contains("\"Index Cond\": \"(tags @> '{2,4}'");
    }

    @Test
    @DisplayName("쿼리 타임아웃을 넘기면 PostgreSQL이 쿼리를 취소한다")
    void 쿼리_타임아웃() {
        StoreNearbyQueryRepository oneSecond = new StoreNearbyQueryRepository(dataSource,
                new StoreSearchProperties(10000, 10000, 5, 20, Duration.ofSeconds(1), false));
        // 1.5초 걸리는 조건. 극단 조건에서 쿼리가 오래 걸리는 상황을 흉내 낸다
        StoreSearchCondition slow = new StoreSearchCondition() {
            @Override
            public String toSql() {
                return "(SELECT pg_sleep(1.5)) IS NOT NULL";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of();
            }
        };
        insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");

        long start = System.nanoTime();
        assertThatThrownBy(() -> oneSecond.findMatchingWithinRadius(query(1000, 5, List.of(slow))))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(((DataAccessException) e).getMostSpecificCause())
                        .isInstanceOfSatisfying(SQLException.class,
                                sql -> assertThat(sql.getSQLState()).isEqualTo("57014")));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중: 여는 시각은 포함하고 닫는 시각은 제외한다")
    void 영업_중_경계(DynamicMethod method) {
        long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(storeId, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);

        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "09:59:59")).isEmpty();
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "10:00")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "20:59:59")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "21:00")).isEmpty();
        assertThat(openNowAt(method, DayOfWeek.THURSDAY, "15:00")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중: 자정을 넘기는 영업은 다음 날 새벽까지 전날 영업시간으로 판단한다")
    void 자정을_넘기는_영업(DynamicMethod method) {
        long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(storeId, DayOfWeek.TUESDAY, "18:00", "02:00", false);

        assertThat(openNowAt(method, DayOfWeek.TUESDAY, "17:59")).isEmpty();
        assertThat(openNowAt(method, DayOfWeek.TUESDAY, "23:30")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "01:30")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "02:00")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중: 월요일 새벽은 일요일(7) 영업시간으로 판단한다")
    void 월요일_새벽은_일요일_영업시간(DynamicMethod method) {
        long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(storeId, DayOfWeek.SUNDAY, "20:00", "03:00", false);

        assertThat(openNowAt(method, DayOfWeek.MONDAY, "02:59")).containsExactly(storeId);
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중: 00:00~00:00은 24시간 영업으로 본다")
    void 스물네시간_영업(DynamicMethod method) {
        long storeId = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(storeId, DayOfWeek.WEDNESDAY, "00:00", "00:00", false);

        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "00:00")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "23:59:59")).containsExactly(storeId);
        assertThat(openNowAt(method, DayOfWeek.THURSDAY, "00:00")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중: 휴무일, 영업시간 행 없음, 시각 비어 있음은 영업 중이 아니다")
    void 영업시간을_모르면_제외한다(DynamicMethod method) {
        long holiday = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(holiday, DayOfWeek.WEDNESDAY, "10:00", "21:00", true);
        insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        long blank = insertStore(BASE_LATITUDE, BASE_LONGITUDE, "OPEN");
        insertHours(blank, DayOfWeek.WEDNESDAY, null, null, false);

        assertThat(openNowAt(method, DayOfWeek.WEDNESDAY, "15:00")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DynamicMethod.class)
    @DisplayName("영업 중 + 업무: 두 조건을 모두 만족하는 매장을 가까운 순으로, 반경 안에서만 반환한다")
    void 영업_중과_업무_조건(DynamicMethod method) {
        long closedUsim = insertUsimStore(BASE_LATITUDE + 0.001, BASE_LONGITUDE);
        insertHours(closedUsim, DayOfWeek.WEDNESDAY, "10:00", "12:00", false);
        long openPlain = insertStore(BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");
        insertHours(openPlain, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);
        long openUsimFar = insertUsimStore(BASE_LATITUDE + 0.004, BASE_LONGITUDE);
        insertHours(openUsimFar, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);
        long openUsimNear = insertUsimStore(BASE_LATITUDE + 0.003, BASE_LONGITUDE);
        insertHours(openUsimNear, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);
        long outside = insertUsimStore(BASE_LATITUDE + 0.010, BASE_LONGITUDE);
        insertHours(outside, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);

        List<StoreSearchCondition> conditions = List.of(USIM.get(0), openNow(DayOfWeek.WEDNESDAY, "15:00"));

        assertThat(searchDynamic(method, 1000, 20, conditions))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(openUsimNear, openUsimFar);
    }

    @Test
    @DisplayName("후보 확인: 가까운 후보가 모두 영업 종료면 후보 밖 영업 매장이 있어도 못 채운다(서비스가 반경 전체로 다시 찾는다)")
    void 후보_확인은_후보_밖을_보지_않는다() {
        for (int i = 1; i <= 3; i++) {
            long closed = insertStore(BASE_LATITUDE + 0.001 * i, BASE_LONGITUDE, "OPEN");
            insertHours(closed, DayOfWeek.WEDNESDAY, "10:00", "12:00", false);
        }
        long open = insertStore(BASE_LATITUDE + 0.005, BASE_LONGITUDE, "OPEN");
        insertHours(open, DayOfWeek.WEDNESDAY, "10:00", "21:00", false);
        StoreNearbyQueryRepository.Query query = query(1000, 1, List.of(openNow(DayOfWeek.WEDNESDAY, "15:00")));

        assertThat(repository.findNearestCandidatesMatching(query, 3)).isEmpty();
        assertThat(repository.findNearestCandidatesMatching(query, 4))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(open);
        assertThat(repository.findMatchingWithinRadius(query))
                .extracting(StoreNearbyQueryRepository.Row::storeId).containsExactly(open);
    }

    @Test
    @DisplayName("후보 확인: 인덱스로 처리할 조건만 있거나 후보 수가 요청 개수보다 적으면 거부한다")
    void 후보_확인_입력_검증() {
        // @Repository 예외 변환으로 IllegalArgumentException 이 InvalidDataAccessApiUsageException 으로 바뀐다
        assertThatThrownBy(() -> repository.findNearestCandidatesMatching(query(1000, 5, USIM), 50))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> repository.findNearestCandidatesMatching(
                query(1000, 5, List.of(openNow(DayOfWeek.WEDNESDAY, "15:00"))), 4))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    private List<StoreNearbyQueryRepository.Row> search(
            Method method, int radiusMeters, int limit, List<StoreSearchCondition> conditions
    ) {
        StoreNearbyQueryRepository.Query query = query(radiusMeters, limit, conditions);
        return switch (method) {
            case NEAREST -> repository.findNearest(query);
            case MATCHING_WITHIN_RADIUS -> repository.findMatchingWithinRadius(query);
            case NEAREST_MATCHING -> repository.findNearestMatching(query);
        };
    }

    private List<StoreNearbyQueryRepository.Row> searchDynamic(
            DynamicMethod method, int radiusMeters, int limit, List<StoreSearchCondition> conditions
    ) {
        StoreNearbyQueryRepository.Query query = query(radiusMeters, limit, conditions);
        return switch (method) {
            case NEAREST_CANDIDATES_MATCHING -> repository.findNearestCandidatesMatching(query, limit * 10);
            case MATCHING_WITHIN_RADIUS -> repository.findMatchingWithinRadius(query);
        };
    }

    private List<Long> openNowAt(DynamicMethod method, DayOfWeek dayOfWeek, String time) {
        return searchDynamic(method, 1000, 20, List.of(openNow(dayOfWeek, time))).stream()
                .map(StoreNearbyQueryRepository.Row::storeId)
                .toList();
    }

    private OpenNowCondition openNow(DayOfWeek dayOfWeek, String time) {
        return new OpenNowCondition(dayOfWeek, LocalTime.parse(time));
    }

    private StoreNearbyQueryRepository.Query query(int radiusMeters, int limit, List<StoreSearchCondition> conditions) {
        return new StoreNearbyQueryRepository.Query(BASE_LATITUDE, BASE_LONGITUDE, radiusMeters, limit, conditions);
    }

    private long insertStore(double latitude, double longitude, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO stores (name, phone, address, latitude, longitude, status)
                VALUES ('테스트 매장', '070-0000-0000', '테스트 주소', ?, ?, ?)
                RETURNING store_id
                """, Long.class, latitude, longitude, status);
    }

    private long insertUsimStore(double latitude, double longitude) {
        long storeId = insertStore(latitude, longitude, "OPEN");
        addServices(storeId, StoreServiceType.Code.USIM_REISSUE);
        return storeId;
    }

    private void insertHours(long storeId, DayOfWeek dayOfWeek, String open, String close, boolean closed) {
        jdbcTemplate.update("""
                INSERT INTO store_hours (store_id, day_of_week, open_time, close_time, is_closed)
                VALUES (?, ?, ?::time, ?::time, ?)
                """, storeId, dayOfWeek.getValue(), open, close, closed);
    }

    private int updatedAtYear(long storeId) {
        return jdbcTemplate.queryForObject(
                "SELECT extract(year FROM updated_at AT TIME ZONE 'UTC')::int FROM stores WHERE store_id = ?",
                Integer.class, storeId);
    }

    private List<Integer> tags(long storeId) {
        return jdbcTemplate.queryForList(
                "SELECT unnest(tags) FROM stores WHERE store_id = ? ORDER BY 1", Integer.class, storeId);
    }

    // 업무 종류는 dev 시드에만 있어 없으면 넣는다
    private void addServices(long storeId, StoreServiceType.Code... codes) {
        for (StoreServiceType.Code code : codes) {
            jdbcTemplate.update("""
                    INSERT INTO store_service_types (code, name) VALUES (?, ?)
                    ON CONFLICT (code) DO NOTHING
                    """, code.name(), code.name());
            jdbcTemplate.update("""
                    INSERT INTO store_services (store_id, service_type_id)
                    SELECT ?, service_type_id FROM store_service_types WHERE code = ?
                    """, storeId, code.name());
        }
    }
}
