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
}
