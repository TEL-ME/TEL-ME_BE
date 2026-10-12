package com.telme.consult.config;

import com.telme.chat.converter.ChatStoreConverter;
import com.telme.consult.service.ChatStoreAnswerProvider;
import com.telme.consult.service.NamedLocationStoreSearchPort;
import com.telme.store.service.StoreSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
public class ConsultStoreSearchConfiguration {
    // 명시 지역 구현이 연결되기 전에도 GPS로 대체 검색하지 않고 실패 안내로 끝낸다.
    @Bean
    @ConditionalOnMissingBean(NamedLocationStoreSearchPort.class)
    NamedLocationStoreSearchPort unavailableNamedLocationSearch() {
        return (location, serviceTypes) -> NamedLocationStoreSearchPort.SearchResult.failed();
    }

    @Bean
    ChatStoreAnswerProvider chatStoreAnswerProvider(
            StoreSearchService nearbySearch, NamedLocationStoreSearchPort namedSearch, ChatStoreConverter converter) {
        return new ChatStoreAnswerProvider(nearbySearch, namedSearch, converter);
    }
}
