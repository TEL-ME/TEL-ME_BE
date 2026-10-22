package com.telme.store.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.KakaoLegRouteResponse;
import com.telme.store.dto.res.KakaoNaviDirectionsResponse;
import com.telme.store.dto.res.KakaoTransitRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoreRouteConverterTest {

    private final StoreRouteConverter converter = new StoreRouteConverter();

    @Nested
    @DisplayName("자동차 경로 변환")
    class CarRouteTest {

        @Test
        @DisplayName("sections가 null이거나 비어있으면 503 예외를 던진다")
        void sections_누락_또는_빈배열() {
            var routeWithNullSections = new KakaoNaviDirectionsResponse.Route(0, "OK",
                    new KakaoNaviDirectionsResponse.Summary(1000, 600), null);
            var routeWithEmptySections = new KakaoNaviDirectionsResponse.Route(0, "OK",
                    new KakaoNaviDirectionsResponse.Summary(1000, 600), List.of());

            assertThatThrownBy(() -> converter.toCarResponse(routeWithNullSections))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);

            assertThatThrownBy(() -> converter.toCarResponse(routeWithEmptySections))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }

        @Test
        @DisplayName("roads가 비어있거나 좌표가 전혀 없으면 503 예외를 던진다")
        void roads_빈배열_또는_좌표없음() {
            var sectionWithEmptyRoads = new KakaoNaviDirectionsResponse.Section(List.of());
            var routeEmptyRoads = new KakaoNaviDirectionsResponse.Route(0, "OK",
                    new KakaoNaviDirectionsResponse.Summary(1000, 600), List.of(sectionWithEmptyRoads));

            var sectionWithNoVertexes = new KakaoNaviDirectionsResponse.Section(
                    List.of(new KakaoNaviDirectionsResponse.Road(List.of())));
            var routeNoVertexes = new KakaoNaviDirectionsResponse.Route(0, "OK",
                    new KakaoNaviDirectionsResponse.Summary(1000, 600), List.of(sectionWithNoVertexes));

            assertThatThrownBy(() -> converter.toCarResponse(routeEmptyRoads))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);

            assertThatThrownBy(() -> converter.toCarResponse(routeNoVertexes))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }

        @Test
        @DisplayName("정상 경로와 좌표가 주어지면 올바르게 변환한다")
        void 정상_변환() {
            var road = new KakaoNaviDirectionsResponse.Road(List.of(127.0, 37.5, 127.1, 37.6));
            var section = new KakaoNaviDirectionsResponse.Section(List.of(road));
            var route = new KakaoNaviDirectionsResponse.Route(0, "OK",
                    new KakaoNaviDirectionsResponse.Summary(2000, 300), List.of(section));

            StoreRouteResponse response = converter.toCarResponse(route);

            assertThat(response.mode()).isEqualTo(RouteMode.CAR);
            assertThat(response.distanceMeters()).isEqualTo(2000);
            assertThat(response.durationSeconds()).isEqualTo(300);
            assertThat(response.steps()).hasSize(1);
            assertThat(response.steps().get(0).path()).hasSize(2);
            assertThat(response.steps().get(0).path().get(0).latitude()).isEqualTo(37.5);
            assertThat(response.steps().get(0).path().get(0).longitude()).isEqualTo(127.0);
        }
    }

    @Nested
    @DisplayName("대중교통 경로 변환")
    class TransitRouteTest {

        @Test
        @DisplayName("steps가 null이거나 비어있으면 503 예외를 던진다")
        void steps_누락_또는_빈배열() {
            var props = new KakaoTransitRouteResponse.RouteProperties(1000, 600, 0, null);
            var routeNull = new KakaoTransitRouteResponse.Route(props, null);
            var routeEmpty = new KakaoTransitRouteResponse.Route(props, List.of());

            assertThatThrownBy(() -> converter.toTransitResponse(routeNull))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);

            assertThatThrownBy(() -> converter.toTransitResponse(routeEmpty))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }

        @Test
        @DisplayName("환승 횟수가 음수이면 503 예외를 던진다")
        void transfers_음수() {
            var props = new KakaoTransitRouteResponse.RouteProperties(1000, 600, -1, null);
            var stepProps = new KakaoTransitRouteResponse.StepProperties("이동", "BUS", 1000, 600,
                    List.of(new KakaoTransitRouteResponse.Vehicle("76")));
            var step = new KakaoTransitRouteResponse.Step(stepProps,
                    new KakaoTransitRouteResponse.Path(List.of(List.of(127.0, 37.5))));
            var route = new KakaoTransitRouteResponse.Route(props, List.of(step));

            assertThatThrownBy(() -> converter.toTransitResponse(route))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }

        @Test
        @DisplayName("요금이 음수이면 503 예외를 던진다")
        void fare_음수() {
            var props = new KakaoTransitRouteResponse.RouteProperties(1000, 600, 0,
                    new KakaoTransitRouteResponse.Fare(-100));
            var stepProps = new KakaoTransitRouteResponse.StepProperties("이동", "BUS", 1000, 600,
                    List.of(new KakaoTransitRouteResponse.Vehicle("76")));
            var step = new KakaoTransitRouteResponse.Step(stepProps,
                    new KakaoTransitRouteResponse.Path(List.of(List.of(127.0, 37.5))));
            var route = new KakaoTransitRouteResponse.Route(props, List.of(step));

            assertThatThrownBy(() -> converter.toTransitResponse(route))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("도보·자전거 경로 변환")
    class LegRouteTest {

        @Test
        @DisplayName("legs가 null이거나 비어있으면 503 예외를 던진다")
        void legs_누락_또는_빈배열() {
            var props = new KakaoLegRouteResponse.RouteProperties(1000, 600);
            var routeNull = new KakaoLegRouteResponse.Route(props, null);
            var routeEmpty = new KakaoLegRouteResponse.Route(props, List.of());

            assertThatThrownBy(() -> converter.toLegResponse(RouteMode.WALK, routeNull))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);

            assertThatThrownBy(() -> converter.toLegResponse(RouteMode.WALK, routeEmpty))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }

        @Test
        @DisplayName("leg 내부 steps가 null이거나 비어있으면 503 예외를 던진다")
        void leg_steps_누락_또는_빈배열() {
            var props = new KakaoLegRouteResponse.RouteProperties(1000, 600);
            var legNull = new KakaoLegRouteResponse.Leg(null);
            var legEmpty = new KakaoLegRouteResponse.Leg(List.of());

            assertThatThrownBy(() -> converter.toLegResponse(RouteMode.WALK,
                    new KakaoLegRouteResponse.Route(props, List.of(legNull))))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);

            assertThatThrownBy(() -> converter.toLegResponse(RouteMode.WALK,
                    new KakaoLegRouteResponse.Route(props, List.of(legEmpty))))
                    .isInstanceOf(GeneralException.class)
                    .extracting("errorCode").isEqualTo(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
    }
}
