package com.telme.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "store_service_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class StoreServiceType {

    public enum Code { NEW_LINE, PORT_IN, NAME_CHANGE, USIM_REISSUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_type_id")
    private Long serviceTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private Code code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;
}
