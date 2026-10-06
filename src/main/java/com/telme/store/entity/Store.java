package com.telme.store.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Store {

    public enum Status { OPEN, CLOSED_DOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    // DB 기본값이 채운다. 안 읽어오면 등록 응답에 null이 나간다
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    // DB 기본값과 trg_stores_updated_at 트리거가 채운다. 안 읽어오면 등록·수정 응답에 낡은 값이 나간다
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;
    
    // 영업시간·업무만 바뀌면 stores 행이 그대로라 @Version은 오르지 않는다.
    @Column(name = "lock_version", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Integer lockVersion;

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoreService> services = new ArrayList<>();

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoreHours> hours = new ArrayList<>();
    
    public void update(String name, String address, String phone, String regionCode, BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.regionCode = regionCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // 같은 (store_id, day_of_week) 키로 지우고 다시 넣으면 Hibernate 식별자 충돌이 나서 기존 행의 값만 바꾼다.
    // 빠진 요일이 있으면 그 요일만 새로 만든다
    public void changeHours(short dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
        StoreHours target = hours.stream()
                .filter(h -> h.getId().getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElseGet(() -> {
                    StoreHours added = StoreHours.builder()
                            .id(new StoreHours.Id(storeId, dayOfWeek))
                            .store(this)
                            .build();
                   hours.add(added);
                   return added;
                });
        target.change(openTime, closeTime, closed);
    }
    
    // 요청에 없는 업무만 빼고 새 업무만 더한다. 전부 지우고 다시 넣으면 같은 키 때문에 식별자 충돌이 난다
    public void replaceServices(Collection<StoreServiceType> types) {
        Set<Long> wanted =
                types.stream().map(StoreServiceType::getServiceTypeId).collect(Collectors.toSet());
        services.removeIf(s -> !wanted.contains(s.getServiceType().getServiceTypeId()));
        
        Set<Long> existing = services.stream()
                .map(s -> s.getServiceType().getServiceTypeId())
                .collect(Collectors.toSet());
        types.stream()
                .filter(type -> !existing.contains(type.getServiceTypeId()))
                .forEach(type -> services.add(StoreService.builder()
                        .id(new StoreService.Id(storeId, type.getServiceTypeId()))
                        .store(this)
                        .serviceType(type)
                        .build()));
    }
}
