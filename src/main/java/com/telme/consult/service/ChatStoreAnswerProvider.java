package com.telme.consult.service;

import com.telme.chat.converter.ChatStoreConverter;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse.Type;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.NamedLocationStoreSearchPort.SearchResult;
import com.telme.consult.service.NamedLocationStoreSearchPort.Status;
import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.entity.StoreServiceType;
import com.telme.store.service.StoreSearchService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;

@RequiredArgsConstructor
public class ChatStoreAnswerProvider {
    private final StoreSearchService nearbySearch;
    private final NamedLocationStoreSearchPort namedSearch;
    private final ChatStoreConverter converter;
    private final StoreSearchAssessment assessment = new StoreSearchAssessment();

    public GeneratedAnswer generate(AnswerInput input) {
        String location = input.confirmedConditions().get("location");
        Set<StoreServiceType.Code> serviceTypes;
        try {
            String serviceType = input.confirmedConditions().get("serviceType");
            serviceTypes = serviceType == null || serviceType.isBlank()
                    ? Set.of() : Set.of(StoreServiceType.Code.valueOf(serviceType));
        } catch (IllegalArgumentException exception) {
            return guidance("취급 업무를 확인하기 어려워요. 원하는 업무를 다시 알려주세요.", null);
        }
        SearchResult result;
        // 예외 처리는 조회 경계에만 둔다. 답변 변환·DB 저장 실패를 검색 안내로 숨기지 않는다.
        try {
            // location은 구체적인 지역·장소를 전제로 한다. "내 근처" 등 현재 위치 표현은 라우터에서 구분해야 한다.
            // 현재 문자열 계약으로는 이전 지역 유지와 현재 위치로 변경하는 의도를 구분할 수 없어 AI 파트와 협의가 필요하다.
            if (location != null && !location.isBlank()) {
                result = namedSearch.search(location, serviceTypes);
            } else if (input.coordinates() != null) {
                var response = nearbySearch.findNearbyStores(StoreNearbySearchRequest.builder()
                        .latitude(input.coordinates().latitude()).longitude(input.coordinates().longitude())
                        .serviceTypes(serviceTypes).build());
                result = new SearchResult(Status.SUCCESS,
                        response.stores().stream().map(converter::fromNearby).toList(),
                        new ChatStoreSearchContextResponse(Type.CURRENT_LOCATION, "현재 위치",
                                response.radiusMeters()));
            } else {
                return guidance("어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.", null);
            }
        } catch (GeneralException | DataAccessException exception) {
            result = SearchResult.failed();
        }
        if (result.status() == Status.LOCATION_NOT_FOUND) {
            return guidance("말씀하신 위치를 찾지 못했어요. 시·구를 포함한 지역명이나 주소를 알려주세요.",
                    result.context());
        }
        var nextStep = assessment.assess(new StoreSearchAssessment.SearchOutcome(
                result.status() == Status.SUCCESS
                        ? StoreSearchAssessment.SearchStatus.SUCCESS : StoreSearchAssessment.SearchStatus.FAILED,
                result.stores().size()));
        return switch (nextStep) {
            case USE_RESULTS -> GeneratedAnswer.withoutSources(new ChatAnswer(
                    ChatMessage.MessageType.STORE_RESULT,
                    result.context().label() + " 기준으로 찾은 매장이에요.",
                    null, List.of(), converter.toSnapshots(result.stores()), result.context()));
            case OFFER_CONDITION_CHANGE -> guidance(emptyMessage(result.context(), !serviceTypes.isEmpty()),
                    result.context());
            case SEARCH_FAILURE -> guidance(
                    "현재 가까운 매장 정보를 바로 확인하기 어려워요. 잠시 후 다시 시도하거나 고객센터를 이용해 주세요.",
                    result.context());
            case SEARCH_REQUIRED -> throw new IllegalStateException("매장 검색이 실행되지 않았습니다.");
        };
    }

    private String emptyMessage(ChatStoreSearchContextResponse context, boolean filtered) {
        String scope = context.radiusMeters() == null ? context.label()
                : context.label() + " 기준 반경 " + context.radiusMeters() + "m";
        return scope + "에서 조건에 맞는 매장을 찾지 못했어요. "
                + (filtered ? "업무 조건을 해제하거나 다른 지역을 알려주세요." : "다른 지역을 알려주세요.");
    }

    private GeneratedAnswer guidance(String content, ChatStoreSearchContextResponse context) {
        return GeneratedAnswer.withoutSources(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, content, ChatMessage.AnswerBasis.NO_EVIDENCE,
                List.of(), null, context));
    }
}
