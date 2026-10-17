package com.telme.store.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StoreTranslationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    
    public record Text(String name, String address) {
    }
    
    // 한국어는 stores의 원문이라 조회하지 않는다. 번역이 없는 매장은 결과에서 빠지고 호출부가 원문을 쓴다
    public Map<Long, Text> findByStoreIds(Locale locale, Collection<Long> storeIds) {
        if (storeIds.isEmpty() || Locale.KOREAN.getLanguage().equals(locale.getLanguage())) {
            return Map.of();
        }
        Map<Long, Text> texts = new HashMap<>();
        jdbc.query("""
                SELECT store_id, name, address FROM store_translations
                WHERE locale = :locale AND store_id IN (:storeIds)
                """, 
                Map.of("locale", locale.getLanguage(), "storeIds", storeIds),
                row -> {
                    texts.put(row.getLong("store_id"), new Text(row.getString("name"), row.getString("address")));
                });
        return texts;
    }
}
