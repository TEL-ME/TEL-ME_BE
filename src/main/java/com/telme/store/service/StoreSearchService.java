package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreSearchProperties;
import com.telme.store.converter.StoreConverter;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.repository.OpenNowCondition;
import com.telme.store.repository.StoreNearbyQueryRepository;
import com.telme.store.repository.StoreSearchCondition;
import com.telme.store.repository.StoreTag;
import com.telme.store.repository.StoreTagCondition;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSearchService {

    // 매장을 찾는 국내 서비스 지역 밖이면 DB를 조회하지 않고 빈 결과를 돌려준다.
    private static final double SERVICE_AREA_MIN_LATITUDE = 33.0;
    private static final double SERVICE_AREA_MAX_LATITUDE = 38.7;
    private static final double SERVICE_AREA_MIN_LONGITUDE = 124.5;
    private static final double SERVICE_AREA_MAX_LONGITUDE = 132.0;

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");

    // 동적 조건 KNN선행시 limit의 몇배로 찾을지 결정
    private static final int OPEN_NOW_CANDIDATE_MULTIPLIER = 10;

    private final StoreNearbyQueryRepository storeNearbyQueryRepository;
    private final StoreConverter storeConverter;
    private final StoreSearchProperties storeSearchProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StoreNearbyResponse> findNearbyStores(StoreNearbySearchRequest request) {
        StoreNearbyQueryRepository.Query query = new StoreNearbyQueryRepository.Query(
                validateLatitude(request.latitude()),
                validateLongitude(request.longitude()),
                resolveRadius(request.radiusMeters()),
                resolveLimit(request.limit()),
                toConditions(request));
        if (!isInServiceArea(query.latitude(), query.longitude())) {
            return List.of();
        }
        return search(query)
                .stream()
                .map(storeConverter::toNearbyResponse)
                .toList();
    }

    // 필터 없음: 영업 매장 인덱스에서 KNN으로 가장 가까운 곳부터 찾고 반경(기본 10km) 밖은 뺀다.
    // 정적 필터(업무 종류 등 매장 태그)만 있음: (geog, tags) 공간 인덱스에서 KNN 후 반경으로 거른다.
    // 동적 필터(영업 중)가 섞임: 인덱스로 만들 수 없어 searchWithDynamicConditions 로 간다
    private List<StoreNearbyQueryRepository.Row> search(StoreNearbyQueryRepository.Query query) {
        try {
            if (query.conditions().isEmpty()) {
                return storeNearbyQueryRepository.findNearest(query);
            }
            if (query.conditions().stream().allMatch(StoreSearchCondition::backedBySpatialIndex)) {
                return storeNearbyQueryRepository.findNearestMatching(query);
            }
            return searchWithDynamicConditions(query);
        } catch (DataAccessException e) {
            if (isQueryTimeout(e)) {
                throw new GeneralException(StoreErrorCode.SEARCH_TIMEOUT);
            }
            throw e;
        }
    }

    // 1. 가까운 후보(limit의 N배수)부터 확인해 요청 개수를 채우면 끝낸다
    // 2. 못 채우면 반경 안을 모두 확인한다.
    private List<StoreNearbyQueryRepository.Row> searchWithDynamicConditions(StoreNearbyQueryRepository.Query query) {
        List<StoreNearbyQueryRepository.Row> rows = storeNearbyQueryRepository.findNearestCandidatesMatching(
                query, query.limit() * OPEN_NOW_CANDIDATE_MULTIPLIER);
        if (rows.size() == query.limit()) {
            return rows;
        }
        return storeNearbyQueryRepository.findMatchingWithinRadius(query);
    }

    private boolean isQueryTimeout(DataAccessException e) {
        if (e instanceof QueryTimeoutException) {
            return true;
        }
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && "57014".equals(sqlException.getSQLState());
    }

    private boolean isInServiceArea(double latitude, double longitude) {
        return latitude >= SERVICE_AREA_MIN_LATITUDE && latitude <= SERVICE_AREA_MAX_LATITUDE
                && longitude >= SERVICE_AREA_MIN_LONGITUDE && longitude <= SERVICE_AREA_MAX_LONGITUDE;
    }

    // 해외지역은 Exception대신 빈 list 반환
    private double validateLatitude(Double latitude) {
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new GeneralException(StoreErrorCode.INVALID_COORDINATE);
        }
        return latitude;
    }

    private double validateLongitude(Double longitude) {
        if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new GeneralException(StoreErrorCode.INVALID_COORDINATE);
        }
        return longitude;
    }

    private int resolveRadius(Integer radiusMeters) {
        if (radiusMeters == null) {
            return storeSearchProperties.defaultRadiusMeters();
        }
        if (radiusMeters < 1 || radiusMeters > storeSearchProperties.maxRadiusMeters()) {
            throw new GeneralException(StoreErrorCode.INVALID_SEARCH_RADIUS);
        }
        return radiusMeters;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return storeSearchProperties.defaultLimit();
        }
        if (limit < 1 || limit > storeSearchProperties.maxLimit()) {
            throw new GeneralException(StoreErrorCode.INVALID_SEARCH_LIMIT);
        }
        return limit;
    }

    // 정적 조건은 모두 StoreTag로 바꿔 StoreTagCondition 하나로 묶는다. 새 정적 조건은 요청 필드를 StoreTag로
    // 바꿔
    // 같은 조건에 더한다(docs/STORE_SEARCH_OPERATIONS.md 2절)
    private List<StoreSearchCondition> toConditions(StoreNearbySearchRequest request) {
        List<StoreSearchCondition> conditions = new ArrayList<>();
        Set<StoreServiceType.Code> serviceTypes = request.serviceTypes();
        if (serviceTypes != null && !serviceTypes.isEmpty()) {
            // JSON 배열의 null 원소는 Jackson이 막지 않아 여기서 거른다.
            // Set.of()로 만든 불변 Set은 contains(null)에서 예외를 던지므로 스트림으로 검사한다
            if (serviceTypes.stream().anyMatch(Objects::isNull)) {
                throw new GeneralException(StoreErrorCode.INVALID_SERVICE_TYPE);
            }
            conditions.add(StoreTagCondition.of(serviceTypes.stream().map(StoreTag::of).toList()));
        }
        if (Boolean.TRUE.equals(request.openNow())) {
            conditions.add(openNowCondition());
        }
        return conditions;
    }

    // 꺼져 있을 때 조건을 조용히 빼면 영업 종료 매장이 "영업 중" 결과처럼 보이므로 거부한다
    private OpenNowCondition openNowCondition() {
        if (!storeSearchProperties.openNowFilterEnabled()) {
            throw new GeneralException(StoreErrorCode.OPEN_NOW_FILTER_DISABLED);
        }
        return OpenNowCondition.at(LocalDateTime.now(clock.withZone(STORE_ZONE)));
    }
}
