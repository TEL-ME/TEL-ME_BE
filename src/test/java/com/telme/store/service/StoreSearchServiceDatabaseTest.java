package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.entity.StoreServiceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 시드 매장(dev-migration)과 섞이지 않도록 매장이 없는 서해 해상 좌표를 기준점으로 쓴다.
// 필터 없는 검색은 반경 없이 가장 가까운 매장을 찾으므로, 멀리 있는 시드 매장이 섞이지 않게 요청 개수를 테스트 매장 수에 맞춘다.
// 반경 규칙은 필터 있는 검색에만 적용되므로 반경 테스트는 업무 조건을 걸어 확인한다.
// 각 테스트는 트랜잭션 롤백으로 DB에 흔적을 남기지 않는다
@SpringBootTest
@Transactional
class StoreSearchServiceDatabaseTest {

    private static final double BASE_LATITUDE = 36.0;
    private static final double BASE_LONGITUDE = 124.5;
    // 위도 0.001도 ≈ 111.2m
    private static final double METERS_PER_MILLI_DEGREE = 111.195;
    private static final Set<StoreServiceType.Code> USIM = Set.of(StoreServiceType.Code.USIM_REISSUE);

    @Autowired
    private StoreSearchService storeSearchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("필터 없음: 가까운 순으로 정렬하고 거리를 함께 반환한다")
    void 가까운_순으로_정렬한다() {
        long far = insertStore("먼 매장", BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");
        long near = insertStore("가까운 매장", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long middle = insertStore("중간 매장", BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");

        List<StoreNearbyResponse> result = search(null, 3, null);

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(near, middle, far);
        assertThat(result.get(0).distanceMeters()).isCloseTo((int) Math.round(METERS_PER_MILLI_DEGREE), within(1));
        assertThat(result.get(2).distanceMeters()).isCloseTo((int) Math.round(3 * METERS_PER_MILLI_DEGREE), within(1));
    }

    @Test
    @DisplayName("필터 없음: 거리가 같으면 매장 ID 순으로 정렬해 결과 순서가 매번 같다")
    void 거리가_같으면_ID순() {
        long first = insertStore("같은 위치 1", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long second = insertStore("같은 위치 2", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");

        assertThat(search(null, 2, null)).extracting(StoreNearbyResponse::storeId).containsExactly(first, second);
    }

    @Test
    @DisplayName("필터 없음: 폐업이나 새로 추가된 상태값의 매장은 가장 가까워도 제외한다")
    void OPEN이_아닌_매장은_제외한다() {
        // 관리자 파트가 삭제용 상태값을 새로 만들어도 검색에 노출되지 않아야 한다
        insertStore("폐업 매장", BASE_LATITUDE, BASE_LONGITUDE, "CLOSED_DOWN");
        insertStore("삭제 매장", BASE_LATITUDE, BASE_LONGITUDE, "DELETED");
        long open = insertStore("영업 매장", BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");

        assertThat(search(null, 1, null)).extracting(StoreNearbyResponse::storeId).containsExactly(open);
    }

    @Test
    @DisplayName("필터 없음: 반경과 관계없이 가장 가까운 매장을 반환한다")
    void 반경_밖이어도_가장_가까운_매장() {
        // 약 50km 떨어진 매장. 필터 있는 검색이라면 반경 1km 밖이라 빠진다
        long far = insertStore("50km 매장", BASE_LATITUDE + 0.45, BASE_LONGITUDE, "OPEN");

        List<StoreNearbyResponse> result = search(1000, 1, null);

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(far);
        assertThat(result.get(0).distanceMeters()).isGreaterThan(49_000);
    }

    @Test
    @DisplayName("필터 없음: 요청한 개수만큼만 반환한다")
    void 개수만큼만_반환한다() {
        long first = insertStore("1번", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");
        long second = insertStore("2번", BASE_LATITUDE + 0.002, BASE_LONGITUDE, "OPEN");
        insertStore("3번", BASE_LATITUDE + 0.003, BASE_LONGITUDE, "OPEN");

        assertThat(search(null, 2, null)).extracting(StoreNearbyResponse::storeId).containsExactly(first, second);
    }

    @Test
    @DisplayName("필터 없음: 기준점과 같은 좌표의 매장은 거리 0으로 반환한다")
    void 같은_좌표는_거리_0() {
        long same = insertStore("같은 좌표", BASE_LATITUDE, BASE_LONGITUDE, "OPEN");

        List<StoreNearbyResponse> result = search(null, 1, null);

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(same);
        assertThat(result.get(0).distanceMeters()).isZero();
    }

    @Test
    @DisplayName("매장 정보(이름·주소·전화·좌표)를 그대로 담아 반환한다")
    void 매장_정보를_담아_반환한다() {
        long storeId = insertStore("정보 확인 매장", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");

        StoreNearbyResponse response = search(null, 1, null).get(0);

        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.name()).isEqualTo("정보 확인 매장");
        assertThat(response.address()).isEqualTo("테스트 주소");
        assertThat(response.phone()).isEqualTo("070-0000-0000");
        assertThat(response.latitude()).isEqualByComparingTo(BigDecimal.valueOf(BASE_LATITUDE + 0.001));
        assertThat(response.longitude()).isEqualByComparingTo(BigDecimal.valueOf(BASE_LONGITUDE));
    }

    @Test
    @DisplayName("필터 있음: 업무 종류를 고르면 그 업무가 되는 매장만 반환한다")
    void 업무_종류로_거른다() {
        long usim = insertUsimStore("유심 매장", BASE_LATITUDE + 0.002, BASE_LONGITUDE);
        insertStore("업무 없는 매장", BASE_LATITUDE + 0.001, BASE_LONGITUDE, "OPEN");

        assertThat(search(1000, 20, USIM)).extracting(StoreNearbyResponse::storeId).containsExactly(usim);
    }

    @Test
    @DisplayName("필터 있음: 대각선 방향으로 반경 경계 근처에 있는 매장도 포함한다")
    void 대각선_경계_매장도_포함한다() {
        // 북동쪽 약 992m (위도 0.0063도 ≈ 700m, 위도 36도에서 경도 0.0078도 ≈ 702m)
        long diagonal = insertUsimStore("대각선 매장", BASE_LATITUDE + 0.0063, BASE_LONGITUDE + 0.0078);

        List<StoreNearbyResponse> result = search(1000, 20, USIM);

        assertThat(result).extracting(StoreNearbyResponse::storeId).containsExactly(diagonal);
        assertThat(result.get(0).distanceMeters()).isBetween(985, 999);
    }

    @Test
    @DisplayName("필터 있음: 반경 밖 매장은 방향과 관계없이 제외한다")
    void 반경_밖은_제외한다() {
        long inside = insertUsimStore("반경 안", BASE_LATITUDE + 0.008, BASE_LONGITUDE);
        insertUsimStore("반경 밖", BASE_LATITUDE + 0.010, BASE_LONGITUDE);
        // 위도 36도에서 경도 0.012도 ≈ 1,080m: 위도 차이로만 보면 안쪽처럼 보여도 실제로는 반경 밖이다
        insertUsimStore("경도 방향 반경 밖", BASE_LATITUDE, BASE_LONGITUDE + 0.012);

        assertThat(search(1000, 20, USIM)).extracting(StoreNearbyResponse::storeId).containsExactly(inside);
    }

    @Test
    @DisplayName("필터 있음: 반경 안에 조건 맞는 매장이 없으면 빈 목록을 반환한다")
    void 결과가_없으면_빈_목록() {
        insertUsimStore("반경 밖 유심 매장", BASE_LATITUDE + 0.05, BASE_LONGITUDE);

        assertThat(search(1000, 20, USIM)).isEmpty();
    }

    private List<StoreNearbyResponse> search(Integer radiusMeters, int limit, Set<StoreServiceType.Code> serviceTypes) {
        return storeSearchService.findNearbyStores(StoreNearbySearchRequest.builder()
                .latitude(BASE_LATITUDE)
                .longitude(BASE_LONGITUDE)
                .radiusMeters(radiusMeters)
                .limit(limit)
                .serviceTypes(serviceTypes)
                .build());
    }

    private long insertUsimStore(String name, double latitude, double longitude) {
        long storeId = insertStore(name, latitude, longitude, "OPEN");
        jdbcTemplate.update("""
                INSERT INTO store_service_types (code, name) VALUES ('USIM_REISSUE', '유심재발급')
                ON CONFLICT (code) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO store_services (store_id, service_type_id)
                SELECT ?, service_type_id FROM store_service_types WHERE code = 'USIM_REISSUE'
                """, storeId);
        return storeId;
    }

    private long insertStore(String name, double latitude, double longitude, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO stores (name, phone, address, latitude, longitude, status)
                VALUES (?, '070-0000-0000', '테스트 주소', ?, ?, ?)
                RETURNING store_id
                """, Long.class, name, latitude, longitude, status);
    }
}
