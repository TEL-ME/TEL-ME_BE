package com.telme.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.dashboard.converter.AdminIntentDistributionConverter;
import com.telme.dashboard.dto.req.AdminIntentDistributionSearchRequest;
import com.telme.dashboard.exception.DashboardErrorCode;
import com.telme.dashboard.service.AdminIntentDistributionQueryService;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.intent.repository.RoutingCount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminIntentDistributionQueryServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-08T00:00:00Z");

    @Mock
    private QueryRoutingRepository repository;

    private AdminIntentDistributionQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminIntentDistributionQueryService(repository, new AdminIntentDistributionConverter());
    }

    @Test
    void 의도와_방식은_같은_분모로_세고_복합_질문은_한_건이다() {
        when(repository.countDistribution(FROM, TO)).thenReturn(List.of(
                new RoutingCount(Intent.FAQ, Method.LLM, 2),
                new RoutingCount(Intent.FAQ, Method.RULE, 1),
                new RoutingCount(Intent.STORE, Method.LLM, 1),
                new RoutingCount(Intent.BOTH, Method.RULE, 1),
                new RoutingCount(Intent.UNKNOWN, null, 1)));

        var result = service.getDistribution(new AdminIntentDistributionSearchRequest(FROM, TO));

        assertThat(result.totalCount()).isEqualTo(6);
        assertThat(result.intents()).extracting(share -> share.count()).containsExactly(3L, 1L, 1L, 1L);
        assertThat(result.intents()).extracting(share -> share.percentage().toPlainString())
                .containsExactly("50.00", "16.67", "16.67", "16.67");
        assertThat(result.methods()).extracting(share -> share.count()).containsExactly(2L, 3L, 1L);
        assertThat(result.methods()).extracting(share -> share.percentage().toPlainString())
                .containsExactly("33.33", "50.00", "16.67");
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(TO);
        verify(repository).countDistribution(FROM, TO);
    }

    @Test
    void 없는_유형도_0으로_반환한다() {
        when(repository.countDistribution(FROM, TO))
                .thenReturn(List.of(new RoutingCount(Intent.STORE, Method.LLM, 1)));

        var result = service.getDistribution(new AdminIntentDistributionSearchRequest(FROM, TO));

        assertThat(result.intents()).extracting(share -> share.count()).containsExactly(0L, 1L, 0L, 0L);
        assertThat(result.methods()).extracting(share -> share.count()).containsExactly(0L, 1L, 0L);
        assertThat(result.intents().get(1).percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void 전체_0건이면_모든_비율은_0이다() {
        when(repository.countDistribution(FROM, TO)).thenReturn(List.of());

        var result = service.getDistribution(new AdminIntentDistributionSearchRequest(FROM, TO));

        assertThat(result.totalCount()).isZero();
        assertThat(result.intents()).hasSize(4).allSatisfy(share -> {
            assertThat(share.count()).isZero();
            assertThat(share.percentage()).isEqualByComparingTo("0.00");
        });
        assertThat(result.methods()).hasSize(3).allSatisfy(share -> {
            assertThat(share.count()).isZero();
            assertThat(share.percentage()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void 기간_생략은_전체_조회다() {
        when(repository.countDistributionAll()).thenReturn(List.of());

        var result = service.getDistribution(new AdminIntentDistributionSearchRequest(null, null));

        assertThat(result.from()).isNull();
        assertThat(result.to()).isNull();
        verify(repository).countDistributionAll();
    }

    @Test
    void 기간_한쪽만_지정하면_반대쪽은_전체_경계다() {
        Instant max = Instant.parse("9999-12-31T23:59:59Z");
        when(repository.countDistribution(FROM, max)).thenReturn(List.of());
        when(repository.countDistribution(Instant.EPOCH, TO)).thenReturn(List.of());

        var result1 = service.getDistribution(new AdminIntentDistributionSearchRequest(FROM, null));
        var result2 = service.getDistribution(new AdminIntentDistributionSearchRequest(null, TO));

        assertThat(result1.to()).isNull();
        assertThat(result2.from()).isNull();
        verify(repository).countDistribution(FROM, max);
        verify(repository).countDistribution(Instant.EPOCH, TO);
    }

    @Test
    void 역전된_기간은_DB_조회_전에_거부한다() {
        assertThatThrownBy(() -> service.getDistribution(new AdminIntentDistributionSearchRequest(TO, FROM)))
                .isInstanceOf(GeneralException.class)
                .hasFieldOrPropertyWithValue("errorCode", DashboardErrorCode.INVALID_PERIOD);
        verifyNoInteractions(repository);
    }
}
