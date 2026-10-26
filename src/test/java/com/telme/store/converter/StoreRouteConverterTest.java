package com.telme.store.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    @DisplayName("대중교통 걷는 단계 안내 문구")
    class TransitGuidanceTest {

        // 2026-10-23 카카오 대중교통 실제 응답(서울숲 → 구리시청 부근)의 한 경로. 좌표는 단계마다 처음·끝만 남겼다
        private static final String KAKAO_TRANSIT_SAMPLE = """
                {"status":"OK","routes":[{"properties":{"totalDistance":17201,"totalTime":3296,"transfers":2,"fare":{"value":1850}},
                "steps":[
                {"properties":{"guidance":"간선 145외 2대 (뚝섬서울숲 > 성동구청)","type":"BUS","distance":2268,"time":392,
                 "vehicles":[{"name":"145"}],"stops":[{"name":"뚝섬서울숲"},{"name":"응봉사거리"},{"name":"무학여고앞"},{"name":"성동구청"}]},
                 "path":{"points":[[127.0366,37.5447],[127.0364,37.564]]}},
                {"properties":{"guidance":"왕십리왕십리역까지 도보로 이동 (12번 출구)","type":"WALKING","distance":510,"time":552,
                 "stops":[{"name":"성동구청"},{"name":"왕십리"}]},
                 "path":{"points":[[127.0364,37.564],[127.0383,37.5621]]}},
                {"properties":{"guidance":"경의중앙선 (왕십리 > 구리)","type":"SUBWAY","distance":11900,"time":1110,
                 "vehicles":[{"name":"경의중앙선"}],"stops":[{"name":"왕십리"},{"name":"청량리"},{"name":"회기"},{"name":"상봉"},{"name":"구리"}]},
                 "path":{"points":[[127.0383,37.5621],[127.1434,37.6033]]}},
                {"properties":{"guidance":"구리역.롯데백화점구리역.롯데백화점정류장까지 도보로 이동 (3번 출구)","type":"WALKING","distance":76,"time":158,
                 "stops":[{"name":"구리"},{"name":"구리역.롯데백화점"}]},
                 "path":{"points":[[127.1433,37.6033],[127.1427,37.603]]}},
                {"properties":{"guidance":"마을 6-1외 1대 (구리역.롯데백화점 > 구리시청.구리아트홀)","type":"BUS","distance":2024,"time":645,
                 "vehicles":[{"name":"6-1"}],"stops":[{"name":"구리역.롯데백화점"},{"name":"구리시청.구리아트홀"}]},
                 "path":{"points":[[127.1427,37.603],[127.1309,37.5949]]}}]}]}
                """;

        @Test
        @DisplayName("카카오 실제 응답에서 걷는 단계 앞에 겹쳐 붙은 도착지 이름을 떼고, 타는 단계 문구는 그대로 둔다")
        void 실제_응답의_겹친_이름을_뗀다() throws Exception {
            KakaoTransitRouteResponse response = new ObjectMapper()
                    .readValue(KAKAO_TRANSIT_SAMPLE, KakaoTransitRouteResponse.class);

            StoreRouteResponse route = converter.toTransitResponse(response.routes().get(0));

            assertThat(route.steps()).extracting(StoreRouteResponse.Step::guidance).containsExactly(
                    "간선 145외 2대 (뚝섬서울숲 > 성동구청)",
                    "왕십리역까지 도보로 이동 (12번 출구)",
                    "경의중앙선 (왕십리 > 구리)",
                    "구리역.롯데백화점정류장까지 도보로 이동 (3번 출구)",
                    "마을 6-1외 1대 (구리역.롯데백화점 > 구리시청.구리아트홀)");
        }

        @Test
        @DisplayName("도착지 이름과 다른 이름이 앞에 붙은 경우와 환승 안내도 정리한다")
        void 다른_이름과_환승도_정리한다() {
            assertThat(walkingGuidance("왕십리(성동구청)왕십리역까지 도보로 이동 (4번 출구)", "성동구청", "왕십리(성동구청)"))
                    .isEqualTo("왕십리역까지 도보로 이동 (4번 출구)");
            assertThat(walkingGuidance("경동시장앞경동시장앞정류장까지 도보로 이동", "경동시장앞", "경동시장앞"))
                    .isEqualTo("경동시장앞정류장까지 도보로 이동");
            assertThat(walkingGuidance("왕십리왕십리역 환승", "왕십리", "왕십리"))
                    .isEqualTo("왕십리역 환승");
        }

        @Test
        @DisplayName("도착지 이름으로 시작해도 이름이 겹치지 않은 문구는 그대로 둔다")
        void 겹치지_않은_문구는_그대로_둔다() {
            assertThat(walkingGuidance("판교역 1번 출구까지 역사 내 이동", "판교역1번출구", "판교"))
                    .isEqualTo("판교역 1번 출구까지 역사 내 이동");
            assertThat(walkingGuidance("서울숲역까지 도보로 이동 (2번 출구)", "서울숲역2번출구", "서울숲"))
                    .isEqualTo("서울숲역까지 도보로 이동 (2번 출구)");
        }

        @Test
        @DisplayName("정류장 정보가 없으면 문구를 그대로 둔다")
        void 정류장_정보가_없으면_그대로_둔다() {
            var stepProps = new KakaoTransitRouteResponse.StepProperties("출발지에서 걷기", "WALKING", 80, 60, null);

            assertThat(transitGuidance(stepProps)).isEqualTo("출발지에서 걷기");
        }

        private String walkingGuidance(String guidance, String from, String to) {
            return transitGuidance(new KakaoTransitRouteResponse.StepProperties(guidance, "WALKING", 100, 60, null,
                    List.of(new KakaoTransitRouteResponse.Stop(from), new KakaoTransitRouteResponse.Stop(to))));
        }

        private String transitGuidance(KakaoTransitRouteResponse.StepProperties stepProps) {
            var step = new KakaoTransitRouteResponse.Step(stepProps,
                    new KakaoTransitRouteResponse.Path(List.of(List.of(127.0, 37.5), List.of(127.001, 37.501))));
            var route = new KakaoTransitRouteResponse.Route(
                    new KakaoTransitRouteResponse.RouteProperties(100, 60, 0, null), List.of(step));
            return converter.toTransitResponse(route).steps().get(0).guidance();
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
