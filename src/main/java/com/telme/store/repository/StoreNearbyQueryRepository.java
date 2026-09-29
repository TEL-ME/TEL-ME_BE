package com.telme.store.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import com.telme.store.config.StoreSearchProperties;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// 사용자 최근접 검색 전용 조회.
@Repository
public class StoreNearbyQueryRepository {

    // 검증을 마친 검색 조건
    public record Query(double latitude, double longitude, int radiusMeters, int limit,
            List<StoreSearchCondition> conditions) {

        public Query {
            conditions = List.copyOf(conditions);
        }
    }

    public record Row(long storeId, String name, String phone, String address,
            BigDecimal latitude, BigDecimal longitude, double distanceMeters) {
    }

    private static final String POINT = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography";

    private static final String COLUMNS = "store_id, name, phone, address, latitude, longitude, distance_meters";

    private final NamedParameterJdbcTemplate jdbc;

    // 매장 검색에만 쿼리 타임아웃을 건다.
    public StoreNearbyQueryRepository(DataSource dataSource, StoreSearchProperties storeSearchProperties) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout((int) storeSearchProperties.queryTimeout().toSeconds());
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    // 필터가 없을 때. 반경 없이 KNN으로 가까운 순서대로 꺼내다 limit개를 채우면 멈춘다.
    public List<Row> findNearest(Query query) {
        if (!query.conditions().isEmpty()) {
            throw new IllegalArgumentException("조건이 있는 검색은 findNearest로 처리하지 않습니다.");
        }
        return jdbc.query(nearestSql("", false), baseParameters(query), this::mapRow);
    }

    // 정적 조건(매장 태그)만 있을 때.
    // nearestSql로 꺼낸 곳들의 거리를 구해 제한반경 안에있는지 확정
    public List<Row> findNearestMatching(Query query) {
        if (query.conditions().isEmpty()) {
            throw new IllegalArgumentException("조건 없는 검색은 findNearest로 처리합니다.");
        }
        return jdbc.query(nearestSql(conditionSql(query.conditions()), true), baseParameters(query), this::mapRow);
    }

    // (geog, tags) 공간 인덱스에서 태그 조건을 인덱스 안에서 거르며 KNN으로 가까운 limit곳만 꺼낸다.
    private String nearestSql(String conditionSql, boolean withinRadius) {
        return """
                WITH knn AS (
                    SELECT s.geog <-> %1$s AS distance_meters
                    FROM stores s
                    WHERE s.status = 'OPEN'
                """.formatted(POINT) + conditionSql + """
                    ORDER BY s.geog <-> %1$s
                    LIMIT :limit
                )
                SELECT %2$s FROM (
                    SELECT s.store_id, s.name, s.phone, s.address, s.latitude, s.longitude,
                           s.geog <-> %1$s AS distance_meters
                    FROM stores s
                    WHERE s.status = 'OPEN'
                      AND ST_DWithin(s.geog, %1$s, (SELECT max(distance_meters) FROM knn) + 0.01, false)
                """.formatted(POINT, COLUMNS) + conditionSql + """
                ) d
                %s
                ORDER BY d.distance_meters, d.store_id
                LIMIT :limit
                """.formatted(withinRadius ? "WHERE d.distance_meters <= :radiusMeters" : "");
    }

    // 공간 인덱스가 없는 조건(영업 중)이 섞였을 때 먼저 쓰는 경로.
    // 인덱스가 있는 조건만으로 가까운 후보를 KNN으로 꺼내며 나머지 조건을 확인하고, limit곳을 채우면 바로 멈춘다.
    public List<Row> findNearestCandidatesMatching(Query query, int candidateLimit) {
        List<StoreSearchCondition> indexed = query.conditions().stream()
                .filter(StoreSearchCondition::backedBySpatialIndex)
                .toList();
        List<StoreSearchCondition> checked = query.conditions().stream()
                .filter(condition -> !condition.backedBySpatialIndex())
                .toList();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("공간 인덱스로 처리할 수 있는 조건은 findNearestMatching으로 처리합니다.");
        }
        if (candidateLimit < query.limit()) {
            throw new IllegalArgumentException("후보 수는 요청 개수보다 적을 수 없습니다.");
        }
        String sql = """
                SELECT %2$s FROM (
                    SELECT s.store_id, s.name, s.phone, s.address, s.latitude, s.longitude,
                           s.geog <-> %1$s AS distance_meters
                    FROM stores s
                    WHERE s.status = 'OPEN'
                """.formatted(POINT, COLUMNS) + conditionSql(indexed) + """
                    ORDER BY s.geog <-> %s
                    LIMIT :candidateLimit
                ) s
                WHERE s.distance_meters <= :radiusMeters
                """.formatted(POINT) + conditionSql(checked) + """
                ORDER BY s.distance_meters, s.store_id
                LIMIT :limit
                """;
        MapSqlParameterSource params = baseParameters(query).addValue("candidateLimit", candidateLimit);
        return jdbc.query(sql, params, this::mapRow);
    }

    // 공간 인덱스가 없는 조건(영업 중)이 섞였을 때 findNearestCandidatesMatching를 쓰고도 limit개가 다 차지
    // 않은경우
    // 1. 공간 인덱스로 반경(지도 축척, 최대 10km) 안에서 조건에 맞는 매장을 모두 모아 거리를 구한다.
    // 2. 동적 조건을 반경내 모든 매장 확인
    public List<Row> findMatchingWithinRadius(Query query) {
        String sql = """
                SELECT %2$s FROM (
                    SELECT s.store_id, s.name, s.phone, s.address, s.latitude, s.longitude,
                           s.geog <-> %1$s AS distance_meters
                    FROM stores s
                    WHERE s.status = 'OPEN'
                      AND ST_DWithin(s.geog, %1$s, :radiusMeters, false)
                """.formatted(POINT, COLUMNS) + conditionSql(query.conditions()) + """
                    OFFSET 0
                ) d
                ORDER BY d.distance_meters, d.store_id
                LIMIT :limit
                """;
        return jdbc.query(sql, baseParameters(query), this::mapRow);
    }

    private String conditionSql(List<StoreSearchCondition> conditions) {
        StringBuilder sql = new StringBuilder();
        for (StoreSearchCondition condition : conditions) {
            sql.append("\n  AND ").append(condition.toSql());
        }
        return sql.append('\n').toString();
    }

    private MapSqlParameterSource baseParameters(Query query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("latitude", query.latitude())
                .addValue("longitude", query.longitude())
                .addValue("radiusMeters", query.radiusMeters())
                .addValue("limit", query.limit());
        return conditionParameters(params, query.conditions());
    }

    private MapSqlParameterSource conditionParameters(MapSqlParameterSource params,
            List<StoreSearchCondition> conditions) {
        conditions.forEach(condition -> params.addValues(condition.parameters()));
        return params;
    }

    private Row mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getLong("store_id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getDouble("distance_meters"));
    }
}
