package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.telme.chat.converter.ChatStoreConverter;
import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse.Type;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatCoordinates;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.NamedLocationStoreSearchPort.SearchResult;
import com.telme.consult.service.NamedLocationStoreSearchPort.Status;
import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.dto.res.StoreNearbySearchResponse;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.service.StoreSearchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.res.StoreHoursResponse;
import com.telme.store.service.StoreHoursQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataAccessResourceFailureException;

class ChatStoreAnswerProviderTest {
    final StoreSearchService nearby = mock(StoreSearchService.class);
    final NamedLocationStoreSearchPort named = mock(NamedLocationStoreSearchPort.class);
    final ChatStoreAnswerProvider provider = new ChatStoreAnswerProvider(nearby, named, new ChatStoreConverter());

    AnswerInput input(Map<String, String> conditions, ChatCoordinates coordinates) {
        return new AnswerInput(1, 2, 3, Purpose.NEARBY_STORE, "매장 찾아줘", "매장", conditions, coordinates);
    }

    @Test
    void explicitLocationWithoutGpsKeepsRegionalDistanceNull() {
        var context = new ChatStoreSearchContextResponse(Type.REGION, "서울 강남구", null);
        when(named.search("서울 강남구", Set.of())).thenReturn(new SearchResult(Status.SUCCESS,
                List.of(new ChatStoreResponse(1L, "매장", "주소", "전화",
                        BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null)), context));
        var answer = provider.generate(input(Map.of("location", "서울 강남구"), null)).answer();
        verifyNoInteractions(nearby);
        assertThat(answer.messageType()).isEqualTo(ChatMessage.MessageType.STORE_RESULT);
        assertThat(answer.storeSearchContext()).isEqualTo(context);
        assertThat(answer.storeResults().getFirst()).containsEntry("distanceMeters", null)
                .containsEntry("latitude", BigDecimal.valueOf(37.5));
    }

    @Test
    void gpsWinsOverLocationAndUsesDefaultsAndConvertsServiceType() {
        when(nearby.findNearbyStores(any())).thenReturn(new StoreNearbySearchResponse(
                List.of(new StoreNearbyResponse(2L, "매장", "주소", "전화",
                        BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), 300)), 10000));
        var answer = provider.generate(input(Map.of("location", "서울 강남구", "serviceType", "USIM_REISSUE"),
                new ChatCoordinates(37.4, 127.1))).answer();
        var captor = ArgumentCaptor.forClass(StoreNearbySearchRequest.class);
        verify(nearby).findNearbyStores(captor.capture());
        assertThat(captor.getValue().latitude()).isEqualTo(37.4);
        assertThat(captor.getValue().longitude()).isEqualTo(127.1);
        assertThat(captor.getValue().serviceTypes()).containsExactly(StoreServiceType.Code.USIM_REISSUE);
        assertThat(captor.getValue().radiusMeters()).isNull();
        assertThat(captor.getValue().limit()).isNull();
        assertThat(answer.storeResults().getFirst()).containsEntry("distanceMeters", 300);
        assertThat(answer.storeSearchContext().type()).isEqualTo(Type.CURRENT_LOCATION);
        assertThat(answer.storeSearchContext().radiusMeters()).isEqualTo(10000);
        verifyNoInteractions(named);
    }

    @Test
    void emptySearchSuggestsConditionChangeWithoutImpossibleRadiusExpansion() {
        when(nearby.findNearbyStores(any())).thenReturn(new StoreNearbySearchResponse(List.of(), 10000));
        var answer = provider.generate(input(Map.of("serviceType", "PORT_IN"),
                new ChatCoordinates(37.5, 127))).answer();
        assertThat(answer.messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(answer.content()).contains("10000m", "업무 조건").doesNotContain("반경을 넓");
        assertThat(answer.storeResults()).isNull();
    }

    @Test
    void searchTimeoutAndDatabaseFailureBecomeGuidance() {
        for (RuntimeException exception : List.of(
                new GeneralException(StoreErrorCode.SEARCH_TIMEOUT),
                new org.springframework.dao.DataAccessResourceFailureException("mock"))) {
            doThrow(exception).when(nearby).findNearbyStores(any());
            var answer = provider.generate(input(Map.of(), new ChatCoordinates(37.5, 127))).answer();
            assertThat(answer.messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
            assertThat(answer.content()).contains("잠시 후 다시");
        }
    }

    @Test
    void unresolvedLocationIsDifferentFromNoMatchingStores() {
        when(named.search(eq("중앙동"), any())).thenReturn(
                new SearchResult(Status.LOCATION_NOT_FOUND, List.of(), null));
        var answer = provider.generate(input(Map.of("location", "중앙동"), null)).answer();
        assertThat(answer.content()).contains("위치를 찾지 못", "시·구");
        verifyNoInteractions(nearby);
    }

    @Test
    void invalidServiceTypeDoesNotSilentlyDropFilter() {
        var answer = provider.generate(input(Map.of("serviceType", "UNKNOWN"),
                new ChatCoordinates(37.5, 127))).answer();
        assertThat(answer.content()).contains("업무를 다시");
        verifyNoInteractions(nearby, named);
    }

    @Test
    void missingCoordinatesNeverCallsSearch() {
        assertThat(provider.generate(input(Map.of(), null)).answer().content()).contains("어느 지역");
        verifyNoInteractions(nearby, named);
    }

    @Test
    void unconnectedNamedSearchWithoutGpsReturnsGuidance() {
        when(named.search(eq("강남역"), any())).thenReturn(SearchResult.failed());
        assertThat(provider.generate(input(Map.of("location", "강남역"),
                null)).answer().content()).contains("잠시 후");
        verifyNoInteractions(nearby);
    }

    // 2026-10-21(수) 11:00 한국 시각 = 02:00 UTC
    private ChatStoreAnswerProvider providerAt(String utc, StoreHoursQueryService hours) {
        return new ChatStoreAnswerProvider(nearby, named, new ChatStoreConverter(), hours,
                Clock.fixed(Instant.parse(utc), ZoneOffset.UTC));
    }

    private void givenOneStore() {
        var context = new ChatStoreSearchContextResponse(Type.REGION, "홍대입구역 2호선", null);
        when(named.search("홍대입구역", Set.of())).thenReturn(new SearchResult(Status.SUCCESS,
                List.of(new ChatStoreResponse(7L, "텔미 홍대점", "주소", "전화", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null)),
                context));
    }

    private Map<Long, List<StoreHoursResponse>> weekHours() {
        return Map.of(7L, List.of(
                new StoreHoursResponse("WEDNESDAY", LocalTime.of(10, 0), LocalTime.of(19, 0), false),
                new StoreHoursResponse("SATURDAY", LocalTime.of(10, 0), LocalTime.of(17, 0), false),
                new StoreHoursResponse("SUNDAY", null, null, true)));
    }

    private AnswerInput question(String text) {
        return new AnswerInput(1, 2, 3, Purpose.NEARBY_STORE, text, "매장", Map.of("location", "홍대입구역"), null);
    }

    @Test
    void 요일을_묻는_말에는_그_요일_영업시간을_본문에_붙인다() {
        givenOneStore();
        var hours = mock(StoreHoursQueryService.class);
        when(hours.hoursOf(List.of(7L))).thenReturn(weekHours());

        var answer = providerAt("2026-10-21T02:00:00Z", hours).generate(question("거기 토요일에도 열어?")).answer();

        assertThat(answer.messageType()).isEqualTo(ChatMessage.MessageType.STORE_RESULT);
        assertThat(answer.content()).startsWith("홍대입구역 2호선 기준으로 찾은 매장이에요.")
                .contains("토요일 영업시간이에요.", "- 텔미 홍대점: 10:00~17:00");
        assertThat(answer.storeResults()).hasSize(1);
    }

    @Test
    void 요일이_없으면_오늘_영업시간을_붙이고_휴무는_휴무로_쓴다() {
        givenOneStore();
        var hours = mock(StoreHoursQueryService.class);
        when(hours.hoursOf(List.of(7L))).thenReturn(weekHours());

        var wednesday = providerAt("2026-10-21T02:00:00Z", hours).generate(question("홍대입구역 매장 몇시까지해요?")).answer();
        var sunday = providerAt("2026-10-25T02:00:00Z", hours).generate(question("몇 시까지 해요?")).answer();

        assertThat(wednesday.content()).contains("오늘 영업시간이에요.", "10:00~19:00");
        assertThat(sunday.content()).contains("- 텔미 홍대점: 휴무");
    }

    @Test
    void 주말을_물으면_요일별로_나란히_쓴다() {
        givenOneStore();
        var hours = mock(StoreHoursQueryService.class);
        when(hours.hoursOf(List.of(7L))).thenReturn(weekHours());

        var answer = providerAt("2026-10-21T02:00:00Z", hours).generate(question("주말에도 영업해요?")).answer();

        assertThat(answer.content()).contains("주말 영업시간이에요.", "토 10:00~17:00, 일 휴무");
    }

    @Test
    void 지금_열었는지는_한국_시각으로_비교한다() {
        givenOneStore();
        var hours = mock(StoreHoursQueryService.class);
        when(hours.hoursOf(List.of(7L))).thenReturn(weekHours());

        var open = providerAt("2026-10-21T02:00:00Z", hours).generate(question("지금 열었어요?")).answer();
        var closed = providerAt("2026-10-21T12:00:00Z", hours).generate(question("지금 열었어요?")).answer();

        assertThat(open.content()).contains("지금 영업 여부예요.", "지금 영업 중이에요(19:00까지)");
        assertThat(closed.content()).contains("지금은 영업 시간이 아니에요(오늘 10:00~19:00)");
    }

    @Test
    void 영업을_묻지_않는_말에는_영업시간을_읽지도_붙이지도_않는다() {
        givenOneStore();
        var hours = mock(StoreHoursQueryService.class);

        var answer = providerAt("2026-10-21T02:00:00Z", hours).generate(question("홍대입구역 매장 찾아줘")).answer();

        assertThat(answer.content()).isEqualTo("홍대입구역 2호선 기준으로 찾은 매장이에요.");
        verifyNoInteractions(hours);
    }

    @Test
    void 영업시간을_읽지_못하거나_데이터가_없으면_매장_목록만_안내한다() {
        givenOneStore();
        var failing = mock(StoreHoursQueryService.class);
        when(failing.hoursOf(any())).thenThrow(new DataAccessResourceFailureException("연결 실패"));
        var empty = mock(StoreHoursQueryService.class);
        when(empty.hoursOf(any())).thenReturn(Map.of());

        var failed = providerAt("2026-10-21T02:00:00Z", failing).generate(question("토요일에도 열어?")).answer();
        var noData = providerAt("2026-10-21T02:00:00Z", empty).generate(question("토요일에도 열어?")).answer();

        assertThat(failed.content()).isEqualTo("홍대입구역 2호선 기준으로 찾은 매장이에요.");
        assertThat(failed.storeResults()).hasSize(1);
        assertThat(noData.content()).isEqualTo("홍대입구역 2호선 기준으로 찾은 매장이에요.");
    }

    @Test
    void 영업시간_조회가_없는_구성은_이전처럼_목록만_답한다() {
        givenOneStore();

        var answer = provider.generate(question("토요일에도 열어?")).answer();

        assertThat(answer.content()).isEqualTo("홍대입구역 2호선 기준으로 찾은 매장이에요.");
    }
}
